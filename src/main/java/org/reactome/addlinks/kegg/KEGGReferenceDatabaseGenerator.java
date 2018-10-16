package org.reactome.addlinks.kegg;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.reactome.addlinks.db.ReferenceDatabaseCreator;
import org.reactome.addlinks.db.ReferenceObjectCache;
import org.reactome.addlinks.linkchecking.LinksToCheckCache;

public class KEGGReferenceDatabaseGenerator
{
	private static final Logger logger = LogManager.getLogger();
	private static final String KEGG_URL = "http://www.genome.jp/dbget-bin/www_bget?###SP3######ID###";
	private static ReferenceDatabaseCreator dbCreator;
	private static MySQLAdaptor adaptor;
	
	// private constructor to avoid instantiation.
	private KEGGReferenceDatabaseGenerator()
	{
	}
	
	public static void setDBAdaptor(MySQLAdaptor adaptor)
	{
		KEGGReferenceDatabaseGenerator.adaptor = adaptor;
	}
	
	public static void setDBCreator(ReferenceDatabaseCreator creator)
	{
		KEGGReferenceDatabaseGenerator.dbCreator = creator;
	}
	
	private static Long createReferenceDatabase(String dbName, String speciesName, String speciesURL, ReferenceObjectCache objectCache) throws Exception
	{
		if (objectCache.getRefDbNamesToIds().keySet().contains(dbName))
		{
			// Even if the cache contains a reference database with this name, we should check that the accessUrl is different. This is because
			// KEGG sometimes has different codes for one species name. For example, "Oryza sativa japonica" can map to either "dosa" or "osa".
			// and "Mycobacterium tuberculosis H37Rv" can have codes "mtu" or"mtv", and in these cases, new ReferenceDatabase objects should be
			// created with the correct KEGG species code.
			@SuppressWarnings("unchecked")
			Set<GKInstance> preexistingRefDBs = (Set<GKInstance>) adaptor.fetchInstanceByAttribute(ReactomeJavaConstants.ReferenceDatabase, ReactomeJavaConstants.accessUrl, "=", speciesURL);
			
			// If there IS a preexisting reference database that has the same name AND accessUrl, then return that DB_ID.
			if (preexistingRefDBs != null && preexistingRefDBs.size() > 0)
			{
				logger.debug("KEGG ReferenceDatabase name {} already exists, nothing new will be created.", dbName);
				// Just take the DB_ID of the first one.
				//return Long.parseLong(objectCache.getRefDbNamesToIds().get(dbName).get(0));
				return (new ArrayList<GKInstance>(preexistingRefDBs)).get(0).getDBID();
			}
		}
		// If we got to this point, it means that no pre-existing reference database could be found, so let's create a new one.
		logger.debug("Adding a KEGG ReferenceDatabase {} for species: {} with accessURL: {}", dbName, speciesName, speciesURL);
		return KEGGReferenceDatabaseGenerator.dbCreator.createReferenceDatabaseWithAliases(KEGG_URL, speciesURL, dbName, "KEGG", "KEGG Gene");

	}
	
