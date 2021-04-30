package org.reactome.addlinks.dataretrieval;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.gk.persistence.MySQLAdaptor;
import org.reactome.release.common.dataretrieval.FileRetriever;

/**
 * Retrieves data from http://rest.kegg.jp/get/hsa:$kegg_gene_id
 * Then extract the DBLINKS section from the result.
 * @author sshorser
 *
 */
public class KEGGFileRetriever extends FileRetriever
{
	// 1) get UniProt-to-KEGG mappings from UniProt WS.
	// 2) get KEGG entries from those results.
	// 3) foreach KEGG entry: extract DEFINITION field as Reactome Name, extract IDENTIFIER (from ORTHOLOGY) to use as Reactome Identifier
	// (if not available, use the first NAME value from the KEGG Name field, if not available, use the hsa:####).
	// 4) Extract other xrefs from KEGG entry? Ask Robin. Answer: No.
	// 5) KEGG for non-humans? ask Robin. Answer: Yes.

	private static final int sleepIncrSeconds = 5;
	private MySQLAdaptor adapter;

	//private static final Logger logger = LogManager.getLogger();

	// We need to have the lists of uniprot-to-kegg mappings before we attempt to get the KEGG entries.
	// This file will have the KEGG identifiers that we will look up.
	private List<Path> uniprotToKEGGFiles;

	public KEGGFileRetriever()
	{
		super(null);
	}

	public KEGGFileRetriever(String retrieverName)
	{
		super(retrieverName);
	}

