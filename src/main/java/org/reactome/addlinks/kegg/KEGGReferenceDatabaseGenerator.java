package org.reactome.addlinks.kegg;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.reactome.addlinks.db.ReferenceDatabaseCreator;
import org.reactome.addlinks.db.ReferenceObjectCache;
import org.reactome.addlinks.linkchecking.LinksToCheckCache;

public class KEGGReferenceDatabaseGenerator
{
	private static final Logger logger = LogManager.getLogger();
	private static final String KEGG_URL = "http://www.genome.jp/dbget-bin/www_bget?###SP3######ID###";
	private static ReferenceDatabaseCreator dbCreator;
	
	private KEGGReferenceDatabaseGenerator()
	{
		
	}
	
	public static void setDBCreator(ReferenceDatabaseCreator creator)
	{
		KEGGReferenceDatabaseGenerator.dbCreator = creator;
	}
	
	private static void createReferenceDatabase(String dbName, String speciesName, String speciesURL, ReferenceObjectCache objectCache) throws Exception
	{
		if (objectCache.getRefDbNamesToIds().keySet().contains(dbName))
		{
			logger.debug("KEGG ReferenceDatabase name {} already exists, nothing new will be created.", dbName);
		}
		else
		{
			logger.debug("Adding a KEGG ReferenceDatabase {} for species: {} with accessURL: {}", dbName, speciesName, speciesURL);
			KEGGReferenceDatabaseGenerator.dbCreator.createReferenceDatabaseWithAliases(KEGG_URL, speciesURL, dbName, "KEGG", "KEGG Gene");
			
		}

	}
	
	/**
	 * Generate new species-specific KEGG reference databases. The new database will be named in the form "KEGG Gene (${SPECIES_NAME})". 
	 * @param objectCache - The object cache. The list of species in this cache will be what the new reference databases are based on.
	 */
	public static void generateSpeciesSpecificReferenceDatabases(ReferenceObjectCache objectCache) 
	{
		for (String speciesName : objectCache.getSpeciesNamesToIds().keySet())
		{
			String keggCode = KEGGSpeciesCache.getKEGGCode(speciesName);
			if (keggCode != null)
			{
				try
				{
					String speciesURL = KEGG_URL.replace("###SP3###", keggCode + ":");
					String newDBName = "KEGG Gene ("+speciesName + ")";
					createReferenceDatabase(newDBName, speciesName, speciesURL, objectCache);
					LinksToCheckCache.getRefDBsToCheck().add(newDBName);
				}
				catch(Exception e)
				{
					logger.error("Error while attempting to create a KEGG reference database: {}", e.getMessage());
					e.printStackTrace();
				}
			}
			else
			{
				logger.info("No KEGG code for {}, so no KEGG species-specific database will be created.", speciesName);
			}
		}
	}
	
	/**
	 * Generate a KEGG reference database name.
	 * @param objectCache
	 * @param speciesID
	 * @return A string of the form "KEGG Gene (${SPECIES_NAME})"
	 */
	public static String generateKeggDBName(ReferenceObjectCache objectCache, String speciesID)
	{
		String targetDB = null;
		if (speciesID != null && objectCache.getSpeciesNamesByID().get(speciesID) != null)
		{
			String speciesName = objectCache.getSpeciesNamesByID().get(speciesID).stream()
															.filter(s ->
															{
																return KEGGSpeciesCache.getKEGGCode(s) != null;
															})
															.findFirst().orElse(null);
			if (speciesName != null)
			{
				targetDB = "KEGG Gene (" + speciesName + ")";
			}
		}
		return targetDB;
	}
}
