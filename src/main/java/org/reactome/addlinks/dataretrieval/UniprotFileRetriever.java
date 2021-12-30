package org.reactome.addlinks.dataretrieval;

import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import org.apache.http.HttpStatus;
import org.reactome.release.common.dataretrieval.FileRetriever;

public class UniprotFileRetriever extends FileRetriever
{
	// To be used to wait 500 ms to retry if a URL from UniProt returns nothing.
	private static final int RETRY_DELAY_MS = 3000;
	private static final int MAX_NUM_ATTEMPTS = 5;
	private String mapFromDb="";
	private String mapToDb="";
	private BufferedInputStream inStream;
	// A list of paths that were actually downloaded to.
	private static List<String> actualFetchDestinations = Collections.synchronizedList(new ArrayList<String>());

	private final int maxAttemptCount = 5;
	/**
	 * This enum provides a  mapping between Reactome names for reference
	 * databases and the Uniprot ID that is used by their mapping service.
	 * @author sshorser
	 *
	 */
	public enum UniprotDB
	{
		// For a list of database IDs that Uniprot can map from, see: https://www.uniprot.org/help/api_idmapping
		OMIM("MIM_ID"),
		PDB("PDB_ID"),
		RefSeqPeptide("P_REFSEQ_AC"),
		RefSeqRNA("REFSEQ_NT_ID"),
		ENSEMBL("ENSEMBL_ID"),
		ENSEMBLProtein("ENSEMBL_PRO_ID"),
		ENSEMBLGenomes("ENSEMBLGENOME_ID"),
		ENSEMBLTranscript("ENSEMBL_TRS_ID"),
		Ensembl("ENSEMBL_ID"),
		Wormbase("WORMBASE_ID"),
		Entrez_Gene("P_ENTREZGENEID"),
		GeneName("GENENAME"),
		KEGG("KEGG_ID"),
		UniProt("ACC+ID"),
		UCSC("UCSC_ID");

		private String uniprotName;
		private static Map<String,UniprotDB> mapToEnum;

		private UniprotDB(String s)
		{
			this.uniprotName = s;
			updateMap(s);
		}

		private void updateMap(String s)
		{
			if (mapToEnum == null )
			{
				mapToEnum = new HashMap<String,UniprotDB>(10);
			}
			mapToEnum.put(s, this);
		}

		public String getUniprotName()
		{
			return this.uniprotName;
		}

		public static UniprotDB uniprotDBFromUniprotName(String uniprotName)
		{
			return mapToEnum.get(uniprotName);
		}
	}

	public UniprotFileRetriever() { super(); }

	public UniprotFileRetriever(String retrieverName)
	{
		super(retrieverName);
	}

	private String getURIStringFromDataLocation(String location) throws URISyntaxException
	{
		//URIBuilder builder = new URIBuilder();
		if (location == null || location.length() == 0)
		{
			logger.error("Location was NULL/0-length!");
			return "";
		}
		String[] parts = location.split("\\?");
		String[] schemeAndHost = parts[0].split("://");


		String scheme = schemeAndHost[0];
		String host = schemeAndHost[1];

		StringBuilder uriStringBuilder = new StringBuilder();
		if (scheme != null && host != null)
		{
			uriStringBuilder.append(scheme).append("://").append(host);
		}
		else
		{
			logger.warn("schemeAndHost had length < 2: {} ; will use try to host from original URI: {}", schemeAndHost, this.uri.getHost());
			uriStringBuilder.append(this.uri.getScheme()).append("://").append(this.uri.getHost()).append(host);
		}

		if (parts.length>1)
		{
			String parameterString = parts[1];
			uriStringBuilder.append("?");
			uriStringBuilder.append(parameterString);
			// If the Location header string contains query information, we need to properly reformat that before requesting it.
//			String[] params = parts[1].split("&");
//
//			Arrays.stream(params).map(param -> param.split("="))
//			for(String s : params)
//			{
//				String[] nameAndValue = s.split("=");
//				String parameterName = nameAndValue[0];
//				String parameterValue = nameAndValue[1];
//				uriStringBuilder.append(parameterName).append("=").append(parameterValue);
//				//builder.addParameter(nameAndValue[0], nameAndValue[1]);
//			}
		}
		else
		{
			//Add .tab to get table.
			if (!host.endsWith(".tab"))
				uriStringBuilder.append(".tab");
		}
		return uriStringBuilder.toString();
	}

