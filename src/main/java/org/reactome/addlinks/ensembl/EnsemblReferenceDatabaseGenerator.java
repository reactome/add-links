package org.reactome.addlinks.ensembl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;

import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.reactome.addlinks.db.ReferenceDatabaseCreator;
import org.reactome.addlinks.db.ReferenceObjectCache;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * This class will query ENSEMBL to get a list of supported species,
 * and then generate ReferenceDatabase objects in the database with
 * the appropriate species-specific accessURL.
 * The URL queried is https://rest.ensembl.org/info/species?content-type=text/xml 
 * @author sshorser
 *
 */
public final class EnsemblReferenceDatabaseGenerator
{
	private static final String ENSEMBL_URL = "http://www.ensembl.org";
	private static final Logger logger = LogManager.getLogger();
	private static ReferenceDatabaseCreator dbCreator;
	private static String speciesURL = "https://rest.ensembl.org/info/species?content-type=text/xml";
	private static final String xpathForSpeciesNames = "/opt/data/species/@name";
	private static XPathExpression pathToSpeciesNames;


	static
	{
		try
		{
			pathToSpeciesNames = XPathFactory.newInstance().newXPath().compile(xpathForSpeciesNames);
		}
		catch (XPathExpressionException e)
		{
			logger.error("Error in static init creating the xpath expression: {}",e.getMessage());
			throw new RuntimeException(e);
		}
	}
	
	/**
	 * private constructor in a final class: This class is really more of a utility
	 * class - creating multiple instances of it probably wouldn't make sense. 
	 */
	private EnsemblReferenceDatabaseGenerator()
	{
		
	}
	
	
	public static void generateSpeciesSpecificReferenceDatabases(ReferenceObjectCache objectCache) throws URISyntaxException, ClientProtocolException, IOException, XPathExpressionException, Exception
	{
		URI uri = new URI(EnsemblReferenceDatabaseGenerator.speciesURL);
		HttpGet get = new HttpGet(uri );
		//Need to multiply by 1000 because timeouts are in milliseconds.
		//RequestConfig config = RequestConfig.copy(RequestConfig.DEFAULT).build();
		try( CloseableHttpClient client = HttpClients.createDefault();
			CloseableHttpResponse response = client.execute(get) )
		{
			logger.info("Response: {}",response.getStatusLine());
			//The response will be a big XML string, we need to extract all /opt/data/species/@name from it.
			String s = EntityUtils.toString(response.getEntity());
			InputStream inStream = new ByteArrayInputStream(s.getBytes());
			InputSource source = new InputSource(inStream);
			NodeList nodeList = (NodeList) EnsemblReferenceDatabaseGenerator.pathToSpeciesNames.evaluate(source, XPathConstants.NODESET);
			if (nodeList.getLength() > 0)
			{
				logger.info("{} ENSEMBL species to attempt to create refDBs for.",nodeList.getLength());
				for (int i = 0 ; i < nodeList.getLength() ; i ++)
				{
					String speciesName = nodeList.item(i).getTextContent();
					
					//Now that we have a species name, we can create a species-specific ReferenceDatabase.
					try
					{
						//TODO: Maybe instead of creating them all in the database, we should store this information in the cache
						//and only create a ReferenceDatbase object when it's discovered that one is needed.
						String speciesURL = "http://www.ensembl.org/"+speciesName+"/geneview?gene=###ID###&db=core";
						
						// Before we create a new ENSEMBL reference, let's see if it already exists, but with alternate spelling. In that case, we'll just create an alias to the existing database.
						String newDBName = "ENSEMBL_"+speciesName.replaceAll(" ", "_")+"_PROTEIN";
						// OLD style: Capitalization and spaces.
						String oldStyleDBName = "ENSEMBL_"+speciesName.substring(0, 1).toUpperCase() + speciesName.substring(1).replace("_", " ")+"_PROTEIN";
						// Less common: Capitalization AND_underscores
						String oldStyleDBName2 = "ENSEMBL_"+speciesName.substring(0, 1).toUpperCase() + speciesName.substring(1).replace(" ", "_")+"_PROTEIN";
						createReferenceDB(objectCache, speciesName, speciesURL, newDBName, oldStyleDBName);
						if (objectCache.getRefDbNamesToIds().containsKey(oldStyleDBName2))
						{
							// Only create an alias of newDBName to oldStyleDBName2 if oldStyleDBName2 actually exists in the database.
							EnsemblReferenceDatabaseGenerator.dbCreator.createReferenceDatabaseToURL(ENSEMBL_URL, speciesURL, oldStyleDBName2, newDBName);
						}

						newDBName = "ENSEMBL_"+speciesName.replaceAll(" ", "_")+"_GENE";
						oldStyleDBName = "ENSEMBL_"+speciesName.substring(0, 1).toUpperCase() + speciesName.substring(1).replace("_", " ")+"_GENE";
						oldStyleDBName2 = "ENSEMBL_"+speciesName.substring(0, 1).toUpperCase() + speciesName.substring(1).replace(" ", "_")+"_GENE";
						createReferenceDB(objectCache, speciesName, speciesURL, newDBName, oldStyleDBName);
						if (objectCache.getRefDbNamesToIds().containsKey(oldStyleDBName2))
						{
							// Only create an alias of newDBName to oldStyleDBName2 if oldStyleDBName2 actually exists in the database.
							EnsemblReferenceDatabaseGenerator.dbCreator.createReferenceDatabaseToURL(ENSEMBL_URL, speciesURL, oldStyleDBName2, newDBName);
						}
						//EnsemblReferenceDatabaseGenerator.dbCreator.createReferenceDatabase("http://www.ensembl.org", speciesURL, "ENSEMBL_"+speciesName.replaceAll(" ", "_")+"_TRANSCRIPT");
					}
					catch (Exception e)
					{
						logger.error("An error occurred while trying to create an Ensembl species-specific URL: {}",e.getMessage());
						e.printStackTrace();
						// Throw this back up the stack: there's probably no good way to recover from this without more information.
						throw e;
					}
				}
			}
			
		}
	}


	private static void createReferenceDB(ReferenceObjectCache objectCache, String speciesName, String speciesURL, String newDBName, String oldStyleDBName) throws Exception
	{
		if (objectCache.getRefDbNamesToIds().keySet().contains(oldStyleDBName))
		{
			logger.debug("Adding alias {} to existing ReferenceDatabase {} for species {} with accessURL: {}", newDBName, oldStyleDBName, speciesName, speciesURL);
			EnsemblReferenceDatabaseGenerator.dbCreator.createReferenceDatabaseToURL(ENSEMBL_URL, speciesURL, oldStyleDBName, newDBName);
		}
		else
		{
			logger.debug("Adding an ENSEMBL ReferenceDatabase {} for species: {} with accessURL: {}", newDBName, speciesName, speciesURL);
			EnsemblReferenceDatabaseGenerator.dbCreator.createReferenceDatabaseWithAliases(ENSEMBL_URL, speciesURL, newDBName);
		}
	}

	public static void setDbCreator(ReferenceDatabaseCreator dbCreator)
	{
		EnsemblReferenceDatabaseGenerator.dbCreator = dbCreator;
	}

	public static void setSpeciesURL(String speciesURL)
	{
		EnsemblReferenceDatabaseGenerator.speciesURL = speciesURL;
	}
	
	
}
