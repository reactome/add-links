package org.reactome.addlinks.ensembl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.reactome.addlinks.CustomLoggable;
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
public final class EnsemblReferenceDatabaseGenerator implements CustomLoggable
{
	private static final String FUNGI_URL = "fungi.ensembl.org";
	private static final String PROTISTS_URL = "protists.ensembl.org";
	private static final String METAZOA_URL = "metazoa.ensembl.org";
	private static final String ENSEMBL_URL = "http://www.ensembl.org";
	private static Logger logger; // = LogManager.getLogger();
	private static ReferenceDatabaseCreator dbCreator;
	private static String speciesURL = "https://rest.ensembl.org/info/species?content-type=text/xml";
	
//	private static final Set<String> nonCoreSpecies = new HashSet<>(Arrays.asList("drosophila_melanogaster", "caenorhabditis_elegans", "dictyostelium_discoideum", "schizosaccharomyces_pombe", "saccharomyces_cerevisiae", "plasmodium_falciparum"));

	private static Map<String, String> nonCoreSpeciesURLs = new HashMap<>(6);
	
	static
	{
		nonCoreSpeciesURLs.put("drosophila_melanogaster", METAZOA_URL);
		nonCoreSpeciesURLs.put("caenorhabditis_elegans", METAZOA_URL);
		
		nonCoreSpeciesURLs.put("dictyostelium_discoideum", PROTISTS_URL);
		nonCoreSpeciesURLs.put("plasmodium_falciparum", PROTISTS_URL);
		
		nonCoreSpeciesURLs.put("schizosaccharomyces_pombe", FUNGI_URL);
		nonCoreSpeciesURLs.put("saccharomyces_cerevisiae", FUNGI_URL);
	}
	
	/**
	 * private constructor (to prevent instantiation) in a final class: This class is really more of a utility
	 * class - creating multiple instances of it probably wouldn't make sense. 
	 */
	private EnsemblReferenceDatabaseGenerator()
	{
		if (EnsemblReferenceDatabaseGenerator.logger  == null)
		{
			EnsemblReferenceDatabaseGenerator.logger = this.createLogger("ENSEMBLReferenceDatabaseCreator", "RollingRandomAccessFile", this.getClass().getName(), true, Level.DEBUG);
		}
	}
	
	static
	{
		// Ugh... the constructor is private because this class never really needed a constructor. But *now* 
		// we want to use the CustomLoggable methods to log the output to a separate log file. But those methods require an instance.
		// So... a static initializer to create an instance that will trigger the creation of the custom logger.
		@SuppressWarnings("unused")
		EnsemblReferenceDatabaseGenerator generator = new EnsemblReferenceDatabaseGenerator();
	}
	
	/**
	 * Generate species-specific ENSEMBL ReferenceDatabases.
	 * This function will query ENSEMBL for a list of species, transform the resulting XML into simple-to-parse text,
	 * and then create an ENSEMBL ReferenceDatabase for each species. Aliases of species will also be added as additional
	 * names for ReferenceDatabase objects.
	 * @param objectCache - A ReferenceObjectCache
	 * @throws URISyntaxException
	 * @throws ClientProtocolException
	 * @throws IOException
	 * @throws XPathExpressionException
	 * @throws Exception
	 */
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
			
