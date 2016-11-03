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
import org.gk.schema.SchemaClass;
import org.gk.util.GKApplicationUtilities;

public class ReferenceCreator
{
	private static final Logger logger = LogManager.getLogger();

	//The class of the Reference we will create.
	private SchemaClass schemaClass;
	
	//The class of the thing we will refer to with this new Reference.
	private SchemaClass referringToSchemaClass;

	//The attribute on referringToSchemaClass that will refer to the Reference we will create.
	private GKSchemaAttribute referringAttribute;
	
	private MySQLAdaptor dbAdapter;

	/**
	 * 
	 * @param schemaClass - References that are created by this object will be of type <i>schemaClass</i>.
	 * @param referringSchemaClass - References that are created by this object will be referred by existing objects of type <i>referringSchemaClass</i>
	 * @param referringAttribute - References that are created by this object will be referred to by the <i>referringAttribute</i> of <i>referringSchemaClass</i>
	 * @param adapter - A database adapter.
	 */
	public ReferenceCreator( SchemaClass schemaClass, SchemaClass referringSchemaClass, GKSchemaAttribute referringAttribute, MySQLAdaptor adapter)
	{
		this.dbAdapter = adapter;
		this.schemaClass = schemaClass;
		this.referringToSchemaClass = referringSchemaClass;
		this.referringAttribute = referringAttribute;
	}

