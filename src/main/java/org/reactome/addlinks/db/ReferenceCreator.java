package org.reactome.addlinks.db;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.model.GKInstance;
import org.gk.model.InstanceDisplayNameGenerator;
import org.gk.model.PersistenceAdaptor;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.GKSchemaAttribute;
import org.gk.schema.InvalidAttributeException;
import org.gk.schema.InvalidAttributeValueException;
import org.gk.schema.SchemaAttribute;
import org.gk.schema.SchemaClass;
import org.gk.util.GKApplicationUtilities;

import com.mysql.jdbc.MysqlDataTruncation;

public class ReferenceCreator
{
	private Logger logger = LogManager.getLogger();

	//The class of the Reference we will create.
	private SchemaClass schemaClass;
	
	//The class of the thing we will refer to with this new Reference.
	private SchemaClass referringToSchemaClass;

	//The attribute on referringToSchemaClass that will refer to the Reference we will create.
	private GKSchemaAttribute referringAttribute;
	
	private MySQLAdaptor dbAdapter;
	
	// Each reference creator should have an InstanceEdit object - this way all objects created by this object will share the instance edit.
	private GKInstance instanceEdit = null;

	private ReferenceObjectCache objectCache;
	
	/**
	 * 
	 * @param schemaClass - References that are created by this object will be of type <i>schemaClass</i>.
	 * @param referringSchemaClass - References that are created by this object will be referred to by existing objects of type <i>referringSchemaClass</i>
	 * @param referringAttribute - References that are created by this object will be referred to by the <i>referringAttribute</i> of <i>referringSchemaClass</i>
	 * @param adapter - A database adapter.
	 * @param logger - The logger - this can be used when other components that create references what the output of *this* class to  be written to *their own* log files.
	 */
	public ReferenceCreator( SchemaClass schemaClass, SchemaClass referringSchemaClass, GKSchemaAttribute referringAttribute, MySQLAdaptor adapter, Logger logger)
	{
		this.dbAdapter = adapter;
		this.schemaClass = schemaClass;
		this.referringToSchemaClass = referringSchemaClass;
		this.referringAttribute = referringAttribute;
		this.logger = logger;
		// create a cache object, lazy-loaded.
		this.objectCache = new ReferenceObjectCache(adapter, true);
	}
	
	/**
	 * 
	 * @param schemaClass - References that are created by this object will be of type <i>schemaClass</i>.
	 * @param referringSchemaClass - References that are created by this object will be referred to by existing objects of type <i>referringSchemaClass</i>
	 * @param referringAttribute - References that are created by this object will be referred to by the <i>referringAttribute</i> of <i>referringSchemaClass</i>
	 * @param adapter - A database adapter.
	 */
	public ReferenceCreator( SchemaClass schemaClass, SchemaClass referringSchemaClass, GKSchemaAttribute referringAttribute, MySQLAdaptor adapter)
	{
		this(schemaClass, referringSchemaClass, referringAttribute, adapter, LogManager.getLogger());
	}

	/**
	 * Creates an identifier with a speceis (if species is an allowed attribute)
	 * @param identifierValue - The Identifying string.
	 * @param referenceToValue - The DB ID of the pre-existing thing this Identifier identifiers. 
	 * @param refDB - The reference database that this Identifier comes from, such as FlyBase, HMDB, etc...
	 * @param personID - The ID of the Person who is creating this Identifier.
	 * @param creatorName - A string which identifiers the code that created this Identifier. 
	 * @param speciesID - The ID of the species. If the Reference being created does not allow the "species" attribute, a warning message will be printed.
	 * @return The DB_ID of the newly created instance.
	 * @throws Exception if anything goes wrong. 
	 */
	public Long createIdentifier(String identifierValue, String referenceToValue, String refDB, long personID, String creatorName, Long speciesID) throws Exception
	{
		return createIdentifier(identifierValue, referenceToValue, refDB, personID, creatorName, speciesID, null);
	}
	
