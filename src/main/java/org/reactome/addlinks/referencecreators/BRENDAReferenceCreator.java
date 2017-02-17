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

public class BRENDAReferenceCreator extends SimpleReferenceCreator<List<String>>
{
	private static final Logger logger = LogManager.getLogger();
	public BRENDAReferenceCreator(MySQLAdaptor adapter, String classToCreate, String classReferring, String referringAttribute, String sourceDB, String targetDB)
	{
		super(adapter, classToCreate, classReferring, referringAttribute, sourceDB, targetDB);
	}

	@Override
	public void createIdentifiers(long personID, Map<String, List<String>> mapping, List<GKInstance> sourceReferences) throws Exception
	{
		int sourceIdentifiersWithNoMapping = 0;
		int sourceIdentifiersWithNewIdentifier = 0;
		int sourceIdentifiersWithExistingIdentifier = 0;
		int totalNumberNewIdentifiers = 0;
		
		for (GKInstance instance : sourceReferences)
		{
			String sourceReferenceIdentifier = (String) instance.getAttributeValue(ReactomeJavaConstants.identifier);
			Long speciesID = null;
			for (GKSchemaAttribute attrib : (Collection<GKSchemaAttribute>)instance.getSchemaAttributes())
			{
				if (attrib.getName().equals(ReactomeJavaConstants.species) )
				{
					GKInstance speciesInst = (GKInstance) instance.getAttributeValue(ReactomeJavaConstants.species);
					if (speciesInst != null)
					{
						speciesID = new Long(speciesInst.getDBID());
					}
				}
			}
			
			if (mapping.containsKey(sourceReferenceIdentifier))
			{
				sourceIdentifiersWithNewIdentifier ++;
				for (String ecNumber : mapping.get(sourceReferenceIdentifier))
				{
					logger.trace("{} ID: {}; {} ID: {}", this.sourceRefDB, sourceReferenceIdentifier, this.targetRefDB, ecNumber);
					// Look for cross-references.
					boolean xrefAlreadyExists = checkXRefExists(instance, ecNumber);
					if (!xrefAlreadyExists)
					{
						logger.trace("\tNeed to create a new identifier!");
						totalNumberNewIdentifiers++;
						if (!this.testMode)
						{
							this.refCreator.createIdentifier(ecNumber, String.valueOf(instance.getDBID()), this.targetRefDB, personID, this.getClass().getName(), speciesID);
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
				+ "\t\tTotal # new identifiers: {};\n"
				+ "\t# {} identifiers which already had the same {} reference (nothing new was created): {};\n"
				+ "\t# {} identifiers not in the {} mapping file (no new {} reference was created for them): {} ",
				this.targetRefDB,
				this.sourceRefDB, this.targetRefDB, this.targetRefDB, sourceIdentifiersWithNewIdentifier,
				totalNumberNewIdentifiers,
				this.sourceRefDB, this.targetRefDB, sourceIdentifiersWithExistingIdentifier,
				this.sourceRefDB, this.targetRefDB, this.targetRefDB, sourceIdentifiersWithNoMapping);
	}
	
}
