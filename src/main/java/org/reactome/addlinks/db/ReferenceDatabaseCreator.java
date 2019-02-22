package org.reactome.addlinks.db;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.gk.model.GKInstance;
import org.gk.model.InstanceDisplayNameGenerator;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.InvalidAttributeException;
import org.gk.schema.InvalidAttributeValueException;
import org.gk.schema.SchemaAttribute;
import org.gk.schema.SchemaClass;
import org.reactome.addlinks.CustomLoggable;

public class ReferenceDatabaseCreator implements CustomLoggable
{
	private MySQLAdaptor adapter;
	private static Logger logger ;
	
	public ReferenceDatabaseCreator(MySQLAdaptor adapter)
	{
		this.adapter = adapter;
		if (ReferenceDatabaseCreator.logger  == null)
		{
			ReferenceDatabaseCreator.logger = this.createLogger("ReferenceDatabaseCreator", "RollingRandomAccessFile", this.getClass().getName(), true, Level.DEBUG);
		}
	}
	
	/**
	 * Creates a reference database with a primary name and some (optional) aliases.
	 * This will not create a reference database if an existing reference database has the same primaryName. 
	 * @param url - The URL of the ReferenceDatabase.
	 * @param accessUrl - The access URL of the RefereneDatabase.
	 * @param primaryName - The primary name for this reference database (will have name_rank==0)
	 * @param aliases - Other names.
	 * @return the DB_ID of the new ReferenceDatabase.
	 * @throws Exception 
	 */
	public Long createReferenceDatabaseWithAliases(String url, String accessUrl, String primaryName, String ... aliases) throws Exception
	{
		Long dbid = null;
		//First, let's check that the Reference Database doesn't already exist. all we have to go on is the name...
		try
		{
			SchemaClass refDBClass = adapter.getSchema().getClassByName(ReactomeJavaConstants.ReferenceDatabase);
			SchemaAttribute dbNameAttrib = refDBClass.getAttribute(ReactomeJavaConstants.name);
			SchemaAttribute accessUrlAttrib = refDBClass.getAttribute(ReactomeJavaConstants.accessUrl);
			// Try to get pre-existing ReferenceDatabase objects based on accessURL, but if there is no accessUrl, use the name.
			@SuppressWarnings("unchecked")
			Collection<GKInstance> preexistingReferenceDBs = accessUrl != null
																? (Collection<GKInstance>) adapter.fetchInstanceByAttribute(accessUrlAttrib, "=", accessUrl)
																: (Collection<GKInstance>) adapter.fetchInstanceByAttribute(dbNameAttrib, "=", primaryName);
			// Now that we have a bunch of things that contain primaryName, we need to find the ones where the rank of that name-attribute is 0.
			if (preexistingReferenceDBs!=null && preexistingReferenceDBs.size() > 0)
			{
				for (GKInstance refDBInst : preexistingReferenceDBs)
				{
					// It looks like the MySQLAdapter class will order the results by "*_rank" so the primary name
					// should be the first thing in the list, if there's anything at all.
					@SuppressWarnings("unchecked")
					List<String> names = (List<String>) refDBInst.getAttributeValuesList(ReactomeJavaConstants.name);
					
					// if primaryName is not already in use...
					if ( (names==null || names.size() == 0) || (!names.get(0).equals(primaryName)) )
					{
						dbid = createRefDBWithAliases(url, accessUrl, primaryName, refDBClass, aliases);
					}
					else
					{
						logger.warn("The primaryName {} appears to already be in use by {}", primaryName, refDBInst);
						dbid = refDBInst.getDBID();
					}
				}
			}
			else
			{
				dbid = createRefDBWithAliases(url, accessUrl, primaryName, refDBClass, aliases);
			}
			
		}
		catch (Exception e)
		{
			logger.error("Error while trying to create a Reference Database object: "+e.getMessage());
			e.printStackTrace();
			throw e;
		}
		return dbid;
	}

