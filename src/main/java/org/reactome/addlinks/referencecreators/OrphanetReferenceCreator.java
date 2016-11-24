package org.reactome.addlinks.referencecreators;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.GKSchemaAttribute;
import org.gk.schema.InvalidAttributeException;
import org.gk.schema.SchemaClass;
import org.reactome.addlinks.db.ReferenceCreator;

public class OrphanetReferenceCreator
{

	private boolean testMode = true;
	
	private MySQLAdaptor adapter;
	private ReferenceCreator refCreator;
	private static final Logger logger = LogManager.getLogger();
	
	private final static String classToCreateName = ReactomeJavaConstants.DatabaseIdentifier;
	private final static String classReferringToRefName = ReactomeJavaConstants.ReferenceGeneProduct;
	private final static String referringAttributeName = ReactomeJavaConstants.referenceGene;
	private final static String REF_DB = "Orphanet";

	public OrphanetReferenceCreator(MySQLAdaptor adapter)
	{
		// Y'know, this code was lifted straight from PROReferenceCreator and is pretty much unchanged. Perhaps these two (and others to follow) could pull
		// this code up into a common parent class/interface...
		this.adapter = adapter;
		SchemaClass schemaClass = this.adapter.getSchema().getClassByName(classToCreateName);
		// It looks like there might be some ReferenceIsoForms that have PRO cross-references. But looking at the old Perl code, it's hard to tell 
		// where they might have come from. Need to look into that in more detail later.
		SchemaClass referringSchemaClass = adapter.getSchema().getClassByName(classReferringToRefName);
		
		GKSchemaAttribute referringAttribute = null;
		try
		{
			// This should never fail, but we still need to handle the exception.
			referringAttribute = (GKSchemaAttribute) referringSchemaClass.getAttribute(referringAttributeName);
		}
		catch (InvalidAttributeException e)
		{
			logger.error("Failed to get GKSchemaAttribute with name {} from class {}. This shouldn't have happened, but somehow it did."
						+ " Check that the classes/attributes you have chosen match the data model in the database.",
						referringAttribute, referringSchemaClass );
			e.printStackTrace();
			// Can't recover if there is no valid attribute object, throw it up the stack. 
			throw new RuntimeException (e);
		}
		 
		refCreator = new ReferenceCreator(schemaClass , referringSchemaClass, referringAttribute, this.adapter);
	}

	public void createIdentifiers(long personID, Map<String, ?> mapping, List<GKInstance> uniprotReferences) throws Exception
	{
		int uniprotsWithNoMapping = 0;
		int uniprotsWithNewIdentifier = 0;
		int uniprotsWithExistingIdentifier = 0;
		logger.traceEntry();
		for (GKInstance uniprotReference : uniprotReferences)
		{
			String uniprotID = (String) uniprotReference.getAttributeValue(ReactomeJavaConstants.identifier);
			//logger.debug("UniProt ID: {}",uniprotID);
			if (mapping.containsKey(uniprotID))
			{
				String orphanetIdentifier = (String)mapping.get(uniprotID);
				// Look for cross-references.
				Collection<GKInstance> xrefs = uniprotReference.getAttributeValuesList(referringAttributeName);
				boolean createNewXref = true;
				for (GKInstance xref : xrefs)
				{
					//logger.debug("\tcross-reference: {}",xref.getAttributeValue(ReactomeJavaConstants.identifier).toString());
					// We won't add a cross-reference if it already exists
					if (!xref.getAttributeValue(ReactomeJavaConstants.identifier).toString().equals( mapping.get(uniprotID) ))
					{
						createNewXref = false;
					}
				}
				if (createNewXref)
				{
					//logger.debug("Need to create a new identifier!");
					uniprotsWithNewIdentifier ++;
					if (!this.testMode)
					{
						refCreator.createIdentifier(orphanetIdentifier, String.valueOf(uniprotReference.getDBID()), REF_DB, personID, this.getClass().getName());
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
		logger.info("PRO reference creation summary: \n"
				+ "\t# UniProt IDs with a new Orphanet identifier which had a new mapping created: {};\n"
				+ "\t# UniProt identifiers which already had the same Orphanet mapping: {};\n"
				+ "\t# UniProt identifiers not in the Orphanet mapping file (no new Orphanet mapping was created for them): {} ",uniprotsWithNewIdentifier, uniprotsWithExistingIdentifier, uniprotsWithNoMapping);
	}
	
	public boolean getTestMode()
	{
		return this.testMode;
	}
	
	public void setTestMode(boolean testMode)
	{
		this.testMode = testMode;
	}
}
