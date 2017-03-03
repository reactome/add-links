package org.reactome.addlinks.referencecreators;

import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;

public class OneToOneReferenceCreator extends SimpleReferenceCreator<Object>
{
	private static final Logger logger = LogManager.getLogger();
	public OneToOneReferenceCreator(MySQLAdaptor adapter, String classToCreate, String classReferring, String referringAttribute, String sourceDB, String targetDB, String refCreatorName)
	{
		super(adapter, classToCreate, classReferring, referringAttribute, sourceDB, targetDB, refCreatorName);
	}

	public OneToOneReferenceCreator(MySQLAdaptor adapter, String classToCreate, String classReferring, String referringAttribute, String sourceDB, String targetDB)
	{
		super(adapter, classToCreate, classReferring, referringAttribute, sourceDB, targetDB);
	}
	
	@Override
	public void createIdentifiers(long personID, Map<String, Object> mapping, List<GKInstance> sourceReferences) throws Exception
	{
		logger.warn("This function does not make use of the \"mapping\" parameter. The override \"createIdentifiers(long personID, List<GKInstance> sourceReferences)\". will be called for you. Just so you know...");
		this.createIdentifiers(personID, sourceReferences);
	}
	
	
	/**
	 * createIdentifiers creates a 1:1 mapping from UniProt identifiers to OTHER references so there is no input mapping, just the list of UniProt IDs.
	 * @param personID
	 * @param sourceReferences
	 * @throws Exception
	 */
	public void createIdentifiers(long personID, List<GKInstance> sourceReferences) throws Exception
	{
		int createdRefCount = 0;
		int preexistingRefCount = 0;
		// these references are easy: Just create 1:1 mappings with the Uniprot ID.
		logger.traceEntry();
		for (GKInstance sourceReference : sourceReferences)
		{
			String sourceRefDBIdentifier = (String) sourceReference.getAttributeValue(ReactomeJavaConstants.identifier);
			{
				String targetRefDBIdentifier = sourceRefDBIdentifier;
				logger.trace("{} ID: {}; {} ID: {}", this.sourceRefDB, sourceRefDBIdentifier, this.targetRefDB, targetRefDBIdentifier);
				// Look for cross-references.
				boolean xrefAlreadyExists = checkXRefExists(sourceReference, targetRefDBIdentifier);
				if (!xrefAlreadyExists)
				{
					logger.trace("\tNeed to create a new identifier!");
					createdRefCount ++ ;
					if (!this.testMode)
					{
						refCreator.createIdentifier(targetRefDBIdentifier, String.valueOf(sourceReference.getDBID()), this.targetRefDB, personID, this.getClass().getName());
						
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
					"\t# {} references that already existed (and were not touched): {} \n", this.targetRefDB, this.targetRefDB, createdRefCount, this.targetRefDB, preexistingRefCount);
	}
}
