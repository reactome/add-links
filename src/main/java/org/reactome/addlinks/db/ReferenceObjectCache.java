package org.reactome.addlinks.db;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;

public final class ReferenceObjectCache 
{

	//TODO: Find a way to store the caches on disk. For *testing* purposes - only!
	// ...it would probably be faster to load this from a file than from database queries.
	//TODO: Add filtering abilities. Will be useful for testing to speed things up.
	
	private static final Logger logger = LogManager.getLogger();
	//private static ReferenceObjectCache cache;
	private static boolean cachesArePopulated = false;
	
//	private static final String query = "select _displayName, _class, DatabaseObject.db_id as object_db_id, ReferenceEntity.identifier, ReferenceEntity.referenceDatabase as refent_refdb, ReferenceSequence.species " +
//										" from DatabaseObject "+
//										" inner join ReferenceGeneProduct on ReferenceGeneProduct.db_id = DatabaseObject.db_id "+ 
//										" inner join ReferenceEntity on ReferenceEntity.db_id = ReferenceGeneProduct.db_id "+
//										" inner join ReferenceSequence on ReferenceSequence.db_id = ReferenceEntity.db_id "+
//										" where _class  = \'ReferenceGeneProduct\'; ";
//	
//	private static final String refdbMappingQuery = " select distinct name, db_id from ReferenceDatabase_2_name order by name asc; ";
//	
//	private static final String speciesMappingQuery = " select distinct name, db_id, name_rank from Taxon_2_name where name_rank = 0 order by db_id asc, name_rank asc, name asc; ";
	

	private static boolean cacheInitializedMessageHasBeenPrinted = false;
	
	private static MySQLAdaptor adapter;
	
	public ReferenceObjectCache(MySQLAdaptor adapter) 
	{
		//if (ReferenceObjectCache.cache == null)
		if (!ReferenceObjectCache.cachesArePopulated)
		{
			ReferenceObjectCache.adapter = adapter;
			logger.info("Cache is not initialized. Will initialize now.");
			//ReferenceObjectCache.cache = ReferenceObjectCache.populateCaches(ReferenceObjectCache.adapter);
			ReferenceObjectCache.populateCaches(adapter);
		}
		else
		{
			if (!cacheInitializedMessageHasBeenPrinted)
			{
				logger.info("Cache is already initialized.");
				ReferenceObjectCache.cacheInitializedMessageHasBeenPrinted = true;
			}
		}
		
		//return ReferenceObjectCache.cache;
	}
	
//	public static void setAdapter(MySQLAdaptor adapter)
//	{
//		ReferenceObjectCache.adapter = adapter;
//	}
	
