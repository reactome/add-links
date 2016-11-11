package org.reactome.addlinks.db;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.model.GKInstance;
import org.gk.model.InstanceDisplayNameGenerator;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.SchemaAttribute;
import org.gk.schema.SchemaClass;

public class ReferenceDatabaseCreator
{
	private MySQLAdaptor adapter;
	private static final Logger logger = LogManager.getLogger();
	
	public ReferenceDatabaseCreator(MySQLAdaptor adapter)
	{
		this.adapter = adapter;
	}
	
	/**
	 * Creates a ReferenceDatabase. If the ReferenceDatabase already exists, it will not be created.
	 * @param url - The URL of the ReferenceDatabase.
	 * @param accessUrl - The access URL of the RefereneDatabase.
	 * @param names - A list of names for this ReferenceDatabase. If none of these values are already in the database, then a new ReferenceDatabase
	 * will be created and these names will be associated with it. If any of these names already exist, and the other names will be associated with it.
	 * The URLs will not be updated in the case that a ReferenceDatabase name is pre-existing in the database.
	 * @throws Exception 
	 */
	public void createReferenceDatabase(String url, String accessUrl, String ... names) throws Exception
	{
		//First, let's check that the Reference Database doesn't already exist. all we have to go on is the name...
		SchemaClass refDBClass = adapter.getSchema().getClassByName(ReactomeJavaConstants.ReferenceDatabase);
		
		try
		{
			SchemaAttribute dbNameAttrib = refDBClass.getAttribute(ReactomeJavaConstants.name);
			
			List<String> namesNotYetInDB = new ArrayList<String>(names.length);
			List<GKInstance> instancesInDB = new ArrayList<GKInstance>();
			for (String name : names)
			{
				// This is tricky: If there is an existing ReferenceDatabase that has a name match, we should probably ADD the other names to that ReferenceDatabase,
				// Rather than create new ones. Unless the URLs don't match then maybe we should create new ReferenceDatabases? This needsa bit more thought.
				Collection<GKInstance> preexistingReferenceDBs =  adapter.fetchInstanceByAttribute(dbNameAttrib, "=", name);
				
				// Add to the list if the name is not yet in the database.
				if (preexistingReferenceDBs.size() == 0)
				{
					namesNotYetInDB.add(name);
				}
				else
				{
					logger.info("It looks like there were already {} Reference Databases with the name {}, so no new Reference Databases with this name will be created.", preexistingReferenceDBs.size(), name);
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
				this.adapter.storeInstance(newReferenceDB);
			}
			//Othwerwise, some ReferenceDatabase object(s) already exist with some of the names given here. So, we need to update it with the new names. 
			else
			{
				for (GKInstance preexistingRefDB : instancesInDB)
				{
					List<String> preexistingNames = (List<String>)preexistingRefDB.getAttributeValuesList(dbNameAttrib);
					// the names to add: everything that is in the input parameter "names" but not in "preexistingNames"
					List<String> namesToAdd = (Arrays.asList(names)).stream().filter(p -> !preexistingNames.contains(p)).collect(Collectors.toList());
					
					for (String n : namesToAdd)
					{
						preexistingRefDB.addAttributeValue(dbNameAttrib, n);
						this.adapter.updateInstanceAttribute(preexistingRefDB, dbNameAttrib);
					}
				}
			}

		}
		catch (Exception e)
		{
			logger.error("Error while trying to create a Reference Dataabse object: "+e.getMessage());
			throw e;
		}
	}
}