	@Override
	protected void downloadData() throws Exception
	{
		Random rand = new Random();
		this.logger.debug("{} Uniprot-to-Kegg mapping files: {}", this.uniprotToKEGGFiles.size(), this.uniprotToKEGGFiles);
		for (Path uniprot2kegg : this.uniprotToKEGGFiles)
		{
			// UniProt-to-KEGG files should be named like this: uniprot_mapping_Uniprot_To_KEGG.48887.2.txt
			// We need to extract the species code from the file name, and then get its name from the ReferenceObject cache.
			String[] parts = uniprot2kegg.getFileName().toString().split("\\.");
			String speciesCode = parts[1];
			this.logger.debug("Species code: {}", speciesCode);
			//ReferenceObjectCache cache = new ReferenceObjectCache(this.adapter);
			//String speciesName = cache.getSpeciesMappings().get(speciesCode).get(0);
			// Get the KEGG species code:
			//String keggSpeciesCode = KEGGSpeciesCache.getKEGGCode(speciesName);

			// The file has two columns: left is UniProt ID, right is KEGG ID.
			// We want all the KEGG IDs, collected into a list.
			List<String> keggIdentifiers = Files.lines(uniprot2kegg)
												.filter(p -> !p.startsWith("From") && !p.trim().equals(""))
												.map( line -> Arrays.asList(line.split("\t")).get(1) )
												.collect(Collectors.toList());
			Path path = Paths.get(new URI("file://" + this.destination));
			// Delete any old files (if they weren't cleaned up before) - they could be incomplete
			// files if a previous KEGG File Retriever was interrupted, so let's just get rid of them
			// and start fresh.
			try
			{
				if (Files.exists(path))
				{
					Files.delete(path);
				}
				Files.createDirectories(path.getParent());
				//Create the file.
				Files.createFile(path);
			}
			catch (IOException e)
			{
				e.printStackTrace();
				throw new Error(e);
			}

			this.logger.debug("Total # of identifiers to lookup: {}", keggIdentifiers.size());
			// Could these API requests be made in parallel? Probably not be a good idea if we're already running multiple KEGGFileRetrievers in parallel.
			// We seem to run into problems when we send too many simultaneous requests to KEGG.
			for (int i = 0; i < keggIdentifiers.size(); i+=10)
			{
				int sleepMillis = 0;
				String identifiersForRequest = "";
				// KEGG only accepts 10 identifiers at a time.
				for (int j = i; j < i+10; j++ )
				{
					if (j < keggIdentifiers.size())
					{
						// You don't need to worry about prefixing with KEGG species code - that comes from the UniProt-to-KEGG mapping.
						identifiersForRequest += (keggIdentifiers.get(j) + "+");
					}
				}
				int attemptCount = 0;
				boolean done = false;
				while(!done)
				{
					URIBuilder builder = new URIBuilder();
					// Append the list of identifiers to the URL string
					builder.setHost(this.uri.getHost())
							.setPath(this.uri.getPath() + identifiersForRequest)
							.setScheme(this.uri.getScheme());
					HttpGet get = new HttpGet(builder.build());
					this.logger.trace("URI: "+get.getURI());

					try (CloseableHttpClient getClient = HttpClients.createDefault();
							CloseableHttpResponse getResponse = getClient.execute(get);)
					{
						attemptCount++;
						switch (getResponse.getStatusLine().getStatusCode())
						{

							case HttpStatus.SC_OK:
								// Write the response to a file. Because we can only do 10 at a time, we need to constantly APPEND to the file.
								// File creation should have been performed earlier, outside the loop.
								String responseEntityString = EntityUtils.toString(getResponse.getEntity());

								Files.write(path,responseEntityString.getBytes(), StandardOpenOption.APPEND);
								if (!Files.isReadable(path))
								{
									throw new Exception("The new file "+ path +" is not readable!");
								}
								done = true;
								break;
							case HttpStatus.SC_NOT_FOUND:
								this.logger.error("\"NOT FOUND\" response was received: {}, URL was: {}", getResponse.getStatusLine().toString(), get.getURI());
								done = true;
								break;
							case HttpStatus.SC_BAD_REQUEST:
								this.logger.error("\"BAD REQUEST\" response was received: {}, URL was: {}", getResponse.getStatusLine().toString(), get.getURI());
								done = true;
								break;
							case HttpStatus.SC_FORBIDDEN:
								this.logger.error("\"FORBIDDEN\" response was received: {}, URL was: {}", getResponse.getStatusLine().toString(), get.getURI());
								// If we get a FORBIDDEN response, we might have some luck if we back off and wait for a little bit.
								if (attemptCount <= this.numRetries)
								{
									done = false;
									// increase the sleep amount by sleepIncrSeconds PLUS some random number of milliseconds (could be *up to* 2 seconds' worth),
									// to ensure that if multiple requests are happening, they don't all go at the exact same moment.
									sleepMillis += ((KEGGFileRetriever.sleepIncrSeconds * 1000) + rand.nextInt(2000));
									this.logger.info("Backing off for {} seconds after {} attempts, then will try again.", Duration.ofMillis(sleepMillis).toString(), attemptCount);
									Thread.sleep(sleepMillis);
								}
								else
								{
									// We've exhausted all attempts and still couldn't get data. Log an error telling the user they may
									// need to retry for this particular file.
									this.logger.warn("Reached max number of attempts ({}), will not try again. Downloaded data might not be complete,"
												+ "you may need to re-run the Download portion of AddLinks just for KEGG, for this file: {}\t "
												+ "Suggested remdiation: (re)move/rename the aforementioned file and then re-run the download process to force repopulation of the file.",
												this.numRetries, path);
									done = true;
								}
								break;
							default:
								this.logger.info("Unexpected response code: {} ; full response message: {}, URL was: {}", getResponse.getStatusLine().getStatusCode(), getResponse.getStatusLine().toString(), get.getURI());
								done = true;
								break;
								// From KEGG response: use DEFINITION as name,
								// For Identifier: use the KEGG name if available, otherwise use the KEGG ID.
								// Actually, maybe ALWAYS include the "hsa:####" as an extra ReferenceEntity name.
						}
					}
					catch (Exception e)
					{
						e.printStackTrace();
					}
				}
			}

		}
	}

	public List<Path> getUniprotToKEGGFiles()
	{
		return this.uniprotToKEGGFiles;
	}

	public void setUniprotToKEGGFiles(List<Path> uniprotToKEGGFiles)
	{
		this.uniprotToKEGGFiles = uniprotToKEGGFiles;
	}

	public MySQLAdaptor getAdapter()
	{
		return this.adapter;
	}

	public void setAdapter(MySQLAdaptor adapter)
	{
		this.adapter = adapter;
	}

	public String getFetchDestination()
	{
		return this.destination;
	}
}