	private static void buildReferenceCaches(String className, Map<String,List<GKInstance>> cacheBySpecies, Map<String,GKInstance> cacheByID, Map<String,List<GKInstance>> cacheByRefDB) throws Exception
	{
		logger.debug("Building caches of {}", className);
		
		@SuppressWarnings("unchecked")
		Collection<GKInstance> referenceObjects = ReferenceObjectCache.adapter.fetchInstancesByClass(className);
		for (GKInstance referenceObject : referenceObjects)
		{
			//Get all the other values.
			//(
			// I still don't like doing this... I think doing it all in one query would run faster, but I'm starting to think 
			// it's better to stick with the existing API. It's a little weird, but it seemsd to work. Once you get used to it, that is. ;)
			//)
			//adapter.fastLoadInstanceAttributeValues(refGeneProduct);
			
			//Now, insert into the caches.

			// ReferenceMolecules do not have associated species.
			if ( !className.equals(ReactomeJavaConstants.ReferenceMolecule) )
			{
				//String species = null;
				List<String> allSpecies = null;
				//ReferenceRNASequence objects don't always have Species info, for some reason, so we need to get that from the associated ReferenceGeneProduct
				if (className.equals(ReactomeJavaConstants.ReferenceRNASequence))
				{
					if (null == referenceObject.getAttributeValue(ReactomeJavaConstants.species))
					{
						Collection<GKInstance> referringRefTranscripts = (Collection<GKInstance>) referenceObject.getReferers(ReactomeJavaConstants.referenceTranscript);
						allSpecies = new ArrayList<>(referringRefTranscripts.size());
						// Populate a list of species that this ReferenceRNASequence could be cached by.
						for (GKInstance refTranscript : referringRefTranscripts)
						{
							allSpecies.add(  ((GKInstance)refTranscript.getAttributeValue(ReactomeJavaConstants.species)).getDBID().toString() );
						}
					}
				}

				// If the list wasn't initialized yet, it means that we are probably dealing with a ReferenceGeneProduct or a ReferenceDNASequence which should
				// have its own species.
				if (allSpecies == null)
				{
					allSpecies = new ArrayList<String>(1);
					String species = String.valueOf( ((GKInstance) referenceObject.getAttributeValue(ReactomeJavaConstants.species)).getDBID() );
					allSpecies.add(species);
				}
				
				for (String species : allSpecies)
				{
					//Add to the Species Cache
					if (! cacheBySpecies.containsKey(species))
					{
						List<GKInstance> bySpecies = new LinkedList<GKInstance>();
						bySpecies.add(referenceObject);
						cacheBySpecies.put(species, bySpecies);
					}
					else
					{
						cacheBySpecies.get(species).add(referenceObject);
					}
				}
			}
			//ReferenceDatabase Cache
			//If this species is not yet cached...
			String refDBID = String.valueOf(((GKInstance) referenceObject.getAttributeValue(ReactomeJavaConstants.referenceDatabase)).getDBID());
			if (! cacheByRefDB.containsKey(refDBID))
			{
				List<GKInstance> byRefDb = new LinkedList<GKInstance>();
				byRefDb.add(referenceObject);
				cacheByRefDB.put(refDBID, byRefDb);
			}
			else
			{
				cacheByRefDB.get(refDBID).add(referenceObject);
			}
			// ID Cache
			cacheByID.put(String.valueOf(referenceObject.getDBID()), referenceObject);
		}
		logger.debug("Built {} caches: cacheById, cacheByRefDb, and cacheBySpecies caches.",className);
		logger.info("\n\tKeys in cache-by-refdb: {};"
				+ "\n\tkeys in cache-by-species: {};"
				+ "\n\tkeys in cache-by-id: {};",
					cacheByRefDB.size(),
					(!className.equals(ReactomeJavaConstants.ReferenceMolecule) ? cacheBySpecies.size() : "N/A"),
					cacheByID.size()
				);
	}

	private static synchronized void populateCaches(MySQLAdaptor adapter)
	{
		//ReferenceObjectCache.setAdapter(adapter);
		ReferenceObjectCache.adapter = adapter;
		if (ReferenceObjectCache.adapter!=null)
		{
			try
			{
				logger.info("Building ReferenceObject caches...");
				ReferenceObjectCache.buildReferenceCaches(ReactomeJavaConstants.ReferenceGeneProduct, refGeneProdCacheBySpecies, refGeneProdCacheById, refGeneProdCacheByRefDb);
				
				ReferenceObjectCache.buildReferenceCaches(ReactomeJavaConstants.ReferenceDNASequence, refDNASeqCacheBySpecies, refDNASeqCacheById, refDNASeqCacheByRefDb);
				
				ReferenceObjectCache.buildReferenceCaches(ReactomeJavaConstants.ReferenceRNASequence, refRNASeqCacheBySpecies, refRNASeqCacheById, refRNASeqCacheByRefDb);
				
				ReferenceObjectCache.buildReferenceCaches(ReactomeJavaConstants.ReferenceMolecule, null, moleculeCacheByID, moleculeCacheByRefDB);
				
				// Build up the Reference Database caches.
				@SuppressWarnings("unchecked")
				Collection<GKInstance> refDBs = adapter.fetchInstancesByClass(ReactomeJavaConstants.ReferenceDatabase);
				
				for (GKInstance refDB : refDBs)
				{
					String db_id = refDB.getDBID().toString();
					@SuppressWarnings("unchecked")
					List<String> names = (List<String>) refDB.getAttributeValuesList(ReactomeJavaConstants.name);
					List<String> listOfIds;
					// Because in some cases (I'm mostly thinking of Ensembl here), there could be a number of ReferenceDatabase
					// objects all sharing the same name (WHERE name_rank = 0). We want ALL names, not just the first one.
					for (String name : names)
					{
						// if the 1:n cache of names-to-IDs already has "name" then just add the db_id to the existing list.
						if (ReferenceObjectCache.refDbNamesToIds.containsKey(name))
						{
							listOfIds = ReferenceObjectCache.refDbNamesToIds.get(name);
						}
						else
						{
							listOfIds = new ArrayList<String>(1);
						}
						listOfIds.add(db_id);
		
						ReferenceObjectCache.refDbNamesToIds.put(name, listOfIds);
					
						// refdbMapping is a 1:n from db_ids to names.
						List<String> listOfNames;
						if (ReferenceObjectCache.refdbMapping.containsKey(db_id))
						{
							listOfNames = ReferenceObjectCache.refdbMapping.get(db_id);
						}
						else
						{
							listOfNames = new ArrayList<String>(1);
						}
						listOfNames.add(name);
						ReferenceObjectCache.refdbMapping.put(db_id,listOfNames);
					}
				}
				logger.debug("Built refdbMapping cache.");
				
				// Build up the species caches.
				@SuppressWarnings("unchecked")
				Collection<GKInstance> species = adapter.fetchInstancesByClass(ReactomeJavaConstants.Species);
				for (GKInstance singleSpecies : species)
				{
					String db_id = singleSpecies.getDBID().toString();
					String name = (String)singleSpecies.getAttributeValue(ReactomeJavaConstants.name);
					List<String> listOfIds;
					if (ReferenceObjectCache.speciesNamesToIds.containsKey(name))
					{
						listOfIds = ReferenceObjectCache.speciesNamesToIds.get(name);
					}
					else
					{
						listOfIds = new ArrayList<String>(1);
					}
					listOfIds.add(db_id);
					ReferenceObjectCache.speciesNamesToIds.put(name, listOfIds);
					
					List<String> listOfNames;
					if (ReferenceObjectCache.speciesMapping.containsKey(db_id))
					{
						listOfNames = ReferenceObjectCache.speciesMapping.get(db_id);
					}
					else
					{
						listOfNames = new ArrayList<String>(1);
					}
					listOfNames.add(name);
					ReferenceObjectCache.speciesMapping.put(db_id,listOfNames);
				}
				logger.debug("Built speciesMapping cache.");

				logger.info("All caches initialized."
						+ "\n\tkeys in refDbMapping: {};"
						+ "\n\tkeys in speciesMapping: {}",
								ReferenceObjectCache.refdbMapping.size(),
								ReferenceObjectCache.speciesMapping.size());
				ReferenceObjectCache.cachesArePopulated = true;
			}
			catch (Exception e)
			{
				logger.error("Error ocurred while building the caches: {}",e.getMessage());
				e.printStackTrace();
				// If we can't even build the caches, something's gone very wrong. Throw an exception up the stack.
				throw new RuntimeException(e);
			}
		}
		else
		{
			logger.error("The adapter is null. Please set the adapter before attempting to initialize the cache.");
			
		}
	}
	
