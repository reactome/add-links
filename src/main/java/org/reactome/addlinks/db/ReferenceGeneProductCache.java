package org.reactome.addlinks.db;

import java.sql.ResultSet;
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
import org.gk.schema.InvalidAttributeException;

public final class ReferenceGeneProductCache 
{
	//TODO: Update this class so that it can cache other ReferenceEntities: ReferenceMolecules, ReferenceIsoForm and ReferenceDNASequences, for example.
	public class ReferenceGeneProductShell
	{
		private String displayName;
		private String reactomeClass;
		private String dbId;
		private String identifier;
		private String referenceDatabase;
		private String species;
		
		public String getDisplayName() {
			return this.displayName;
		}
		public void setDisplayName(String displayName) {
			this.displayName = displayName;
		}
		public String getReactomeClass() {
			return this.reactomeClass;
		}
		public void setReactomeClass(String reactomeClass) {
			this.reactomeClass = reactomeClass;
		}
		public String getDbId() {
			return this.dbId;
		}
		public void setDbId(String dbId) {
			this.dbId = dbId;
		}
		public String getIdentifier() {
			return this.identifier;
		}
		public void setIdentifier(String identifier) {
			this.identifier = identifier;
		}
		public String getReferenceDatabase() {
			return this.referenceDatabase;
		}
		public void setReferenceDatabase(String referenceDatabase) {
			this.referenceDatabase = referenceDatabase;
		}
		public String getSpecies() {
			return this.species;
		}
		public void setSpecies(String species) {
			this.species = species;
		}
	}
	
	private static final Logger logger = LogManager.getLogger();
	private static ReferenceGeneProductCache cache;
	
	private static final String query = "select _displayName, _class, DatabaseObject.db_id as object_db_id, ReferenceEntity.identifier, ReferenceEntity.referenceDatabase as refent_refdb, ReferenceSequence.species " +
										" from DatabaseObject "+
										" inner join ReferenceGeneProduct on ReferenceGeneProduct.db_id = DatabaseObject.db_id "+ 
										" inner join ReferenceEntity on ReferenceEntity.db_id = ReferenceGeneProduct.db_id "+
										" inner join ReferenceSequence on ReferenceSequence.db_id = ReferenceEntity.db_id "+
										" where _class  = \'ReferenceGeneProduct\'; ";
	
	private static final String refdbMappingQuery = " select distinct name, db_id from ReferenceDatabase_2_name order by name asc; ";
	
	private static final String speciesMappingQuery = " select distinct name, db_id, name_rank from Taxon_2_name where name_rank = 0 order by db_id asc, name_rank asc, name asc; ";
	
	private static String host;
	private static String database;
	private static String username;
	private static String password;
	private static int port;
	
	private static boolean cacheInitializedMessageHasBeenPrinted = false;
	private static MySQLAdaptor adapter;
	
	public static synchronized ReferenceGeneProductCache getInstance() 
	{
		if (ReferenceGeneProductCache.cache == null)
		{
			logger.info("Cache is not initialized. Will initialize now.");
			ReferenceGeneProductCache.cache = new ReferenceGeneProductCache();
		}
		else if (!cacheInitializedMessageHasBeenPrinted)
		{
			logger.info("Cache is already initialized.");
			cacheInitializedMessageHasBeenPrinted = true;
		}
		
		return ReferenceGeneProductCache.cache;
	}
	
	public static void setAdapter(MySQLAdaptor adapter)
	{
		ReferenceGeneProductCache.adapter = adapter;
	}
	
	public static void setDbParams(String host, String database, String username, String password, int port)
	{
		ReferenceGeneProductCache.host = host;
		ReferenceGeneProductCache.database = database;
		ReferenceGeneProductCache.username = username;
		ReferenceGeneProductCache.password = password;
		ReferenceGeneProductCache.port = port;
	}
	
