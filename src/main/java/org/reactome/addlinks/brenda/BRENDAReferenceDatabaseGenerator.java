package org.reactome.addlinks.brenda;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.reactome.addlinks.db.ReferenceDatabaseCreator;

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
	 * Creates a BRENDA species-specific ReferenceDatabase object for a given species name. You should ensure that you only uses species
	 * which you *know* are valid for BRENDA.
	 * @param speciesName
	 * @throws Exception 
	 */
	public static void createReferenceDatabase(String speciesName)
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
