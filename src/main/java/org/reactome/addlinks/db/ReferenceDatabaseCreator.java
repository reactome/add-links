package org.reactome.addlinks.db;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
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
import org.reactome.release.common.database.InstanceEditUtils;

public class ReferenceDatabaseCreator implements CustomLoggable
{
	private MySQLAdaptor adapter;
	private static Logger logger ;
	private long personID;
	
	public ReferenceDatabaseCreator(MySQLAdaptor adapter, long personID)
	{
		this.personID = personID;
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
			// Try to get pre-existing ReferenceDatabase objects based on accessURL, but if there is no accessUrl, use the name.
			@SuppressWarnings("unchecked")
			Set<GKInstance> preexistingReferenceDBs = accessUrl != null
															? (Set<GKInstance>) adapter.fetchInstanceByAttribute(ReactomeJavaConstants.ReferenceDatabase, ReactomeJavaConstants.accessUrl, "=", accessUrl)
															: (Set<GKInstance>) adapter.fetchInstanceByAttribute(ReactomeJavaConstants.ReferenceDatabase, ReactomeJavaConstants.name, "=", primaryName);

			// If there is an accessUrl, filter preexistingReferenceDBs so that it only contains instances with names that match primaryName.
			// This mostly applies to ReferenceDatabases like ENSEMBL - there are many databases, and some may have the same species-specific URL
			// but different names such as "ENSEMBL_*_PROTEIN" and "ENSEMBL_*_GENE".
			if (accessUrl != null)
			{
				Predicate<GKInstance> nameIsPreexisting = (GKInstance refDB) -> {
					try
					{
						@SuppressWarnings("unchecked")
						Set<String> names = new HashSet<>(refDB.getAttributeValuesList(ReactomeJavaConstants.name));
						return names.contains(primaryName);
					}
					catch (Exception e)
					{
						e.printStackTrace();
					}
					return false;
				};
				// Now filter out the referenceDBs whose primaryNames don't match
				preexistingReferenceDBs = preexistingReferenceDBs.stream().filter(nameIsPreexisting).collect(Collectors.toSet());
			}
			SchemaClass refDBClass = this.adapter.getSchema().getClassByName(ReactomeJavaConstants.ReferenceDatabase);
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
		long refDBID = storeNewReferenceDatabaseObject(url, accessUrl, newReferenceDB);
		logger.info("New ReferenceDatabase has been created: {}", newReferenceDB);
		return refDBID;
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
				refDBID = storeNewReferenceDatabaseObject(url, accessUrl, newReferenceDB);
				logger.info("New ReferenceDatabase has been created: {}", newReferenceDB);
			}
			//Othwerwise, some ReferenceDatabase object(s) already exist with some of the names given here. So, we need to update it with the new names. 
			else
			{
				GKInstance updateRefDBInstanceEdit = InstanceEditUtils.createInstanceEdit(adapter, personID, "Updating ReferenceDatabase object from "+this.getClass().getName());
				for (GKInstance preexistingRefDB : instancesInDB)
				{
					@SuppressWarnings("unchecked")
					Set<String> preexistingNames = new HashSet<>(preexistingRefDB.getAttributeValuesList(dbNameAttrib));
					// the names to add: everything that is in the input parameter "names" but not in "preexistingNames"
					List<String> namesToAdd = (Arrays.asList(names)).stream().filter(p -> !preexistingNames.contains(p)).collect(Collectors.toList());
					
					for (String name : namesToAdd)
					{
						logger.info("Adding the name {} to the existing ReferenceDatabase {}",name,preexistingRefDB + "( " + preexistingRefDB.getAttributeValuesList(ReactomeJavaConstants.name).toString() + " )");
						preexistingRefDB.addAttributeValue(dbNameAttrib, name);
						// Load the list of "modified" instance edits
						preexistingRefDB.getAttributeValuesList(ReactomeJavaConstants.modified);
						// Add the InstanceEdit for modification
						preexistingRefDB.addAttributeValue(ReactomeJavaConstants.modified, updateRefDBInstanceEdit);
						this.adapter.updateInstanceAttribute(preexistingRefDB, dbNameAttrib);
						this.adapter.updateInstanceAttribute(preexistingRefDB, ReactomeJavaConstants.modified);
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
	 * Populates a new ReferenceDatabase GKInstance, and then stores it in the database.
	 * This will also create a new InstanceEdit and put it into the "created" attribute of the 
	 * ReferenceDatabase object.
	 * @param url
	 * @param accessUrl
	 * @param newReferenceDB
	 * @return
	 * @throws InvalidAttributeException
	 * @throws InvalidAttributeValueException
	 * @throws Exception
	 */
	private long storeNewReferenceDatabaseObject(String url, String accessUrl, GKInstance newReferenceDB) throws InvalidAttributeException, InvalidAttributeValueException, Exception
	{
		long refDBID;
		GKInstance createdInstanceEdit = InstanceEditUtils.createInstanceEdit(this.adapter, this.personID, "Added to database by "+this.getClass().getName());
		newReferenceDB.setAttributeValue(ReactomeJavaConstants.created, createdInstanceEdit);
		newReferenceDB.setAttributeValue(ReactomeJavaConstants.url, url);
		newReferenceDB.setAttributeValue(ReactomeJavaConstants.accessUrl, accessUrl);
		newReferenceDB.setDbAdaptor(this.adapter);
		InstanceDisplayNameGenerator.setDisplayName(newReferenceDB);
		refDBID = this.adapter.storeInstance(newReferenceDB);
		return refDBID;
	}
}
