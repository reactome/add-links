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

public class ReferenceCreator
{
	private static final Logger logger = LogManager.getLogger();
	
	Collection<GKInstance> identifiers;
	
	private MySQLAdaptor dbAdapter;

	
	public ReferenceCreator(String identifier, MySQLAdaptor adapter)
	{
		this.dbAdapter = adapter;
		GKSchemaAttribute attribute = new GKSchemaAttribute();
		attribute.setName("Identifier");
		try
		{
			//Try to see if this Identifier is already in the database.  Ideally, this will not return anything.
			this.identifiers = (Collection<GKInstance>) this.dbAdapter.fetchInstanceByAttribute(attribute , "=", identifier);
		}
		catch (Exception e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	public void updateIdentifierAttribute(String attribute, String value, long personID, String creatorName)
	{
		// Before, purge all alt-db IDs so you have clean slate, make this purging optional? Or check for pre-existing alt-db IDs...
		// For given uniprot ID, find referenced alt-db ID and if it already exists and matches given ref val, do nothing.
		// other wise, create it. 
		
		if (this.identifiers != null && this.identifiers.size() > 0)
		{
			GKInstance instanceEdit = createInstanceEdit(personID, creatorName);
			if (instanceEdit != null)
			{
				for (GKInstance identifier : this.identifiers)
				{
					try
					{
						identifier.addAttributeValue(attribute, value);
						// TODO: Also need to create an InstanceEdit to track the modifications.					
						
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
			else
			{
				logger.error("InstanceEdit was null. Cannot create/update Identifiers without a valid InstanceEdit!");
				// throw a runtime exception - this type of error should terminate the program.
				throw new RuntimeException("Could not create an InstanceEdit. Aborting execution.");
			}
		}
	}



	private GKInstance createInstanceEdit(long personID, String creatorName)
	{
		GKInstance instanceEdit = null;
		try
		{
			instanceEdit = createDefaultIE(this.dbAdapter, personID, true);
			instanceEdit.getDBID();
			instanceEdit.setAttributeValue(ReactomeJavaConstants.note, "crossReference inserted by " + creatorName);
			this.dbAdapter.updateInstance(instanceEdit);
		}
		catch (InvalidAttributeException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch (InvalidAttributeValueException e)
		{
			e.printStackTrace();
		}
		catch (Exception e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return instanceEdit;
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