	private ReferenceGeneProductCache()
	{
		try
		{
			MySQLAdaptor adapter = new MySQLAdaptor(ReferenceGeneProductCache.host, ReferenceGeneProductCache.database, ReferenceGeneProductCache.username, ReferenceGeneProductCache.password, ReferenceGeneProductCache.port);
			//ResultSet rs = adapter.executeQuery(ReferenceGeneProductCache.query, null);
			Collection<GKInstance> referenceGeneProducts = adapter.fetchInstancesByClass(ReactomeJavaConstants.ReferenceGeneProduct);
			//while (rs.next())
			for (GKInstance refGeneProduct : referenceGeneProducts)
			{
				//Get all the other values.
				//(
				// I still don't like doing this... I think doing it all in one query would run faster, but I'm starting to think 
				// it's better to stick with the existing API. It's a little weird, but it seemsd to work. Once you get used to it, that is. ;)
				//)
				adapter.loadInstanceAttributeValues(refGeneProduct);
				
//				ReferenceGeneProductShell shell = new ReferenceGeneProductShell();
//				shell.setDbId(rs.getString("object_db_id"));
//				shell.setDisplayName(rs.getString("_displayName"));
//				shell.setIdentifier(rs.getString("identifier"));
//				shell.setReactomeClass(rs.getString("_class"));
//				shell.setReferenceDatabase(rs.getString("refent_refdb"));
//				shell.setSpecies(rs.getString("species"));
				
				//Now, insert into the caches.
				//
				//Species Cache
				//If this species is not yet cached...
				
				String species = (String) refGeneProduct.getAttributeValue(ReactomeJavaConstants.species);
				if (! ReferenceGeneProductCache.cacheBySpecies.containsKey(species))
				{
					List<GKInstance> bySpecies = new LinkedList<GKInstance>();
					bySpecies.add(refGeneProduct);
					ReferenceGeneProductCache.cacheBySpecies.put(species, bySpecies);
				}
				else
				{
					ReferenceGeneProductCache.cacheBySpecies.get(species).add(refGeneProduct);
				}
				//ReferenceDatabase Cache
				//If this species is not yet cached...
				String refDB = (String) refGeneProduct.getAttributeValue(ReactomeJavaConstants.referenceDatabase);
				if (! ReferenceGeneProductCache.cacheByRefDb.containsKey(refDB))
				{
					List<GKInstance> byRefDb = new LinkedList<GKInstance>();
					byRefDb.add(refGeneProduct);
					ReferenceGeneProductCache.cacheByRefDb.put(refDB, byRefDb);
				}
				else
				{
					ReferenceGeneProductCache.cacheByRefDb.get(refDB).add(refGeneProduct);
				}
				// ID Cache
				ReferenceGeneProductCache.cacheById.put(Long.toString(refGeneProduct.getDBID()), refGeneProduct);
			}
			//rs.close();
			
			// Build up the Reference Database caches.
			ResultSet refDbResultSet = adapter.executeQuery(ReferenceGeneProductCache.refdbMappingQuery,null);
			while (refDbResultSet.next())
			{
				String db_id = refDbResultSet.getString("db_id");
				String name = refDbResultSet.getString("name");
				List<String> listOfIds;
				// if the 1:n cache of names-to-IDs already has "name" then just add the db_id to the existing list.
				if (ReferenceGeneProductCache.refDbNamesToIds.containsKey(name))
				{
					listOfIds = ReferenceGeneProductCache.refDbNamesToIds.get(name);
				}
				else
				{
					listOfIds = new ArrayList<String>(1);
				}
				listOfIds.add(db_id);

				ReferenceGeneProductCache.refDbNamesToIds.put(name, listOfIds);

				// refdbMapping is a 1:n from db_ids to names.
				List<String> listOfNames;
				if (ReferenceGeneProductCache.refdbMapping.containsKey(db_id))
				{
					listOfNames = ReferenceGeneProductCache.refdbMapping.get(db_id);
				}
				else
				{
					listOfNames = new ArrayList<String>(1);
				}
				listOfNames.add(name);
				ReferenceGeneProductCache.refdbMapping.put(db_id,listOfNames);
			}
			refDbResultSet.close();
			
			// Build up the species caches.
			ResultSet speciesResultSet = adapter.executeQuery(ReferenceGeneProductCache.speciesMappingQuery,null);
			while (speciesResultSet.next())
			{
				String db_id = speciesResultSet.getString("db_id");
				String name = speciesResultSet.getString("name");
				List<String> listOfIds;
				if (ReferenceGeneProductCache.speciesNamesToIds.containsKey(name))
				{
					listOfIds = ReferenceGeneProductCache.speciesNamesToIds.get(name);
				}
				else
				{
					listOfIds = new ArrayList<String>(1);
				}
				listOfIds.add(db_id);
				ReferenceGeneProductCache.speciesNamesToIds.put(name, listOfIds);
				
				List<String> listOfNames;
				if (ReferenceGeneProductCache.speciesMapping.containsKey(db_id))
				{
					listOfNames = ReferenceGeneProductCache.speciesMapping.get(db_id);
				}
				else
				{
					listOfNames = new ArrayList<String>(1);
				}
				listOfNames.add(name);
				ReferenceGeneProductCache.speciesMapping.put(db_id,listOfNames);
			}
			speciesResultSet.close();
			
			adapter.cleanUp();
			logger.info("Caches initialized."
					+ " Keys in cache-by-refdb: {};"
					+ " keys in cache-by-species: {};"
					+ " keys in cache-by-id: {};"
					+ " keys in refDbMapping: {};"
					+ " keys in speciesMapping: {}",
							ReferenceGeneProductCache.cacheByRefDb.size(),
							ReferenceGeneProductCache.cacheBySpecies.size(),
							ReferenceGeneProductCache.cacheById.size(),
							ReferenceGeneProductCache.refdbMapping.size(),
							ReferenceGeneProductCache.speciesMapping.size());
		}
		catch (Exception e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
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
		return ReferenceGeneProductCache.cacheByRefDb.containsKey(refDb) ? ReferenceGeneProductCache.cacheByRefDb.get(refDb) : new ArrayList<GKInstance>(0);
	}
	
	/**
	 * Get a list of ReferenceGeneProduct shells keyed by Species.
	 * @param species
	 * @return
	 */
	public List<GKInstance> getBySpecies(String species)
	{
		return ReferenceGeneProductCache.cacheBySpecies.get(species);
	}
	
	/**
	 * Get a ReferenceGeneProduct shell by its DB_ID.
	 * @param id
	 * @return
	 */
	public GKInstance getById(String id)
	{
		return ReferenceGeneProductCache.cacheById.get(id);
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
				return p.getAttributeValue(ReactomeJavaConstants.species).equals(species);
			}
			catch (InvalidAttributeException e)
			{
				e.printStackTrace();
				throw new RuntimeException(e);
			}
			catch (Exception e)
			{
				e.printStackTrace();
				throw new RuntimeException(e);
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
		return ReferenceGeneProductCache.cacheByRefDb.keySet();
	}

	/**
	 * Returns a map of Reference Database names, keyed by their DB_IDs.
	 * @return
	 */
	public Map<String,List<String>> getRefDBMappings()
	{
		return ReferenceGeneProductCache.refdbMapping;
	}
	
	/**
	 * Returns a 1:n mapping of Reference Datbase names mapped to a list of DB_IDs.
	 * @return
	 */
	public Map<String,List<String>> getRefDbNamesToIds()
	{
		return ReferenceGeneProductCache.refDbNamesToIds;
	}
	
	/**
	 * Returns a 1:n mapping of Species names mapped to a list of DB_IDs.
	 * @return
	 */
	public Map<String,List<String>> getSpeciesNamesToIds()
	{
		return ReferenceGeneProductCache.speciesNamesToIds;
	}
	
	/**
	 * Returns a set of the keys used to cache by Species.
	 * @return
	 */
	public Set<String> getListOfSpecies()
	{
		return ReferenceGeneProductCache.cacheBySpecies.keySet();
	}
	
	/**
	 * Returns a map of Species names, keyed by their DB_IDs.
	 * @return
	 */
	public Map<String,List<String>> getSpeciesMappings()
	{
		return ReferenceGeneProductCache.speciesMapping;
	}
}