	/**
	 * Creates a ReferenceDatabase with a name and some alias-names.
	 * @param url - The URL for the ReferenceDatabase. 
	 * @param accessUrl - The URL that will be used to actually access data. Should contain ###ID### which will be used when other parts of Reactome need to insert the ID of the thing being accessed at this URL.
	 * @param primaryName - The primary name for this Reference database.
	 * @param refDBClass - The SchemaClass for the object that will be created.
	 * @param aliases - A list of alternate names for the ReferenceDatabase object.
	 * @return The DB_ID of the new ReferenceDatabase object that was just created.
	 * @throws InvalidAttributeException
	 * @throws InvalidAttributeValueException
	 * @throws Exception
	 */
	private long createRefDBWithAliases(String url, String accessUrl, String primaryName, SchemaClass refDBClass, String... aliases) throws InvalidAttributeException, InvalidAttributeValueException, Exception
	{
		// Create new GKInstance, with the appropriate primary name.
		GKInstance newReferenceDB = new GKInstance(refDBClass);
		newReferenceDB.addAttributeValue(ReactomeJavaConstants.name, primaryName);
		// Add aliases.
		for (String alias : aliases)
		{
			newReferenceDB.addAttributeValue(ReactomeJavaConstants.name, alias);
		}
		// Set othe attributes.
		newReferenceDB.setAttributeValue(ReactomeJavaConstants.url, url);
		newReferenceDB.setAttributeValue(ReactomeJavaConstants.accessUrl, accessUrl);
		newReferenceDB.setDbAdaptor(this.adapter);
		InstanceDisplayNameGenerator.setDisplayName(newReferenceDB);
		//Persist to storage.
		return this.adapter.storeInstance(newReferenceDB);
	}
	
	/**
	 * Creates a ReferenceDatabase. If the ReferenceDatabase already exists, it will not be created.
	 * @param url - The URL of the ReferenceDatabase.
	 * @param accessUrl - The access URL of the RefereneDatabase.
	 * @param names - A list of names for this ReferenceDatabase. If *none* of these values are already in the database, then a new ReferenceDatabase
	 * will be created and these names will be associated with it. If *any* of these names already exist, then the other names will be associated with it.
	 * The URLs will *not* be updated in the case that a ReferenceDatabase name is pre-existing in the database.
	 * @return - The ID of the reference database, whether it was created by this function call, or if it was pre-existing.
	 * @throws Exception 
	 */
	public long createReferenceDatabaseToURL(String url, String accessUrl, String ... names) throws Exception
	{
		//First, let's check that the Reference Database doesn't already exist. all we have to go on is the name...
		SchemaClass refDBClass = adapter.getSchema().getClassByName(ReactomeJavaConstants.ReferenceDatabase);
		long refDBID = -1L;
		try
		{
			SchemaAttribute dbNameAttrib = refDBClass.getAttribute(ReactomeJavaConstants.name);
			
			List<String> namesNotYetInDB = new ArrayList<String>(names.length);
			List<GKInstance> instancesInDB = new ArrayList<GKInstance>();
			for (String name : names)
			{
				@SuppressWarnings("unchecked")
				Collection<GKInstance> preexistingReferenceDBs = (Collection<GKInstance>) adapter.fetchInstanceByAttribute(ReactomeJavaConstants.ReferenceDatabase, ReactomeJavaConstants.name, "=", name);
				
				// Add to the list if the name is not yet in the database.
				if (preexistingReferenceDBs.size() == 0  )
				{
					namesNotYetInDB.add(name);
				}
				else
				{
					logger.info("It looks like there were already {} Reference Databases with the name {} for accessURL {}, so no new Reference Databases with *this* name will be created.", preexistingReferenceDBs.size(), name, accessUrl);
					// I really don't expect preexistingReferenceDBs to have more than 1 element, but just in case it does...
					instancesInDB.addAll(preexistingReferenceDBs);
				}
			}
			
			//No RefDatabases that match any of the given names exist, so we're creating the Reference Database with all names now. 
			if (instancesInDB.size() == 0)
			{
				GKInstance newReferenceDB = new GKInstance(refDBClass);
				// A reference database could have multiple names/aliases, so keep adding them.
				for (String name : names)
				{
					newReferenceDB.addAttributeValue(dbNameAttrib, name);
				}

				newReferenceDB.setAttributeValue(ReactomeJavaConstants.url, url);
				newReferenceDB.setAttributeValue(ReactomeJavaConstants.accessUrl, accessUrl);
				newReferenceDB.setDbAdaptor(this.adapter);
				InstanceDisplayNameGenerator.setDisplayName(newReferenceDB);
				refDBID = this.adapter.storeInstance(newReferenceDB);
				logger.info("New ReferenceDatabase has been created: {}", newReferenceDB);
			}
			//Othwerwise, some ReferenceDatabase object(s) already exist with some of the names given here. So, we need to update it with the new names. 
			else
			{
				for (GKInstance preexistingRefDB : instancesInDB)
				{
					@SuppressWarnings("unchecked")
					List<String> preexistingNames = (List<String>)preexistingRefDB.getAttributeValuesList(dbNameAttrib);
					// the names to add: everything that is in the input parameter "names" but not in "preexistingNames"
					List<String> namesToAdd = (Arrays.asList(names)).stream().filter(p -> !preexistingNames.contains(p)).collect(Collectors.toList());
					
					for (String name : namesToAdd)
					{
						logger.info("Adding the name {} to the existing ReferenceDatabase {}",name,preexistingRefDB + "( " + preexistingRefDB.getAttributeValuesList(ReactomeJavaConstants.name).toString() + " )");
						preexistingRefDB.addAttributeValue(dbNameAttrib, name);
						this.adapter.updateInstanceAttribute(preexistingRefDB, dbNameAttrib);
						refDBID = preexistingRefDB.getDBID();
					}
				}
			}
		}
		catch (Exception e)
		{
			logger.error("Error while trying to create a Reference Database object: "+e.getMessage());
			throw e;
		}
		return refDBID;
	}
	
