package org.reactome.addlinks.db;

import java.util.*;
import java.util.stream.Collectors;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.model.GKInstance;
import org.gk.model.InstanceDisplayNameGenerator;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.GKSchemaAttribute;
import org.gk.schema.InvalidAttributeValueException;
import org.gk.schema.SchemaAttribute;
import org.gk.schema.SchemaClass;
import org.reactome.addlinks.linkchecking.LinksToCheckCache;
import org.reactome.release.common.database.InstanceEditUtils;

import com.mysql.cj.jdbc.exceptions.MysqlDataTruncation;

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
	
	private GKInstance refDBInstance;
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
			// Get the refDB and add it as an attribute.
			// Store refDBInstance at the ReferenceCreator *instance* level to speed things up for the reference creation process.
			// If a user puts a numeric string into refDB, assume it is the DB_ID of a ReferenceDatabase object.
			// TODO: Refactor to take a REAL long instead of a long in a string
			if (this.refDBInstance == null)
			{
				// Look up by DB_ID
				if (refDB.trim().matches("\\d+"))
				{
					this.refDBInstance = this.dbAdapter.fetchInstance(Long.parseLong(refDB));
				}
				// Look up name in cache.
				else
				{
					this.refDBInstance = getReferenceDatabaseByName(refDB);
				}
			}
			else
			{
				if (refDB.trim().matches("\\d+"))
				{
					// When refDB is numeric, comapre DB_ID
					if (!this.refDBInstance.getDBID().equals(new Long(refDB)) )
					{
						this.refDBInstance = this.dbAdapter.fetchInstance(Long.parseLong(refDB));
					}
				}
				else // Comparing names isn't the best way to do it. Really should only allow Long (for DB_ID) in refDB parameter. Oh well, refactor later.
				{
					if (!this.refDBInstance.getDisplayName().equals(refDB))
					{
						this.refDBInstance = getReferenceDatabaseByName(refDB);
					}
				}
			}
			
			// If it's still null, there is a problem!
			if (this.refDBInstance == null)
			{
				throw new Error("Could not retrieve a DatabaseObject for ReferenceDatabase with name " + refDB);
			}

			// Special case if the incoming identifierValue is going to be conencted with a KEGG database:
			// If the identifierValue begins with a species code that is ALSO in the accessUrl, we need to remove 
			// the species code to avoid generating URLs with two species codes such as "hsa:hsa:1234", since KEGG
			// won't accept that. So far, I think the only situation that could cause this is the Uniprot-to-KEGG
			// reference creator. Since UniProt references are downloaded, processed, and created in a fairly generic fashion,
			// there is no easy way to integrate this sort of check in the UniProt-related classes.
			boolean needToRemoveSpeciesCode = this.refDBInstance.getAttributeValue(ReactomeJavaConstants.name).toString().startsWith("KEGG")
											&& identifierValue.length() > 3 && this.refDBInstance.getAttributeValue(ReactomeJavaConstants.accessUrl).toString().contains( (identifierValue.substring(0, 3)+":") );
			if ( needToRemoveSpeciesCode )
			{
				identifierValue = identifierValue.replaceFirst(identifierValue.substring(3)+":", "");
			}
			
			if (identifierValue == null || identifierValue.trim().equals(""))
			{
				this.logger.error("You tried to create a reference to ref DB {} via attribute {} for the object with DB_ID: {} with an empty/NULL Identifier", refDB, this.referringAttribute, referenceToValue);
				throw new NullPointerException("Enmpty-string/NULL identifier value is not allowed!");
			}
			
			GKSchemaAttribute identifierAttribute = (GKSchemaAttribute) this.schemaClass.getAttribute(ReactomeJavaConstants.identifier);
			// Try to see if this Identifier is already in the database.
			// Ideally, this will not return anything.
			// Of course, we should only look at cross-references on the source object.
			// It's possible that the Identifier might already exist but for a different object. Note that by querying the cache-by-identifier cache,
			// objects created *during* the execution of AddLinks will not be found since they won't have been added to the cache which gets populated 
			// when the application starts.
			Collection<GKInstance> identifiers = this.objectCache.getByIdentifier(identifierValue, this.schemaClass.getName());

			// TODO: Maybe have a flag that can turn this functionality (check for pre-existing "new" identifiers) on or off.
			if (identifiers != null && identifiers.size() > 0)
			{
				boolean needToDeleteIdentifier = false;
				// If the identifiers already exist, they should be deleted and
				// then re-added.
				for (GKInstance identifier : identifiers)
				{
					if (identifier != null)
					{
						// If a preexisting instance has the identifier ${identifierValue}, we need to see what it is referred to by. 
						// If they are the same as referenceToValue then the xref should be deleted and re-inserted. 
						@SuppressWarnings("unchecked")
						Map<SchemaAttribute, Collection<GKInstance>> referers = (Map<SchemaAttribute, Collection<GKInstance>>)identifier.getReferers();
	
						for (SchemaAttribute key : referers.keySet())
						{
							Set<GKInstance> referersByAttrib = (Set<GKInstance>) referers.get(key);
							for (GKInstance inst : referersByAttrib)
							{
								// We found an instance in the database that has the same Identifier as what this function was asked to create AND that instance
								// is *already* referred to by the same DB_ID this function was using for source object...
								// it means we need to delete that instance and re-create it.
								if (inst.getDBID() == Long.valueOf(referenceToValue))
								{
									this.logger.warn( "Identifier {} already existed (and has DB_ID {}), *and* was referred to by {}, but it shouldn't have existed (maybe you've already tried to creat this identifier?). We will delete it so it can be added fresh.", identifier.getAttributeValue(identifierAttribute), identifier.getAttributeValue(ReactomeJavaConstants.DB_ID), referenceToValue);
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
					else
					{
						this.logger.error("The GKInstance for the Identifier was NULL. That should not happen, you should probably investigate this deeper. Some debug info: identifierValue: {}; referenceToValue: {}; refDB: {}; creatorName: {}; speciesID: {}; class queried: {}", identifierValue, referenceToValue, refDB, creatorName, speciesID, this.schemaClass.getName());
						this.logger.error("The identifiers list contains {} elements, and {} were NULL.", identifiers.size(), identifiers.stream().filter(p -> p == null).count());
					}
				}
				if (!needToDeleteIdentifier)
				{
					this.logger.trace("Pre-existing identifier {} did not need to be deleted because it was not already referred to by {}", identifierValue, referenceToValue);
				}
			}
			
			// Create a new instance of the necessary type.
			GKInstance identifierInstance = new GKInstance(this.schemaClass,null,this.dbAdapter);
			identifierInstance.addAttributeValue(identifierAttribute, identifierValue);

			if (this.instanceEdit == null)
			{
				this.instanceEdit = InstanceEditUtils.createInstanceEdit(this.dbAdapter, personID, creatorName);
			}
			
			if (this.instanceEdit != null)
			{
				identifierInstance.addAttributeValue(ReactomeJavaConstants.created, this.instanceEdit);
				
				GKSchemaAttribute refDBAttribute = (GKSchemaAttribute) this.schemaClass.getAttribute(ReactomeJavaConstants.referenceDatabase);
				
				// Now we add the species, if it's allowed.
				@SuppressWarnings("unchecked")
				Collection<GKSchemaAttribute> attributes = (Collection<GKSchemaAttribute>)identifierInstance.getSchemClass().getAttributes();
				if ( speciesID != null && attributes.stream().filter(attr -> attr.getName().equals(ReactomeJavaConstants.species)).findFirst().isPresent())
				{
					GKInstance species = this.dbAdapter.fetchInstance(ReactomeJavaConstants.Species,speciesID.longValue());
					identifierInstance.addAttributeValue(ReactomeJavaConstants.species, species);
				}
				
				identifierInstance.addAttributeValue(refDBAttribute, this.refDBInstance);
								
				// If the user wanted to specify any other attributes, add them here. 
				if (otherAttribs != null && otherAttribs.keySet().size() > 0)
				{
					for (String otherAttributeName : otherAttribs.keySet())
					{
						//List<String> otherAttributeValues = otherAttribs.computeIfAbsent(otherAttributeName, k -> new ArrayList<>()).stream().filter(Objects::nonNull).collect(Collectors.toList());
						//otherAttributeValues.removeIf(Objects::isNull);
						identifierInstance.setAttributeValue(otherAttributeName, otherAttribs.get(otherAttributeName));
					}
				}

				GKInstance instanceReferredToByIdentifier = this.dbAdapter.fetchInstance(this.referringToSchemaClass.getName(), new Long(referenceToValue));
				if (instanceReferredToByIdentifier == null)
				{
					throw new Exception("Could not find the instance of type " + this.referringToSchemaClass.getName() + " with ID "+referenceToValue );
				}

				// Try to set the geneName based on geneName of referred-to object.
				if ( ((Collection<GKSchemaAttribute>) instanceReferredToByIdentifier.getSchemaAttributes())
													.parallelStream()
													.filter( attrib -> attrib.getName().equals(ReactomeJavaConstants.geneName) )
													.findFirst()
													.isPresent()
					&& instanceReferredToByIdentifier.getAttributeValue(ReactomeJavaConstants.geneName) != null)
				{
					// If we found a geneName in the referred-to object, we should try to apply it to the new Identifier if possible.
					if (this.schemaClass.isValidAttribute(ReactomeJavaConstants.geneName))
					{
						identifierInstance.addAttributeValue(ReactomeJavaConstants.geneName, instanceReferredToByIdentifier.getAttributeValue(ReactomeJavaConstants.geneName));
					}
				}

				
				InstanceDisplayNameGenerator.setDisplayName(identifierInstance);
				// If there is no geneName attribute or name attribute, then the _displayName will end with " Unknown" but
				// this looks bad in the UI, so remove it. The old Perl code didn't do this, it used an empty string in
				// that situation
				String newDisplayName = identifierInstance.getDisplayName().replaceAll(" Unknown$", "");
				identifierInstance.setDisplayName(newDisplayName);

				//Save changes to the new Identifier.
				newInstanceID = this.dbAdapter.storeInstance(identifierInstance);
				//Retreieve newly created Instance from the database.
				GKInstance createdIdentifier = this.dbAdapter.fetchInstance(newInstanceID);
				this.dbAdapter.loadInstanceAttributeValues(createdIdentifier);
	
				//Set up references between original RefGeneProduc and new RefDNASeq.
				GKSchemaAttribute xrefAttrib = this.referringAttribute;
				//logger.debug("referringToSchemaClass Available attributes: {}", this.referringToSchemaClass.getAttributes());
				try
				{
					instanceReferredToByIdentifier.addAttributeValue(xrefAttrib, createdIdentifier);
				}
				catch (InvalidAttributeValueException e)
				{
					this.logger.error("Invalid Attribute: {} added to object: {}", xrefAttrib, instanceReferredToByIdentifier);
					throw new Error(e);
				}

				try
				{
					// make sure the "modified" list is loaded.
					instanceReferredToByIdentifier.getAttributeValuesList(ReactomeJavaConstants.modified);
					// add this instanceEdit to the "modified" list, and update.
					instanceReferredToByIdentifier.addAttributeValue(ReactomeJavaConstants.modified, this.instanceEdit);
					this.dbAdapter.updateInstanceAttribute(instanceReferredToByIdentifier, ReactomeJavaConstants.modified);
				}
				catch (InvalidAttributeValueException e)
				{
					this.logger.error("Invalid Attribute: {} with value {} was added to object: {}", ReactomeJavaConstants.modified,  this.instanceEdit, instanceReferredToByIdentifier);
					throw new Error(e);
				}
				// Only update the relevant attribute, better than updating the entire instance.
				this.dbAdapter.updateInstanceAttribute(instanceReferredToByIdentifier, xrefAttrib);
				this.logger.trace("Object with DB_ID: {} has new reference (via {} attribute): DB_ID: {}, Type: {}, Identifier Value: {}",
							instanceReferredToByIdentifier.getDBID(), xrefAttrib.getName(), newInstanceID, createdIdentifier.getSchemClass().getName(), identifierValue );
				// Only now that the reference has been created, we will update the Links-to-check cache. This cache will be used later to ensure
				// that the external links we created are valid.
				LinksToCheckCache.addLinkToCache(this.refDBInstance, createdIdentifier);
				
			}
			else
			{
				this.logger.error("InstanceEdit was null! Could not create Reference because there was no InstanceEdit to associate it with.");
			}
		}
		catch (MysqlDataTruncation e)
		{
			// This could happen if a string from an external source is too long for the field you are trying to fit it into.
			this.logger.error("Data truncation error: \"{}\" while trying to insert reference with identifier value: {} ", e.getMessage(), identifierValue);
			throw new Error(e);
		}
		catch (Exception e)
		{
			this.logger.catching(Level.ERROR, e);
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
	protected GKInstance getReferenceDatabaseByName(String dbName) throws Exception
	{
		List<String> dbIds = this.objectCache.getRefDbNamesToIds().get(dbName);
		
		GKInstance refDb = null;

		if (dbIds != null && dbIds.size() > 0)
		{
			if (dbIds.size() > 1)
			{
				this.logger.trace("{} DB_IDs came back for \"{}\": {}", dbIds.size(), dbName, dbIds);
			}
			refDb = this.dbAdapter.fetchInstance(Long.valueOf(dbIds.get(0)));
		}
		else
		{
			throw new Error("Unknown database: "+dbName+", known databases: "+this.objectCache.getRefDbNamesToIds());
		}
		
		return refDb;
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
