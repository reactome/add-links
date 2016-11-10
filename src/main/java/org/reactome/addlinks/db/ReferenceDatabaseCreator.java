package org.reactome.addlinks.db;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.model.GKInstance;
import org.gk.model.InstanceDisplayNameGenerator;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.persistence.MySQLAdaptor.QueryRequest;
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
	
	public void createReferenceDatabase(String url, String accessUrl, String ... names)
	{
		//First, let's check that the Reference Database doesn't already exist. all we have to go on is the name...
		SchemaClass refDBClass = adapter.getSchema().getClassByName(ReactomeJavaConstants.ReferenceDatabase);
		
		try
		{
			SchemaAttribute dbNameAttrib = refDBClass.getAttribute(ReactomeJavaConstants.name);
			GKInstance newReferenceDB = null ;
			for (String name : names)
			{
				// This is tricky: If there is an existing ReferenceDatabase that has a name match, we should probably ADD the other names to that ReferenceDatabase,
				// Rather than create new ones. Unless the URLs don't match then maybe we should create new ReferenceDatabases? This needsa bit more thought.
				Collection<GKInstance> preexistingReferenceDBs =  adapter.fetchInstanceByAttribute(dbNameAttrib, "=", name);
				
				// Only proceed if there are no other databases with the same name.
				if (preexistingReferenceDBs.size() == 0)
				{
					// Only create the ReferenceDatabase object for the first name we see.
					if (newReferenceDB == null)
					{
						newReferenceDB = new GKInstance(refDBClass);
					}
					// A reference database could have multiple names/aliases, so keep adding them.
					newReferenceDB.addAttributeValue(dbNameAttrib, name);
					// There should only be one url and accessUrl so SET the attribute - it's OK to set the same attribute to the same value multiple times in this loop.
					newReferenceDB.setAttributeValue(ReactomeJavaConstants.url, url);
					newReferenceDB.setAttributeValue(ReactomeJavaConstants.accessUrl, url);
				}
				else
				{
					logger.info("It looks like there were already {} Reference Databases with the name {}, so no new Reference Databases with this name will be created.", preexistingReferenceDBs.size(), name);
				}
			}
			// If the ReferenceDatabase object actually got created, we need to store it.
			if (newReferenceDB != null)
			{
				InstanceDisplayNameGenerator.setDisplayName(newReferenceDB);
				adapter.storeInstance(newReferenceDB);
			}
		}
		catch (Exception e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
