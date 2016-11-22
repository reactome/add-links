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

	private static final Logger logger = LogManager.getLogger();
	private static ReferenceObjectCache cache;
	
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
	
	public static synchronized ReferenceObjectCache getInstance() 
	{
		if (ReferenceObjectCache.cache == null)
		{
			logger.info("Cache is not initialized. Will initialize now.");
			ReferenceObjectCache.cache = new ReferenceObjectCache();
		}
		else
		{
			if (!cacheInitializedMessageHasBeenPrinted)
			{
				logger.info("Cache is already initialized.");
				cacheInitializedMessageHasBeenPrinted = true;
			}
		}
		
		return ReferenceObjectCache.cache;
	}
	
	public static void setAdapter(MySQLAdaptor adapter)
	{
		ReferenceObjectCache.adapter = adapter;
	}
	

	private ReferenceObjectCache()
	{
		if (ReferenceObjectCache.adapter!=null)
		{
			try
			{
				logger.info("Building ReferenceObject caches...");
				Collection<GKInstance> referenceGeneProducts = ReferenceObjectCache.adapter.fetchInstancesByClass(ReactomeJavaConstants.ReferenceGeneProduct);
				for (GKInstance refGeneProduct : referenceGeneProducts)
				{
					//Get all the other values.
					//(
					// I still don't like doing this... I think doing it all in one query would run faster, but I'm starting to think 
					// it's better to stick with the existing API. It's a little weird, but it seemsd to work. Once you get used to it, that is. ;)
					//)
					//adapter.fastLoadInstanceAttributeValues(refGeneProduct);
					
					//Now, insert into the caches.
					//
					//Species Cache
					//If this species is not yet cached...

					String species = String.valueOf( ((GKInstance) refGeneProduct.getAttributeValue(ReactomeJavaConstants.species)).getDBID() );
					if (! ReferenceObjectCache.cacheBySpecies.containsKey(species))
					{
						List<GKInstance> bySpecies = new LinkedList<GKInstance>();
						bySpecies.add(refGeneProduct);
						ReferenceObjectCache.cacheBySpecies.put(species, bySpecies);
					}
					else
					{
						ReferenceObjectCache.cacheBySpecies.get(species).add(refGeneProduct);
					}
					//ReferenceDatabase Cache
					//If this species is not yet cached...
					String refDB = String.valueOf(((GKInstance) refGeneProduct.getAttributeValue(ReactomeJavaConstants.referenceDatabase)).getDBID());
					if (! ReferenceObjectCache.cacheByRefDb.containsKey(refDB))
					{
						List<GKInstance> byRefDb = new LinkedList<GKInstance>();
						byRefDb.add(refGeneProduct);
						ReferenceObjectCache.cacheByRefDb.put(refDB, byRefDb);
					}
					else
					{
						ReferenceObjectCache.cacheByRefDb.get(refDB).add(refGeneProduct);
					}
					// ID Cache
					ReferenceObjectCache.cacheById.put(String.valueOf(refGeneProduct.getDBID()), refGeneProduct);
				}
				logger.debug("Built cacheById, cacheByRefDb, and cacheBySpecies caches.");
				
				// Build up the Reference Database caches.
				Collection<GKInstance> refDBs = adapter.fetchInstancesByClass(ReactomeJavaConstants.ReferenceDatabase);
				
				for (GKInstance refDB : refDBs)
				{
					String db_id = refDB.getDBID().toString();
					String name = (String) refDB.getAttributeValue(ReactomeJavaConstants.name);
					List<String> listOfIds;
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
				logger.debug("Built refdbMapping cache.");
				
				// Build up the species caches.
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

				logger.info("Caches initialized."
						+ "\n\tKeys in cache-by-refdb: {};"
						+ "\n\tkeys in cache-by-species: {};"
						+ "\n\tkeys in cache-by-id: {};"
						+ "\n\tkeys in refDbMapping: {};"
						+ "\n\tkeys in speciesMapping: {}",
								ReferenceObjectCache.cacheByRefDb.size(),
								ReferenceObjectCache.cacheBySpecies.size(),
								ReferenceObjectCache.cacheById.size(),
								ReferenceObjectCache.refdbMapping.size(),
								ReferenceObjectCache.speciesMapping.size());
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
	
	private static Map<String,List<GKInstance>> cacheBySpecies = new HashMap<String,List<GKInstance>>();
	private static Map<String,List<GKInstance>> cacheByRefDb = new HashMap<String,List<GKInstance>>();
	private static Map<String,GKInstance> cacheById = new HashMap<String,GKInstance>();
	
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
	public List<GKInstance> getByRefDb(String refDb)
	{
		return ReferenceObjectCache.cacheByRefDb.containsKey(refDb) ? ReferenceObjectCache.cacheByRefDb.get(refDb) : new ArrayList<GKInstance>(0);
	}
	
	/**
	 * Get a list of ReferenceGeneProduct shells keyed by Species.
	 * @param species
	 * @return
	 */
	public List<GKInstance> getBySpecies(String species)
	{
		return ReferenceObjectCache.cacheBySpecies.get(species);
	}
	
	/**
	 * Get a ReferenceGeneProduct shell by its DB_ID.
	 * @param id
	 * @return
	 */
	public GKInstance getById(String id)
	{
		return ReferenceObjectCache.cacheById.get(id);
	}
	
	/**
	 * Retreive a list of ReferenceGeneProduct shells filtered by Reference Database <em>and</em> Species.
	 * @param refDb
	 * @param species
	 * @return
	 */
	public List<GKInstance> getByRefDbAndSpecies(String refDb, String species)
	{
		//Get a list by referenceDatabase
		List<GKInstance> byRefDb = this.getByRefDb(refDb);
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
	public Set<String> getListOfRefDbs()
	{
		return ReferenceObjectCache.cacheByRefDb.keySet();
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
		return ReferenceObjectCache.cacheBySpecies.keySet();
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
