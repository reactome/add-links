package org.reactome.addlinks.referencecreators;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.GKSchemaAttribute;
import org.gk.schema.InvalidAttributeException;
import org.gk.schema.SchemaClass;
import org.reactome.addlinks.db.ReferenceCreator;
import org.reactome.addlinks.db.ReferenceObjectCache;

/**
 * Creates references from one database to another.
 * @param T - The type that will be mapped to in the createIdentifiers method. <i>This</i> class will assume String,
 * but subclasses may have more complex mappings, such as List&lt;String&gt; - this will also require those subclasses
 * to override the createIdentifiers method.
 * @author sshorser
 *
 */
public class SimpleReferenceCreator<T> implements BatchReferenceCreator<T>
{
	protected boolean testMode = true;
	
	protected MySQLAdaptor adapter;
	protected ReferenceCreator refCreator;
	protected Logger logger;
	
	protected String classToCreateName ;
	protected String classReferringToRefName ;
	protected String referringAttributeName ;
	protected String targetRefDB ;
	protected String sourceIdentifyingAttribute = ReactomeJavaConstants.identifier;
	protected String sourceRefDB ;
	
	protected ReferenceObjectCache cache;
	
	public SimpleReferenceCreator(MySQLAdaptor adapter, String classToCreate, String classReferring, String referringAttribute, String sourceDB, String targetDB)
	{
		this(adapter, classToCreate, classReferring, referringAttribute, sourceDB, targetDB, null);
	}
	
	public SimpleReferenceCreator(MySQLAdaptor adapter, String classToCreate, String classReferring, String referringAttribute, String sourceDB, String targetDB, String refCreatorName)
	{
		// Reference creators run a TRACE level so that we can get ALL the details of what they are creating.
		this.logger = this.createLogger(refCreatorName, "RollingRandomAccessFile", this.getClass().getName(), true, Level.TRACE, this.logger, "Reference Creator");
		
		this.setClassReferringToRefName(classReferring);
		this.setClassToCreateName(classToCreate);
		this.setReferringAttributeName(referringAttribute);
		this.setSourceRefDB(sourceDB);
		this.setTargetRefDB(targetDB);
		
		// Y'know, this code was lifted straight from OrphanetReferenceCreator and is pretty much unchanged. Perhaps these two (and others to follow) could pull
		// this code up into a common parent class/interface...
		this.adapter = adapter;
		SchemaClass schemaClass = this.adapter.getSchema().getClassByName(this.classToCreateName);

		SchemaClass referringSchemaClass = adapter.getSchema().getClassByName(this.classReferringToRefName);
		
		GKSchemaAttribute referringSchemaAttribute = null;
		try
		{
			// This should never fail, but we still need to handle the exception.
			referringSchemaAttribute = (GKSchemaAttribute) referringSchemaClass.getAttribute(this.referringAttributeName);
		}
		catch (InvalidAttributeException e)
		{
			this.logger.error("Failed to get GKSchemaAttribute with name {} from class {}. This shouldn't have happened, but somehow it did."
						+ " Check that the classes/attributes you have chosen match the data model in the database.",
						referringSchemaAttribute, referringSchemaClass );
			e.printStackTrace();
			// Can't recover if there is no valid attribute object, throw it up the stack.
			throw new RuntimeException (e);
		}
		if (this.cache == null)
		{
			this.cache = new ReferenceObjectCache(this.adapter, true);
		}
		this.refCreator = new ReferenceCreator(schemaClass , referringSchemaClass, referringSchemaAttribute, this.adapter, this.logger);
	}
	
