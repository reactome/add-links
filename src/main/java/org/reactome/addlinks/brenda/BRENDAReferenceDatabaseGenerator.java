package org.reactome.addlinks.brenda;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.persistence.MySQLAdaptor;
import org.reactome.addlinks.dataretrieval.brenda.BRENDASoapClient;
import org.reactome.addlinks.db.ReferenceDatabaseCreator;
import org.reactome.addlinks.db.ReferenceObjectCache;

public final class BRENDAReferenceDatabaseGenerator
{
	private static final String BRENDA_URL = "http://www.brenda-enzymes.info/index.php4";
	private static final Logger logger = LogManager.getLogger();
	private static ReferenceDatabaseCreator dbCreator;
	private static String accessURL = "http://www.brenda-enzymes.info/enzyme.php?ecno=###ID###&organism[]=###SP###";

	/**
	 * private constructor in a final class: This class is really more of a utility
	 * class - creating multiple instances of it probably wouldn't make sense. 
	 */
	private BRENDAReferenceDatabaseGenerator()
	{
		
	}

	public static void setDBCreator(ReferenceDatabaseCreator creator)
	{
		BRENDAReferenceDatabaseGenerator.dbCreator = creator;
	}
	
	/**
	 * Create species-specific reference databases for use with BRENDA. To do this, you will need to make a web-service 
	 * call to BRENDA to get their species list.
	 * @param client - WebService client, for communicating with BRENDA.
	 * @param speciesURL - Webservice endpoint for getting the BRENDA species list.
	 * @param objectCache
	 * @param dbAdapter
	 */
	public static void createReferenceDatabases(BRENDASoapClient client, String speciesURL, ReferenceObjectCache objectCache, MySQLAdaptor dbAdapter, long personID)
	{
		BRENDASpeciesCache.buildCache(client, speciesURL, objectCache, dbAdapter, personID);
		// Now that the cache exists, loop through it and create a ReferenceDatabase object for each cached species name.
		for (String speciesName : BRENDASpeciesCache.getCache())
		{
			BRENDAReferenceDatabaseGenerator.createReferenceDatabase(speciesName);
		}
	}
	
	/**
	 * Creates a BRENDA species-specific ReferenceDatabase object for a given species name. You should ensure that you only uses species
	 * which you *know* are valid for BRENDA.
	 * @param speciesName
	 * @throws Exception 
	 */
	protected static void createReferenceDatabase(String speciesName)
	{
		// The whitespace in the species name needs to be replaced with a "+" for BRENDA.
		try
		{
			BRENDAReferenceDatabaseGenerator.dbCreator.createReferenceDatabaseToURL(BRENDA_URL, accessURL.replace("###SP###", speciesName.replace(" ", "+")), "BRENDA ("+speciesName+")");
		}
		catch (Exception e)
		{
			logger.error("Error ({}) occurred while creating BRENDA ReferenceDatabase for species {}", e.getMessage(), speciesName);
			e.printStackTrace();
		}
	}
	
}