	/**
	 * Generate new species-specific KEGG reference databases. The new database will be named in the form "KEGG Gene (${SPECIES_NAME})". 
	 * @param objectCache - The object cache. The list of species in this cache will be what the new reference databases are based on.
	 */
	public static void generateSpeciesSpecificReferenceDatabases(ReferenceObjectCache objectCache) 
	{
		for (String speciesName : objectCache.getSpeciesNamesToIds().keySet())
		{
			List<String> keggCodes = KEGGSpeciesCache.getKEGGCodes(speciesName);
			// If we couldn't get a KEGG code on a direct match, let's try an indirect match. It could work,
			// as sometimes KEGG and Reactome have *similar* names, for example: "Plasmodium falciparum HB3" (KEGG) and "Plasmodium falciparum" (Reactome)
			// If this works, the database will be created using the *REACTOME* species name.
			if (keggCodes == null)
			{
				String keggSpeciesName = KEGGSpeciesCache.getKeggSpeciesNames().parallelStream().filter(keggName -> keggName.contains(speciesName) || speciesName.contains(keggName)).findFirst().orElse(null);
				keggCodes = KEGGSpeciesCache.getKEGGCodes(keggSpeciesName);
			}
			
			if (keggCodes != null)
			{
				// Sometimes, a KEGG species will have multiple KEGG Codes. For example, "Oryza sativa japonica (Japanese rice)" has two codes: "osa" and "dosa".
				for (String code : keggCodes)
				{
					try
					{
						String speciesURL = KEGG_URL.replace("###SP3###", code + ":");
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
			}
			else
			{
				logger.debug("No KEGG code for {}, so no KEGG species-specific database will be created.", speciesName);
			}
		}
		// Special cases for viruses and "addendum"
		String speciesURL = KEGG_URL.replace("###SP3###", "vg:");
		String newDBName = "KEGG Gene (Viruses)";
		try
		{
			createReferenceDatabase(newDBName, "Viruses", speciesURL, objectCache);
			speciesURL = KEGG_URL.replace("###SP3###", "ag:");
			newDBName = "KEGG Gene (Addendum)";
			createReferenceDatabase(newDBName, "Addendum", speciesURL, objectCache);
		}
		catch (Exception e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		LinksToCheckCache.getRefDBsToCheck().add(newDBName);
		
	}
	
	/**
	 * Generate a KEGG reference database name.
	 * @param objectCache
	 * @param speciesID
	 * @return A string of the form "KEGG Gene (${SPECIES_NAME})"
	 */
	public static String generateDBNameFromReactomeSpecies(ReferenceObjectCache objectCache, String speciesID)
	{
		String targetDB = null;
		if (speciesID != null && objectCache.getSpeciesNamesByID().get(speciesID) != null)
		{
			String keggSpeciesName = objectCache.getSpeciesNamesByID().get(speciesID).stream()
															.filter(s ->
															{
																return KEGGSpeciesCache.getKEGGCodes(s) != null;
															})
															.findFirst().orElse(null);
//			if (keggSpeciesName == null)
//			{
//				// Ok, there was no exact match. Maybe there's a substring match?
//				// We'll have to see if the Reactome species names are substrings of any KEGG species names, or vice-versa.
//				try
//				{
//					GKInstance species = KEGGReferenceDatabaseGenerator.adaptor.fetchInstance(Long.parseLong(speciesID));
//
//					@SuppressWarnings("unchecked")
//					Collection<String> reactomeSpeciesNames = (Collection<String>) species.getAttributeValuesList(ReactomeJavaConstants.name);
//
//					// Note: if this succeeds, the value in keggSpeciesName will actually be a closely-matching *REACTOME* species name
//					// This is because when we created the databases, we did it with the REACTOME names, so we need to return a REACTOME name
//					// in the case where KEGG and Reactome have species names that are close but not exact matches.
//					keggSpeciesName = reactomeSpeciesNames.parallelStream()
//										.filter( rName -> KEGGSpeciesCache.getKeggSpeciesNames().stream()
//																			.filter(kName -> kName.contains(rName) || rName.contains(kName))
//																			.collect(Collectors.toList()).size() > 0)
//										.findFirst().orElse(null);
//
//				}
//				catch (Exception e)
//				{
//					e.printStackTrace();
//				}
//			}
			if (keggSpeciesName != null)
			{
				targetDB = "KEGG Gene (" + keggSpeciesName + ")";
			}
//			else
//			{
//				logger.warn("Tried to generate a KEGG DB Name for the species {} ({}) but no KEGG species name was found!", speciesID, objectCache.getSpeciesNamesByID().get(speciesID));
//			}
		}
		return targetDB;
	}
	
	public static String generateDBNameFromKeggSpeciesCode(ReferenceObjectCache objectCache, String keggSpeciesCode)
	{
		String targetDB = null;
		
		String keggSpeciesName = KEGGSpeciesCache.getSpeciesName(keggSpeciesCode);
		String possibleDBName = "Kegg Gene ("+keggSpeciesName+")";
		if (objectCache.getRefDbNamesToIds().keySet().contains(possibleDBName))
		{
			targetDB = possibleDBName;
		}
		return targetDB;
	}
	
	/**
	 * To be used when you have a valid KEGG identifier but you can't figure out which Reactome species it should be for because there
	 * is a different between the KEGG and Reactome species names. Use this method to create a new ReferenceDatabase object based on the KEGG 
	 * prefix and species name. Rebuilding caches is <em>STRONGLY</em> recommended after calling this method.
	 * @param keggPrefix
	 * @param keggSpeciesName
	 * @return The name of the new ReferenceDatabase.
	 */
	public static synchronized String createReferenceDatabaseFromKEGGData(String keggPrefix, String keggSpeciesName, ReferenceObjectCache objectCache)
	{
		String speciesURL = KEGG_URL.replace("###SP3###", keggPrefix + ":");
		String newDBName = "KEGG Gene ("+keggSpeciesName + ")";
		try
		{
			return new Long(createReferenceDatabase(newDBName, keggSpeciesName, speciesURL, objectCache)).toString();
		}
		catch (Exception e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return newDBName;
	}
}
