package org.reactome.addlinks.referencecreators;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
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

public class DOCKBlasterReferenceCreator
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

	public DOCKBlasterReferenceCreator(MySQLAdaptor adapter, String classToCreate, String classReferring, String referringAttribute, String sourceDB, String targetDB)
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

	public void createIdentifiers(long personID, Map<String, ArrayList<String>> mapping, List<GKInstance> sourceReferences) throws Exception
	{
		AtomicInteger uniprotsWithNoMapping = new AtomicInteger(0);
		AtomicInteger uniprotsWithNewIdentifier = new AtomicInteger(0);
		AtomicInteger uniprotsWithExistingIdentifier = new AtomicInteger(0);
		logger.traceEntry();
		
		Map<String, GKInstance> sourceMap = new HashMap<>(sourceReferences.size());
		
		//build a map of source objects
		for (GKInstance sourceInstance : sourceReferences)
		{
			//Pretty sure that identifier is always a single-valued attribute.
			sourceMap.put(sourceInstance.getAttributeValue(ReactomeJavaConstants.identifier).toString(), sourceInstance);
		}
//		Map<Long,MySQLAdaptor> adapterPool = new HashMap<Long,MySQLAdaptor>();
		// one PDB ID could map to several UniProt IDs
		//for (String pdbID : mapping.keySet())
		mapping.keySet().stream().sequential().forEach(pdbID -> {
			try
			{
//				MySQLAdaptor localAdapter ;
//				long threadID = Thread.currentThread().getId();
//				//logger.debug("Thread ID: {}",threadID);
//				if (adapterPool.containsKey(threadID))
//				{
//					localAdapter = adapterPool.get(threadID);
//				}
//				else
//				{
//					logger.debug("Creating new SQL Adaptor for thread {}", Thread.currentThread().getId());
//					localAdapter = new MySQLAdaptor(this.adapter.getDBHost(), this.adapter.getDBName(), this.adapter.getDBUser(),this.adapter.getDBPwd(), this.adapter.getDBPort());
//					adapterPool.put(threadID, localAdapter);
//				}
//				
				// For each UniProt ID that is in the source and also mapped from PDB (via DOCKBlaster)...
				List<String> uniProtIDs = mapping.get(pdbID).stream().parallel().filter(uniprotID -> sourceMap.containsKey(uniprotID)).collect(Collectors.toList());
				logger.trace("{}",uniProtIDs);
				for (String uniProtID : uniProtIDs)
				{
					// Check that this cross-reference doesn't already exist
					logger.trace("{} ID: {}; {} ID: {}", this.sourceRefDB, uniProtID, this.targetRefDB, pdbID);
					GKInstance sourceReference = sourceMap.get(uniProtID);
					// Look for cross-references.

					Collection<GKInstance> xrefs = sourceReference.getAttributeValuesList(referringAttributeName);
					boolean xrefAlreadyExists = false;
					for (GKInstance xref : xrefs)
					{
						logger.trace("\tcross-reference: {}",xref.getAttributeValue(ReactomeJavaConstants.identifier).toString());
						// We won't add a cross-reference if it already exists
						if (xref.getAttributeValue(ReactomeJavaConstants.identifier).toString().equals( uniProtID ))
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
						uniprotsWithNewIdentifier.incrementAndGet();
						if (!this.testMode)
						{
							refCreator.createIdentifier(pdbID, String.valueOf(sourceReference.getDBID()), this.targetRefDB, personID, this.getClass().getName());
						}
					}
					else
					{
						uniprotsWithExistingIdentifier.incrementAndGet();
					}			
				}
			}
			catch (InvalidAttributeException e)
			{
				e.printStackTrace();
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		});
		
//		for (GKInstance sourceReference : sourceReferences)
//		{
//			String sourceReferenceIdentifier = (String) sourceReference.getAttributeValue(ReactomeJavaConstants.identifier);
//			if (mapping.containsKey(sourceReferenceIdentifier))
//			{
//				String targetRefDBIdentifier = (String)mapping.get(sourceReferenceIdentifier);
//				logger.trace("{} ID: {}; {} ID: {}", this.sourceRefDB, sourceReferenceIdentifier, this.targetRefDB, targetRefDBIdentifier);
//				// Look for cross-references.
//				Collection<GKInstance> xrefs = sourceReference.getAttributeValuesList(referringAttributeName);
//				boolean xrefAlreadyExists = false;
//				for (GKInstance xref : xrefs)
//				{
//					logger.trace("\tcross-reference: {}",xref.getAttributeValue(ReactomeJavaConstants.identifier).toString());
//					// We won't add a cross-reference if it already exists
//					if (xref.getAttributeValue(ReactomeJavaConstants.identifier).toString().equals( mapping.get(sourceReferenceIdentifier) ))
//					{
//						xrefAlreadyExists = true;
//						// Break out of the xrefs loop - we found an existing cross-reference that matches so there's no point 
//						// in letting the loop run longer.
//						// TODO: rewrite into a while-loop condition (I don't like breaks that much).
//						break;
//					}
//				}
//				if (!xrefAlreadyExists)
//				{
//					logger.trace("\tNeed to create a new identifier!");
//					uniprotsWithNewIdentifier ++;
//					if (!this.testMode)
//					{
//						refCreator.createIdentifier(targetRefDBIdentifier, String.valueOf(sourceReference.getDBID()), this.targetRefDB, personID, this.getClass().getName());
//					}
//				}
//				else
//				{
//					uniprotsWithExistingIdentifier ++;
//				}
//			}
//			else
//			{
//				uniprotsWithNoMapping ++;
//				//logger.debug("UniProt ID {} is NOT in the database.", uniprotID);
//			}
//		}
		logger.info("{} reference creation summary: \n"
				+ "\t# {} IDs with a new {} identifier (a new {} reference was created): {};\n"
				+ "\t# {} identifiers which already had the same {} reference (nothing new was created): {};\n"
				+ "\t# {} identifiers not in the {} mapping file (no new {} reference was created for them): {} ",
				this.targetRefDB,
				this.sourceRefDB, this.targetRefDB, this.targetRefDB, uniprotsWithNewIdentifier,
				this.sourceRefDB, this.targetRefDB, uniprotsWithExistingIdentifier,
				this.sourceRefDB, this.targetRefDB, this.targetRefDB, uniprotsWithNoMapping);	}
	
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
