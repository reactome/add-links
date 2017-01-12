package org.reactome.addlinks.dataretrieval.ensembl;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

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
import org.reactome.addlinks.dataretrieval.FileRetriever;
import org.reactome.addlinks.dataretrieval.ensembl.EnsemblServiceResponseProcessor.EnsemblServiceResult;

public class EnsemblFileRetriever extends FileRetriever
{
	private static final Logger logger = LogManager.getLogger();
	// Let's assume that initially we can make 10 requests. This will be reset once we get the first actual response from the server.
	//private static AtomicInteger numRequestsRemaining = new AtomicInteger(10);
	private String mapFromDb="";
	private String mapToDb="";
	private String species;
	private List<String> identifiers;
	//private int retryCount = 0;
	//private static final int MAX_RETRIES = 5;
	//private InputStream inStream;

	public enum EnsemblDB
	{
		ENSEMBL("ENSEMBL"),
		ENSP("ENSP_ident"),
		ENSEMBLProtein("ENSEMBL_PRO_ID"),
		ENSEMBLGene("ENSEMBLGENOME_ID"),
		ENSEMBLTranscript("ENSEMBL_TRS_ID"),
		ALL_DATABASES("%"),
		EnsemblGene("ENSG"),
		EMBL("EMBL"),
		OMIM("MIM_GENE"),
		//Wormbase("wormbase_id"),
		// ENSEMBL Rest API is aware of a  number of external wormbase 
		// databases (wormbase_id, wormbase_gene, etc...) but wormbase_gene 
		// is what the old Perl code used.
		Wormbase("wormbase_gene"), 
		EntrezGene("EntrezGene"),
		RefSeqPeptide("RefSeq_peptide"),
		RefSeqRNA("RefSeq_mRNA"),
		KEGG("KEGG"),
		IntAct("IntAct"),
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
		
		public static EnsemblDB ensemblDBFromEnsemblName(String ensemblName)
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
		if (this.mapFromDb == null || this.mapFromDb.trim().length() == 0)
		{
			throw new RuntimeException("You must provide a database name to map from!");
		}
		else if(this.mapToDb == null || this.mapToDb.trim().length() == 0)
		{
			throw new RuntimeException("You must provide a database name to map to!");
		}
		else if (this.species == null || this.species.trim().length() == 0)
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

		// 2017-01-05
		// This will all need to be redone! Getting data from ENSEMBL REST API isn't as simple as I thought.
		// Here's how it needs to happen:
		// 1) POST to ENSEMBL lookup (see: http://rest.ensembl.org/documentation/info/lookup_post)
		// This needs to be done once per species, for ALL IDs of a given species (but maybe test if you can do a multi-species POST? The fewer total requests we send, the better)
		// You can test this with this curl command: `curl -H "Content-type: application/json" -H "Accept:text/xml" -X POST -d '{ "ids":["ENSGALP00000056694","ENSGALP00000056695"]}' http://rest.ensembl.org/lookup/id/?species=gallus_gallus`
		// 2) Process the results. Extract the "Parent" value for each "id" in the resultset. This Parent is probably a Transcript ID which you can use to do another lookup (or maybe batch of lookups?)
		// 3) Process the results again. This time, the "Parent" should be a gene ID. This can be used to query against the xref endpoint (http://rest.ensembl.org/xrefs/id/) but it does not accept POST so must do them 1 by 1!
		
		try
		{
			Path path = Paths.get(new URI("file://" + this.destination));
			StringBuilder sb = new StringBuilder("<ensemblResponses>\n");
			logger.info("");
			int i = 0;
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
				logger.trace("URI: "+get.getURI());
				
				boolean done = false;
//				boolean okToQuery = true;
//				logger.info("Query quota is: "+EnsemblFileRetriever.numRequestsRemaining.get());
				while (!done /*&& okToQuery*/)
				{
					try (CloseableHttpClient getClient = HttpClients.createDefault();
							CloseableHttpResponse getResponse = getClient.execute(get);)
					{
						EnsemblServiceResult result = EnsemblServiceResponseProcessor.processResponse(getResponse);
						if (!result.getWaitTime().equals(Duration.ZERO))
						{
							logger.info("Need to wait: {} seconds.", result.getWaitTime().getSeconds());
							Thread.currentThread().wait(result.getWaitTime().toMillis());
							done = false;
						}
						else
						{
							// Only record the successful responses.
							if (result.getStatus() == HttpStatus.SC_OK)
							{
								String content = result.getResult();
								sb.append("<ensemblResponse id=\""+identifier+"\" URL=\"" + URLEncoder.encode(get.getURI().toString(), "UTF-8")  + "\">\n"+content+"</ensemblResponse>\n");
							}
							done = true;
						}
//						if ( getResponse.containsHeader("Retry-After") )
//						{
//							logger.debug("Response code: {}", getResponse.getStatusLine().getStatusCode());
//							Duration waitTime = Duration.ofSeconds(Integer.valueOf(getResponse.getHeaders("Retry-After")[0].getValue().toString()));
//							logger.info("The server told us to wait, so we will wait for {} before trying again.",waitTime);
//							Thread.sleep(waitTime.toMillis());
//						}
//						else
//						{
//							switch (getResponse.getStatusLine().getStatusCode())
//							{
//								case HttpStatus.SC_OK:
//									String content = EntityUtils.toString(getResponse.getEntity());
//									//We'll store everything in an XML and then use a FileProcessor to sort out the details later.
//									// ... or maybe we should process the XML response here? In that case, you will need this xpath expression:
//									// - to get all primary IDs: //data/@primary_id
//									// - to get all synonyms: //data/synonyms/text() (though I'm not so sure the synonyms should be included in the results...)
//									sb.append("<ensemblResponse id=\""+identifier+"\" URL=\"" + URLEncoder.encode(get.getURI().toString(), "UTF-8")  + "\">\n"+content+"</ensemblResponse>\n");
//									done = true;
//									break;
//								case HttpStatus.SC_NOT_FOUND:
//									logger.error("Response code 404 (\"Not found\") received, check that your URL is correct: {}", get.getURI().toString());
//									okToQuery = false;
//									break;
//								case HttpStatus.SC_INTERNAL_SERVER_ERROR:
//									logger.error("Error 500 detected! Message: {}",getResponse.getStatusLine().getReasonPhrase());
//									// If we get 500 error then we should just get  out of here. Maybe throw an exception?
//									okToQuery = false;
//									break;
//								case HttpStatus.SC_BAD_REQUEST:
//									String s = EntityUtils.toString(getResponse.getEntity());
//									logger.error("Response code was 400 (\"Bad request\"). Message from server: {}", s);
//									sb.append("<ensemblResponse id=\""+identifier+"\" URL=\"" + URLEncoder.encode(get.getURI().toString(), "UTF-8") + "\">\n"+ s +"</ensemblResponse>\n");
//									okToQuery = false;
//									break;
//							}
//						}
//						int numRequestsRemaining = Integer.valueOf(getResponse.getHeaders("X-RateLimit-Remaining")[0].getValue().toString());
//						EnsemblFileRetriever.numRequestsRemaining.set(numRequestsRemaining);
					} 
				}
				i++;
				if (i%100 == 0)
				{
					logger.info("{} requests remaining.", EnsemblServiceResponseProcessor.getNumRequestsRemaining());
				}
			}
			Files.createDirectories(path.getParent());
			sb.append("</ensemblResponses>");
			Files.write(path, sb.toString().getBytes(), StandardOpenOption.CREATE);
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