	/**
	 * Creates identifiers.
	 * @param personID - Newly created Identifiers will be associated with an InstanceEdit. That InstanceEdit will be associated with the Person entity whose ID == personID
	 * @param mapping - The mapping of Identifiers to create. This is a map of existing source identifiers to new identifiers. The Values in this case should be single-valued.
	 * The mapping is from identifiers in the source system to identifiers in the target system.
	 * @param sourceReferences - A list of GKInstance objects that were in the database. References will be created if they are in the keys of mapping and also in sourceReferences.
	 * This is to handle cases where a mapping comes from a third-party file that may contain source identifiers that are in *their* system but aren't actually in Reactome. 
	 * @throws Exception
	 */
	@Override
	public void createIdentifiers(long personID, Map<String, T> mapping, List<GKInstance> sourceReferences) throws Exception
	{
		AtomicInteger sourceIdentifiersWithNoMapping = new AtomicInteger(0);
		AtomicInteger sourceIdentifiersWithNewIdentifier = new AtomicInteger(0);
		AtomicInteger sourceIdentifiersWithExistingIdentifier = new AtomicInteger(0);
		this.logger.traceEntry();
		
		List<String> thingsToCreate = Collections.synchronizedList(new ArrayList<String>());
		
		sourceReferences.parallelStream().forEach(sourceReference ->
		{
			try
			{
				String sourceReferenceIdentifier = (String) sourceReference.getAttributeValue(this.sourceIdentifyingAttribute);
				
				if (mapping.containsKey(sourceReferenceIdentifier))
				{
					// It's possible that we could get a list of things from some third-party that contains mappings for multiple species.
					// So we need to get the species for EACH thing we iterate on. I worry this will slow it down, but  it needs to be done
					// if we want new identifiers to have the same species of the thing which they refer to.
					Long speciesID = null;
					@SuppressWarnings("unchecked")
					Collection<GKSchemaAttribute> attributes = (Collection<GKSchemaAttribute>) sourceReference.getSchemClass().getAttributes();
					if ( attributes.stream().filter(attr -> attr.getName().equals(ReactomeJavaConstants.species)).findFirst().isPresent())
					{
						GKInstance speciesInst = (GKInstance) sourceReference.getAttributeValue(ReactomeJavaConstants.species);
						if (speciesInst != null)
						{
							speciesID = new Long(speciesInst.getDBID());
						}
					}

					String targetRefDBIdentifier = (String)mapping.get(sourceReferenceIdentifier);
					this.logger.trace("{} ID: {}; {} ID: {}", this.sourceRefDB, sourceReferenceIdentifier, this.targetRefDB, targetRefDBIdentifier);
					// Look for cross-references.
					boolean xrefAlreadyExists = checkXRefExists(sourceReference, targetRefDBIdentifier);
					if (!xrefAlreadyExists)
					{
						this.logger.trace("\tCross-reference {} does not yet exist, need to create a new identifier!", targetRefDBIdentifier);
						sourceIdentifiersWithNewIdentifier.incrementAndGet();
						thingsToCreate.add(targetRefDBIdentifier+","+String.valueOf(sourceReference.getDBID())+","+speciesID);
					}
					else
					{
						sourceIdentifiersWithExistingIdentifier.incrementAndGet();
					}
				}
				else
				{
					sourceIdentifiersWithNoMapping.incrementAndGet();
				}
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		});

		for(String thing : thingsToCreate)
		{
			String[] parts = thing.split(",");
			String targetRefDBIdentifier = parts[0];
			String sourceDBID = parts[1];
			String speciesID = parts[2];
			
			if (!this.testMode)
			{
				this.refCreator.createIdentifier(targetRefDBIdentifier, sourceDBID, this.targetRefDB, personID, this.getClass().getName(), Long.valueOf(speciesID));
			}

		}
		
		this.logger.info("{} reference creation summary: \n"
				+ "\t# {} IDs with a new {} identifier (a new {} reference was created): {};\n"
				+ "\t# {} identifiers which already had the same {} reference (nothing new was created): {};\n"
				+ "\t# {} identifiers not in the {} mapping file (no new {} reference was created for them): {} ",
				this.targetRefDB,
				this.sourceRefDB, this.targetRefDB, this.targetRefDB, sourceIdentifiersWithNewIdentifier,
				this.sourceRefDB, this.targetRefDB, sourceIdentifiersWithExistingIdentifier,
				this.sourceRefDB, this.targetRefDB, this.targetRefDB, sourceIdentifiersWithNoMapping);

	}

	/**
	 * Checks to see if a cross-reference with a specific Identifier exists on a DatabaseObject.
	 * @param sourceReference - The source Object that has cross references.
	 * @param targetRefDBIdentifier - The identifier that you are looking for.
	 * @return - TRUE of sourceReference has a cross-reference to an identifier whose value is targetRefDBIdentifier. Otherwise, FALSE.
	 * @throws InvalidAttributeException
	 * @throws Exception
	 */
	protected boolean checkXRefExists(GKInstance sourceReference, String targetRefDBIdentifier) throws InvalidAttributeException, Exception
	{
		return checkXRefExists(sourceReference, targetRefDBIdentifier, this.targetRefDB);
	}
	
	/**
	 * Checks to see if a cross-reference with a specific Identifier exists on a DatabaseObject.
	 * @param sourceReference - The source Object that has cross references.
	 * @param targetRefDBIdentifier - The identifier that you are looking for.
	 * @param targetReferenceDB - The name or DB_ID of the target reference database you want to use.  Use this to override this.targetRefDB. This is useful when working
	 * with species-specific ReferenceDatabases.
	 * @return - TRUE of sourceReference has a cross-reference to an identifier whose value is targetRefDBIdentifier. Otherwise, FALSE.
	 * @throws InvalidAttributeException
	 * @throws Exception

	 */
	protected boolean checkXRefExists(GKInstance sourceReference, String targetRefDBIdentifier, String  targetReferenceDB) throws InvalidAttributeException, Exception
	{
		GKInstance refDB;
		// Look up by DB_ID
		if (targetReferenceDB.trim().matches("\\d+"))
		{
			refDB = this.adapter.fetchInstance(Long.parseLong(targetReferenceDB));
		}
		// Look up name in cache.
		else
		{
			refDB = this.adapter.fetchInstance(Long.parseLong(this.cache.getRefDbNamesToIds().get(targetReferenceDB).get(0)));
		}
		return checkXRefExists(sourceReference, targetRefDBIdentifier, refDB);
	}
	
	/**
	 * Checks to see if a cross-reference with a specific Identifier exists on a DatabaseObject.
	 * @param sourceReference - The source Object that has cross references.
	 * @param targetRefDBIdentifier - The identifier that you are looking for.
	 * @param targetReferenceDB - The GKInstance of a target ReferenceDatabase.
	 * @return - TRUE of sourceReference has a cross-reference to an identifier whose value is targetRefDBIdentifier. Otherwise, FALSE.
	 * @throws InvalidAttributeException
	 * @throws Exception
	 */
	protected boolean checkXRefExists(GKInstance sourceReference, String targetRefDBIdentifier, GKInstance targetReferenceDB) throws InvalidAttributeException, Exception
	{
		@SuppressWarnings("unchecked")
		Collection<GKInstance> xrefs = (Collection<GKInstance>) sourceReference.getAttributeValuesList(this.referringAttributeName);
		StringBuilder xrefsb = new StringBuilder();
		if (xrefs.size() > 0)
		{
			for (GKInstance xref : xrefs)
			{
				String identifier = xref.getAttributeValue(ReactomeJavaConstants.identifier).toString();
				xrefsb.append(identifier);
				GKInstance xrefRefDB = (GKInstance) xref.getAttributeValue(ReactomeJavaConstants.referenceDatabase);
				Object refdbDisplayName = xrefRefDB.getAttributeValue(ReactomeJavaConstants._displayName);
				xrefsb.append("@").append(refdbDisplayName).append(",\t");
				// We won't add a cross-reference if it already exists
				if (identifier.equals( targetRefDBIdentifier ))
				{
					// We found a cross-reference with the same identifiers, but it's possible this identifier is used by more than one Ref DB. Need to check...
					// Found a cross reference with the same identifer AND the same ref db displayname.
					//if (refdbDisplayName.equals(targetReferenceDB))
					if (xrefRefDB.getDBID().equals(targetReferenceDB.getDBID()))
					{
						this.logger.trace("\tcross-references *include* \"{}\": \t{}", targetRefDBIdentifier, xrefsb.toString().trim());
						return true;	
					}
					else
					{
						this.logger.trace("\tcross-references *include* \"{}\" but the cross-reference is associated with a different ref db: {} instead of {}", targetRefDBIdentifier, refdbDisplayName, targetReferenceDB);
					}
					
				}
			}
			this.logger.trace("\tcross-references do *not* include \"{}\": \t{}", targetRefDBIdentifier, xrefsb.toString().trim());
		}
		return false;
	}
	
	public boolean isTestMode()
	{
		return this.testMode;
	}

	public void setTestMode(boolean testMode)
	{
		this.testMode = testMode;
	}

	public String getClassToCreateName()
	{
		return this.classToCreateName;
	}

	public void setClassToCreateName(String classToCreateName)
	{
		this.classToCreateName = classToCreateName;
	}

	@Override
	public String getClassReferringToRefName()
	{
		return this.classReferringToRefName;
	}

	public void setClassReferringToRefName(String classReferringToRefName)
	{
		this.classReferringToRefName = classReferringToRefName;
	}

	public String getReferringAttributeName()
	{
		return this.referringAttributeName;
	}

	public void setReferringAttributeName(String referringAttributeName)
	{
		this.referringAttributeName = referringAttributeName;
	}

	public String getTargetRefDB()
	{
		return this.targetRefDB;
	}

	public void setTargetRefDB(String targetRefDB)
	{
		this.targetRefDB = targetRefDB;
	}

	@Override
	public String getSourceRefDB()
	{
		return this.sourceRefDB;
	}

	public void setSourceRefDB(String sourceRefDB)
	{
		this.sourceRefDB = sourceRefDB;
	}

	public String getSourceIdentifyingAttribute()
	{
		return this.sourceIdentifyingAttribute;
	}

	public void setSourceIdentifyingAttribute(String sourceIdentifyingAttribute)
	{
		this.sourceIdentifyingAttribute = sourceIdentifyingAttribute;
	}
	
}
