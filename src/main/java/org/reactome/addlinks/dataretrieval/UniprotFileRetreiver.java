package org.reactome.addlinks.dataretrieval;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.HttpEntity;
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
	private InputStream inStream;
	
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
			HttpPost post = new HttpPost(this.uri);
			logger.debug("URI: {}", post.getURI().toURL());
			HttpEntity attachment = MultipartEntityBuilder.create()
					.addBinaryBody("file",
							this.inStream,
							ContentType.TEXT_PLAIN, "uniprot_ids.txt")
					.addPart("format", new StringBody("tab", ContentType.MULTIPART_FORM_DATA))
					.addPart("from", new StringBody(this.mapFromDb, ContentType.MULTIPART_FORM_DATA))
					.addPart("to", new StringBody(this.mapToDb, ContentType.MULTIPART_FORM_DATA))
					.build();
			post.setEntity(attachment);

			try (CloseableHttpClient postClient = HttpClients.createDefault();
					CloseableHttpResponse postResponse = postClient.execute(post);)
			{

				logger.debug("Status: {}", postResponse.getStatusLine());
				if (postResponse.getStatusLine().getStatusCode() == 500)
				{
					logger.error("Error 500 detected! Message: {}",postResponse.getStatusLine().getReasonPhrase());
				}
				else
				{
					//The uniprot response will contain the actual URL for the data in the "Location" header.
					String location = postResponse.getHeaders("Location")[0].getValue();
					logger.debug("Location of data: {}",location);
					URIBuilder builder = new URIBuilder();
					
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
						builder.setHost(builder.getHost() + ".tab");
					}
					
					HttpGet get = new HttpGet(builder.build());
					try (CloseableHttpClient getClient = HttpClients.createDefault();
							CloseableHttpResponse getResponse = getClient.execute(get);)
					{
						Path path = Paths.get(new URI("file://" + this.destination));
						Files.createDirectories(path.getParent());
						Files.write(path, EntityUtils.toByteArray(getResponse.getEntity()));
					}
					
					//If the Location returned by the service was a simple URL with no query string, it means 
					//that we had to append .tab to the URL. That means we also need to get the "not" file
					//which contains everything which was not mapped successfully.
					if (parts.length == 1)
					{
						builder = new URIBuilder();
						builder.setScheme(schemeAndHost[0]);
						builder.setHost(schemeAndHost[1]);
						builder.setHost(builder.getHost() + ".not");
						HttpGet getUnmappedIdentifiers = new HttpGet(builder.build());
						try (CloseableHttpClient getClient = HttpClients.createDefault();
								CloseableHttpResponse getResponse = postClient.execute(getUnmappedIdentifiers);)
						{
							String unmappedIdentifierDestination;
							String[] filenameParts = this.destination.split("\\.");
							unmappedIdentifierDestination = this.destination.replace( filenameParts[filenameParts.length - 1] , "notMapped." + filenameParts[filenameParts.length - 1] );
							Path path = Paths.get(new URI("file://" + unmappedIdentifierDestination));
							Files.createDirectories(path.getParent());
							Files.write(path, EntityUtils.toByteArray(getResponse.getEntity()));
						}
					}
				}
			}
		}
		catch (URISyntaxException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch (UnsupportedEncodingException e1)
		{
			// TODO Auto-generated catch block
			e1.printStackTrace();
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
	
	public void setDataInputStream(InputStream inStream)
	{
		this.inStream = inStream;
	}
	
	public String getFetchDestination()
	{
		return this.destination;
	}
}