			logger.info("Flattened species list from ENSEMBL:\n{}",outputStream.toString());
			String[] lines = outputStream.toString().split("\n");
			// for each line, create a database for the proper name 
			for (String line : lines)
			{
				// Lines will have the format "<species_name> : <alias1> , <alias2> , ..."
				// Aliases can be ignored - they are not needed.
				String[] parts = line.split(" : ");
				String speciesName = parts[0].trim().toLowerCase().replace("_", " ");
				// It looks like we might need to create a ReferenceDatabase for all ENSEMBL species,
				// for use by some of the ENSEMBL Uniprot-mapped ENSEMBL identifiers.
				// Otherwise, we get "Requested ENSEMBL ReferenceDatabase "ENSEMBL_cricetulus_griseus_crigri_GENE" does not exists."
				// In the Reactome database, the closest species name we have to that is "Cricetulus", so it *can* be mapped to.
				EnsemblReferenceDatabaseGenerator.createReferenceDatabase(objectCache, speciesName);
			}
		}
	}

	/**
	 * Creates a ReferenceDatabase for a species.
	 * @param objectCache - A ReferenceObjectCache 
	 * @param speciesName - The speciesName
	 * @throws Exception
	 */
	private static void createReferenceDatabase(ReferenceObjectCache objectCache, String speciesName) throws Exception
	{
		try
		{
			String normalizedSpeciesName = speciesName.toLowerCase().replaceAll(" ", "_");
			//TODO: Maybe instead of creating them all in the database, we should store this information in the cache
			//and only create a ReferenceDatbase object when it's discovered that one is needed.
			String speciesURL = "http://www.ensembl.org/"+normalizedSpeciesName+"/geneview?gene=###ID###&db=core";
			
			// Special case: "Non-core" species use a different URL prefix than other species.
			if (nonCoreSpeciesURLs.containsKey(normalizedSpeciesName))
			{
				speciesURL = speciesURL.replace("www.ensembl.org", nonCoreSpeciesURLs.get(normalizedSpeciesName));
			}
			
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

	/**
	 * Creates a ReferenceDatabase.
	 * @param objectCache - A ReferenceObjectCache
	 * @param speciesName - The name of a species
	 * @param speciesURL - The species-specific URL - this will be used for the accessURL attribute of the ReferenceDatabase object.
	 * @param newDBName - a "new"-styled (with "_" as a separator instead of " ", and in lowercase) ReferenceDatabase name for this ReferenceDatabase.
	 * @param oldStyleDBName - a "old"-styled (in UPPERCASE with "_" OR " " as a separator) ReferenceDatabase name for this ReferenceDatabase.
	 * @throws Exception
	 */
	private static void createReferenceDB(ReferenceObjectCache objectCache, String speciesName, String speciesURL, String newDBName, String oldStyleDBName) throws Exception
	{
		// Check the cache for old-style names. If we could normalize/merge the pre-existing ENSEMBL reference database, this probably wouldn't be necesary,
		// nor would the old-style vs. new-style names.
		// NOTE: the cache doesn't get updated while this code runs. Fortunately, ReferenceDatabaseCreator.createReferenceDatabaseToURL will
		// check more carefully by querying the database for name and accessUrl
		if (objectCache.getRefDbNamesToIds().keySet().contains(oldStyleDBName))
		{
			// If the old-style name already exists, try to create a new-style alias to it.
			logger.debug("Trying to add an alias \"{}\" to existing ReferenceDatabase {} for species {} with accessURL: {}", newDBName, oldStyleDBName, speciesName, speciesURL);
			EnsemblReferenceDatabaseGenerator.dbCreator.createReferenceDatabaseToURL(ENSEMBL_URL, speciesURL, oldStyleDBName, newDBName);
		}
		// Only try to create a new database if it's not already there. If this is the first time youv'e run this code on a database,
		// then newDBName will probably *not* be in the cache. But, if you're re-running on a database that has already had some
		// ENSEMBL ReferenceDatabases created, this can help prevent duplicated names/refdbs from being created.
		else if (!objectCache.getRefDbNamesToIds().keySet().contains(newDBName))
		{
			logger.debug("Trying to add an ENSEMBL ReferenceDatabase {} for species: {} with accessURL: {}", newDBName, speciesName, speciesURL);
			EnsemblReferenceDatabaseGenerator.dbCreator.createReferenceDatabaseWithAliases(ENSEMBL_URL, speciesURL, newDBName);
		}
	}

	/**
	 * Set the ReferenceDatabaseCreator object.
	 * @param dbCreator
	 */
	public static void setDbCreator(ReferenceDatabaseCreator dbCreator)
	{
		EnsemblReferenceDatabaseGenerator.dbCreator = dbCreator;
	}

	/**
	 * Set the species-specific URL
	 * @param speciesURL
	 */
	public static void setSpeciesURL(String speciesURL)
	{
		EnsemblReferenceDatabaseGenerator.speciesURL = speciesURL;
	}
	
	
}
