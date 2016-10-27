package org.reactome.addlinks.db;

import java.util.Collection;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.database.DefaultInstanceEditHelper;
import org.gk.model.GKInstance;
import org.gk.model.InstanceDisplayNameGenerator;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.GKSchemaAttribute;
import org.gk.schema.InvalidAttributeException;
import org.gk.schema.InvalidAttributeValueException;
import org.gk.util.GKApplicationUtilities;

public class IdentifierUpdater
{
	private static final Logger logger = LogManager.getLogger();
	
	Collection<GKInstance> identifiers;
	
	private MySQLAdaptor dbAdapter;
	
	public IdentifierUpdater(long id, MySQLAdaptor adapter)
	{
		this.dbAdapter = adapter;
		
		try
		{
			// When user provides a DB ID, there should only be one returned object.
			this.identifiers.add( this.dbAdapter.fetchInstance(id) );
		}
		catch (Exception e)
		{
			logger.error("Could not get GKInstance for DatabaseIdentifier with DB ID: {}. Error was: {}", id, e.getMessage());
			e.printStackTrace();
		}
	}
	

	
	public IdentifierUpdater(String identifier, MySQLAdaptor adapter)
	{
		this.dbAdapter = adapter;
		GKSchemaAttribute atttribute = new GKSchemaAttribute();
		atttribute.setName("Identifier");
		try
		{
			this.identifiers = (Collection<GKInstance>) this.dbAdapter.fetchInstanceByAttribute(atttribute , "=", identifier);
		}
		catch (Exception e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	public void updateIdentifierAttribute(String attribute, String value, long personID, String creatorName)
	{
		for (GKInstance identifier : this.identifiers)
		{
			try
			{
				identifier.addAttributeValue(attribute, value);
				// TODO: Also need to create an InstanceEdit to track the modifications.
				
				GKInstance instanceEdit = createDefaultIE(this.dbAdapter, personID, true);
				
				instanceEdit.getDBID();
				instanceEdit.setAttributeValue(ReactomeJavaConstants.note, "crossReference inserted by " + creatorName);
				this.dbAdapter.updateInstance(instanceEdit);
				identifier.addAttributeValue(ReactomeJavaConstants.created, instanceEdit);
				
				this.dbAdapter.updateInstance(identifier);
				
			}
			catch (InvalidAttributeException e)
			{
				logger.error("The attribte {} is invalid.", attribute);
				e.printStackTrace();
			}
			catch (InvalidAttributeValueException e)
			{
				logger.error("The value \"{}\" given for the attribute \"{}\" is not a valid value.", attribute, value);
				e.printStackTrace();
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}
	}
	
	private static GKInstance createDefaultIE(MySQLAdaptor dba, Long defaultPersonId, boolean needStore) throws Exception
	{
		GKInstance defaultPerson = dba.fetchInstance(defaultPersonId);
		DefaultInstanceEditHelper ieHelper = new DefaultInstanceEditHelper();
		GKInstance newIE = ieHelper.createDefaultInstanceEdit(defaultPerson);
		newIE.addAttributeValue(ReactomeJavaConstants.dateTime, GKApplicationUtilities.getDateTime());
		InstanceDisplayNameGenerator.setDisplayName(newIE);
		if (needStore)
		{
			dba.storeInstance(newIE);
		}
		return newIE;
	}
}
