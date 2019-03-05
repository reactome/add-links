package org.reactome.addlinks.db;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.model.GKInstance;
import org.gk.model.InstanceDisplayNameGenerator;
import org.gk.model.PersistenceAdaptor;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.InvalidAttributeException;
import org.gk.schema.InvalidAttributeValueException;
import org.gk.schema.SchemaClass;
import org.gk.util.GKApplicationUtilities;

class InstanceEditUtils
{
	private static Logger logger = LogManager.getLogger();
	/**
	 * Create an InstanceEdit.
	 * @param personID - ID of the associated Person entity.
	 * @param dbAdapter - A database adapter
	 * @param note - The text to put into the "note" field of the InstanceEdit
	 * @return
	 */
	static GKInstance createInstanceEdit(long personID, MySQLAdaptor dbAdapter, String note)
	{
		GKInstance instanceEdit = null;
		try
		{
			instanceEdit = InstanceEditUtils.createDefaultIE(dbAdapter, personID, true, note);
			instanceEdit.getDBID();
			dbAdapter.updateInstance(instanceEdit);
		}
		catch (Exception e)
		{
			InstanceEditUtils.logger.error("Exception caught while trying to create an InstanceEdit: {}", e.getMessage());
			e.printStackTrace();
		}

		return instanceEdit;
	}

	/**
	 * Create and save in the database a default InstanceEdit associated with the Person entity whose DB_ID is <i>defaultPersonId</i>.
	 * @param dba
	 * @param defaultPersonId
	 * @param needStore
	 * @param note
	 * @return an InstanceEdit object.
	 * @throws Exception
	 */
	private static GKInstance createDefaultIE(MySQLAdaptor dba, Long defaultPersonId, boolean needStore, String note) throws Exception
	{
		GKInstance defaultPerson = dba.fetchInstance(defaultPersonId);
		if (defaultPerson != null)
		{
			GKInstance newIE = InstanceEditUtils.createDefaultInstanceEdit(defaultPerson);
			newIE.addAttributeValue(ReactomeJavaConstants.dateTime, GKApplicationUtilities.getDateTime());
			newIE.addAttributeValue(ReactomeJavaConstants.note, note);
			InstanceDisplayNameGenerator.setDisplayName(newIE);

			if (needStore)
			{
				dba.storeInstance(newIE);
			}
			return newIE;
		}
		else
		{
			throw new Exception("Could not fetch Person entity with ID " + defaultPersonId + ". Please check that a Person entity exists in the database with this ID.");
		}
	}

	/**
	 * Create a default IE based on a default Person instance. The returned 
	 * GKInstance has not yet been stored in the database - it has only been
	 * created and had the "author" attribute populated.
	 * @param person
	 */
	public static GKInstance createDefaultInstanceEdit(GKInstance person)
	{
		GKInstance instanceEdit = new GKInstance();
		PersistenceAdaptor adaptor = person.getDbAdaptor();
		instanceEdit.setDbAdaptor(adaptor);
		SchemaClass cls = adaptor.getSchema().getClassByName(ReactomeJavaConstants.InstanceEdit);
		instanceEdit.setSchemaClass(cls);
		
		try
		{
			instanceEdit.addAttributeValue(ReactomeJavaConstants.author, person);
		}
		catch (InvalidAttributeException | InvalidAttributeValueException e)
		{
			e.printStackTrace();
			// throw this back up the stack - no way to recover from in here. 
			throw new Error(e);
		}
		
		return instanceEdit;
	}
}