	// ReferenceMolecule caches
	private static Map<String, GKInstance> moleculeCacheByID = new HashMap<String, GKInstance>();
	private static Map<String, List<GKInstance>> moleculeCacheByRefDB = new HashMap<String, List<GKInstance>>();
	
	// ReferenceDNASequence caches
	private static Map<String,List<GKInstance>> refDNASeqCacheBySpecies = new HashMap<String,List<GKInstance>>();
	private static Map<String,List<GKInstance>> refDNASeqCacheByRefDb = new HashMap<String,List<GKInstance>>();
	private static Map<String,GKInstance> refDNASeqCacheById = new HashMap<String,GKInstance>();

	// ReferenceRNASequence caches
	private static Map<String,List<GKInstance>> refRNASeqCacheBySpecies = new HashMap<String,List<GKInstance>>();
	private static Map<String,List<GKInstance>> refRNASeqCacheByRefDb = new HashMap<String,List<GKInstance>>();
	private static Map<String,GKInstance> refRNASeqCacheById = new HashMap<String,GKInstance>();
	
	// ReferenceGeneProduct caches
	private static Map<String,List<GKInstance>> refGeneProdCacheBySpecies = new HashMap<String,List<GKInstance>>();
	private static Map<String,List<GKInstance>> refGeneProdCacheByRefDb = new HashMap<String,List<GKInstance>>();
	private static Map<String,GKInstance> refGeneProdCacheById = new HashMap<String,GKInstance>();
	
	//also need some secondary mappings: species name-to-id and refdb name-to-id
	//These really should be 1:n mappings...
	private static Map<String,List<String>> speciesMapping = new HashMap<String,List<String>>();
	private static Map<String,List<String>> refdbMapping = new HashMap<String,List<String>>();
	//...Aaaaaand mappings from names to IDs which will be 1:n
	private static Map<String,List<String>> refDbNamesToIds = new HashMap<String,List<String>>();
	private static Map<String,List<String>> speciesNamesToIds = new HashMap<String,List<String>>();
	
