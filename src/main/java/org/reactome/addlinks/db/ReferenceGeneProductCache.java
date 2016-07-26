package org.reactome.addlinks.db;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.persistence.MySQLAdaptor;

public final class ReferenceGeneProductCache 
{
	
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
	
	private static final String speciesMappingQuery = " select distinct name, db_id from Taxon_2_name order by name asc; ";
	
	private static String host;
	private static String database;
	private static String username;
	private static String password;
	private static int port;
	
	private static boolean cacheInitializedMessageHasBeenPrinted = false;
	
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
			ResultSet rs = adapter.executeQuery(ReferenceGeneProductCache.query, null);
			
			while (rs.next())
			{
				ReferenceGeneProductShell shell = new ReferenceGeneProductShell();
				shell.setDbId(rs.getString("object_db_id"));
				shell.setDisplayName(rs.getString("_displayName"));
				shell.setIdentifier(rs.getString("identifier"));
				shell.setReactomeClass(rs.getString("_class"));
				shell.setReferenceDatabase(rs.getString("refent_refdb"));
				shell.setSpecies(rs.getString("species"));
				
				//Now, insert into the caches.
				//
				//Species Cache
				//If this species is not yet cached...
				if (! ReferenceGeneProductCache.cacheBySpecies.containsKey(shell.getSpecies()))
				{
					List<ReferenceGeneProductShell> bySpecies = new LinkedList<ReferenceGeneProductShell>();
					bySpecies.add(shell);
					ReferenceGeneProductCache.cacheBySpecies.put(shell.getSpecies(), bySpecies);
				}
				else
				{
					ReferenceGeneProductCache.cacheBySpecies.get(shell.getSpecies()).add(shell);
				}
				//ReferenceDatabase Cache
				//If this species is not yet cached...
				if (! ReferenceGeneProductCache.cacheByRefDb.containsKey(shell.getReferenceDatabase()))
				{
					List<ReferenceGeneProductShell> byRefDb = new LinkedList<ReferenceGeneProductShell>();
					byRefDb.add(shell);
					ReferenceGeneProductCache.cacheByRefDb.put(shell.getReferenceDatabase(), byRefDb);
				}
				else
				{
					ReferenceGeneProductCache.cacheByRefDb.get(shell.getReferenceDatabase()).add(shell);
				}
				// ID Cache
				ReferenceGeneProductCache.cacheById.put(shell.getDbId(), shell);
			}
			rs.close();
			
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
	
	private static Map<String,List<ReferenceGeneProductShell>> cacheBySpecies = new HashMap<String,List<ReferenceGeneProductShell>>();
	private static Map<String,List<ReferenceGeneProductShell>> cacheByRefDb = new HashMap<String,List<ReferenceGeneProductShell>>();
	private static Map<String,ReferenceGeneProductShell> cacheById = new HashMap<String,ReferenceGeneProductShell>();
	
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
	public List<ReferenceGeneProductShell> getByRefDb(String refDb)
	{
		return ReferenceGeneProductCache.cacheByRefDb.get(refDb);
	}
	
	/**
	 * Get a list of ReferenceGeneProduct shells keyed by Species.
	 * @param species
	 * @return
	 */
	public List<ReferenceGeneProductShell> getBySpecies(String species)
	{
		return ReferenceGeneProductCache.cacheBySpecies.get(species);
	}
	
	/**
	 * Get a ReferenceGeneProduct shell by its DB_ID.
	 * @param id
	 * @return
	 */
	public ReferenceGeneProductShell getById(String id)
	{
		return ReferenceGeneProductCache.cacheById.get(id);
	}
	
	/**
	 * Retreive a list of ReferenceGeneProduct shells filtered by Reference Database <em>and</em> Species.
	 * @param refDb
	 * @param species
	 * @return
	 */
	public List<ReferenceGeneProductShell> getByRefDbAndSpecies(String refDb, String species)
	{
		//Get a list by referenceDatabase
		List<ReferenceGeneProductShell> byRefDb = this.getByRefDb(refDb);
		//Now filter the items in that list by species.
		List<ReferenceGeneProductShell> objectsByRefDbAndSpecies = byRefDb.stream().filter(p -> p.getSpecies().equals(species)).collect(Collectors.toList());
		
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
