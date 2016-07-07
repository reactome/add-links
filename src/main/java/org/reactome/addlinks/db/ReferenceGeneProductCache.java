package org.reactome.addlinks.db;

import java.sql.ResultSet;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.persistence.MySQLAdaptor;

public final class ReferenceGeneProductCache {
	private static final Logger logger = LogManager.getLogger();
	private static ReferenceGeneProductCache cache;
	
	private static final String query = "select _displayName, _class, DatabaseObject.db_id as object_db_id, ReferenceEntity.identifier, ReferenceEntity.referenceDatabase as refent_refdb, ReferenceSequence.species " +
										" from DatabaseObject "+
										" inner join ReferenceGeneProduct on ReferenceGeneProduct.db_id = DatabaseObject.db_id "+ 
										" inner join ReferenceEntity on ReferenceEntity.db_id = ReferenceGeneProduct.db_id "+
										" inner join ReferenceSequence on ReferenceSequence.db_id = ReferenceEntity.db_id "+
										" where _class  = \'ReferenceGeneProduct\'; ";
	
	private static String host;
	private static String database;
	private static String username;
	private static String password;
	private static int port;
	
	public static synchronized ReferenceGeneProductCache getInstance() 
	{
		if (ReferenceGeneProductCache.cache == null)
		{
			logger.info("Cache is not initialized. Will initialize now.");
			ReferenceGeneProductCache.cache = new ReferenceGeneProductCache();
		}
		else
		{
			logger.info("Cache is already initialized.");
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
		try {
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
			adapter.cleanUp();
			logger.info("Caches initialized. cache-by-refdb size: {}; cache-by-species size: {}; cache-by-id size: {}",
							ReferenceGeneProductCache.cacheByRefDb.size(),
							ReferenceGeneProductCache.cacheBySpecies.size(),
							ReferenceGeneProductCache.cacheById.size());
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
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
	
	
	private static Map<String,List<ReferenceGeneProductShell>> cacheBySpecies = new HashMap<String,List<ReferenceGeneProductShell>>();
	private static Map<String,List<ReferenceGeneProductShell>> cacheByRefDb = new HashMap<String,List<ReferenceGeneProductShell>>();
	private static Map<String,ReferenceGeneProductShell> cacheById = new HashMap<String,ReferenceGeneProductShell>();
	
	public List<ReferenceGeneProductShell> getByRefDb(String refDb)
	{
		return ReferenceGeneProductCache.cacheByRefDb.get(refDb);
	}
	
	public List<ReferenceGeneProductShell> getBySpecies(String species)
	{
		return ReferenceGeneProductCache.cacheBySpecies.get(species);
	}
	
	public ReferenceGeneProductShell getById(String id)
	{
		return ReferenceGeneProductCache.cacheById.get(id);
	}
	
	public List<ReferenceGeneProductShell> getByRefDbAndSpecies(String refDb, String species)
	{
		//Get a list by referenceDatabase
		List<ReferenceGeneProductShell> byRefDb = this.getByRefDb(refDb);
		//Now filter the items in that list by species.
		List<ReferenceGeneProductShell> objectsByRefDbAndSpecies = byRefDb.stream().filter(p -> p.getSpecies().equals(species)).collect(Collectors.toList());
		
		return objectsByRefDbAndSpecies;
	}
}
