package org.reactome.addlinks.db;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.InvalidAttributeException;

public final class ReferenceObjectCache 
{

	//TODO: Find a way to store the caches on disk. For *testing* purposes - only!
	// ...it would probably be faster to load this from a file than from database queries.
	//TODO: Add filtering abilities. Will be useful for testing to speed things up.
	
	private static final Logger logger = LogManager.getLogger();
	//private static ReferenceObjectCache cache;
	private static boolean cachesArePopulated = false;
	
	private static boolean lazyLoad = false;
	
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
	
	public ReferenceObjectCache(MySQLAdaptor adapter, boolean lazyLoad)
	{
		ReferenceObjectCache.lazyLoad = lazyLoad;
		ReferenceObjectCache.adapter = adapter;
		if (!lazyLoad)
		{
			if (!ReferenceObjectCache.cachesArePopulated)
			{
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
		}
		else
		{
			logger.info("lazy-loading is enabled. Caches will only be populated on request.");
		}
	}
	
	public ReferenceObjectCache(MySQLAdaptor adapter)
	{
		this(adapter, false);
	}
	
	// This also needs to be synchronized in case lazy-loading is enabled and populateCaches isn't called from the constructor.
	private static synchronized void buildReferenceCaches(String className, Map<String,List<GKInstance>> cacheBySpecies, Map<String,GKInstance> cacheByID, Map<String,List<GKInstance>> cacheByRefDB) throws Exception
	{
		logger.debug("Building caches of {}", className);
		
		@SuppressWarnings("unchecked")
		Collection<GKInstance> referenceObjects = (Collection<GKInstance>) ReferenceObjectCache.adapter.fetchInstancesByClass(className);
		
		Map<Long,MySQLAdaptor> adapterPool = new HashMap<Long,MySQLAdaptor>();
		
		referenceObjects.stream().parallel().forEach( referenceObject -> 
		{
			MySQLAdaptor localAdapter ;
			long threadID = Thread.currentThread().getId();
			if (adapterPool.containsKey(threadID))
			{
				localAdapter = adapterPool.get(threadID);
			}
			else
			{
				logger.debug("Creating new SQL Adaptor for thread {}", Thread.currentThread().getId());
				try
				{
					localAdapter = new MySQLAdaptor( ((MySQLAdaptor)referenceObject.getDbAdaptor()).getDBHost(),
													((MySQLAdaptor)referenceObject.getDbAdaptor()).getDBName(),
													((MySQLAdaptor)referenceObject.getDbAdaptor()).getDBUser(),
													((MySQLAdaptor)referenceObject.getDbAdaptor()).getDBPwd(),
													((MySQLAdaptor)referenceObject.getDbAdaptor()).getDBPort());
					adapterPool.put(threadID, localAdapter);
				}
				catch (SQLException e)
				{
					e.printStackTrace();
					throw new Error(e);
				}
			}
			// We don't explicitly call the adapter in this code. We rely on each GKInstance object to have a reference to a PersistenceAdapter.
			// To allow for multiuple threads, we need to ensure that these objects use the local adapter from the pool.
			referenceObject.setDbAdaptor(localAdapter);
			// Retreive the Identifier because that is an attribute we will want later.
			try
			{
				referenceObject.getAttributeValue(ReactomeJavaConstants.identifier);
			}
			catch (InvalidAttributeException e)
			{
				logger.error("Could not get the \"{}\" attribute for {} because it not valid for this object.", ReactomeJavaConstants.identifier, referenceObject);
			}
			catch (Exception e)
			{
				logger.error("Could not get the \"{}\" attribute for {}. Reason: {}", ReactomeJavaConstants.identifier, referenceObject, e.getMessage());
			}

			// ReferenceMolecules do not have associated species.
			if ( !className.equals(ReactomeJavaConstants.ReferenceMolecule) )
			{
				List<String> allSpecies = null;
				//ReferenceRNASequence objects don't always have Species info, for some reason, so we need to get that from the associated ReferenceGeneProduct
				if (className.equals(ReactomeJavaConstants.ReferenceRNASequence))
				{
					Object species = null;
					try
					{
						species = referenceObject.getAttributeValue(ReactomeJavaConstants.species);
					}
					catch (InvalidAttributeException e)
					{
						logger.error("Could not get the \"{}\" attribute for {} because it not valid for this object.", ReactomeJavaConstants.identifier, referenceObject);
					}
					catch (Exception e)
					{
						logger.error("Could not get the \"{}\" attribute for {}. Reason: {}", ReactomeJavaConstants.identifier, referenceObject, e.getMessage());
					}
					
					if (null == species)
					{
						Collection<GKInstance> referringRefTranscripts;
						try
						{
							referringRefTranscripts = (Collection<GKInstance>) referenceObject.getReferers(ReactomeJavaConstants.referenceTranscript);
							allSpecies = new ArrayList<>(referringRefTranscripts.size());
							// Populate a list of species that this ReferenceRNASequence could be cached by.
							for (GKInstance refTranscript : referringRefTranscripts)
							{
								try
								{
									allSpecies.add(  ((GKInstance)refTranscript.getAttributeValue(ReactomeJavaConstants.species)).getDBID().toString() );
								}
								catch (InvalidAttributeException e)
								{
									logger.error("Could not get the \"{}\" attribute for {} because it not valid for this object.", ReactomeJavaConstants.identifier, refTranscript);
								}
								catch (Exception e)
								{
									logger.error("Could not get the \"{}\" attribute for {}. Reason: {}", ReactomeJavaConstants.identifier, refTranscript, e.getMessage());
								}
							}
						}
						catch (Exception e1)
						{
							logger.error("Could not get ReferenceTranscripts for {} (while trying to determine the species)", referenceObject);
						}
						
					}
				}

				// If the list wasn't initialized yet, it means that we are probably dealing with a ReferenceGeneProduct or a ReferenceDNASequence which should
				// have its own species.
				if (allSpecies == null)
				{
					allSpecies = new ArrayList<String>(1);
					String species;
					try
					{
						species = String.valueOf( ((GKInstance) referenceObject.getAttributeValue(ReactomeJavaConstants.species)).getDBID() );
						allSpecies.add(species);
					}
					catch (InvalidAttributeException e)
					{
						logger.error("Could not get the \"{}\" attribute for {} because it not valid for this object.", ReactomeJavaConstants.identifier, referenceObject);
					}
					catch (Exception e)
					{
						logger.error("Could not get the \"{}\" attribute for {}. Reason: {}", ReactomeJavaConstants.identifier, referenceObject, e.getMessage());
					}
					
				}
				
				for (String species : allSpecies)
				{
					//Add to the Species Cache
					if (! cacheBySpecies.containsKey(species))
					{
						List<GKInstance> bySpecies = Collections.synchronizedList(new LinkedList<GKInstance>());
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
			String refDBID;
			try
			{
				refDBID = String.valueOf(((GKInstance) referenceObject.getAttributeValue(ReactomeJavaConstants.referenceDatabase)).getDBID());
				if (! cacheByRefDB.containsKey(refDBID))
				{
					List<GKInstance> byRefDb = Collections.synchronizedList( new LinkedList<GKInstance>() );
					byRefDb.add(referenceObject);
					cacheByRefDB.put(refDBID, byRefDb);
				}
				else
				{
					cacheByRefDB.get(refDBID).add(referenceObject);
				}
			}
			catch (InvalidAttributeException e)
			{
				logger.error("Could not get the \"{}\" attribute for {} because it not valid for this object.", ReactomeJavaConstants.referenceDatabase, referenceObject);
			}
			catch (Exception e)
			{
				logger.error("Could not get the \"{}\" attribute for {}. Reason: {}", ReactomeJavaConstants.identifier, referenceObject, e.getMessage());
				e.printStackTrace();
			}
			
			// ID Cache
			cacheByID.put(String.valueOf(referenceObject.getDBID()), referenceObject);
		});
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
		ReferenceObjectCache.adapter = adapter;
		if (ReferenceObjectCache.adapter!=null)
		{
			try
			{
				logger.info("Building ReferenceObject caches...");
				// Populate the main caches.
				ReferenceObjectCache.buildReferenceCaches(ReactomeJavaConstants.ReferenceGeneProduct, refGeneProdCacheBySpecies, refGeneProdCacheById, refGeneProdCacheByRefDb);
				
				ReferenceObjectCache.buildReferenceCaches(ReactomeJavaConstants.ReferenceDNASequence, refDNASeqCacheBySpecies, refDNASeqCacheById, refDNASeqCacheByRefDb);
				
				ReferenceObjectCache.buildReferenceCaches(ReactomeJavaConstants.ReferenceRNASequence, refRNASeqCacheBySpecies, refRNASeqCacheById, refRNASeqCacheByRefDb);
				
				ReferenceObjectCache.buildReferenceCaches(ReactomeJavaConstants.ReferenceMolecule, null, moleculeCacheByID, moleculeCacheByRefDB);
				
				// Build up the Reference Database caches.
				buildReferenceDatabaseCache(adapter);
				// Build up the species caches.
				buildSpeciesCache(adapter);

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

	/**
	 * Used for building up two 1:n caches. Needs to be syncrhonized in case lazy-loading is enabled and the populateCaches method does not get called. 
	 * @param adapter - The db adapter.
	 * @param lookupClass - The Reactome Class that will be used to look for items to put in the cache.
	 * @param nameToIDCache - The name-to-DB_ID cache.
	 * @param idToNameCache - The DB_ID-to-name cache.
	 * @throws Exception
	 * @throws InvalidAttributeException
	 */
	private static synchronized void buildOneToManyCache(MySQLAdaptor adapter, String lookupClass, Map<String, List<String>> nameToIDCache, Map<String, List<String>> idToNameCache) throws Exception, InvalidAttributeException
	{
		logger.debug("Building caches for {}", lookupClass);
		@SuppressWarnings("unchecked")
		Collection<GKInstance> dbOjects = adapter.fetchInstancesByClass(lookupClass);
		for (GKInstance dbOject : dbOjects)
		{
			String db_id = dbOject.getDBID().toString();
			//Because names could be many-valued...
			@SuppressWarnings("unchecked")
			List<String> names = (List<String>)dbOject.getAttributeValuesList(ReactomeJavaConstants.name);
			for (String name : names)
			{
				List<String> listOfIds;
				if (nameToIDCache.containsKey(name))
				{
					listOfIds = nameToIDCache.get(name);
				}
				else
				{
					listOfIds = new ArrayList<String>(1);
				}
				listOfIds.add(db_id);
				nameToIDCache.put(name, listOfIds);
				
				List<String> listOfNames;
				if (idToNameCache.containsKey(db_id))
				{
					listOfNames = idToNameCache.get(db_id);
				}
				else
				{
					listOfNames = new ArrayList<String>(1);
				}
				listOfNames.add(name);
				idToNameCache.put(db_id,listOfNames);
			}
		}
		logger.info("Keys in name-to-ID cache: {}; keys in ID-to_NAME: {}", nameToIDCache.size(), idToNameCache.size());
	}
	
	private static void buildSpeciesCache(MySQLAdaptor adapter) throws Exception, InvalidAttributeException
	{
		buildOneToManyCache(adapter, ReactomeJavaConstants.Species, ReferenceObjectCache.speciesNamesToIds, ReferenceObjectCache.speciesMapping);
	}

	private static void buildReferenceDatabaseCache(MySQLAdaptor adapter) throws Exception, InvalidAttributeException
	{
		buildOneToManyCache(adapter, ReactomeJavaConstants.ReferenceDatabase, ReferenceObjectCache.refDbNamesToIds, ReferenceObjectCache.refdbMapping);
	}
	
	// ReferenceMolecule caches
	private static Map<String, GKInstance> moleculeCacheByID = new ConcurrentHashMap<String, GKInstance>();
	private static Map<String, List<GKInstance>> moleculeCacheByRefDB = new ConcurrentHashMap<String, List<GKInstance>>();
	
	// ReferenceDNASequence caches
	private static Map<String,List<GKInstance>> refDNASeqCacheBySpecies = new ConcurrentHashMap<String,List<GKInstance>>();
	private static Map<String,List<GKInstance>> refDNASeqCacheByRefDb = new ConcurrentHashMap<String,List<GKInstance>>();
	private static Map<String,GKInstance> refDNASeqCacheById = new ConcurrentHashMap<String,GKInstance>();

	// ReferenceRNASequence caches
	private static Map<String,List<GKInstance>> refRNASeqCacheBySpecies = new ConcurrentHashMap<String,List<GKInstance>>();
	private static Map<String,List<GKInstance>> refRNASeqCacheByRefDb = new ConcurrentHashMap<String,List<GKInstance>>();
	private static Map<String,GKInstance> refRNASeqCacheById = new ConcurrentHashMap<String,GKInstance>();
	
	// ReferenceGeneProduct caches
	private static Map<String,List<GKInstance>> refGeneProdCacheBySpecies = new ConcurrentHashMap<String,List<GKInstance>>();
	private static Map<String,List<GKInstance>> refGeneProdCacheByRefDb = new ConcurrentHashMap<String,List<GKInstance>>();
	private static Map<String,GKInstance> refGeneProdCacheById = new ConcurrentHashMap<String,GKInstance>();
	
	//also need some secondary mappings: species name-to-id and refdb name-to-id
	//These really should be 1:n mappings...
	private static Map<String,List<String>> speciesMapping = new ConcurrentHashMap<String,List<String>>();
	private static Map<String,List<String>> refdbMapping = new ConcurrentHashMap<String,List<String>>();
	//...Aaaaaand mappings from names to IDs which will be 1:n
	private static Map<String,List<String>> refDbNamesToIds = new ConcurrentHashMap<String,List<String>>();
	private static Map<String,List<String>> speciesNamesToIds = new ConcurrentHashMap<String,List<String>>();
	
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
			{
				if (ReferenceObjectCache.lazyLoad)
				{
					buildLazilyLoadedCaches(ReactomeJavaConstants.ReferenceGeneProduct, ReferenceObjectCache.refGeneProdCacheBySpecies, ReferenceObjectCache.refGeneProdCacheById, ReferenceObjectCache.refGeneProdCacheByRefDb);
				}
				return ReferenceObjectCache.refGeneProdCacheByRefDb.containsKey(refDb) ? ReferenceObjectCache.refGeneProdCacheByRefDb.get(refDb) : new ArrayList<GKInstance>(0);
			}
			case ReactomeJavaConstants.ReferenceDNASequence:
			{
				if (ReferenceObjectCache.lazyLoad)
				{
					buildLazilyLoadedCaches(ReactomeJavaConstants.ReferenceDNASequence, ReferenceObjectCache.refDNASeqCacheBySpecies, ReferenceObjectCache.refDNASeqCacheById, ReferenceObjectCache.refDNASeqCacheByRefDb);
				}
				return ReferenceObjectCache.refDNASeqCacheByRefDb.containsKey(refDb) ? ReferenceObjectCache.refDNASeqCacheByRefDb.get(refDb) : new ArrayList<GKInstance>(0);
			}
			case ReactomeJavaConstants.ReferenceRNASequence:
			{
				if (ReferenceObjectCache.lazyLoad)
				{
					buildLazilyLoadedCaches(ReactomeJavaConstants.ReferenceRNASequence, ReferenceObjectCache.refRNASeqCacheBySpecies, ReferenceObjectCache.refRNASeqCacheById, ReferenceObjectCache.refRNASeqCacheByRefDb);
				}
				return ReferenceObjectCache.refRNASeqCacheByRefDb.containsKey(refDb) ? ReferenceObjectCache.refRNASeqCacheByRefDb.get(refDb) : new ArrayList<GKInstance>(0);
			}
			case ReactomeJavaConstants.ReferenceMolecule:
			{
				if (ReferenceObjectCache.lazyLoad)
				{
					buildLazilyLoadedCaches(ReactomeJavaConstants.ReferenceMolecule, null, ReferenceObjectCache.moleculeCacheByID, ReferenceObjectCache.moleculeCacheByRefDB);
				}
				return ReferenceObjectCache.moleculeCacheByRefDB.containsKey(refDb) ? ReferenceObjectCache.moleculeCacheByRefDB.get(refDb) : new ArrayList<GKInstance>(0);
			}
			default:
				logger.error("Invalid className: {} Nothing will be returned.", className);
				return null;
		}
	}
	
	/**
	 * Build a *set* of lazy-loaded caches: the objects-by-species, the cache-by-ID of those objects, the cache-by-refDB of those objects.
	 * @param objectClass 
	 * @param objectCacheBySpecies
	 * @param objectCacheByID
	 * @param objectCacheByRefDB
	 */
	private void buildLazilyLoadedCaches(String objectClass, Map<String, List<GKInstance>> objectCacheBySpecies, Map<String, GKInstance> objectCacheByID, Map<String, List<GKInstance>> objectCacheByRefDB)
	{
		if (objectCacheBySpecies.keySet().size() == 0)
		{
			logger.info("Lazy-loading caches for {}", objectClass);
			try
			{
				ReferenceObjectCache.buildReferenceCaches(objectClass, objectCacheBySpecies, objectCacheByID, objectCacheByRefDB);
				// Build up the Reference Database caches.
				buildReferenceDatabaseCache(adapter);
				// Build up the species caches.
				buildSpeciesCache(adapter);
			}
			catch (Exception e)
			{
				e.printStackTrace();
				logger.error("An Exception was caught while trying to populate the caches: {}\n"
						+ "Caches might not be populated, any results returned might not be what you'd have hoped for. Sorry.", e.getMessage());
			}
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
			{
				if (ReferenceObjectCache.lazyLoad)
				{
					buildLazilyLoadedCaches(ReactomeJavaConstants.ReferenceGeneProduct, ReferenceObjectCache.refGeneProdCacheBySpecies, ReferenceObjectCache.refGeneProdCacheById, ReferenceObjectCache.refGeneProdCacheByRefDb);
				}
				return ReferenceObjectCache.refGeneProdCacheBySpecies.get(species);
			}
			case ReactomeJavaConstants.ReferenceDNASequence:
			{
				if (ReferenceObjectCache.lazyLoad)
				{
					buildLazilyLoadedCaches(ReactomeJavaConstants.ReferenceDNASequence, ReferenceObjectCache.refDNASeqCacheBySpecies, ReferenceObjectCache.refDNASeqCacheById, ReferenceObjectCache.refDNASeqCacheByRefDb);
				}
				return ReferenceObjectCache.refDNASeqCacheBySpecies.get(species);
			}
			case ReactomeJavaConstants.ReferenceRNASequence:
			{
				if (ReferenceObjectCache.lazyLoad)
				{
					buildLazilyLoadedCaches(ReactomeJavaConstants.ReferenceRNASequence, ReferenceObjectCache.refRNASeqCacheBySpecies, ReferenceObjectCache.refRNASeqCacheById, ReferenceObjectCache.refRNASeqCacheByRefDb);
				}
				return ReferenceObjectCache.refRNASeqCacheBySpecies.get(species);
			}
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
	
//	/**
//	 * Returns a set of the keys used to cache by Reference Database.
//	 * @return
//	 */
//	public Set<String> getListOfRefGeneProdRefDbs()
//	{
//		if (ReferenceObjectCache.lazyLoad)
//		{
//			buildLazilyLoadedCaches(ReactomeJavaConstants.ReferenceGeneProduct, ReferenceObjectCache.refGeneProdCacheBySpecies, ReferenceObjectCache.refGeneProdCacheById, ReferenceObjectCache.refGeneProdCacheByRefDb);
//		}
//		return ReferenceObjectCache.refGeneProdCacheByRefDb.keySet();
//	}

	/**
	 * Returns a map of Reference Database names, keyed by their DB_IDs.
	 * @return
	 */
	public Map<String,List<String>> getRefDBMappings()
	{
		if (ReferenceObjectCache.lazyLoad)
		{
			try
			{
				buildReferenceDatabaseCache(ReferenceObjectCache.adapter);
			}
			catch (InvalidAttributeException e)
			{
				e.printStackTrace();
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}
		return ReferenceObjectCache.refdbMapping;
	}
	
	/**
	 * Returns a 1:n mapping of Reference Database names mapped to a list of DB_IDs, keyed by their names.
	 * @return
	 */
	public Map<String,List<String>> getRefDbNamesToIds()
	{
		if (ReferenceObjectCache.lazyLoad)
		{
			try
			{
				buildReferenceDatabaseCache(ReferenceObjectCache.adapter);
			}
			catch (InvalidAttributeException e)
			{
				e.printStackTrace();
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}
		return ReferenceObjectCache.refDbNamesToIds;
	}
	
	/**
	 * Returns a 1:n mapping of Species names mapped to a list of DB_IDs.
	 * @return
	 */
	public Map<String,List<String>> getSpeciesNamesToIds()
	{
		if (ReferenceObjectCache.lazyLoad)
		{
			try
			{
				buildSpeciesCache(ReferenceObjectCache.adapter);
			}
			catch (InvalidAttributeException e)
			{
				e.printStackTrace();
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}
		return ReferenceObjectCache.speciesNamesToIds;
	}
	
	/**
	 * Returns a set of the keys used to cache by Species.
	 * //@deprecated This should not be used since it only returns data related to ReferenceGeneProducts. THIS MAY NO LONGER BE TRUE. 
	 * @return
	 */
	public Set<String> getListOfSpeciesNames()
	{
		if (ReferenceObjectCache.lazyLoad)
		{
			try
			{
				buildSpeciesCache(ReferenceObjectCache.adapter);
			}
			catch (InvalidAttributeException e)
			{
				e.printStackTrace();
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}
		return ReferenceObjectCache.refGeneProdCacheBySpecies.keySet();
	}
	
	/**
	 * Returns a map of Species names, keyed by their DB_IDs.
	 * @return
	 */
	public Map<String,List<String>> getSpeciesMappings()
	{
		if (ReferenceObjectCache.lazyLoad)
		{
			try
			{
				buildSpeciesCache(ReferenceObjectCache.adapter);
			}
			catch (InvalidAttributeException e)
			{
				e.printStackTrace();
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}
		return ReferenceObjectCache.speciesMapping;
	}
}
