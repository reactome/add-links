package org.reactome.addlinks.dataretrieval;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class UniprotFileRetreiver extends FileRetriever
{
	private static final Logger logger = LogManager.getLogger();

	private String mapFromDb="";
	private String mapToDb="";
	private BufferedInputStream inStream;

	private final int maxAttemptCount = 5;
	/**
	 * This enum provides a  mapping between Reactome names for reference
	 * databases and the Uniprot ID that is used by their mapping service.
	 * @author sshorser
	 *
	 */
	public enum UniprotDB
	{
		OMIM("MIM_ID"),
		PDB("PDB_ID"),
		RefSeqPeptide("P_REFSEQ_AC"),
		RefSeqRNA("REFSEQ_NT_ID"), 
		ENSEMBL("ENSEMBL_ID"),
		ENSEMBLProtein("ENSEMBL_PRO_ID"),
		ENSEMBLGene("ENSEMBLGENOME_ID"),
		ENSEMBLTranscript("ENSEMBL_TRS_ID"),
		Ensembl("ENSEMBL_ID"), 
		Wormbase("WORMBASE_ID"), 
		Entrez_Gene("P_ENTREZGENEID"),
		GeneName("GENENAME"),
		KEGG("KEGG_ID"),
		UniProt("ACC+ID");
		
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
//		
		public String getUniprotName()
		{
			return this.uniprotName;
		}
		
		public static UniprotDB uniprotDBFromUniprotName(String uniprotName)
		{
			return mapToEnum.get(uniprotName);
		}
	}
	
	private URIBuilder uriBuilderFromDataLocation(String location) throws URISyntaxException
	{
		URIBuilder builder = new URIBuilder();
		if (location == null || location.length() == 0)
		{
			logger.error("Location was NULL/0-length!");
			return null;
		}
		String[] parts = location.split("\\?");
		String[] schemeAndHost = parts[0].split("://");
		builder.setScheme(schemeAndHost[0]);
		if (schemeAndHost.length > 1)
		{
			builder.setHost(schemeAndHost[1]);
		}
		else
		{
			logger.debug("schemeAndHost had length < 2: {} ; will use try to host from original URI: {}", schemeAndHost, this.uri.getHost());
			builder.setHost(this.uri.getHost());
		}
		
		if (parts.length>1)
		{
			// If the Location header string contains query information, we need to properly reformat that before requesting it. 
			String[] params = parts[1].split("&");
			for(String s : params)
			{
				String[] nameAndValue = s.split("=");
				builder.addParameter(nameAndValue[0], nameAndValue[1]);
			}
		}
		else
		{
			//Add .tab to get table.
			if (!builder.getHost().endsWith(".tab"))
				builder.setHost(builder.getHost() + ".tab");
		}
		return builder;
	}
	
	private byte[] attemptGetFromUniprot(HttpGet get) throws IOException, URISyntaxException, InterruptedException
	{
		byte[] result = null;
		boolean done = false;
		int attemptCount = 0;
		while(!done)
		{
			logger.debug("getting from: {}",get.getURI());
			try (CloseableHttpClient getClient = HttpClients.createDefault();
					CloseableHttpResponse getResponse = getClient.execute(get);)
			{
				switch (getResponse.getStatusLine().getStatusCode())
				{
					case HttpStatus.SC_SERVICE_UNAVAILABLE:
					case HttpStatus.SC_INTERNAL_SERVER_ERROR:
					case HttpStatus.SC_BAD_GATEWAY:
					case HttpStatus.SC_GATEWAY_TIMEOUT:
						logger.error("Error {} detected! Message: {}", getResponse.getStatusLine().getStatusCode() ,getResponse.getStatusLine().getReasonPhrase());
						// logger.error("File: \"{}\" was not written! Re-execute the File Retriever to try again.", this.destination);
						attemptCount++;
						break;
	
					case HttpStatus.SC_OK:
					case HttpStatus.SC_MOVED_PERMANENTLY:
					case HttpStatus.SC_MOVED_TEMPORARILY:
						attemptCount++; 
						logger.debug("HTTP Status: {}",getResponse.getStatusLine().toString());
						result = EntityUtils.toByteArray(getResponse.getEntity());
						if (result != null)
						{
							done = true;
						}
						else
						{
							logger.warn("Response did not contain data.");
						}
						break;
	
					default:
						attemptCount++;
						logger.warn("Nothing was downloaded due to an unexpected status code and message: {} / {} ",getResponse.getStatusLine().getStatusCode(), getResponse.getStatusLine());
						break;
	
				}
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
					logger.info("Re-trying...");
					Thread.sleep(Duration.ofSeconds(2).toMillis());
					
				}
			}
		}
		return result;
	}
	
	private String attemptPostToUniprot(HttpPost post) throws ClientProtocolException, IOException, InterruptedException
	{
		String mappingLocationURI = null;
		{
			try (CloseableHttpClient postClient = HttpClients.createDefault();
					CloseableHttpResponse postResponse = postClient.execute(post);)
			{
				switch (postResponse.getStatusLine().getStatusCode())
				{
					case HttpStatus.SC_SERVICE_UNAVAILABLE:
					case HttpStatus.SC_INTERNAL_SERVER_ERROR:
						logger.error("Error {} detected! Message: {}", postResponse.getStatusLine().getStatusCode() ,postResponse.getStatusLine().getReasonPhrase());
						break;
					
					case HttpStatus.SC_OK:
					case HttpStatus.SC_MOVED_PERMANENTLY:
					case HttpStatus.SC_MOVED_TEMPORARILY:
						if (postResponse.containsHeader("Location"))
						{
							mappingLocationURI = postResponse.getHeaders("Location")[0].getValue();
							logger.debug("Location of data: {}",mappingLocationURI);
						}
						else
						{
							logger.debug("Status was {}, \"Location\" header was not prsent. Other headers are: {}", postResponse.getStatusLine().toString(), Arrays.stream(postResponse.getAllHeaders()).map( (h -> h.toString()) ).collect(Collectors.joining(" ; ")));
						}
						break;
				}
			}
		}
		return mappingLocationURI;
	}
	
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
			int attemptCount = 0;
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			byte[] buffer = new byte[2048];
			int len;
			while ((len = inStream.read(buffer)) > -1)
			{
				baos.write(buffer,0,len);
			}
			String location = null;
			while (location == null && attemptCount < maxAttemptCount)
			{
				InputStream fileData = new ByteArrayInputStream(baos.toByteArray());
				HttpPost post = new HttpPost(this.uri);
				logger.debug("URI: {}", post.getURI().toURL());
				HttpEntity attachment = MultipartEntityBuilder.create()
						.addBinaryBody("file", fileData, ContentType.TEXT_PLAIN, "uniprot_ids.txt")
						.addPart("format", new StringBody("tab", ContentType.MULTIPART_FORM_DATA))
						.addPart("from", new StringBody(this.mapFromDb, ContentType.MULTIPART_FORM_DATA))
						.addPart("to", new StringBody(this.mapToDb, ContentType.MULTIPART_FORM_DATA))
						.build();
				post.setEntity(attachment);
				
				location = this.attemptPostToUniprot(post);
				attemptCount++;
				if (location == null)
				{
					if (attemptCount > 0 && attemptCount < maxAttemptCount)
					{
						Random r = new Random(System.nanoTime());
						long delay = (long) (1000 + (attemptCount * r.nextFloat()));
						logger.debug("Attempt {} out of {}, next attempt in {} ms", attemptCount, maxAttemptCount, delay);
						Thread.sleep( delay );
					}
					else if (attemptCount >= maxAttemptCount)
					{
						logger.error("Could not get the Location of the data in {} attempts.", attemptCount);
					}
				}
				
			}
			if (location != null)
			{
				HttpGet get = new HttpGet( this.uriBuilderFromDataLocation(location).build() );
				byte[] result = this.attemptGetFromUniprot(get);
				if (result != null && result.length > 0)
				{
					logger.debug(".tab result size: {}", result.length);
					
					Path path = Paths.get(new URI("file://" + this.destination));
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
				}
				else
				{
					throw new Exception("Result for .tab file was null/empty!");
				}

				URIBuilder builder = uriBuilderFromDataLocation(location);
				HttpGet getUnmappedIdentifiers = new HttpGet(builder.setHost(builder.getHost().replace(".tab", ".not")).build());
				byte[] unmappedIdentifiersResult = this.attemptGetFromUniprot(getUnmappedIdentifiers);
				if (unmappedIdentifiersResult != null && unmappedIdentifiersResult.length > 0)
				{
					logger.debug(".not result size: {}", unmappedIdentifiersResult.length);
					String unmappedIdentifierDestination;
					String[] filenameParts = this.destination.split("\\.");
					unmappedIdentifierDestination = this.destination.replace( filenameParts[filenameParts.length - 1] , "notMapped." + filenameParts[filenameParts.length - 1] );
					Path unmappedIdentifierspath = Paths.get(new URI("file://" + unmappedIdentifierDestination));
					Files.createDirectories(unmappedIdentifierspath.getParent());
					//Files.write(unmappedIdentifierspath, unmappedIdentifiersResult);
					BufferedWriter writer = Files.newBufferedWriter(unmappedIdentifierspath);
					writer.write(new String(unmappedIdentifiersResult));
					writer.flush();
					writer.close();
					if (!Files.isReadable(unmappedIdentifierspath))
					{
						throw new Exception("The new file "+ unmappedIdentifierspath +" is not readable!");
					}
				}
				else
				{
					throw new Exception("Result for .not file was null/empty!");
				}
			}
			else
			{
				logger.error("We could not determine the location of the data, file was not downloaded.");
			}
		}
		catch (URISyntaxException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch (UnsupportedEncodingException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch (ClientProtocolException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch (IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch (InterruptedException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch (Exception e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
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
}
