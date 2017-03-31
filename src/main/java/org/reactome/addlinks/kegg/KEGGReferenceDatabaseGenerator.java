package org.reactome.addlinks.kegg;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.reactome.addlinks.db.ReferenceDatabaseCreator;
import org.reactome.addlinks.db.ReferenceObjectCache;

public class KEGGReferenceDatabaseGenerator
{
	private static final Logger logger = LogManager.getLogger();
	private static final String ENSEMBL_URL = "http://www.genome.jp/dbget-bin/www_bget?###SP3###+###ID###";
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
			KEGGReferenceDatabaseGenerator.dbCreator.createReferenceDatabaseWithAliases(ENSEMBL_URL, speciesURL, dbName, "KEGG", "KEGG Gene");
			
		}

	}
	
	public static void generateSpeciesSpecificReferenceDatabases(ReferenceObjectCache objectCache) 
	{
		for (String speciesName : objectCache.getSpeciesNamesToIds().keySet())
		{
			String keggCode = KEGGSpeciesCache.getKEGGCode(speciesName);
			if (keggCode != null)
			{
				try
				{
					String speciesURL = ENSEMBL_URL.replace("###SP3###", keggCode + ":");
					String newDBName = "KEGG Gene_"+speciesName.replace(" ", "_");
					createReferenceDatabase(newDBName, speciesName, speciesURL, objectCache);
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
}
