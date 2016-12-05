package org.reactome.addlinks.referencecreators;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.GKSchemaAttribute;
import org.gk.schema.InvalidAttributeException;
import org.gk.schema.SchemaClass;
import org.reactome.addlinks.db.ReferenceCreator;

/**
 * Creates references from one database to another.
 * @param T - The type that will be mapped to in the createIdentifiers method. <i>This</i> class will assume String,
 * but subclasses may have more complex mappings, such as List&lt;String&gt; - this will also require those subclasses
 * to override the createIdentifiers method.
 * @author sshorser
 *
 */
public class SimpleReferenceCreator <T>
{
	protected boolean testMode = true;
	
	protected MySQLAdaptor adapter;
	protected ReferenceCreator refCreator;
	private static final Logger logger = LogManager.getLogger();
	
	protected String classToCreateName ;
	protected String classReferringToRefName ;
	protected String referringAttributeName ;
	protected String targetRefDB ;
	protected String sourceRefDB ;
	
	public SimpleReferenceCreator(MySQLAdaptor adapter, String classToCreate, String classReferring, String referringAttribute, String sourceDB, String targetDB)
	{
		this.setClassReferringToRefName(classReferring);
		this.setClassToCreateName(classToCreate);
		this.setReferringAttributeName(referringAttribute);
		this.setSourceRefDB(sourceDB);
		this.setTargetRefDB(targetDB);
		
		// Y'know, this code was lifted straight from OrphanetReferenceCreator and is pretty much unchanged. Perhaps these two (and others to follow) could pull
		// this code up into a common parent class/interface...
		this.adapter = adapter;
		SchemaClass schemaClass = this.adapter.getSchema().getClassByName(classToCreateName);

		SchemaClass referringSchemaClass = adapter.getSchema().getClassByName(classReferringToRefName);
		
		GKSchemaAttribute referringSchemaAttribute = null;
		try
		{
			// This should never fail, but we still need to handle the exception.
			referringSchemaAttribute = (GKSchemaAttribute) referringSchemaClass.getAttribute(referringAttributeName);
		}
		catch (InvalidAttributeException e)
		{
			logger.error("Failed to get GKSchemaAttribute with name {} from class {}. This shouldn't have happened, but somehow it did."
						+ " Check that the classes/attributes you have chosen match the data model in the database.",
						referringSchemaAttribute, referringSchemaClass );
			e.printStackTrace();
			// Can't recover if there is no valid attribute object, throw it up the stack. 
			throw new RuntimeException (e);
		}
		 
		refCreator = new ReferenceCreator(schemaClass , referringSchemaClass, referringSchemaAttribute, this.adapter);
	}
	
	/**
	 * Creates identifiers.
	 * @param personID - Newly created Identifiers will be associated with an InstanceEdit. That InstanceEdit will be associated with the Person entity whose ID == personID
	 * @param mapping - The mapping of Identifiers to create. This is a map of existing source identifiers to new identifiers. The Values in this case should be single-valued.
	 * @param sourceReferences - A list of GKInstance objects that were in the database. References will be created if they are in the keys of mapping and also in sourceReferences.
	 * This is to handle cases where a mapping comes from a third-party file that may contain source identifiers that are in *their* system but aren't actually in Reactome. 
	 * @throws Exception
	 */
	public void createIdentifiers(long personID, Map<String, T> mapping, List<GKInstance> sourceReferences) throws Exception
	{
		int uniprotsWithNoMapping = 0;
		int uniprotsWithNewIdentifier = 0;
		int uniprotsWithExistingIdentifier = 0;
		logger.traceEntry();
		for (GKInstance sourceReference : sourceReferences)
		{
			String sourceReferenceIdentifier = (String) sourceReference.getAttributeValue(ReactomeJavaConstants.identifier);
			
			// It's possible that we could get a list of things from some third-party that contains mappings for multiple species.
			// So we need to get the species for EACH thing we iterate on. I worry this will slow it down, but  it needs to be done
			// if we want new identifiers to have the same species of the thing which they refer to.
			Long speciesID = null;
			for (GKSchemaAttribute attrib : (Collection<GKSchemaAttribute>)sourceReference.getSchemaAttributes())
			{
				if (attrib.getName().equals(ReactomeJavaConstants.species) )
				{
					GKInstance speciesInst = (GKInstance) sourceReference.getAttributeValue(ReactomeJavaConstants.species);
					if (speciesInst != null)
					{
						speciesID = new Long(speciesInst.getDBID());
					}
				}
			}
			 
			if (mapping.containsKey(sourceReferenceIdentifier))
			{
				String targetRefDBIdentifier = (String)mapping.get(sourceReferenceIdentifier);
				logger.trace("{} ID: {}; {} ID: {}", this.sourceRefDB, sourceReferenceIdentifier, this.targetRefDB, targetRefDBIdentifier);
				// Look for cross-references.
				Collection<GKInstance> xrefs = sourceReference.getAttributeValuesList(referringAttributeName);
				boolean xrefAlreadyExists = false;
				for (GKInstance xref : xrefs)
				{
					logger.trace("\tcross-reference: {}",xref.getAttributeValue(ReactomeJavaConstants.identifier).toString());
					// We won't add a cross-reference if it already exists
					if (xref.getAttributeValue(ReactomeJavaConstants.identifier).toString().equals( mapping.get(sourceReferenceIdentifier) ))
					{
						xrefAlreadyExists = true;
						// Break out of the xrefs loop - we found an existing cross-reference that matches so there's no point 
						// in letting the loop run longer.
						// TODO: rewrite into a while-loop condition (I don't like breaks that much).
						break;
					}
				}
				if (!xrefAlreadyExists)
				{
					logger.trace("\tNeed to create a new identifier!");
					uniprotsWithNewIdentifier ++;
					if (!this.testMode)
					{
						refCreator.createIdentifier(targetRefDBIdentifier, String.valueOf(sourceReference.getDBID()), this.targetRefDB, personID, this.getClass().getName(), speciesID);
					}
				}
				else
				{
					uniprotsWithExistingIdentifier ++;
				}
			}
			else
			{
				uniprotsWithNoMapping ++;
				//logger.debug("UniProt ID {} is NOT in the database.", uniprotID);
			}
		}
		logger.info("{} reference creation summary: \n"
				+ "\t# {} IDs with a new {} identifier (a new {} reference was created): {};\n"
				+ "\t# {} identifiers which already had the same {} reference (nothing new was created): {};\n"
				+ "\t# {} identifiers not in the {} mapping file (no new {} reference was created for them): {} ",
				this.targetRefDB,
				this.sourceRefDB, this.targetRefDB, this.targetRefDB, uniprotsWithNewIdentifier,
				this.sourceRefDB, this.targetRefDB, uniprotsWithExistingIdentifier,
				this.sourceRefDB, this.targetRefDB, this.targetRefDB, uniprotsWithNoMapping);

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

	public String getSourceRefDB()
	{
		return this.sourceRefDB;
	}

	public void setSourceRefDB(String sourceRefDB)
	{
		this.sourceRefDB = sourceRefDB;
	}
}
