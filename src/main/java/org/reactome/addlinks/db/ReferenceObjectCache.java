package org.reactome.addlinks.db;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
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
	
	private static final Logger logger = LogManager.getLogger();
	private static boolean cachesArePopulated = false;
	private static boolean lazyLoad = true;
	private static boolean cacheInitializedMessageHasBeenPrinted = false;
	private static MySQLAdaptor adapter;
	private static Map<Long,MySQLAdaptor> adapterPool = new HashMap<>();
	
	/**
	 * Sets up the internal caches.
	 * @param adapter - a database adapter.
	 * @param lazyLoad - Should caches be lazy-loaded? If TRUE, caches will only be populated the first time they are accessed. If FALSE, all caches will be populated NOW.
	 */
	public ReferenceObjectCache(MySQLAdaptor adapter, boolean lazyLoad)
	{
		ReferenceObjectCache.lazyLoad = lazyLoad;
		ReferenceObjectCache.adapter = adapter;
		if (!ReferenceObjectCache.lazyLoad)
		{
			if (!ReferenceObjectCache.cachesArePopulated)
			{
				logger.info("Cache is not initialized. Will initialize now.");
				//ReferenceObjectCache.cache = ReferenceObjectCache.populateCaches(ReferenceObjectCache.adapter);
				ReferenceObjectCache.populateCaches(ReferenceObjectCache.adapter);
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
	private static synchronized void buildReferenceCaches(String className, Map<String,List<GKInstance>> cacheBySpecies, Map<String,GKInstance> cacheByID, Map<String, List<GKInstance>> objectCacheByIdentifier, Map<String,List<GKInstance>> cacheByRefDB) throws Exception
	{
		logger.debug("Building caches of {}", className);
		
		@SuppressWarnings("unchecked")
		Collection<GKInstance> referenceObjects = (Collection<GKInstance>) ReferenceObjectCache.adapter.fetchInstancesByClass(className);
		
		referenceObjects.stream().parallel().forEach( referenceObject -> 
		{
			String identifierAttribute = ReactomeJavaConstants.identifier;
			if (className.equals(ReactomeJavaConstants.Reaction))
			{
				identifierAttribute = ReactomeJavaConstants.stableIdentifier;
			}
			
			MySQLAdaptor localAdapter ;
			long threadID = Thread.currentThread().getId();
			localAdapter = getAdaptorForThread(threadID);
			// We don't explicitly call the adapter in this code. We rely on each GKInstance object to have a reference to a PersistenceAdapter.
			// To allow for multiple threads, we need to ensure that these objects use the local adapter from the pool.
			referenceObject.setDbAdaptor(localAdapter);
			// Retrieve the Identifier because that is an attribute we will want later.
			try
			{
				referenceObject.getAttributeValue(identifierAttribute);
			}
			catch (InvalidAttributeException e)
			{
				logger.error("Could not get the \"{}\" attribute for {} because it not valid for this object.", identifierAttribute, referenceObject);
			}
			catch (Exception e)
			{
				logger.error("Could not get the \"{}\" attribute for {}. Reason: {}", identifierAttribute, referenceObject, e.getMessage());
			}

			// ReferenceMolecules and DatabaseIdentifiers and ReferenceTherapeutics do not have associated species.
			if ( !className.equals(ReactomeJavaConstants.ReferenceMolecule) && !className.equals(ReactomeJavaConstants.DatabaseIdentifier) && !className.equals(ReactomeJavaConstants.ReferenceTherapeutic))
			{
				addObjectToSpeciesCache(className, cacheBySpecies, referenceObject);
			}
			//ReferenceDatabase Cache
			//
			if (cacheByRefDB != null)
			{
				addObjectToRefDBCache(cacheByRefDB, referenceObject);
			}
			// DB_ID Cache
			cacheByID.put(String.valueOf(referenceObject.getDBID()), referenceObject);
			
			// Identifier cache
			if ( !className.equals(ReactomeJavaConstants.Reaction) )
			{
				addObjectToIdentifierCache(className, objectCacheByIdentifier, referenceObject);
			}
		});
		logger.info("Built {} caches:"
				+ "\n\tKeys in cache-by-refdb: {};"
				+ "\n\tkeys in cache-by-species: {};"
				+ "\n\tkeys in cache-by-DB_ID: {};"
				+ "\n\tkeys in cache-by-identifier: {};",className,
					(cacheByRefDB!=null ? cacheByRefDB.size() : "N/A"),
					(!className.equals(ReactomeJavaConstants.ReferenceMolecule)
							&& !className.equals(ReactomeJavaConstants.DatabaseIdentifier)
							&& !className.equals(ReactomeJavaConstants.ReferenceTherapeutic)
						? cacheBySpecies.size() : "N/A"),
					objectCacheByIdentifier.size(),
					cacheByID.size()
				);
	}

	/**
	 * Adds an object to a cache that caches by identifier.
	 * @param className - type of object being cached.
	 * @param objectCacheByIdentifier - the cache to add the object into.
	 * @param referenceObject - The object that will be added to a cache.
	 * @throws Error
	 */
	private static void addObjectToIdentifierCache(String className, Map<String, List<GKInstance>> objectCacheByIdentifier, GKInstance referenceObject) throws Error
	{
		try
		{
			if (referenceObject.getSchemClass().isValidAttribute(ReactomeJavaConstants.identifier) && referenceObject.getAttributeValue(ReactomeJavaConstants.identifier) != null)
			{
				String identifier = (String) referenceObject.getAttributeValue(ReactomeJavaConstants.identifier);
				try
				{
					if (!objectCacheByIdentifier.containsKey(identifier))
					{
						objectCacheByIdentifier.put(identifier, Collections.synchronizedList(new ArrayList<GKInstance>(Arrays.asList(referenceObject))) );
					}
					else
					{
						objectCacheByIdentifier.get(identifier).add(referenceObject);
					}
				}
				catch (ArrayIndexOutOfBoundsException e)
				{
					logger.error("ArrayIndexOutOfBounds was caught! Identifier that triggered this was: {}; className: {}; objectCacheByIdentifier has {} items; objectCacheByIdentifier[identifier] has {} items", identifier, className, objectCacheByIdentifier.size(), objectCacheByIdentifier.containsKey(identifier) ? objectCacheByIdentifier.get(identifier).size() : " *THAT IDENTIFIER IS NOT IN THAT MAP!* ");
					e.printStackTrace();
					throw new Error(e);
				}

			}
		}
		catch (InvalidAttributeException e)
		{
			logger.error("Object {} does not have an identifier attribute, error: {}", referenceObject, e.getMessage());
			e.printStackTrace();
		}
		catch (Exception e)
		{
			e.printStackTrace();
			throw new Error(e);
		}
	}

	/**
	 * Adds an object to a cache that is indexed by ReferenceDatabase.
	 * @param cacheByRefDB - the cache to add to.
	 * @param referenceObject - the object to add.
	 */
	private static void addObjectToRefDBCache(Map<String, List<GKInstance>> cacheByRefDB, GKInstance referenceObject)
	{
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
	}

	/**
	 * Adds an object to a cache that is indexed by species.
	 * @param className - the className of the object.
	 * @param cacheBySpecies - the cache to add the object to.
	 * @param referenceObject - the object to add.
	 */
	private static void addObjectToSpeciesCache(String className, Map<String, List<GKInstance>> cacheBySpecies, GKInstance referenceObject)
	{
		// Sets do not allow duplicates.
		Set<String> allSpecies = null;
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
				logger.error("Could not get the \"{}\" attribute for {} because it not valid for this object.", ReactomeJavaConstants.species, referenceObject);
			}
			catch (Exception e)
			{
				logger.error("Could not get the \"{}\" attribute for {}. Reason: {}", ReactomeJavaConstants.species, referenceObject, e.getMessage());
			}
			
			if (null == species)
			{
				try
				{
					@SuppressWarnings("unchecked")
					Collection<GKInstance> referringRefTranscripts = (Collection<GKInstance>) referenceObject.getReferers(ReactomeJavaConstants.referenceTranscript);
					allSpecies = new HashSet<>(referringRefTranscripts.size());
					// Populate a list of species that this ReferenceRNASequence could be cached by.
					for (GKInstance refTranscript : referringRefTranscripts)
					{
						try
						{
							allSpecies.add(  ((GKInstance)refTranscript.getAttributeValue(ReactomeJavaConstants.species)).getDBID().toString() );
						}
						catch (InvalidAttributeException e)
						{
							logger.error("Could not get the \"{}\" attribute for {} because it not valid for this object.", ReactomeJavaConstants.species, refTranscript);
						}
						catch (Exception e)
						{
							logger.error("Could not get the \"{}\" attribute for {}. Reason: {}", ReactomeJavaConstants.species, refTranscript, e.getMessage());
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
			allSpecies = new HashSet<>(1);
			String species;
			try
			{
				species = String.valueOf( ((GKInstance) referenceObject.getAttributeValue(ReactomeJavaConstants.species)).getDBID() );
				allSpecies.add(species);
			}
			catch (InvalidAttributeException e)
			{
				logger.error("Could not get the \"{}\" attribute for {} because it not valid for this object.", ReactomeJavaConstants.species, referenceObject);
			}
			catch (Exception e)
			{
				logger.error("Could not get the \"{}\" attribute for {}. Reason: {}", ReactomeJavaConstants.species, referenceObject, e.getMessage());
			}
			
		}
		if (cacheBySpecies != null)
		{
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
	}

	/**
	 * Gets a database adaptor for a thread.
	 * @param threadID
	 * @return
	 * @throws Error
	 */
	private static MySQLAdaptor getAdaptorForThread(long threadID) throws Error
	{
		MySQLAdaptor localAdapter;
		if (adapterPool.containsKey(threadID))
		{
			localAdapter = adapterPool.get(threadID);
		}
		else
		{
			logger.debug("Creating new SQL Adaptor for thread {}", Thread.currentThread().getId());
			try
			{
				localAdapter = new MySQLAdaptor( ReferenceObjectCache.adapter.getDBHost(), ReferenceObjectCache.adapter.getDBName(),
												ReferenceObjectCache.adapter.getDBUser(), ReferenceObjectCache.adapter.getDBPwd(),
												ReferenceObjectCache.adapter.getDBPort());
				adapterPool.put(threadID, localAdapter);
			}
			catch (SQLException e)
			{
				e.printStackTrace();
				throw new Error(e);
			}
		}
		return localAdapter;
	}
	
	/**
	 * Populates all of the caches.
	 * @param adapter - a database adaptor to use.
	 */
	private static synchronized void populateCaches(MySQLAdaptor adapter)
	{
		ReferenceObjectCache.adapter = adapter;
		if (ReferenceObjectCache.adapter!=null)
		{
			try
			{
				logger.info("Building ReferenceObject caches...");
				// Populate the main caches.
				ReferenceObjectCache.buildReferenceCaches(ReactomeJavaConstants.ReferenceGeneProduct, refGeneProdCacheBySpecies, refGeneProdCacheByDBID, refGeneProdCacheByIdentifier, refGeneProdCacheByRefDb);
				ReferenceObjectCache.buildReferenceCaches(ReactomeJavaConstants.ReferenceDNASequence, refDNASeqCacheBySpecies, refDNASeqCacheByDBID, refDNASeqCacheByIdentifier, refDNASeqCacheByRefDb);
				ReferenceObjectCache.buildReferenceCaches(ReactomeJavaConstants.ReferenceRNASequence, refRNASeqCacheBySpecies, refRNASeqCacheByDBID, refRNASeqCacheByIdentifier, refRNASeqCacheByRefDb);
				ReferenceObjectCache.buildReferenceCaches(ReactomeJavaConstants.ReferenceMolecule, null, moleculeCacheByDBID, moleculeCacheByIdentifier, moleculeCacheByRefDB);
				ReferenceObjectCache.buildReferenceCaches(ReactomeJavaConstants.Reaction, reactionCacheBySpecies, reactionCacheByDBID, reactionCacheByIdentifier, null);
				ReferenceObjectCache.buildReferenceCaches(ReactomeJavaConstants.DatabaseIdentifier, null, databaseIdentifiersByDBID, databaseIdentifiersByIdentifier, databaseIdentifiersByRefDb);
				ReferenceObjectCache.buildReferenceCaches(ReactomeJavaConstants.ReferenceTherapeutic, null, referenceTherapeuticsByDBID, referenceTherapeuticsByIdentifier, referenceTherapeuticsByRefDb);
				
				// Build up the Reference Database caches.
				buildReferenceDatabaseCache(adapter);
				// Build up the species caches.
				buildSpeciesCache(adapter);
				// build the StableIdentifier cache.
				ReferenceObjectCache.buildStableIdentifierCache(adapter);
				// print some stats
				logger.info("All caches initialized."
						+ "\n\tkeys in refDbMapping: {};"
						+ "\n\tkeys in speciesMapping: {}"
						+ "\n\tkeys in StableIdentifierMapping: {}",
								ReferenceObjectCache.refdbMapping.size(),
								ReferenceObjectCache.speciesMapping.size(),
								ReferenceObjectCache.cachedByStableIdentifier.size());
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
		if (nameToIDCache.isEmpty() || nameToIDCache.keySet().isEmpty()
				|| idToNameCache.isEmpty() || idToNameCache.keySet().isEmpty())
		{
			logger.info("Building caches for {}", lookupClass);
			@SuppressWarnings("unchecked")
			Collection<GKInstance> dbOjects = adapter.fetchInstancesByClass(lookupClass);
			for (GKInstance dbOject : dbOjects)
			{
				String dbId = dbOject.getDBID().toString();
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
						listOfIds = new ArrayList<>(1);
					}
					listOfIds.add(dbId);
					nameToIDCache.put(name, listOfIds);
					
					List<String> listOfNames;
					if (idToNameCache.containsKey(dbId))
					{
						listOfNames = idToNameCache.get(dbId);
					}
					else
					{
						listOfNames = new ArrayList<>(1);
					}
					listOfNames.add(name);
					idToNameCache.put(dbId,listOfNames);
				}
			}
			logger.info("Keys in name-to-ID cache: {}; keys in ID-to_NAME: {}", nameToIDCache.size(), idToNameCache.size());
		}
	}
	
	
	private static void buildSpeciesCache(MySQLAdaptor adapter) throws Exception, InvalidAttributeException
	{
		buildOneToManyCache(adapter, ReactomeJavaConstants.Species, ReferenceObjectCache.speciesNamesToIds, ReferenceObjectCache.speciesMapping);
	}

	private static void buildReferenceDatabaseCache(MySQLAdaptor adapter) throws Exception, InvalidAttributeException
	{
		buildOneToManyCache(adapter, ReactomeJavaConstants.ReferenceDatabase, ReferenceObjectCache.refDbNamesToIds, ReferenceObjectCache.refdbMapping);
	}
	
	// Reaction Cache
	private static Map<String, GKInstance> reactionCacheByDBID = new ConcurrentHashMap<>();
	private static Map<String, List<GKInstance>> reactionCacheByIdentifier = new ConcurrentHashMap<>();
	private static Map<String, List<GKInstance>> reactionCacheBySpecies = new ConcurrentHashMap<>();
	
	// ReferenceMolecule caches
	private static Map<String, GKInstance> moleculeCacheByDBID = new ConcurrentHashMap<>();
	private static Map<String, List<GKInstance>> moleculeCacheByIdentifier = new ConcurrentHashMap<>();
	private static Map<String, List<GKInstance>> moleculeCacheByRefDB = new ConcurrentHashMap<>();
	
	// ReferenceDNASequence caches
	private static Map<String,List<GKInstance>> refDNASeqCacheBySpecies = new ConcurrentHashMap<>();
	private static Map<String,List<GKInstance>> refDNASeqCacheByRefDb = new ConcurrentHashMap<>();
	private static Map<String,GKInstance> refDNASeqCacheByDBID = new ConcurrentHashMap<>();
	private static Map<String,List<GKInstance>> refDNASeqCacheByIdentifier = new ConcurrentHashMap<>();

	// ReferenceRNASequence caches
	private static Map<String,List<GKInstance>> refRNASeqCacheBySpecies = new ConcurrentHashMap<>();
	private static Map<String,List<GKInstance>> refRNASeqCacheByRefDb = new ConcurrentHashMap<>();
	private static Map<String,GKInstance> refRNASeqCacheByDBID = new ConcurrentHashMap<>();
	private static Map<String,List<GKInstance>> refRNASeqCacheByIdentifier = new ConcurrentHashMap<>();
	
	// ReferenceGeneProduct caches
	private static Map<String,List<GKInstance>> refGeneProdCacheBySpecies = new ConcurrentHashMap<>();
	private static Map<String,List<GKInstance>> refGeneProdCacheByRefDb = new ConcurrentHashMap<>();
	private static Map<String,GKInstance> refGeneProdCacheByDBID = new ConcurrentHashMap<>();
	private static Map<String,List<GKInstance>> refGeneProdCacheByIdentifier = new ConcurrentHashMap<>();
	
	// DatabaseIdentifier cache
	private static Map<String,List<GKInstance>> databaseIdentifiersByRefDb = new ConcurrentHashMap<>();
	private static Map<String,GKInstance> databaseIdentifiersByDBID = new ConcurrentHashMap<>();
	private static Map<String,List<GKInstance>> databaseIdentifiersByIdentifier = new ConcurrentHashMap<>();
	
	// Cache objects by Stable Identifier
	private static Map<String, GKInstance> cachedByStableIdentifier = new ConcurrentHashMap<>();
	
	//also need some secondary mappings: species name-to-id and refdb name-to-id
	//These really should be 1:n mappings...
	private static Map<String,List<String>> speciesMapping = new ConcurrentHashMap<>();
	private static Map<String,List<String>> refdbMapping = new ConcurrentHashMap<>();
	//...Aaaaaand mappings from names to IDs which will be 1:n
	private static Map<String,List<String>> refDbNamesToIds = new ConcurrentHashMap<>();
	private static Map<String,List<String>> speciesNamesToIds = new ConcurrentHashMap<>();

	// Caches for ReferenceTherapeutics (added to support PharmacoDB references).
	private static Map<String, GKInstance> referenceTherapeuticsByDBID  = new ConcurrentHashMap<>();
	private static Map<String, List<GKInstance>> referenceTherapeuticsByIdentifier = new ConcurrentHashMap<>();
	private static Map<String, List<GKInstance>> referenceTherapeuticsByRefDb = new ConcurrentHashMap<>();
	
	/**
	 * Get a list of ReferenceGeneProduct/ReferenceDNASequeces/ReferenceMolecules/ReferenceRNASequences keyed by Reference Database.
	 * @param refDb - the DB_ID of the reference database.
	 * @param className - the class, one of:  ReferenceGeneProduct/ReferenceDNASequeces/ReferenceMolecules/ReferenceRNASequences
	 * @return A list of GKInstances.
	 */
	public List<GKInstance> getByRefDb(String refDb, String className)
	{
		switch (className)
		{
			case ReactomeJavaConstants.ReferenceGeneProduct:
			{
				buildLazilyLoadedCaches(ReactomeJavaConstants.ReferenceGeneProduct, ReferenceObjectCache.refGeneProdCacheBySpecies, ReferenceObjectCache.refGeneProdCacheByDBID, ReferenceObjectCache.refGeneProdCacheByIdentifier, ReferenceObjectCache.refGeneProdCacheByRefDb, ReferenceObjectCache.lazyLoad);
				return ReferenceObjectCache.refGeneProdCacheByRefDb.containsKey(refDb) ? ReferenceObjectCache.refGeneProdCacheByRefDb.get(refDb) : new ArrayList<>(0);
			}
			case ReactomeJavaConstants.ReferenceDNASequence:
			{
				buildLazilyLoadedCaches(ReactomeJavaConstants.ReferenceDNASequence, ReferenceObjectCache.refDNASeqCacheBySpecies, ReferenceObjectCache.refDNASeqCacheByDBID, ReferenceObjectCache.refDNASeqCacheByIdentifier, ReferenceObjectCache.refDNASeqCacheByRefDb, ReferenceObjectCache.lazyLoad);
				return ReferenceObjectCache.refDNASeqCacheByRefDb.containsKey(refDb) ? ReferenceObjectCache.refDNASeqCacheByRefDb.get(refDb) : new ArrayList<>(0);
			}
			case ReactomeJavaConstants.ReferenceRNASequence:
			{
				buildLazilyLoadedCaches(ReactomeJavaConstants.ReferenceRNASequence, ReferenceObjectCache.refRNASeqCacheBySpecies, ReferenceObjectCache.refRNASeqCacheByDBID, ReferenceObjectCache.refRNASeqCacheByIdentifier, ReferenceObjectCache.refRNASeqCacheByRefDb, ReferenceObjectCache.lazyLoad);
				return ReferenceObjectCache.refRNASeqCacheByRefDb.containsKey(refDb) ? ReferenceObjectCache.refRNASeqCacheByRefDb.get(refDb) : new ArrayList<>(0);
			}
			case ReactomeJavaConstants.ReferenceMolecule:
			{
				buildLazilyLoadedCaches(ReactomeJavaConstants.ReferenceMolecule, null, ReferenceObjectCache.moleculeCacheByDBID, ReferenceObjectCache.moleculeCacheByIdentifier, ReferenceObjectCache.moleculeCacheByRefDB, ReferenceObjectCache.lazyLoad);
				return ReferenceObjectCache.moleculeCacheByRefDB.containsKey(refDb) ? ReferenceObjectCache.moleculeCacheByRefDB.get(refDb) : new ArrayList<>(0);
			}
			case ReactomeJavaConstants.DatabaseIdentifier:
			{
				buildLazilyLoadedCaches(ReactomeJavaConstants.DatabaseIdentifier, null, ReferenceObjectCache.databaseIdentifiersByDBID, ReferenceObjectCache.databaseIdentifiersByIdentifier, ReferenceObjectCache.databaseIdentifiersByRefDb, ReferenceObjectCache.lazyLoad);
				return ReferenceObjectCache.databaseIdentifiersByRefDb.containsKey(refDb) ? ReferenceObjectCache.databaseIdentifiersByRefDb.get(refDb) : new ArrayList<>(0);
			}
			case ReactomeJavaConstants.ReferenceTherapeutic:
			{
				buildLazilyLoadedCaches(ReactomeJavaConstants.ReferenceTherapeutic, null, ReferenceObjectCache.referenceTherapeuticsByDBID, ReferenceObjectCache.referenceTherapeuticsByIdentifier, ReferenceObjectCache.referenceTherapeuticsByRefDb, ReferenceObjectCache.lazyLoad);
				return ReferenceObjectCache.referenceTherapeuticsByRefDb.containsKey(refDb) ? ReferenceObjectCache.referenceTherapeuticsByRefDb.get(refDb) : new ArrayList<>(0);
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
	 * @param objectCacheByDBID
	 * @param objectCacheByIdentifier
	 * @param objectCacheByRefDB
	 */
	private void buildLazilyLoadedCaches(String objectClass, Map<String, List<GKInstance>> objectCacheBySpecies, Map<String, GKInstance> objectCacheByDBID, Map<String, List<GKInstance>> objectCacheByIdentifier, Map<String, List<GKInstance>> objectCacheByRefDB, boolean lazyLoad)
	{
		//if (!lazyLoad)
		{
			// not every class-cache will populate all caches, so only load if they are ALL empty.
			if ( (objectCacheBySpecies == null || objectCacheBySpecies.size() == 0)
				&& (objectCacheByDBID == null || objectCacheByDBID.size() == 0)
				&& (objectCacheByIdentifier == null || objectCacheByIdentifier.size() == 0)
				&& (objectCacheByRefDB == null || objectCacheByRefDB.size() == 0) )
			{
				logger.info("Lazy-loading caches for {}", objectClass);
				try
				{
					ReferenceObjectCache.buildReferenceCaches(objectClass, objectCacheBySpecies, objectCacheByDBID, objectCacheByIdentifier, objectCacheByRefDB);
					// NOTE: ReferenceDatabase cache and Species cache will get rebuilt EVERY time *this* method is called.
					// This could be inefficient when rebuildRefDBCachesWithoutClearing is called because it calls *this* method several times.
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
				buildLazilyLoadedCaches(ReactomeJavaConstants.ReferenceGeneProduct, ReferenceObjectCache.refGeneProdCacheBySpecies, ReferenceObjectCache.refGeneProdCacheByDBID, ReferenceObjectCache.refGeneProdCacheByIdentifier, ReferenceObjectCache.refGeneProdCacheByRefDb, ReferenceObjectCache.lazyLoad);
				return ReferenceObjectCache.refGeneProdCacheBySpecies.get(species);
			}
			case ReactomeJavaConstants.ReferenceDNASequence:
			{
				buildLazilyLoadedCaches(ReactomeJavaConstants.ReferenceDNASequence, ReferenceObjectCache.refDNASeqCacheBySpecies, ReferenceObjectCache.refDNASeqCacheByDBID, ReferenceObjectCache.refDNASeqCacheByIdentifier, ReferenceObjectCache.refDNASeqCacheByRefDb, ReferenceObjectCache.lazyLoad);
				return ReferenceObjectCache.refDNASeqCacheBySpecies.get(species);
			}
			case ReactomeJavaConstants.ReferenceRNASequence:
			{
				buildLazilyLoadedCaches(ReactomeJavaConstants.ReferenceRNASequence, ReferenceObjectCache.refRNASeqCacheBySpecies, ReferenceObjectCache.refRNASeqCacheByDBID, ReferenceObjectCache.refRNASeqCacheByIdentifier, ReferenceObjectCache.refRNASeqCacheByRefDb, ReferenceObjectCache.lazyLoad);
				return ReferenceObjectCache.refRNASeqCacheBySpecies.get(species);
			}
			default:
				logger.error("Invalid className: {} Nothing will be returned.", className);
				return null;
		}
	}
	
	/**
	 * Gets a map that maps Reactome Stable Identifiers to DatabaseObjects.
	 * @return
	 */
	public Map<String, GKInstance> getStableIdentifierCache()
	{
		if (ReferenceObjectCache.lazyLoad)
		{
			ReferenceObjectCache.buildStableIdentifierCache(ReferenceObjectCache.adapter);
		}
		return ReferenceObjectCache.cachedByStableIdentifier;
	}
	
	/**
	 * Build the cache of objects that are cached by StableIdentifier.
	 * @param dbAdaptor - the database adaptor to use.
	 */
	private synchronized static void buildStableIdentifierCache(MySQLAdaptor dbAdaptor)
	{
		if (ReferenceObjectCache.cachedByStableIdentifier.isEmpty())
		{
			try
			{
				@SuppressWarnings("unchecked")
				Collection<GKInstance> instances = (Collection<GKInstance>) dbAdaptor.fetchInstanceByAttribute(ReactomeJavaConstants.DatabaseObject, ReactomeJavaConstants.stableIdentifier, "IS NOT NULL", null);
				instances.parallelStream().forEach( instance -> {
					MySQLAdaptor localAdapter ;
					long threadID = Thread.currentThread().getId();
					localAdapter = getAdaptorForThread(threadID);
					instance.setDbAdaptor(localAdapter);
					
					String stableIdentifier;
					try
					{
						GKInstance stableIdentifierInstance = (GKInstance) instance.getAttributeValue(ReactomeJavaConstants.stableIdentifier);
						stableIdentifier = (String) stableIdentifierInstance.getAttributeValue(ReactomeJavaConstants.identifier);
						ReferenceObjectCache.cachedByStableIdentifier.put(stableIdentifier, instance);
					}
					catch (Exception e)
					{
						e.printStackTrace();
					}
				});
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
			logger.info("{} items cached by Stable Identifier", ReferenceObjectCache.cachedByStableIdentifier.keySet().size());
		}
		//adapterPool.clear();
	}

	/**
	 * Get a ReferenceGeneProduct shell by its DB_ID.
	 * @param id
	 * @return
	 */
	public GKInstance getReferenceGeneProductById(String id)
	{
		return ReferenceObjectCache.refGeneProdCacheByDBID.get(id);
	}
	
	/**
	 * Retreive a list of <em>className</em> objects  filtered by Reference Database <em>and</em> Species.
	 * @param refDb - the Reference Database ID
	 * @param species - the Species ID
	 * @param className - The name of the class.
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
	 * Returns a map of Reference Database names, keyed by their DB_IDs.
	 * @return
	 */
	public Map<String,List<String>> getRefDBMappings()
	{
		if (ReferenceObjectCache.lazyLoad)
		{
			try
			{
				ReferenceObjectCache.buildReferenceDatabaseCache(ReferenceObjectCache.adapter);
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
	 * @return a 1:n mapping of Species names mapped to a list of DB_IDs.
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
	 * @return a set of the keys used to cache by Species.
	 */
	public Set<String> getSetOfSpeciesNames()
	{
		return getSpeciesNamesToIds().keySet();
	}
	
	/**
	 * Returns a map of Species names, keyed by their DB_IDs.
	 * @return a map of Species names, keyed by their DB_IDs.
	 */
	public Map<String,List<String>> getSpeciesNamesByID()
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
	
	/**
	 * Returns a map of Reactions, keyed by db_id.
	 * @return a map of Reactions, keyed by db_id.
	 */
	public Map<String, GKInstance> getReactionsByID()
	{
		buildLazilyLoadedCaches(ReactomeJavaConstants.Reaction, ReferenceObjectCache.reactionCacheBySpecies, ReferenceObjectCache.reactionCacheByDBID, ReferenceObjectCache.reactionCacheByIdentifier, null, ReferenceObjectCache.lazyLoad);
		return ReferenceObjectCache.reactionCacheByDBID;
	}

	/**
	 * Get an object (or list of objects) of type <em>className</em> which have the value <em>identifier</em> for their Identifier attribute.
	 * @param identifier
	 * @param className
	 * @return an object (or list of objects) of type <em>className</em> which have the value <em>identifier</em> for their Identifier attribute.
	 */
	public List<GKInstance> getByIdentifier(String identifier, String className)
	{
		switch (className)
		{
			case ReactomeJavaConstants.ReferenceGeneProduct:
			{
				buildLazilyLoadedCaches(ReactomeJavaConstants.ReferenceGeneProduct, ReferenceObjectCache.refGeneProdCacheBySpecies, ReferenceObjectCache.refGeneProdCacheByDBID, ReferenceObjectCache.refGeneProdCacheByIdentifier, ReferenceObjectCache.refGeneProdCacheByRefDb, ReferenceObjectCache.lazyLoad);
				return ReferenceObjectCache.refGeneProdCacheByIdentifier.get(identifier);
			}
			case ReactomeJavaConstants.ReferenceDNASequence:
			{
				buildLazilyLoadedCaches(ReactomeJavaConstants.ReferenceDNASequence, ReferenceObjectCache.refDNASeqCacheBySpecies, ReferenceObjectCache.refDNASeqCacheByDBID, ReferenceObjectCache.refDNASeqCacheByIdentifier, ReferenceObjectCache.refDNASeqCacheByRefDb, ReferenceObjectCache.lazyLoad);
				return ReferenceObjectCache.refDNASeqCacheByIdentifier.get(identifier);
			}
			case ReactomeJavaConstants.ReferenceRNASequence:
			{
				buildLazilyLoadedCaches(ReactomeJavaConstants.ReferenceRNASequence, ReferenceObjectCache.refRNASeqCacheBySpecies, ReferenceObjectCache.refRNASeqCacheByDBID, ReferenceObjectCache.refRNASeqCacheByIdentifier, ReferenceObjectCache.refRNASeqCacheByRefDb, ReferenceObjectCache.lazyLoad);
				return ReferenceObjectCache.refRNASeqCacheByIdentifier.get(identifier);
			}
			case ReactomeJavaConstants.ReferenceMolecule:
			{
				buildLazilyLoadedCaches(ReactomeJavaConstants.ReferenceMolecule, null, ReferenceObjectCache.moleculeCacheByDBID, ReferenceObjectCache.moleculeCacheByIdentifier, ReferenceObjectCache.moleculeCacheByRefDB, ReferenceObjectCache.lazyLoad);
				return ReferenceObjectCache.moleculeCacheByIdentifier.get(identifier);
			}
			case ReactomeJavaConstants.Reaction:
			{
				buildLazilyLoadedCaches(ReactomeJavaConstants.Reaction, ReferenceObjectCache.reactionCacheBySpecies, ReferenceObjectCache.reactionCacheByDBID, ReferenceObjectCache.reactionCacheByIdentifier, null, ReferenceObjectCache.lazyLoad);
				return ReferenceObjectCache.reactionCacheByIdentifier.get(identifier);
			}
			case ReactomeJavaConstants.DatabaseIdentifier:
			{
				buildLazilyLoadedCaches(ReactomeJavaConstants.DatabaseIdentifier, null, ReferenceObjectCache.databaseIdentifiersByDBID, ReferenceObjectCache.databaseIdentifiersByIdentifier, ReferenceObjectCache.databaseIdentifiersByRefDb, ReferenceObjectCache.lazyLoad);
				return ReferenceObjectCache.databaseIdentifiersByIdentifier.get(identifier);
			}
			case ReactomeJavaConstants.ReferenceTherapeutic:
			{
				buildLazilyLoadedCaches(ReactomeJavaConstants.ReferenceTherapeutic, null, ReferenceObjectCache.referenceTherapeuticsByDBID, ReferenceObjectCache.referenceTherapeuticsByIdentifier, ReferenceObjectCache.referenceTherapeuticsByRefDb, ReferenceObjectCache.lazyLoad);
				return ReferenceObjectCache.referenceTherapeuticsByIdentifier.get(identifier);
			}
			default:
				logger.error("Invalid className: {} Nothing will be returned.", className);
				return null;
		}
	}
	
	/**
	 * This will NOT clear the caches but will attempt to rebuild them. This is only really useful if you 
	 * know that there are new elements in the database but you are not concerned about *removed* elements or *altered* elements.
	 */
	public static void rebuildAllCachesWithoutClearing()
	{
		ReferenceObjectCache.populateCaches(ReferenceObjectCache.adapter);
	}

	/**
	 * Rebuilds caches for all entities that could be cross-referenced by their ReferenceDatabase: ReferenceMolecule, ReferenceDNASequence, ReferenceRNASequence, ReferenceGeneProduct, DatabaseIdentifier.
	 * Cache building is forced (Lazy-load = FALSE).
	 */
	public synchronized void rebuildRefDBCachesWithoutClearing()
	{
		buildLazilyLoadedCaches(ReactomeJavaConstants.ReferenceMolecule, null, ReferenceObjectCache.moleculeCacheByDBID, ReferenceObjectCache.moleculeCacheByIdentifier, ReferenceObjectCache.moleculeCacheByRefDB, false);
		buildLazilyLoadedCaches(ReactomeJavaConstants.ReferenceDNASequence, ReferenceObjectCache.refDNASeqCacheBySpecies, ReferenceObjectCache.refDNASeqCacheByDBID, ReferenceObjectCache.refDNASeqCacheByIdentifier, ReferenceObjectCache.refDNASeqCacheByRefDb, false);
		buildLazilyLoadedCaches(ReactomeJavaConstants.ReferenceRNASequence, ReferenceObjectCache.refRNASeqCacheBySpecies, ReferenceObjectCache.refRNASeqCacheByDBID, ReferenceObjectCache.refRNASeqCacheByIdentifier, ReferenceObjectCache.refRNASeqCacheByRefDb, false);
		buildLazilyLoadedCaches(ReactomeJavaConstants.ReferenceGeneProduct, ReferenceObjectCache.refGeneProdCacheBySpecies, ReferenceObjectCache.refGeneProdCacheByDBID, ReferenceObjectCache.refGeneProdCacheByIdentifier, ReferenceObjectCache.refGeneProdCacheByRefDb, false);
		buildLazilyLoadedCaches(ReactomeJavaConstants.DatabaseIdentifier, null, ReferenceObjectCache.databaseIdentifiersByDBID, ReferenceObjectCache.databaseIdentifiersByIdentifier, ReferenceObjectCache.databaseIdentifiersByRefDb, false);
		buildLazilyLoadedCaches(ReactomeJavaConstants.ReferenceTherapeutic, null, ReferenceObjectCache.referenceTherapeuticsByDBID, ReferenceObjectCache.referenceTherapeuticsByIdentifier, ReferenceObjectCache.referenceTherapeuticsByRefDb, false);
	}
	
	public synchronized void rebuildRefDBNamesAndMappings()
	{
		ReferenceObjectCache.refdbMapping.clear();
		ReferenceObjectCache.refDbNamesToIds.clear();
		getRefDBMappings();
		getRefDbNamesToIds();
	}
	
	/**
	 * Clear and rebuild caches that are of things that could be cached by ReferenceDatabase.
	 */
	public synchronized void rebuildRefDBCachesWithClearing()
	{
		ReferenceObjectCache.clearCachesWithRefDBs();
		this.rebuildRefDBCachesWithoutClearing();
		getRefDBMappings();
		getRefDbNamesToIds();
	}

	/**
	 * Clear all caches that are caches of types that could be cached by ReferenceDatabase.
	 */
	private static void clearCachesWithRefDBs()
	{
		ReferenceObjectCache.moleculeCacheByDBID.clear();
		ReferenceObjectCache.moleculeCacheByIdentifier.clear();
		ReferenceObjectCache.moleculeCacheByRefDB.clear();
		
		ReferenceObjectCache.refDNASeqCacheBySpecies.clear();
		ReferenceObjectCache.refDNASeqCacheByDBID.clear();
		ReferenceObjectCache.refDNASeqCacheByIdentifier.clear();
		ReferenceObjectCache.refDNASeqCacheByRefDb.clear();
		
		ReferenceObjectCache.refRNASeqCacheBySpecies.clear();
		ReferenceObjectCache.refRNASeqCacheByDBID.clear();
		ReferenceObjectCache.refRNASeqCacheByIdentifier.clear();
		ReferenceObjectCache.refRNASeqCacheByRefDb.clear();
		
		ReferenceObjectCache.refGeneProdCacheBySpecies.clear();
		ReferenceObjectCache.refGeneProdCacheByDBID.clear();
		ReferenceObjectCache.refGeneProdCacheByIdentifier.clear();
		ReferenceObjectCache.refGeneProdCacheByRefDb.clear();
		
		ReferenceObjectCache.databaseIdentifiersByDBID.clear();
		ReferenceObjectCache.databaseIdentifiersByIdentifier.clear();
		ReferenceObjectCache.databaseIdentifiersByRefDb.clear();

		ReferenceObjectCache.refdbMapping.clear();
		ReferenceObjectCache.refDbNamesToIds.clear();
		
		ReferenceObjectCache.referenceTherapeuticsByDBID.clear();
		ReferenceObjectCache.referenceTherapeuticsByIdentifier.clear();
		ReferenceObjectCache.referenceTherapeuticsByRefDb.clear();
	}
	
	/**
	 * Clears all of the caches and then rebuilds them all.
	 */
	public static synchronized void clearAndRebuildAllCaches()
	{
		// Method is synchronized because it is clearing out all the caches - don't want two threads to try to do that at the same time,
		// or one thread clearing and one thread trying to read.
		ReferenceObjectCache.reactionCacheByDBID.clear();
		ReferenceObjectCache.reactionCacheByIdentifier.clear();
		ReferenceObjectCache.reactionCacheBySpecies.clear();

		ReferenceObjectCache.speciesMapping.clear();
		ReferenceObjectCache.speciesNamesToIds.clear();
		
		ReferenceObjectCache.clearCachesWithRefDBs();
		
		ReferenceObjectCache.populateCaches(ReferenceObjectCache.adapter);
	}
}
