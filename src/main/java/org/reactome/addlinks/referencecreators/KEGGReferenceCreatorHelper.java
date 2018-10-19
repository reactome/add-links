package org.reactome.addlinks.referencecreators;

import org.apache.logging.log4j.Logger;
import org.reactome.addlinks.db.ReferenceObjectCache;
import org.reactome.addlinks.kegg.KEGGReferenceDatabaseGenerator;
import org.reactome.addlinks.kegg.KEGGSpeciesCache;

/**
 * This helper class can be used to help create a KEGG reference. Functionality provided by this class is to determine which KEGG ReferenceDatabase
 * a KEGG identifier should be associated with. If it is determined that the ReferenceDatabase does yet exist, it will be created.
 * This code exists in this class because this functionality is needed by both KEGGReferenceCreator and also
 * UPMappedIdentifiersReferenceCreator (which can create KEGG references from UniProt mappings). This could probably be refactored into something
 * a little tidier, but for now, having it in one place reduces some redundancy, so that's good enough for now.
 * @author sshorser
 *
 */
class KEGGReferenceCreatorHelper
{
	private ReferenceObjectCache refObjectCache;
	private Logger logger;
	
	public KEGGReferenceCreatorHelper(ReferenceObjectCache cache, Logger logger)
	{
		this.refObjectCache = cache;
		this.logger = logger;
	}
	
	private synchronized String createNewKEGGReferenceDatabase(String targetIdentifier, String keggPrefix)
	{
		String targetDB = null;
		if (keggPrefix != null)
		{
			// we have a valid KEGG prefix, so let's try to use that to create a new RefereneDatabase.
			String keggSpeciesName = KEGGSpeciesCache.getSpeciesName(keggPrefix);
			if (keggSpeciesName != null)
			{
				targetDB = KEGGReferenceDatabaseGenerator.createReferenceDatabaseFromKEGGData(keggPrefix, keggSpeciesName, refObjectCache);
				// TODO: Figure out a way to only refresh the cache if a new database was actually created. If the database already existed, it won't be created.
				this.refObjectCache.rebuildRefDBNamesAndMappings();
			}
			if (targetDB == null)
			{
				logger.error("Could not create a new KEGG ReferenceDatabase for the KEGG code {} for KEGG species \"{}\". Identifier {} will not be added, since there is no ReferenceDatabase for it.", keggPrefix, keggSpeciesName, targetIdentifier);
			}
		}
		return targetDB;
	}
	
//	public synchronized  String createNewKEGGReferenceDatabase(ReferenceObjectCache objectCache, String keggIdentifier, String keggPrefix)
//	{
//		String targetDB = null;
//		// we have a valid KEGG prefix, so let's try to use that to create a new RefereneDatabase.
//		String keggSpeciesName = KEGGSpeciesCache.getSpeciesName(keggPrefix);
//		if (keggSpeciesName != null)
//		{
//			targetDB = KEGGReferenceDatabaseGenerator.createReferenceDatabaseFromKEGGData(keggPrefix, keggSpeciesName, objectCache);
//			objectCache.rebuildRefDBNamesAndMappings();
//		}
//		if (targetDB == null)
//		{
//			logger.error("Could not create a new KEGG ReferenceDatabase for the KEGG code {} for KEGG species \"{}\". Identifier {} will not be added, since there is no ReferenceDatabase for it.", keggPrefix, keggSpeciesName, keggIdentifier);
//		}
//		return targetDB;
//	}
	
	/**
	 * This function will determine which KEGG ReferenceDatabase should be used for a KEGG identifier. If there is no ReferenceDatabase currently in the system,
	 * it will *create* a new ReferenceDatabase object.
	 * @param keggIdentifier - The KEGG Identifier
	 * @param keggPrefix - The prefix for the KEGG Identifier.
	 * @return An array with two elements: the first element is the ReferenceDatabase (a DB_ID, though in some cases, it could be the name of the ReferenceDatabase); the second element is the Kegg identifier, which might be 
	 */
	public synchronized String[] determineKeggReferenceDatabase(String keggIdentifier, String keggPrefix)
	{
		String targetDB = null;
		// "vg:" and "ad:" aren't in the species list because they are not actually species. So that's why it's OK to check for them here, after
		// the identifier has already been pruned.
		if (keggIdentifier.startsWith("vg:"))
		{
			targetDB = "KEGG Gene (Viruses)";
			keggIdentifier = keggIdentifier.replaceFirst("vg:", "");
		}
		else if (keggIdentifier.startsWith("ag:"))
		{
			targetDB = "KEGG Gene (Addendum)";
			keggIdentifier = keggIdentifier.replaceFirst("ag:", "");
		}
		else
		{
			Long targetDBID = KEGGReferenceDatabaseGenerator.getKeggReferenceDatabase(keggPrefix);
			if (targetDBID != null)
			{
				targetDB = targetDBID.toString();
			}
			// If targetDB is STILL NULL, it means we weren't able to determine which KEGG ReferenceDatabase to use for this keggIdentifier. 
			// So, we can't add the cross-reference since we don't know which species-specific ReferenceDatabase to use. 
			if (targetDB == null)
			{
				logger.warn("No KEGG DB Name could be obtained for this identifier: {}. The next step is to try to create a *new* ReferenceDatabase.", keggIdentifier);
				
				if (keggPrefix != null)
				{
					targetDB = this.createNewKEGGReferenceDatabase(keggIdentifier, keggPrefix);
				}
			}
		}
		return new String[] { targetDB, keggIdentifier } ;
	}
}
