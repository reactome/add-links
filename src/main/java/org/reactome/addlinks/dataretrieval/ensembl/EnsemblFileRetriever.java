package org.reactome.addlinks.dataretrieval.ensembl;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.reactome.addlinks.dataretrieval.FileRetriever;
import org.reactome.addlinks.dataretrieval.ensembl.EnsemblServiceResponseProcessor.EnsemblServiceResult;

public class EnsemblFileRetriever extends FileRetriever
{
	//private static final Logger logger = LogManager.getLogger();
	// Let's assume that initially we can make 10 requests. This will be reset once we get the first actual response from the server.
	//private static AtomicInteger numRequestsRemaining = new AtomicInteger(10);
	private String mapFromDb="";
	private String mapToDb="";
	private String species;
	private List<String> identifiers;

	public EnsemblFileRetriever() { }

	public EnsemblFileRetriever(String retrieverName)
	{
		super(retrieverName);
	}
	
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

		public String getEnsemblName()
		{
			return this.ensemblName;
		}
		
		public static EnsemblDB ensemblDBFromEnsemblName(String ensemblName)
		{
			return mapToEnum.get(ensemblName);
		}
	}
	
	@Deprecated
	public String getMapFromDb()
	{
		return this.mapFromDb;
	}
	
	public String getMapToDb()
	{
		return this.mapToDb;
	}
	
	@Deprecated
	public void setMapFromDbEnum(EnsemblDB mapFromDb)
	{
		this.mapFromDb = mapFromDb.getEnsemblName();
	}

	public void setMapToDbEnum(EnsemblDB mapToDb)
	{
		this.mapToDb = mapToDb.getEnsemblName();
	}
	
	@Deprecated
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
		/*if (this.mapFromDb == null || this.mapFromDb.trim().length() == 0)
		{
			throw new RuntimeException("You must provide a database name to map from!");
		}
		else */if(this.mapToDb == null || this.mapToDb.trim().length() == 0)
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
			StringBuffer sb = new StringBuffer("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<ensemblResponses>\n");
			logger.info("");
			//int i = 0;
			AtomicInteger i = new AtomicInteger(0);
			//for (String identifier : identifiers)
			identifiers.parallelStream().forEach( identifier -> { 
			{
				URIBuilder builder = new URIBuilder();

				builder.setHost(this.uri.getHost())
						.setPath(this.uri.getPath() + identifier)
						.setScheme(this.uri.getScheme())
						.addParameter("content-type", "text/xml")
						.addParameter("all_levels", "1")
						.addParameter("species", this.species)
						.addParameter("external_db", this.getMapToDb());
				try
				{
					HttpGet get = new HttpGet(builder.build());
					logger.trace("URI: "+get.getURI());
					
					boolean done = false;
					EnsemblServiceResponseProcessor responseProcessor = new EnsemblServiceResponseProcessor(this.logger);
					while (!done)
					{
						
						try (CloseableHttpClient getClient = HttpClients.createDefault();
								CloseableHttpResponse getResponse = getClient.execute(get);)
						{
							EnsemblServiceResult result = responseProcessor.processResponse(getResponse);
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
									String content = result.getResult().trim();
									sb.append("<ensemblResponse id=\""+identifier+"\" URL=\"" + URLEncoder.encode(get.getURI().toString(), "UTF-8")  + "\">\n"+content+"</ensemblResponse>\n");
								}
								else if (result.getStatus() == HttpStatus.SC_BAD_REQUEST)
								{
									logger.trace("Got BAD_REQUEST reponse. This was the request that was sent: {}", get.toString());
								}
								done = true;
							}
						}
						catch (InterruptedException e)
						{
							e.printStackTrace();
							throw new Error(e);
							
						} 
					}
					//i++;
				}
				catch (URISyntaxException e)
				{
					e.printStackTrace();
					throw new Error(e); 
				}
				catch (ClientProtocolException e)
				{
					e.printStackTrace();
					throw new Error(e);
				}
				catch (IOException e)
				{
					e.printStackTrace();
					throw new Error(e);
				}

				if (i.incrementAndGet() % 100 == 0)
				{
					// of course, this only works if the Identifiers list is > 100 ...
					logger.info("{} requests remaining.", EnsemblServiceResponseProcessor.getNumRequestsRemaining());
				}
			}
			});
			Files.createDirectories(path.getParent());
			String xml10pattern = "[^"
					+ "\u0009\r\n"
					+ "\u0020-\uD7FF"
					+ "\uE000-\uFFFD"
					+ "\ud800\udc00-\udbff\udfff"
					+ "]";
			sb.append("</ensemblResponses>");
			Files.write(path, sb.toString().trim().replaceAll(xml10pattern, "").getBytes(Charset.forName("UTF-8")), StandardOpenOption.CREATE);
		}
		catch (IOException e)
		{
			e.printStackTrace();
			throw new Error(e);
		}
		catch (URISyntaxException e)
		{
			e.printStackTrace();
			throw new Error(e);

		}
	}
}
