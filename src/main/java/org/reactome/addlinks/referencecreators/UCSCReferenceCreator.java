package org.reactome.addlinks.referencecreators;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;

public class UCSCReferenceCreator extends SimpleReferenceCreator
{
	private static final Logger logger = LogManager.getLogger();
	public UCSCReferenceCreator(MySQLAdaptor adapter, String classToCreate, String classReferring, String referringAttribute, String sourceDB, String targetDB)
	{
		super(adapter, classToCreate, classReferring, referringAttribute, sourceDB, targetDB);
	}

	@Override
	public void createIdentifiers(long personID, Map<String, ?> mapping, List<GKInstance> sourceReferences) throws Exception
	{
		logger.warn("This override does not make use of the \"mapping\" parameter. Just so you know...");
		this.createIdentifiers(personID, sourceReferences);
	}
	
	
	/**
	 * UCSC createIdentifiers creates a 1:1 mapping from UniProt identifiers to UCSC references so there is no input mapping, just the list of UniProt IDs.
	 * @param personID
	 * @param sourceReferences
	 * @throws Exception
	 */
	public void createIdentifiers(long personID, List<GKInstance> sourceReferences) throws Exception
	{
		int createdRefCount = 0;
		int preexistingRefCount = 0;
		// UCSC references are easy: Just create 1:1 mappings with the Uniprot ID. 		
		logger.traceEntry();
		for (GKInstance sourceReference : sourceReferences)
		{
			String sourceRefDBIdentifier = (String) sourceReference.getAttributeValue(ReactomeJavaConstants.identifier);
			{
				String targetRefDBIdentifier = sourceRefDBIdentifier;
				logger.trace("{} ID: {}; {} ID: {}", this.sourceRefDB, sourceRefDBIdentifier, this.targetRefDB, targetRefDBIdentifier);
				// Look for cross-references.
				Collection<GKInstance> xrefs = sourceReference.getAttributeValuesList(referringAttributeName);
				boolean xrefAlreadyExists = false;
				for (GKInstance xref : xrefs)
				{
					logger.trace("\tcross-reference: {}",xref.getAttributeValue(ReactomeJavaConstants.identifier).toString());
					// We won't add a cross-reference if it already exists
					if (xref.getAttributeValue(ReactomeJavaConstants.identifier).toString().equals( targetRefDBIdentifier ))
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
					if (!this.testMode)
					{
						refCreator.createIdentifier(targetRefDBIdentifier, String.valueOf(sourceReference.getDBID()), this.targetRefDB, personID, this.getClass().getName());
						createdRefCount ++ ;
					}
				}
				else
				{
					preexistingRefCount ++ ;
				}
			}
		}
		logger.info("{} reference creation summary: \n" +
					"\t# {} references created: {}\n" + 
					"\t# references that already existed (and were not touched): {}\n", this.targetRefDB, this.targetRefDB, createdRefCount, this.targetRefDB, preexistingRefCount);
	}
}
