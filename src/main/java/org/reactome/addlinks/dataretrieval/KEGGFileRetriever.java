package org.reactome.addlinks.dataretrieval;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.persistence.MySQLAdaptor;

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
	
	private MySQLAdaptor adapter;
	
	private static final Logger logger = LogManager.getLogger();

	// We need to have the lists of uniprot-to-kegg mappings before we attempt to get the KEGG entries.
	// This file will have the KEGG identifiers that we will look up.
	private List<Path> uniprotToKEGGFiles;
	
	@Override
	protected void downloadData() throws Exception
	{
		logger.debug("{} Uniprot-to-Kegg mapping files: {}", this.uniprotToKEGGFiles.size(), this.uniprotToKEGGFiles);
		for (Path uniprot2kegg : this.uniprotToKEGGFiles)
		{
			// UniProt-to-KEGG files should be named like this: uniprot_mapping_Uniprot_To_KEGG.48887.2.txt
			// We need to extract the species code from the file name, and then get its name from the ReferenceObject cache.
			String[] parts = uniprot2kegg.getFileName().toString().split("\\.");
			String speciesCode = parts[1];
			logger.debug("Species code: {}", speciesCode);
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

			logger.debug("Total # of identifiers to lookup: {}", keggIdentifiers.size());
			// Could these API requests be made in parallel?
			for (int i = 0; i < keggIdentifiers.size(); i+=10)
			{
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
				
				URIBuilder builder = new URIBuilder();
				// Append the list of identifiers to the URL string
				builder.setHost(this.uri.getHost())
						.setPath(this.uri.getPath() + identifiersForRequest)
						.setScheme(this.uri.getScheme());
				HttpGet get = new HttpGet(builder.build());
				logger.trace("URI: "+get.getURI());
				
				try (CloseableHttpClient getClient = HttpClients.createDefault();
						CloseableHttpResponse getResponse = getClient.execute(get);)
				{
	
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
							break;
						case HttpStatus.SC_NOT_FOUND:
							logger.error("\"NOT FOUND\" response was received: {}", getResponse.getStatusLine().toString());
							break;
						case HttpStatus.SC_BAD_REQUEST:
							logger.error("\"BAD REQUEST\" response was received: {}", getResponse.getStatusLine().toString());
							break;
						default:
							logger.info("Unexpected response code: {} ; full response message: {}", getResponse.getStatusLine().getStatusCode(), getResponse.getStatusLine().toString());
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
