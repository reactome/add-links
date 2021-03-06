package org.reactome.addlinks.kegg;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
	private static Map<String, GKInstance> keggCodesToRefDBMap = new HashMap<String, GKInstance>();
	private static final String KEGG_GENE = "KEGG Gene";

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

	private static synchronized Long createReferenceDatabase(String dbName, String speciesName, String speciesURL, ReferenceObjectCache objectCache) throws Exception
	{
		if (objectCache.getRefDbNamesToIds().keySet().contains(dbName))
		{
			// Even if the cache contains a reference database with this name, we should check that the accessUrl is different. This is because
			// KEGG sometimes has different codes for one species name. For example, "Oryza sativa japonica" can map to either "dosa" or "osa".
			// And "Mycobacterium tuberculosis H37Rv" can have codes "mtu" or"mtv", and in these cases, new ReferenceDatabase objects should be
			// created with the correct KEGG species code.
			@SuppressWarnings("unchecked")
			Set<GKInstance> preexistingRefDBs = (Set<GKInstance>) adaptor.fetchInstanceByAttribute(ReactomeJavaConstants.ReferenceDatabase, ReactomeJavaConstants.accessUrl, "=", speciesURL);

			// If there IS a preexisting reference database that has the same name AND accessUrl, then return that DB_ID.
			if (preexistingRefDBs != null && preexistingRefDBs.size() > 0)
			{
				logger.debug("KEGG ReferenceDatabase name {} already exists, nothing new will be created.", dbName);
				// Just take the DB_ID of the first one. And since the query was based on accessUrl, there should only ever be 1 item in the list.
				return (new ArrayList<GKInstance>(preexistingRefDBs)).get(0).getDBID();
			}
		}
		// If we got to this point, it means that no pre-existing reference database could be found, so let's create a new one.
		logger.debug("Adding a KEGG ReferenceDatabase {} for species: {} with accessURL: {}", dbName, speciesName, speciesURL);
		Long dbId = KEGGReferenceDatabaseGenerator.dbCreator.createReferenceDatabaseWithAliases(KEGG_URL, speciesURL, dbName, "KEGG", "KEGG Gene");
		return dbId;
	}

	/**
	 * Generate new species-specific KEGG reference databases. The new database will be named in the form "KEGG Gene (${SPECIES_NAME})".
	 * @param objectCache - The object cache. The list of species in this cache will be what the new reference databases are based on.
	 */
	public static synchronized void generateSpeciesSpecificReferenceDatabases(ReferenceObjectCache objectCache)
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
						String newDBName = KEGG_GENE + " (" + speciesName + ")";
						if (code.equals("mtu") || code.equals("mtv"))
						{
							newDBName = KEGG_GENE + " ("+speciesName + " - " + code + ")";
						}

						Long dbId = createReferenceDatabase(newDBName, speciesName, speciesURL, objectCache);
						GKInstance refDBInst = adaptor.fetchInstance(dbId);
						KEGGReferenceDatabaseGenerator.keggCodesToRefDBMap.put(code, refDBInst);
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
		String newDBName = KEGG_GENE + " (Viruses)";
		try
		{
			createReferenceDatabase(newDBName, "Viruses", speciesURL, objectCache);
			LinksToCheckCache.getRefDBsToCheck().add(newDBName);
			speciesURL = KEGG_URL.replace("###SP3###", "ag:");
			newDBName = KEGG_GENE + " (Addendum)";
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
	 * Gets the DBID of a KEGG ReferenceDatabase, based on a KEGG species code.
	 * @param keggSpeciesCode KEGG species code.
	 * @return The DBID of the KEGG ReferenceDatabase object, if it exists. If no ReferenceDatabase can be found (or too many matching ReferenceDatabases), then NULL will be returned.
	 */
	public static synchronized Long getKeggReferenceDatabase(String keggSpeciesCode)
	{
		Long dbId = null;
		if (KEGGReferenceDatabaseGenerator.keggCodesToRefDBMap.containsKey(keggSpeciesCode))
		{
			dbId = KEGGReferenceDatabaseGenerator.keggCodesToRefDBMap.get(keggSpeciesCode).getDBID();
		}
		// If it's not in the cache, for some reason, look it up, by the accessUrl which will contain the speciesCode.
		else
		{
			String speciesURL = KEGG_URL.replace("###SP3###", keggSpeciesCode + ":");
			try
			{
				@SuppressWarnings("unchecked")
				Collection<GKInstance> refDBs = (Collection<GKInstance>) KEGGReferenceDatabaseGenerator.adaptor.fetchInstanceByAttribute(ReactomeJavaConstants.ReferenceDatabase, ReactomeJavaConstants.accessUrl, "=", speciesURL);
				// There should be exactly one result!
				if (refDBs.size() == 1)
				{
					GKInstance refDB = refDBs.stream().findFirst().get();
					dbId = refDB.getDBID();
					// And we will add to the cache since it wasn't there.
					KEGGReferenceDatabaseGenerator.keggCodesToRefDBMap.put(keggSpeciesCode, refDB);
				}
				else if (refDBs.isEmpty())
				{
					logger.error("Sorry, there was no KEGG ReferenceDatabase with the KEGG species code {}", keggSpeciesCode);
				}
				else
				{
					logger.warn("More than 1 ({}) KEGG ReferenceDatabases were found in the database with the species code {}. You might want to look into this... ReferenceDatabase objects: {}", refDBs.size(), keggSpeciesCode, refDBs.toString());
				}
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}
		return dbId;
	}

	/**
	 * Generate a KEGG reference database name.
	 * @param objectCache
	 * @param speciesID
	 * @return A string of the form "KEGG Gene (${SPECIES_NAME})"
	 * @deprecated this method looks up KEGG species by the Reactome species ID, which is not the best way to do it.
	 */
	@Deprecated()
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

			if (keggSpeciesName != null)
			{
				targetDB = KEGG_GENE + " (" + keggSpeciesName + ")";
			}
		}
		return targetDB;
	}

	/**
	 * Generates a ReferenceDatabase name based on a KEGG species code. This will do a look-up in the cache for the species code to get the KEGG species name.
	 * @param objectCache
	 * @param keggSpeciesCode A KEGG species code.
	 * @return "Kegg Gene ("+keggSpeciesName+")"
	 */
	public static String generateDBNameFromKeggSpeciesCode(ReferenceObjectCache objectCache, String keggSpeciesCode)
	{
		String targetDB = null;

		String keggSpeciesName = KEGGSpeciesCache.getSpeciesName(keggSpeciesCode);
		String possibleDBName = KEGG_GENE + " ("+keggSpeciesName+")";
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
		String newDBName = KEGG_GENE + " ("+keggSpeciesName + ")";
		if (keggSpeciesName.contains("Mycobacterium tuberculosis H37Rv")) // if we start needing to handle more exceptional cases than just Mycobacterium tuberculosis H37Rv, we may need to rethink the general design...
		{
			newDBName = KEGG_GENE + " ("+keggSpeciesName + " - " + keggPrefix + ")";
		}

		String speciesURL = KEGG_URL.replace("###SP3###", keggPrefix + ":");
		try
		{
			Long dbId = createReferenceDatabase(newDBName, keggSpeciesName, speciesURL, objectCache);
			GKInstance refDBInst = adaptor.fetchInstance(dbId);
			KEGGReferenceDatabaseGenerator.keggCodesToRefDBMap.put(keggPrefix, refDBInst);
			return dbId.toString();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		return newDBName;
	}
}
