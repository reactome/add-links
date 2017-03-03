package org.reactome.addlinks.referencecreators;

import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;

public class IntEnzReferenceCreator extends SimpleReferenceCreator<List<String>>
{
	private static final Logger logger = LogManager.getLogger();
	
	public IntEnzReferenceCreator(MySQLAdaptor adapter, String classToCreate, String classReferring, String referringAttribute, String sourceDB, String targetDB)
	{
		super(adapter, classToCreate, classReferring, referringAttribute, sourceDB, targetDB);
	}
	
	public IntEnzReferenceCreator(MySQLAdaptor adapter, String classToCreate, String classReferring, String referringAttribute, String sourceDB, String targetDB, String refCreatorName)
	{
		super(adapter, classToCreate, classReferring, referringAttribute, sourceDB, targetDB, refCreatorName);
	}

	@Override
	public void createIdentifiers(long personID, Map<String, List<String>> mapping, List<GKInstance> sourceReferences) throws Exception
	{
		int sourceIdentifiersWithNoMapping = 0;
		int sourceIdentifiersWithNewIdentifier = 0;
		int sourceIdentifiersWithExistingIdentifier = 0;
		
		for (GKInstance sourceInst : sourceReferences)
		{

			String sourceIdentifier = (String) sourceInst.getAttributeValue(ReactomeJavaConstants.identifier);
			Long species = ((GKInstance) sourceInst.getAttributeValue(ReactomeJavaConstants.species)).getDBID();
			List<String> mappedECs = mapping.get(sourceIdentifier);
			
			// Check to see if there are any ECs that can be mapped.
			if (mappedECs != null && mappedECs.size() > 0)
			{
				// For each mapped EC number...
				for (String ecNumber : mappedECs)
				{
					if (!this.checkXRefExists(sourceInst, ecNumber))
					{
						sourceIdentifiersWithNewIdentifier ++;
						if (!this.testMode)
						{
							this.refCreator.createIdentifier(ecNumber, String.valueOf(sourceInst.getDBID()), targetRefDB, personID, this.getClass().getName(), species);
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