	/**
	 * Creates an external Identifier in the database. 
	 * @param identifierValue - The Identifying string.
	 * @param referenceToValue - The DB ID of the pre-existing thing this Identifier identifiers. 
	 * @param refDB - The reference database that this Identifier comes from, such as FlyBase, HMDB, etc...
	 * @param personID - The ID of the Person who is creating this Identifier.
	 * @param creatorName - A string which identifiers the code that created this Identifier. 
	 * @throws Exception if anything goes wrong. 
	 */
	public void createIdentifier(String identifierValue, String referenceToValue, String refDB, long personID, String creatorName) throws Exception
	{
		try
		{
			GKSchemaAttribute identifierAttribute = (GKSchemaAttribute) this.schemaClass.getAttribute(ReactomeJavaConstants.identifier);
			// Try to see if this Identifier is already in the database.
			// Ideally, this will not return anything.
			Collection<GKInstance> identifiers = (Collection<GKInstance>) this.dbAdapter.fetchInstanceByAttribute(identifierAttribute, "=", identifierValue);
		
			if (identifiers != null && identifiers.size() > 0)
			{
				// If the identifiers already exist, they should be deleted and
				// then re-added.
				for (GKInstance identifier : identifiers)
				{
					try
					{
						logger.warn( "Identifier {} already existed, but it shouldn't have. We will delete it so it can be added fresh.", identifier.getDisplayName());
						this.dbAdapter.deleteInstance(identifier);
					}
					catch (Exception e)
					{
						e.printStackTrace();
						throw e;
					}
				}
			}
			
			// Create a new instance of the necessary type.
			GKInstance identifierInstance = new GKInstance(this.schemaClass,null,this.dbAdapter);
			
			//logger.debug("Available attributes: {}", this.schemaClass.getAttributes());

			GKInstance instanceEdit = createInstanceEdit(personID, creatorName);
			if (instanceEdit != null)
			{
				//identifierInstance.addAttributeValue(attribute, identifierValue);
				// Need to create an InstanceEdit to track
				// the modifications.
				identifierInstance.addAttributeValue(ReactomeJavaConstants.created, instanceEdit);
				//this.dbAdapter.updateInstance(identifierInstance);
				
				//GKSchemaAttribute identifierAttribute = (GKSchemaAttribute) this.schemaClass.getAttribute(ReactomeJavaConstants.identifier);
				
				GKSchemaAttribute refDBAttribute = (GKSchemaAttribute) this.schemaClass.getAttribute(ReactomeJavaConstants.referenceDatabase);
				identifierInstance.addAttributeValue(identifierAttribute, identifierValue);
				GKInstance refDBInstance = getReferenceDatabase(refDB);
				identifierInstance.addAttributeValue(refDBAttribute, refDBInstance);
				//Save changes to the new Identifier.
				long newInstanceID = this.dbAdapter.storeInstance(identifierInstance);
				//...and then immediately grab it.
				GKInstance createdIdentifier = this.dbAdapter.fetchInstance(newInstanceID);
				this.dbAdapter.loadInstanceAttributeValues(createdIdentifier);
				//logger.debug(createdIdentifier);
	
				//Set up references between original RefGeneProduc and new RefDNASeq.
				GKInstance instanceReferredToByIdentifier = this.dbAdapter.fetchInstance(this.referringToSchemaClass.getName(), new Long(referenceToValue));
				//I think ReferenceGeneProduct should probably be parameterized here. Also, referenceGene.
				//SchemaClass refGeneProdClass = this.referringToSchemaClass; //this.dbAdapter.getSchema().getClassByName(ReactomeJavaConstants.ReferenceGeneProduct);
				GKSchemaAttribute xrefAttrib = this.referringAttribute; //(GKSchemaAttribute) refGeneProdClass.getAttribute(ReactomeJavaConstants.referenceGene);
				instanceReferredToByIdentifier.addAttributeValue(xrefAttrib, createdIdentifier);
				this.dbAdapter.updateInstance(instanceReferredToByIdentifier);
			}
			else
			{
				logger.error("InstanceEdit was null! Could not create Reference because there was no InstanceEdit to associate it with.");
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
			throw e;
		}
	
	}

	/**
	 * A helper method to get a ReferenceDatabase instance for the specified
	 * instance.
	 * 
	 * @param dbName - The name of the database to look up.
	 * @return - A GKInstance representing the Reference Database.
	 * @throws Exception - An exception could be thrown in the case that there is no ReferenceDatabase whose _displayName is <i>dbName</i>. 
	 */
	protected GKInstance getReferenceDatabase(String dbName) throws Exception
	{
		// Using _displayName can fetch local shell instances.
		Collection<?> list = this.dbAdapter.fetchInstanceByAttribute(ReactomeJavaConstants.ReferenceDatabase, ReactomeJavaConstants._displayName, "=", dbName);
		GKInstance refDb = null;
		
		if (list.size() > 0)
		{
			refDb = (GKInstance) list.iterator().next();
		}
		else
		{
			throw new Exception("Unknown database: "+dbName);
		}
		
		return refDb;
	}

	/**
	 * Create an InstanceEdit.
	 * @param personID - ID of the associated Person entity.
	 * @param creatorName - The name of the thing that is creating this InstanceEdit. Typically, you would want to use the package and classname that 
	 * uses <i>this</i> object, so it can be traced to the appropriate part of the program. 
	 * @return
	 */
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

	/**
	 * Create and save in the database a default InstanceEdit associated with the Person entity whose DB_ID is <i>defaultPersonId</i>.
	 * @param dba
	 * @param defaultPersonId
	 * @param needStore
	 * @return an InstanceEdit object.
	 * @throws Exception
	 */
	private static GKInstance createDefaultIE(MySQLAdaptor dba, Long defaultPersonId, boolean needStore) throws Exception
	{
		GKInstance defaultPerson = dba.fetchInstance(defaultPersonId);
		if (defaultPerson != null)
		{
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
		else
		{
			throw new Exception("Could not fetch Person entity with ID " + defaultPersonId + ". Please check that a Person entity exists in the database with this ID.");
		}
	}

	public void setSchemaClass(SchemaClass schemaClass)
	{
		this.schemaClass = schemaClass;
	}

	public void setReferringToSchemaClass(SchemaClass referringToSchemaClass)
	{
		this.referringToSchemaClass = referringToSchemaClass;
	}

	public void setReferringAttribute(GKSchemaAttribute referringAttribute)
	{
		this.referringAttribute = referringAttribute;
	}
}
