package org.reactome.addlinks.ensembl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;

import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.xpath.XPathExpressionException;

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
import org.reactome.addlinks.linkchecking.LinksToCheckCache;

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

			String s = EntityUtils.toString(response.getEntity());
			InputStream inStream = new ByteArrayInputStream(s.getBytes());
			
			TransformerFactory factory = TransformerFactory.newInstance();
			Source xsl = new StreamSource(new File("resources/ensembl-species-transform.xsl"));
			Templates template = factory.newTemplates(xsl);
			Transformer transformer = template.newTransformer();
			Source xml = new StreamSource(inStream);
			ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
			Result result = new StreamResult(outputStream);
			transformer.transform(xml, result);
			
			
			String[] lines = outputStream.toString().split("\n");
			// for each line, create a database for the proper name and all aliases. Except the numeric sequences.
			// So what if you create a lot of database references? Most will be cleaned up afterwards anyway...
			for (String line : lines)
			{
				// Lines will have the format "<species_name> : <alias1> , <alias2> , ..."
				String[] parts = line.split(" : ");
				String speciesName = parts[0].trim();
				// Don't create species-specific ReferenceDatabase objects if Reactome doesn't have that species.
				if (objectCache.getSetOfSpeciesNames().contains(speciesName))
				{
					EnsemblReferenceDatabaseGenerator.createReferenceDatabase(objectCache, speciesName);
					if (parts.length > 1)
					{
						String[] speciesNameAliases = parts[1].split(" , ");
						for (String alias : speciesNameAliases)
						{
							EnsemblReferenceDatabaseGenerator.createReferenceDatabase(objectCache, alias.trim());
						}
					}
				}
			}
		}
	}


	private static void createReferenceDatabase(ReferenceObjectCache objectCache, String speciesName) throws Exception
	{
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
				LinksToCheckCache.getRefDBsToCheck().add(newDBName);
			}

			newDBName = "ENSEMBL_"+speciesName.replaceAll(" ", "_")+"_GENE";
			oldStyleDBName = "ENSEMBL_"+speciesName.substring(0, 1).toUpperCase() + speciesName.substring(1).replace("_", " ")+"_GENE";
			oldStyleDBName2 = "ENSEMBL_"+speciesName.substring(0, 1).toUpperCase() + speciesName.substring(1).replace(" ", "_")+"_GENE";
			createReferenceDB(objectCache, speciesName, speciesURL, newDBName, oldStyleDBName);
			if (objectCache.getRefDbNamesToIds().containsKey(oldStyleDBName2))
			{
				// Only create an alias of newDBName to oldStyleDBName2 if oldStyleDBName2 actually exists in the database.
				EnsemblReferenceDatabaseGenerator.dbCreator.createReferenceDatabaseToURL(ENSEMBL_URL, speciesURL, oldStyleDBName2, newDBName);
				LinksToCheckCache.getRefDBsToCheck().add(newDBName);
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