	/**
	 * Attempt to GET data from UniProt.
	 * @param uri - the URI to query from UniProt.
	 * @return A byte array of the result's content.
	 * @throws IOException
	 * @throws URISyntaxException
	 * @throws InterruptedException
	 */
	private byte[] attemptGetFromUniprot(URI uri) throws IOException, URISyntaxException, InterruptedException
	{
		byte[] result = null;
		logger.trace("getting from: {}", uri);

		HttpURLConnection urlConnection = (HttpURLConnection) uri.toURL().openConnection();
		switch (urlConnection.getResponseCode())
		{
			case HttpURLConnection.HTTP_UNAVAILABLE:
			case HttpURLConnection.HTTP_INTERNAL_ERROR:
			case HttpURLConnection.HTTP_BAD_GATEWAY:
			case HttpURLConnection.HTTP_GATEWAY_TIMEOUT:
				logger.error("Error detected! Message: {}", urlConnection.getResponseMessage());
				break;

			case HttpURLConnection.HTTP_OK:
			case HttpURLConnection.HTTP_MOVED_PERM:
			case HttpURLConnection.HTTP_MOVED_TEMP:
				logger.trace("HTTP Status: {}", urlConnection.getResponseMessage());
				result = getContent(urlConnection).getBytes();
				if (result == null)
				{
					logger.warn("Response did not contain data.");
				}
				break;

			default:
				logger.warn("Nothing was downloaded due to an unexpected status code and message: {} ",
					urlConnection.getResponseMessage());
				break;
		}

		return result;
	}

	/**
	 * Attempt to POST to UniProt. If successful, the URL to the *actual* data will be returned.
	 * @param baos - ByteArrayOutputStream of content to post
	 * @return The URL to find the mapped data at, as a string.
	 * @throws IOException
	 * @throws InterruptedException
	 */
	private String attemptPostToUniprot(ByteArrayOutputStream baos) throws IOException, InterruptedException
	{
		boolean done = false;
		int attemptCount = 0;
		String mappingLocationURI = null;
		while(!done)
		{

			//HttpPost post = new HttpPost(this.uri);
			HttpURLConnection urlConnection = (HttpURLConnection) this.uri.toURL().openConnection();
			urlConnection.setDoOutput(true);
			urlConnection.setRequestMethod("POST");
			String formBoundaryString = "---RandomText---"; // Used to delineate post data
			urlConnection.addRequestProperty("Content-Type", "multipart/form-data; boundary= " + formBoundaryString);

			OutputStream outputStream = urlConnection.getOutputStream();
			BufferedWriter requestWriter = new BufferedWriter(new OutputStreamWriter(outputStream));
			requestWriter.write("\n -- " + formBoundaryString + "\n");
			requestWriter.write("Content-Disposition: form-data");
			requestWriter.write("format=tab;");
			requestWriter.write("from=" + this.mapFromDb + ";");
			requestWriter.write("to=" + this.mapToDb + ";");
			requestWriter.write("\nContent-Type: text/plain\n\n");
			requestWriter.flush();

			outputStream.write(baos.toByteArray(), 0, baos.toByteArray().length);
			outputStream.flush();

			requestWriter.write("\n-- " + formBoundaryString + "--\n");
			requestWriter.flush();;

			outputStream.close();
			requestWriter.close();

			{
				switch (urlConnection.getResponseCode())
				{
					case HttpURLConnection.HTTP_UNAVAILABLE:
					case HttpURLConnection.HTTP_INTERNAL_ERROR:
					case HttpURLConnection.HTTP_BAD_GATEWAY:
					case HttpURLConnection.HTTP_GATEWAY_TIMEOUT:
						logger.error("Error detected! Message: {}",
							urlConnection.getResponseMessage());
						break;

					case HttpStatus.SC_OK:
					case HttpStatus.SC_MOVED_PERMANENTLY:
					case HttpStatus.SC_MOVED_TEMPORARILY:
						if (urlConnection.getHeaderField("Location") != null)
						{
							mappingLocationURI = urlConnection.getHeaderFields().get("Location").get(0);
							logger.trace("Location of data: {}", mappingLocationURI);
							if (mappingLocationURI != null && !mappingLocationURI.equals("http://www.uniprot.org/502.htm"))
							{
								done = true;
							}
							else
							{
								logger.warn("Response did not contain data.");
							}
						}
						else
						{
							logger.warn("Status was {}, \"Location\" header was not present. Other headers are: {}",
								urlConnection.getResponseMessage(),
								urlConnection.getHeaderFields().entrySet().stream()
									.map(h -> h.getKey() + ":" + h.getValue()).collect(Collectors.joining(" ; ")));
						}
						break;
					default:
						logger.warn("Nothing was downloaded due to an unexpected status response: {}",
							urlConnection.getResponseMessage());
						break;
				}
				attemptCount++;

			}
			if (attemptCount > this.maxAttemptCount)
			{
				logger.error("Reached max attempt count! No more attempts.");
				done = true;
			}
			else
			{
				if (attemptCount < this.maxAttemptCount && ! done)
				{
					logger.info("Re-trying... {} attempts made, {} allowed", attemptCount, this.maxAttemptCount);
					Thread.sleep(Duration.ofSeconds(5).toMillis());
				}
			}

		}
		return mappingLocationURI;
	}

