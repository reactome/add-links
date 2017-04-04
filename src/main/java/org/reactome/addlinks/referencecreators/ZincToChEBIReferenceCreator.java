package org.reactome.addlinks.referencecreators;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.GKSchemaAttribute;

public class ZincToChEBIReferenceCreator extends SimpleReferenceCreator<List<String>>
{
	public ZincToChEBIReferenceCreator(MySQLAdaptor adapter, String classToCreate, String classReferring,
			String referringAttribute, String sourceDB, String targetDB)
	{
		super(adapter, classToCreate, classReferring, referringAttribute, sourceDB, targetDB);
	}
	
	public ZincToChEBIReferenceCreator(MySQLAdaptor adapter, String classToCreate, String classReferring,
			String referringAttribute, String sourceDB, String targetDB, String refCreatorName)
	{
		super(adapter, classToCreate, classReferring, referringAttribute, sourceDB, targetDB, refCreatorName);
	}


	@Override
	public void createIdentifiers(long personID, Map<String, List<String>> mapping, List<GKInstance> sourceReferences) throws Exception
	{
		int sourceIdentifiersWithNoMapping = 0;
		int sourceIdentifiersWithNewIdentifier = 0;
		int sourceIdentifiersWithExistingIdentifier = 0;
		logger.traceEntry();
		for (GKInstance sourceReference : sourceReferences)
		{
			String sourceReferenceIdentifier = (String) sourceReference.getAttributeValue(ReactomeJavaConstants.identifier);
			
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
			 
			if (mapping.containsKey(sourceReferenceIdentifier))
			{
				List<String> targetRefDBIdentifiers = (List<String>) mapping.get(sourceReferenceIdentifier);
				for (String targetIdentifier : targetRefDBIdentifiers)
				{
					logger.trace("{} ID: {}; {} ID: {}", this.sourceRefDB, sourceReferenceIdentifier, this.targetRefDB, targetIdentifier);
					// Look for cross-references.
					boolean xrefAlreadyExists = checkXRefExists(sourceReference, targetIdentifier);
					if (!xrefAlreadyExists)
					{
						logger.trace("\tCross-reference {} does not yet exist, need to create a new identifier!", targetIdentifier);
						sourceIdentifiersWithNewIdentifier ++;
						if (!this.testMode)
						{
							this.refCreator.createIdentifier(targetIdentifier, String.valueOf(sourceReference.getDBID()), this.targetRefDB, personID, this.getClass().getName(), speciesID);
						}
					}
					else
					{
						sourceIdentifiersWithExistingIdentifier ++;
					}
				}
			}
			else
			{
				sourceIdentifiersWithNoMapping ++;
				//logger.debug("UniProt ID {} is NOT in the database.", uniprotID);
			}
		}
		logger.info("{} reference creation summary: \n"
				+ "\t# {} IDs with a new {} identifier (a new {} reference was created): {};\n"
				+ "\t# {} identifiers which already had the same {} reference (nothing new was created): {};\n"
				+ "\t# {} identifiers not in the {} mapping file (no new {} reference was created for them): {} ",
				this.targetRefDB,
				this.sourceRefDB, this.targetRefDB, this.targetRefDB, sourceIdentifiersWithNewIdentifier,
				this.sourceRefDB, this.targetRefDB, sourceIdentifiersWithExistingIdentifier,
				this.sourceRefDB, this.targetRefDB, this.targetRefDB, sourceIdentifiersWithNoMapping);
	}
}
