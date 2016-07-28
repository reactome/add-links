package org.reactome.addlinks.dataretrieval;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class EnsemblFileRetriever extends FileRetriever
{
	private static final Logger logger = LogManager.getLogger();
	// Let's assume that initially we can make 10 requests. This will be reset once we get the first actual response from the server.
	private static AtomicInteger numRequestsRemaining = new AtomicInteger(10);
	private String mapFromDb="";
	private String mapToDb="";
	private String species;
	private List<String> identifiers;
	//private InputStream inStream;

	public enum EnsemblDB
	{
		ENSEMBL("ENSEMBL"),
		EMBL("EMBL"),
		OMIM("MIM_GENE"),
		Wormbase("Wormbase"),
		EntrezGene("EntrezGene"),
		RefSeqPeptide("RefSeq_peptide"),
		RefSeqRNA("RefSeq_mRNA"),
		UniProt("Uniprot/SWISSPROT");
		
		private String ensemblName;
		private static Map<String,EnsemblDB> mapToEnum;
		
		private EnsemblDB(String s)
		{
			this.ensemblName = s;
			updateMap(s);
		}
		
		private void updateMap(String s)
		{
			if (mapToEnum == null )
			{
				mapToEnum = new HashMap<String,EnsemblDB>(10);
			}
			mapToEnum.put(s, this);
		}
//		
		public String getEnsemblName()
		{
			return this.ensemblName;
		}
		
		public static EnsemblDB ensemblDBFromUniprotName(String ensemblName)
		{
			return mapToEnum.get(ensemblName);
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
	
	public void setMapFromDbEnum(EnsemblDB mapFromDb)
	{
		this.mapFromDb = mapFromDb.getEnsemblName();
	}

	public void setMapToDbEnum(EnsemblDB mapToDb)
	{
		this.mapToDb = mapToDb.getEnsemblName();
	}
	
	public void setMapFromDb(String mapFromDb)
	{
		this.mapFromDb = mapFromDb;
	}

	public void setMapToDb(String mapToDb)
	{
		this.mapToDb = mapToDb;
	}
	
	public String getFetchDestination()
	{
		return this.destination;
	}
	
	public void setSpecies(String s)
	{
		this.species = s;
	}
	
	public void setIdentifiers(List<String> identifiers)
	{
		this.identifiers = identifiers;
	}
	
	@Override
	public void downloadData()
	{
		// Check inputs:
		if (this.mapFromDb.trim().length() == 0)
		{
			throw new RuntimeException("You must provide a database name to map from!");
		}
		else if(this.mapToDb.trim().length() == 0)
		{
			throw new RuntimeException("You must provide a database name to map to!");
		}
		else if (this.species.trim().length() == 0)
		{
			throw new RuntimeException("You must provide a species name for the mapping!") ;
		}
		else if (this.identifiers == null || this.identifiers.isEmpty())
		{
			throw new RuntimeException("You must provide a list of identifiers to map!") ;
		}
		
		
		// Need to hit URLs that look like this:
		// http://rest.ensembl.org/xrefs/id/ENSOCUT00000003872?content-type=text/xml&all_levels=1&species=Oryctolagus_cuniculus&external_db=RefSeq_peptide_predicted
		// or
		// http://rest.ensembl.org/xrefs/id/ENSOCUT00000003872?content-type=text/xml&all_levels=1&species=Oryctolagus_cuniculus&external_db=uniprot/swissprot
		// or
		// http://rest.ensembl.org/xrefs/id/ENSG00000175899?content-type=text/xml&all_levels=1&species=Homo_Sapiens&external_db=EntrezGene
		// ...then, extract all primary_id attributes. Be sure to get any synonyms as well (the EntrezGene URI demonstrates this). 

		try
		{
			Path path = Paths.get(new URI("file://" + this.destination));
			String responseContent = "";
			logger.info("");
			for (String identifier : identifiers)
			{
				URIBuilder builder = new URIBuilder();

				builder.setHost(this.uri.getHost())
						.setPath(this.uri.getPath() + identifier)
						.setScheme(this.uri.getScheme())
						.addParameter("content-type", "text/xml")
						.addParameter("all_levels", "1")
						.addParameter("species", this.species)
						.addParameter("external_db", this.getMapToDb());
				HttpGet get = new HttpGet(builder.build());
				logger.debug("URI: "+get.getURI());
				
				boolean done = false;
				boolean okToQuery = true;
				logger.info("Query quota is: "+EnsemblFileRetriever.numRequestsRemaining.get());
				while (!done && okToQuery)
				{
					try (CloseableHttpClient getClient = HttpClients.createDefault();
							CloseableHttpResponse getResponse = getClient.execute(get);)
					{
						if (getResponse.getStatusLine().getStatusCode() == 500)
						{
							logger.error("Error 500 detected! Message: {}",getResponse.getStatusLine().getReasonPhrase());
							// If we get 500 error then we should just get  out of here. Maybe throw an exception?
							okToQuery = false;
						}
						else
						{
							// If the server sends back a "Retry-After" header, we must wait until that time passes before retrying.
							if ( getResponse.containsHeader("Retry-After") )
							{
								logger.debug("Response code: {}", getResponse.getStatusLine().getStatusCode());
								Duration waitTime = Duration.ofSeconds(Integer.valueOf(getResponse.getHeaders("Retry-After")[0].getValue().toString()));
								logger.info("The server told us to wait, so we will wait for {} before trying again.",waitTime);
								Thread.sleep(waitTime.toMillis());
							}
							else if ( getResponse.getStatusLine().getStatusCode() == 200)
							{
								String content = EntityUtils.toString(getResponse.getEntity());
								//We'll store everything in an XML and then use a FileProcessor to sort out the details later.
								// ... or maybe we should process the XML response here? In that case, you will need this xpath expression:
								// - to get all primary IDs: //data/@primary_id
								// - to get all synonyms: //data/synonyms/text() (though I'm not so sure the synonyms should be included in the results...)
								responseContent += "<identifier id=\""+identifier+"\">\n"+content+"</identifier>\n";
								done = true;
							}
							//If we didn't get a Retry-After header AND we didn't get an OK 200 response code AND we didn't get an ERR 500 response code,
							//then I have no idea what happened, so just log it and exit.
							else
							{
								logger.info("Got the following response code: {} with this status: {} ; Not sure how to handle this, so giving up!",getResponse.getStatusLine().getStatusCode(), getResponse.getStatusLine().getReasonPhrase());
								okToQuery = false;
							}
							int numRequestsRemaining = Integer.valueOf(getResponse.getHeaders("X-RateLimit-Remaining")[0].getValue().toString());
							EnsemblFileRetriever.numRequestsRemaining.set(numRequestsRemaining);
							// int rateLimitResetTime = Integer.valueOf(getResponse.getHeaders("X-RateLimit-Reset")[0].getValue().toString());
						}
					} 
				}
			}
			Files.createDirectories(path.getParent());
			Files.write(path, responseContent.getBytes(), StandardOpenOption.CREATE);
		}
		catch (InterruptedException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch (URISyntaxException e)
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
	}
}