	/**
	 * Creates an identifier with extra attributes.
	 * @param identifierValue - The Identifying string.
	 * @param referenceToValue - The DB ID of the pre-existing thing this Identifier identifiers. 
	 * @param refDB - The reference database that this Identifier comes from, such as FlyBase, HMDB, etc...
	 * @param personID - The ID of the Person who is creating this Identifier.
	 * @param creatorName - A string which identifiers the code that created this Identifier. 
	 * @param speciesID - The ID of the species. If the Reference being created does not allow the "species" attribute, a warning message will be printed.
	 * @param otherAttribs - Extra attributes. Each attribute name maps to a list of possible values, so you can add multi-valued attributes with this method.
	 * @return The DB_ID of the newly created instance.
	 * @throws Exception if anything goes wrong. 
	 */
	public Long createIdentifier(String identifierValue, String referenceToValue, String refDB, long personID, String creatorName, Long speciesID, Map<String,List<String>> otherAttribs) throws Exception
	{
		Long newInstanceID = null;
		try
		{
			GKSchemaAttribute identifierAttribute = (GKSchemaAttribute) this.schemaClass.getAttribute(ReactomeJavaConstants.identifier);
			// Try to see if this Identifier is already in the database.
			// Ideally, this will not return anything.
			// Of course, we should only look at cross-references on the source object.
			// It's possible that the Identifier might already exist but for a different object.
			@SuppressWarnings("unchecked")
			Collection<GKInstance> identifiers = (Collection<GKInstance>) this.dbAdapter.fetchInstanceByAttribute(identifierAttribute, "=", identifierValue);
		
			// TODO: Maybe have a flag that can turn this functionality (check for pre-existing "new" identifiers) on or off.
			if (identifiers != null && identifiers.size() > 0)
			{
				boolean needToDeleteIdentifier = false;
				// If the identifiers already exist, they should be deleted and
				// then re-added.
				for (GKInstance identifier : identifiers)
				{
					// If a preexisting instance has the identifier ${identifierValue}, we need to see what it is referred to by. 
					// If they are the same as referenceToValue then the xref should be deleted and re-inserted. 
					@SuppressWarnings("unchecked")
					Map<SchemaAttribute, Collection<GKInstance>> referers = (Map<SchemaAttribute, Collection<GKInstance>>)identifier.getReferers();

					for (SchemaAttribute key : referers.keySet())
					{
						List<GKInstance> referersByAttrib = (List<GKInstance>) referers.get(key);
						for (GKInstance inst : referersByAttrib)
						{
							// We found an instance in the database that has the same Identifier as what this function was asked to create AND that instance
							// is *already* referred to by the same DB_ID this function was using for source object...
							// it means we need to delete that instance and re-create it.
							if (inst.getDBID() == Long.valueOf(referenceToValue))
							{
								logger.warn( "Identifier {} already existed (and has DB_ID {}), *and* was referred to by {}, but it shouldn't have existed (maybe you've already tried to creat this identifier?). We will delete it so it can be added fresh.", identifier.getAttributeValue(identifierAttribute), identifier.getAttributeValue(ReactomeJavaConstants.DB_ID), referenceToValue);
								try
								{
									needToDeleteIdentifier = true;
									this.dbAdapter.deleteInstance(identifier);
								}
								catch (Exception e)
								{
									e.printStackTrace();
									throw e;
								}
							}
						}
					}
				}
				if (!needToDeleteIdentifier)
				{
					logger.trace("Pre-existing identifier {} did not need to be deleted because it was not already referred to by {}", identifierValue, referenceToValue);
				}
			}
			
			// Create a new instance of the necessary type.
			GKInstance identifierInstance = new GKInstance(this.schemaClass,null,this.dbAdapter);
			
			//logger.debug("Available attributes: {}", this.schemaClass.getAttributes());

			//GKInstance instanceEdit = createInstanceEdit(personID, creatorName);
			if (this.instanceEdit == null)
			{
				this.instanceEdit = createInstanceEdit(personID, creatorName);
			}
			
			if (this.instanceEdit != null)
			{
				identifierInstance.addAttributeValue(ReactomeJavaConstants.created, instanceEdit);
				//GKSchemaAttribute identifierAttribute = (GKSchemaAttribute) this.schemaClass.getAttribute(ReactomeJavaConstants.identifier);
				
				GKSchemaAttribute refDBAttribute = (GKSchemaAttribute) this.schemaClass.getAttribute(ReactomeJavaConstants.referenceDatabase);
				identifierInstance.addAttributeValue(identifierAttribute, identifierValue);

				// Now we add the species, if it's allowed.
				@SuppressWarnings("unchecked")
				Collection<GKSchemaAttribute> attributes = (Collection<GKSchemaAttribute>)identifierInstance.getSchemClass().getAttributes();
				if ( attributes.stream().filter(attr -> attr.getName().equals(ReactomeJavaConstants.species)).findFirst().isPresent())
				{
					GKInstance species = this.dbAdapter.fetchInstance(ReactomeJavaConstants.Species,speciesID.longValue());
					identifierInstance.addAttributeValue(ReactomeJavaConstants.species, species);
				}
				
				// Get the refDB and add it as an attribute.
				GKInstance refDBInstance = getReferenceDatabase(refDB);
				if (refDBInstance == null)
				{
					throw new Error("Could not retrieve a DatabaseObject for ReferenceDatabase with name " + refDB);
				}
				identifierInstance.addAttributeValue(refDBAttribute, refDBInstance);
				
				// If the user wanted to specify any other attributes, add them here. 
				if (otherAttribs != null && otherAttribs.keySet().size() > 0)
				{
					for (String otherAttributeName : otherAttribs.keySet())
					{
						identifierInstance.setAttributeValue(otherAttributeName, otherAttribs.get(otherAttributeName));
					}
				}
				
				//Save changes to the new Identifier.
				InstanceDisplayNameGenerator.setDisplayName(identifierInstance);
				newInstanceID = this.dbAdapter.storeInstance(identifierInstance);
				//logger.debug("Just created new {} with DB_ID: {}", identifierInstance.getSchemClass().getName(), newInstanceID);
				//...and then immediately grab it.
				GKInstance createdIdentifier = this.dbAdapter.fetchInstance(newInstanceID);
				this.dbAdapter.loadInstanceAttributeValues(createdIdentifier);
				//logger.debug(createdIdentifier);
	
				//Set up references between original RefGeneProduc and new RefDNASeq.
				GKInstance instanceReferredToByIdentifier = this.dbAdapter.fetchInstance(this.referringToSchemaClass.getName(), new Long(referenceToValue));
				if (instanceReferredToByIdentifier == null)
				{
					throw new Exception("Could not find the instance of type " + this.referringToSchemaClass.getName() + " with ID "+referenceToValue );
				}
				GKSchemaAttribute xrefAttrib = this.referringAttribute;
				//logger.debug("referringToSchemaClass Available attributes: {}", this.referringToSchemaClass.getAttributes());
				try
				{
					instanceReferredToByIdentifier.addAttributeValue(xrefAttrib, createdIdentifier);
				}
				catch (InvalidAttributeValueException e)
				{
					logger.error("Invalid Attribute: {} added to object: {}", xrefAttrib, instanceReferredToByIdentifier);
					throw new Error(e);
				}
				
				// Only update the relevant attribute, better than updating the entire instance.
				this.dbAdapter.updateInstanceAttribute(instanceReferredToByIdentifier, xrefAttrib);
				logger.trace("Object with DB_ID: {} has new reference (via {} attribute): DB_ID: {}, Type: {}, Identifier Value: {}",
							instanceReferredToByIdentifier.getDBID(), xrefAttrib.getName(), newInstanceID, createdIdentifier.getSchemClass().getName(), identifierValue );
			}
			else
			{
				logger.error("InstanceEdit was null! Could not create Reference because there was no InstanceEdit to associate it with.");
			}
		}
		catch (MysqlDataTruncation e)
		{
			logger.error("Data truncation error: \"{}\" while trying to insert reference with identifier value: {} ", e.getMessage(), identifierValue);
			throw new Error(e);
		}
		catch (Exception e)
		{
			e.printStackTrace();
			throw e;
		}
		return newInstanceID;
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
	public Long createIdentifier(String identifierValue, String referenceToValue, String refDB, long personID, String creatorName) throws Exception
	{
		return this.createIdentifier(identifierValue, referenceToValue, refDB, personID, creatorName, null);
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
		List<String> dbIds = this.objectCache.getRefDbNamesToIds().get(dbName);
		
		GKInstance refDb = null;

		if (dbIds != null && dbIds.size() > 0)
		{
			logger.trace("{} DB_IDs came back for {}: {}", dbIds.size(), dbName, dbIds);
			refDb = this.dbAdapter.fetchInstance(Long.valueOf(dbIds.get(0)));
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
			instanceEdit = createDefaultIE(this.dbAdapter, personID, true, this.referringToSchemaClass.getClass() + " inserted by " + creatorName);
			instanceEdit.getDBID();
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
	private static GKInstance createDefaultIE(MySQLAdaptor dba, Long defaultPersonId, boolean needStore, String note) throws Exception
	{
		GKInstance defaultPerson = dba.fetchInstance(defaultPersonId);
		if (defaultPerson != null)
		{
			GKInstance newIE = ReferenceCreator.createDefaultInstanceEdit(defaultPerson);
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
	
	/**
	 * Create a default IE based on a default Person instance. The returned 
	 * GKInstance has not filled.
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
