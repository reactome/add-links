package org.reactome.addlinks.db;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.SchemaAttribute;
import org.gk.schema.SchemaClass;

public class ReferenceDatabaseCreator
{
	private MySQLAdaptor adapter;
	
	public ReferenceDatabaseCreator(MySQLAdaptor adapter)
	{
		this.adapter = adapter;
	}
	
	private static final Logger logger = LogManager.getLogger();

	public void createReferenceDatabase(String name, String url, String accessUrl)
	{
		createReferenceDatabase(Arrays.asList(name), url, accessUrl);
	}
	
	public void createReferenceDatabase(List<String> names, String url, String accessUrl)
	{
		//First, let's check that the Reference Database doesn't already exist. all we have to go on is the name...
		SchemaClass refDBClass = adapter.getSchema().getClassByName(ReactomeJavaConstants.ReferenceDatabase);
		
		try
		{
			SchemaAttribute dbNameAttrib = refDBClass.getAttribute(ReactomeJavaConstants.name);
			GKInstance newReferenceDB = new GKInstance(refDBClass);
			for (String name : names)
			{
				Collection<GKInstance> preexistingReferenceDBs =  adapter.fetchInstanceByAttribute(dbNameAttrib, "=", name);
				
				// Only proceed if there are no other databases with the same name.
				if (preexistingReferenceDBs.size() == 0)
				{
					
					newReferenceDB.addAttributeValue(dbNameAttrib, name);
					newReferenceDB.setAttributeValue(ReactomeJavaConstants.url, url);
					newReferenceDB.setAttributeValue(ReactomeJavaConstants.accessUrl, url);
					adapter.updateInstance(newReferenceDB);
				}
				else
				{
					logger.info("It looks like there were already {} Reference Databases with the name {}, so no new Reference Databases will be created.", preexistingReferenceDBs.size(), name);
				}
			}
		}
		catch (Exception e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
