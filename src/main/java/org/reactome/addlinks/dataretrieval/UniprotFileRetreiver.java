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
		builder.setHost(schemeAndHost[1]);
		
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
		//HttpGet get = new HttpGet(builder.build());
		//return get;
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
						logger.error("Error {} detected! Message: {}", getResponse.getStatusLine().getStatusCode() ,getResponse.getStatusLine().getReasonPhrase());
						// logger.error("File: \"{}\" was not written! Re-execute the File Retriever to try again.", this.destination);
						attemptCount++;
						break;
	
					case HttpStatus.SC_OK:
					case HttpStatus.SC_MOVED_PERMANENTLY:
					case HttpStatus.SC_MOVED_TEMPORARILY:
						attemptCount ++ ; 
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
//		while(!done)
		{
			//ByteArrayOutputStream outstream = new ByteArrayOutputStream();
			//post.getEntity().writeTo(outstream);
			//logger.debug("Posting to: {} with content: {}",post.getURI(),outstream.toString() );
			;
			
			try (CloseableHttpClient postClient = HttpClients.createDefault();
					CloseableHttpResponse postResponse = postClient.execute(post);)
			{
				//logger.debug("Status: {}", postResponse.getStatusLine());
				// TODO: Clean up the status response handling!!! Too much redundancy in this class.
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
			boolean done = false;
			int attemptCount = 0;
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			byte[] buffer = new byte[2048];
			int len;
			while ((len = inStream.read(buffer)) > -1)
			{
				baos.write(buffer,0,len);
			}
//			while(!done)
//			{
				

//				try (CloseableHttpClient postClient = HttpClients.createDefault();
//						CloseableHttpResponse postResponse = postClient.execute(post);)
				
//				{
	
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
//					logger.debug("Status: {}", postResponse.getStatusLine());
					// TODO: Clean up the status response handling!!! Too much redundancy in this class.
//					switch (postResponse.getStatusLine().getStatusCode())
//					{
//						case HttpStatus.SC_SERVICE_UNAVAILABLE:
//						case HttpStatus.SC_INTERNAL_SERVER_ERROR:
//							logger.error("Error {} detected! Message: {}", postResponse.getStatusLine().getStatusCode() ,postResponse.getStatusLine().getReasonPhrase());
//							attemptCount++;
//							break;
//						
//						case HttpStatus.SC_OK:
//							logger.info("HTTP Status: {}",postResponse.getStatusLine().getStatusCode() );
//							// attemptCount++;
//							// Status == OK should not count towards retry count.
//							break;
//						case HttpStatus.SC_MOVED_PERMANENTLY:
//						case HttpStatus.SC_MOVED_TEMPORARILY:
//							attemptCount++;
//							//The uniprot response will contain the actual URL for the data in the "Location" header.
//							if (postResponse.containsHeader("Location"))
//							{
////								String location = postResponse.getHeaders("Location")[0].getValue();
//								logger.debug("Location of data: {}",location);
//								
								HttpGet get = new HttpGet( this.uriBuilderFromDataLocation(location).build() );
//								logger.debug("request: {}",get.toString());
//								try (CloseableHttpClient getClient = HttpClients.createDefault();
//										CloseableHttpResponse getResponse = getClient.execute(get);)
//								{
//									switch (getResponse.getStatusLine().getStatusCode())
//									{
//										case HttpStatus.SC_SERVICE_UNAVAILABLE:
//										case HttpStatus.SC_INTERNAL_SERVER_ERROR:
//											logger.error("Error {} detected! Message: {}", getResponse.getStatusLine().getStatusCode() ,getResponse.getStatusLine().getReasonPhrase());
//											logger.error("File: \"{}\" was not written! Re-execute the File Retriever to try again.", this.destination);
//											break;
//	
//										case HttpStatus.SC_OK:
//										case HttpStatus.SC_MOVED_PERMANENTLY:
//										case HttpStatus.SC_MOVED_TEMPORARILY:
//											Path path = Paths.get(new URI("file://" + this.destination));
//											Files.createDirectories(path.getParent());
//											Files.write(path, EntityUtils.toByteArray(getResponse.getEntity()));
//											done = true;
//											break;
//	
//										default:
//											logger.warn("Nothing was downloaded due to an unexpected status code and message: {} / {} ",getResponse.getStatusLine().getStatusCode(), getResponse.getStatusLine());
//											break;
//	
//									}
//								}
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

								//If the Location returned by the service was a simple URL with no query string, it means 
								//that we had to append .tab to the URL. That means we also need to get the "not" file
								//which contains everything which was not mapped successfully.
//								if (parts.length == 1)
//								{
//									builder = new URIBuilder();
//									builder.setScheme(schemeAndHost[0]);
//									builder.setHost(schemeAndHost[1]);
//									builder.setHost(builder.getHost().replace(".tab", ".not"));
//									HttpGet getUnmappedIdentifiers = new HttpGet(builder.build());
									URIBuilder builder = uriBuilderFromDataLocation(location);
									HttpGet getUnmappedIdentifiers = new HttpGet(builder.setHost(builder.getHost().replace(".tab", ".not")).build());
									
//									logger.debug("request: {}",getUnmappedIdentifiers.toString());
//									try (CloseableHttpClient getClient = HttpClients.createDefault();
//											CloseableHttpResponse getResponse = postClient.execute(getUnmappedIdentifiers);)
//									{
//										switch (getResponse.getStatusLine().getStatusCode())
//										{
//											case HttpStatus.SC_SERVICE_UNAVAILABLE:
//											case HttpStatus.SC_INTERNAL_SERVER_ERROR:
//												logger.error("Error {} detected! Message: {}", getResponse.getStatusLine().getStatusCode() ,getResponse.getStatusLine().getReasonPhrase());
//												logger.error("File: \"{}\" was not written! Re-execute the File Retriever to try again.", this.destination);
//												break;
//	
//											case HttpStatus.SC_OK:
//											case HttpStatus.SC_MOVED_PERMANENTLY:
//											case HttpStatus.SC_MOVED_TEMPORARILY:
//												String unmappedIdentifierDestination;
//												String[] filenameParts = this.destination.split("\\.");
//												unmappedIdentifierDestination = this.destination.replace( filenameParts[filenameParts.length - 1] , "notMapped." + filenameParts[filenameParts.length - 1] );
//												Path path = Paths.get(new URI("file://" + unmappedIdentifierDestination));
//												Files.createDirectories(path.getParent());
//												Files.write(path, EntityUtils.toByteArray(getResponse.getEntity()));
//												break;
//	
//											default:
//												logger.warn("Nothing was downloaded due to an unexpected status code and message: {} / {} ",getResponse.getStatusLine().getStatusCode(), getResponse.getStatusLine());
//												break;
//										}
//									}
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
//								}
//							}
//							else
//							{
//								logger.error("Response didn't have a Location header! headers are: {}", Arrays.stream(postResponse.getAllHeaders()).map( (h -> h.toString()) ).collect(Collectors.joining(" ; ")) );
//							}
//							break;
						
//						default:
//							logger.warn("Nothing was downloaded due to an unexpected status code and message: {} / {} ",postResponse.getStatusLine().getStatusCode(), postResponse.getStatusLine());
//							break;
//					}
					}
					else
					{
						logger.error("We could not determine the location of the data, file was not downloaded.");
					}
//				}
//				if (attemptCount > this.maxAttemptCount)
//				{
//					logger.error("Reached max attempt count! No more attempts.");
//					done = true;
//				}
//				else
//				{
//					if (attemptCount < this.maxAttemptCount && ! done)
//					{
//						logger.info("Re-trying...");
//						Thread.sleep(Duration.ofSeconds(2).toMillis());
//						
//					}
//				}
//			}
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