	/**
	 * Getting data from UniProt is a 3-stage process:
	 * 1) POST a list of identifiers to UniProt. The response received contains a URL to the mapped data.
	 * 2) GET the data from the URL in the response from 1).
	 * 3) GET the "not" mapped data from the URL in the response from 1).
	 */
	@Override
	public void downloadData()
	{
		// Check inputs:
		if (this.inStream == null)
		{
			throw new RuntimeException("inStream is null! You must provide an data input stream!");
		}
		else if (this.mapFromDb.trim().length() == 0)
		{
			throw new RuntimeException("You must provide a database name to map from!");
		}
		else if(this.mapToDb.trim().length() == 0)
		{
			throw new RuntimeException("You must provide a database name to map to!");
		}


		try
		{
			String location = getDataLocation();
			if (location != null)
			{
				// Get values that Uniprot was able to map.
				URI mappedUri = createURI(location, true);
				getUniprotValues(mappedUri);
				// Now get the unmapped values.
				URI unmappedUri = createURI(location, false);
				getUniprotValues(unmappedUri);
			}
			else
			{
				logger.error("We could not determine the location of the data, file was not downloaded.");
			}
		}
		catch (URISyntaxException | InterruptedException e)
		{
			logger.error("A problem occurred while trying to get the data location, or the data: {}", e.getMessage());
			e.printStackTrace();
		}
		catch (IOException e)
		{
			// some of the exceptions in the first catch-block are also covered by IOException. They are in the
			// first catch block so we can treat them differently, if necessary.
			logger.error("IOException was caught: {}", e.getMessage());
			e.printStackTrace();
		}
		catch (Exception e)
		{
			logger.error("Exception occurred: {}", e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * This function gets the location of the Uniprot-mapped data.
	 * @return The location of the data, as a string.
	 * @throws IOException
	 * @throws InterruptedException
	 */
	private String getDataLocation() throws IOException, InterruptedException
	{
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		byte[] buffer = new byte[2048];
		int len;
		while ((len = inStream.read(buffer)) > -1)
		{
			baos.write(buffer,0,len);
		}
		String location = null;
		int attemptCount = 0;
		while (location == null && attemptCount < maxAttemptCount)
		{
			InputStream fileData = new ByteArrayInputStream(baos.toByteArray());
			logger.trace("URI: {}", this.uri.toURL());

//			HttpEntity attachment = MultipartEntityBuilder.create()
//					.addBinaryBody("file", fileData, ContentType.TEXT_PLAIN, "uniprot_ids.txt")
//					.addPart("format", new StringBody("tab", ContentType.MULTIPART_FORM_DATA))
//					.addPart("from", new StringBody(this.mapFromDb, ContentType.MULTIPART_FORM_DATA))
//					.addPart("to", new StringBody(this.mapToDb, ContentType.MULTIPART_FORM_DATA))
//					.build();
//			post.setEntity(attachment);

			try
			{
				location = this.attemptPostToUniprot(baos);
			}
			catch (Exception e)
			{
				// If we don't catch this here, but let it go to "catch (IOException e)" in the outer try-block,
				// then we won't be able to retry. Catching it here lets us continue processing: increment the attempt counter, and loop through again.
				logger.error("No HTTP Response! Message: {}", e.getMessage());
				e.printStackTrace();
			}
			attemptCount++;
			if (location == null)
			{
				if (attemptCount < maxAttemptCount)
				{
					Random r = new Random(System.nanoTime());
					long delay = (long) (3000 + (attemptCount * r.nextFloat()));
					logger.warn("Attempt {} out of {}, next attempt in {} ms", attemptCount, maxAttemptCount, delay);
					Thread.sleep( delay );
				}
				else if (attemptCount >= maxAttemptCount)
				{
					logger.error("Could not get the Location of the data in {} attempts.", attemptCount);
				}
			}

		}
		return location;
	}

	/**
	 * Get values from Uniprot.
	 * @param uri - the URL that the data will be at. This is returned from the initial query to UniProt.
	 * @throws Exception Thrown if unable to get uniprot content, creating output file path, or writing output
	 */
	private void getUniprotValues(URI uri) throws Exception
	{
		int numAttempts = 0;
		boolean done = false;

		while (!done)
		{
			byte[] result = this.attemptGetFromUniprot(uri);
			numAttempts++;
			Path path = Paths.get(new URI("file://" + this.destination));
			// The loop is "done" if the number of attempts exceeds the max allowed, OR if the result is not null/empty.
			done = (numAttempts >= MAX_NUM_ATTEMPTS) || (result != null && result.length > 0);
			if (result != null && result.length > 0)
			{
				logger.debug(".tab result size: {}", result.length);
				Files.createDirectories(path.getParent());
				//Files.write(path, result);
				BufferedWriter writer = Files.newBufferedWriter(path);
				writer.write(new String(result));
				writer.flush();
				writer.close();
				if (!Files.isReadable(path))
				{
					throw new Exception("The new file "+ path +" is not readable!");
				}
				UniprotFileRetriever.actualFetchDestinations.add(path.toString());
			}
			else
			{
				handleNullResult(numAttempts, done, path);
			}
		}
	}

	/**
	 * Create the URI to get data from. <code>mapped</code> is used to determine if ".not" will be in the URI, which is used to get
	 * identifiers which were not mapped.
	 * @param location - The URL to the data, for the mapped values. UniProt will return this by default.
	 * @param mapped - Set to true if you want the URI for mapped values. Set to false if you want values that UniProt couldn't map.
	 * <b>NOTE:</b> Setting <code>mapped</code> to false will also modify <code>this.destination</code> to include "notMapped" in the filename.
	 * @return A URI that can be used to get data from UniProt.
	 * @throws URISyntaxException
	 */
	private URI createURI(String location, boolean mapped) throws URISyntaxException
	{
		URI uri;
		if (mapped)
		{
			uri = new URI(this.getURIStringFromDataLocation(location));
		}
		else
		{
			//URIBuilder builder = uriBuilderFromDataLocation(location);
			//uri = builder.setHost(builder.getHost().replace(".tab", ".not")).build();
			uri = new URI(getURIStringFromDataLocation(location).replace(".tab", ".not"));
			String[] filenameParts = this.destination.split("\\.");
			this.destination = this.destination.replace( filenameParts[filenameParts.length - 1] , "notMapped." + filenameParts[filenameParts.length - 1] );
		}
		return uri;
	}

	/**
	 * Handle a NULL result.
	 * @param numAttempts - The number of attempts that have been made (so far), only needed for logging.
	 * @param done - Are we done? If this is true, an exception will be thrown since the maximum number of attempts have been made and the result is still null.
	 * If false, a log message will be printed and this thread will sleep for RETRY_DELAY_MS milliseconds.
	 * @param path - the path to the file that we were *trying* to download. Used for logging purposes.
	 * @throws InterruptedException
	 * @throws Exception
	 */
	private void handleNullResult(int numAttempts, boolean done, Path path) throws InterruptedException, Exception
	{
		if (!done)
		{
			logger.info("Result was NULL. Will sleep for a bit, and try again. {} attempts remaining, {} total are allowed.", MAX_NUM_ATTEMPTS - numAttempts , MAX_NUM_ATTEMPTS);
			// Sleep a half a second...
			Thread.sleep(RETRY_DELAY_MS);
		}
		else
		{
			throw new Exception("Result for .tab file ("+path.toString()+") was null/empty!");
		}
	}

	public String getMapFromDb()
	{
		return this.mapFromDb;
	}

	public String getMapToDb()
	{
		return this.mapToDb;
	}

	public void setMapFromDbEnum(UniprotDB mapFromDb)
	{
		this.mapFromDb = mapFromDb.getUniprotName();
	}

	public void setMapToDbEnum(UniprotDB mapToDb)
	{
		this.mapToDb = mapToDb.getUniprotName();
	}

	public void setMapFromDb(String mapFromDb)
	{
		this.mapFromDb = mapFromDb;
	}

	public void setMapToDb(String mapToDb)
	{
		this.mapToDb = mapToDb;
	}

	public void setDataInputStream(BufferedInputStream inStream)
	{
		this.inStream = inStream;
	}

	public String getFetchDestination()
	{
		return this.destination;
	}

	public List<String> getActualFetchDestinations()
	{
		return UniprotFileRetriever.actualFetchDestinations;
	}

	private String getContent(HttpURLConnection urlConnection) throws IOException {
		BufferedReader bufferedReader =
			new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
		return bufferedReader.lines().collect(Collectors.joining(System.lineSeparator()));
	}
}
