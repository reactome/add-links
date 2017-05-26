package org.reactome.addlinks.referencecreators;

import java.util.List;
import java.util.Map;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;

public class HGNCReferenceCreator extends SimpleReferenceCreator<List<String>>
{

	public HGNCReferenceCreator(MySQLAdaptor adapter, String classToCreate, String classReferring, String referringAttribute, String sourceDB, String targetDB)
	{
		super(adapter, classToCreate, classReferring, referringAttribute, sourceDB, targetDB);
	}
	
	public HGNCReferenceCreator(MySQLAdaptor adapter, String classToCreate, String classReferring, String referringAttribute, String sourceDB, String targetDB, String refCreatorName)
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
			
			if (mapping.containsKey(sourceIdentifier))
			{
				for (String hgncID : mapping.get(sourceIdentifier))
				{
					if (!this.checkXRefExists(sourceInst, hgncID))
					{
						sourceIdentifiersWithNewIdentifier ++;
						if (!this.testMode)
						{
							this.refCreator.createIdentifier(hgncID, String.valueOf(sourceInst.getDBID()), targetRefDB, personID, this.getClass().getName(), species);
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
		// If some of these numbers don't seem to quite add up, remember that sourceIdentifiersWithNewIdentifier
		// (and possibly sourceIdentifiersWithExistingIdentifier) will be incremented in a loop over HGNC IDs. 
		logger.info("{} reference creation summary: \n"
				+ "\t# {} IDs with a new {} identifier (a new {} reference was created): {};\n"
				+ "\t# {} identifiers which already had the same {} reference (nothing new was created): {};\n"
				+ "\t# {} identifiers not in the {} mapping file (no new {} reference was created for them): {}\n"
				+ "\t# {} keys in input mapping: {}\n"
				+ "\t# existing {} input source identifiers: {}",
				this.targetRefDB,
				this.sourceRefDB, this.targetRefDB, this.targetRefDB, sourceIdentifiersWithNewIdentifier,
				this.sourceRefDB, this.targetRefDB, sourceIdentifiersWithExistingIdentifier,
				this.sourceRefDB, this.targetRefDB, this.targetRefDB, sourceIdentifiersWithNoMapping,
				this.sourceRefDB, mapping.keySet().size(),
				this.sourceRefDB, sourceReferences.size());
	}
	
}
