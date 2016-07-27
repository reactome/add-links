package org.reactome.addlinks.dataretrieval;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

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

	private String mapFromDb="";
	private String mapToDb="";
	private InputStream inStream;

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
	
	public void setDataInputStream(InputStream inStream)
	{
		this.inStream = inStream;
	}
	
	public String getFetchDestination()
	{
		return this.destination;
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
		
		
			// Need to hit URLs that look like this:
			// http://rest.ensembl.org/xrefs/id/ENSOCUT00000003872?content-type=text/xml&all_levels=1&species=Oryctolagus_cuniculus&external_db=RefSeq_peptide_predicted
			// or
			// http://rest.ensembl.org/xrefs/id/ENSOCUT00000003872?content-type=text/xml&all_levels=1&species=Oryctolagus_cuniculus&external_db=uniprot/swissprot
			// or
			// http://rest.ensembl.org/xrefs/id/ENSG00000175899?content-type=text/xml&all_levels=1&species=Homo_Sapiens&external_db=EntrezGene
			
			// ...then, extract all primary_id attributes. Be sure to get any synonyms as well (the EntrezGene URI demonstrates this). 

			try
			{
				
				URIBuilder builder = new URIBuilder(this.uri);
				builder.addParameter("content-type", "text/xml")
						.addParameter("all_levels", "1")
						.addParameter("external_db", this.getMapToDb());
				HttpGet get = new HttpGet(builder.build());
				
				try (CloseableHttpClient getClient = HttpClients.createDefault();
						CloseableHttpResponse getResponse = getClient.execute(get);)
				{
					Path path = Paths.get(new URI("file://" + this.destination));
					Files.createDirectories(path.getParent());
					Files.write(path, EntityUtils.toByteArray(getResponse.getEntity()));
				}
				
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