	/**
	 * Adds additional names to a ReferenceDatabase object's list of names.
	 * @param dbId - the DB_ID of the ReferenceDatabase to update.
	 * @param names - An array of names to add.
	 * @throws InvalidAttributeValueException, Exception 
	 * @throws InvalidAttributeException 
	 */
	public void addAliasesToReferenceDatabase(Long dbId, String ...names) throws InvalidAttributeException, InvalidAttributeValueException, Exception
	{
		GKInstance refDB = this.adapter.fetchInstance(dbId);
		if (refDB!=null)
		{
			@SuppressWarnings("unchecked")
			List<String> preexistingNames = (List<String>) refDB.getAttributeValuesList(ReactomeJavaConstants.name);
			List<String> newAliases = Arrays.stream(names)
										.distinct() // no duplicates!
										.filter(name -> !preexistingNames.contains(name)) // remove any name that's already in preexistingNames
										.sorted() // Sort them
										.collect(Collectors.toList());
			logger.info("Adding aliases: \"{}\" to RefDB: {}, which had preexisting names: {}", newAliases.toString(), refDB.toString(), preexistingNames.toString());
			preexistingNames.addAll(newAliases);
			refDB.setAttributeValue(ReactomeJavaConstants.name, preexistingNames);
			this.adapter.updateInstanceAttribute(refDB, ReactomeJavaConstants.name);
		}
	}
	
	/**
	 * Adds additional names to a ReferenceDatabase object's list of names.
	 * @param refDBPrimaryName - the PRIMARY name of the reference database.
	 * @param names - An array of names to add.
	 */
	public void addAliasesToReferenceDatabase(String refDBPrimaryName, String ...names) throws InvalidAttributeException, InvalidAttributeValueException, Exception
	{
		@SuppressWarnings("unchecked")
		Set<GKInstance> refDBs = (Set<GKInstance>) this.adapter.fetchInstanceByAttribute(ReactomeJavaConstants.ReferenceDatabase, ReactomeJavaConstants.name, "=", refDBPrimaryName);
		for (GKInstance refDB : refDBs)
		{
			if (refDB != null)
			{
				this.addAliasesToReferenceDatabase(refDB.getDBID(), names);
			}
			else
			{
				logger.warn("Can't add aliases to ReferenceDatabase \"{}\" because nothing with that name could be found in the database.", refDBPrimaryName);
			}
		}
	}
}