	/**
	 * Get a list of ReferenceGeneProduct shells keyed by Reference Database.
	 * @param refDb
	 * @return
	 */
	public List<GKInstance> getByRefDb(String refDb, String className)
	{
		switch (className)
		{
			case ReactomeJavaConstants.ReferenceGeneProduct:
				return ReferenceObjectCache.refGeneProdCacheByRefDb.containsKey(refDb) ? ReferenceObjectCache.refGeneProdCacheByRefDb.get(refDb) : new ArrayList<GKInstance>(0);
			case ReactomeJavaConstants.ReferenceDNASequence:
				return ReferenceObjectCache.refDNASeqCacheByRefDb.containsKey(refDb) ? ReferenceObjectCache.refDNASeqCacheByRefDb.get(refDb) : new ArrayList<GKInstance>(0);
			case ReactomeJavaConstants.ReferenceRNASequence:
				return ReferenceObjectCache.refRNASeqCacheByRefDb.containsKey(refDb) ? ReferenceObjectCache.refRNASeqCacheByRefDb.get(refDb) : new ArrayList<GKInstance>(0);
			case ReactomeJavaConstants.ReferenceMolecule:
				return ReferenceObjectCache.moleculeCacheByRefDB.containsKey(refDb) ? ReferenceObjectCache.moleculeCacheByRefDB.get(refDb) : new ArrayList<GKInstance>(0);
			default:
				logger.error("Invalid className: {} Nothing will be returned.", className);
				return null;
		}
	}
	
	/**
	 * Get a list of ReferenceGeneProduct shells keyed by Species.
	 * @param species
	 * @return
	 */
	public List<GKInstance> getBySpecies(String species, String className)
	{
		switch (className)
		{
			case ReactomeJavaConstants.ReferenceGeneProduct:
				return ReferenceObjectCache.refGeneProdCacheBySpecies.get(species);
			case ReactomeJavaConstants.ReferenceDNASequence:
				return ReferenceObjectCache.refDNASeqCacheBySpecies.get(species);
			case ReactomeJavaConstants.ReferenceRNASequence:
				return ReferenceObjectCache.refRNASeqCacheBySpecies.get(species);
			default:
				logger.error("Invalid className: {} Nothing will be returned.", className);
				return null;
		}
	}
	
	/**
	 * Get a ReferenceGeneProduct shell by its DB_ID.
	 * @param id
	 * @return
	 */
	public GKInstance getById(String id)
	{
		return ReferenceObjectCache.refGeneProdCacheById.get(id);
	}
	
	/**
	 * Retreive a list of ReferenceGeneProduct shells filtered by Reference Database <em>and</em> Species.
	 * @param refDb
	 * @param species
	 * @return
	 */
	public List<GKInstance> getByRefDbAndSpecies(String refDb, String species, String className)
	{
		//Get a list by referenceDatabase
		List<GKInstance> byRefDb = this.getByRefDb(refDb, className);
		//Now filter the items in that list by species.
		List<GKInstance> objectsByRefDbAndSpecies = byRefDb.stream().filter(p -> {
			try
			{
				return (((GKInstance)p.getAttributeValue(ReactomeJavaConstants.species)).getDBID().longValue()) == Long.valueOf(species); 
			}
			catch (Exception e)
			{
				e.printStackTrace();
				return false;
			}
		}).collect(Collectors.toList());
		
		return objectsByRefDbAndSpecies;
	}
	
	/**
	 * Returns a set of the keys used to cache by Reference Database.
	 * @return
	 */
	public Set<String> getListOfRefGeneProdRefDbs()
	{
		return ReferenceObjectCache.refGeneProdCacheByRefDb.keySet();
	}

	/**
	 * Returns a map of Reference Database names, keyed by their DB_IDs.
	 * @return
	 */
	public Map<String,List<String>> getRefDBMappings()
	{
		return ReferenceObjectCache.refdbMapping;
	}
	
	/**
	 * Returns a 1:n mapping of Reference Datbase names mapped to a list of DB_IDs.
	 * @return
	 */
	public Map<String,List<String>> getRefDbNamesToIds()
	{
		return ReferenceObjectCache.refDbNamesToIds;
	}
	
	/**
	 * Returns a 1:n mapping of Species names mapped to a list of DB_IDs.
	 * @return
	 */
	public Map<String,List<String>> getSpeciesNamesToIds()
	{
		return ReferenceObjectCache.speciesNamesToIds;
	}
	
	/**
	 * Returns a set of the keys used to cache by Species.
	 * @return
	 */
	public Set<String> getListOfSpecies()
	{
		return ReferenceObjectCache.refGeneProdCacheBySpecies.keySet();
	}
	
	/**
	 * Returns a map of Species names, keyed by their DB_IDs.
	 * @return
	 */
	public Map<String,List<String>> getSpeciesMappings()
	{
		return ReferenceObjectCache.speciesMapping;
	}
}
