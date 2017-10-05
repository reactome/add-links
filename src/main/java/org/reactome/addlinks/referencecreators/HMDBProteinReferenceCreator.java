package org.reactome.addlinks.referencecreators;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;

public class HMDBProteinReferenceCreator extends SimpleReferenceCreator<String>
{
	public HMDBProteinReferenceCreator(MySQLAdaptor adapter, String classToCreate, String classReferring, String referringAttribute, String sourceDB, String targetDB)
	{
		super(adapter, classToCreate, classReferring, referringAttribute, sourceDB, targetDB);
	}
	
	public HMDBProteinReferenceCreator(MySQLAdaptor adapter, String classToCreate, String classReferring, String referringAttribute, String sourceDB, String targetDB, String refCreatorName)
	{
		super(adapter, classToCreate, classReferring, referringAttribute, sourceDB, targetDB, refCreatorName);
	}

	/**
	 * Creates references to HMDB
	 * @param mapping - this will be a mapping from an HMDB accession to a list of UniProt IDs.
	 */
	@Override
	public void createIdentifiers(long personID, Map<String, String> mapping, List<GKInstance> sourceReferences) throws Exception
	{
		int sourceIdentifiersWithNoMapping = 0;
		int sourceIdentifiersWithNewIdentifier = 0;
		int sourceIdentifiersWithExistingIdentifier = 0;
		logger.traceEntry();

		// Need to turn the list of source references into a Map for easier access.
		Map<String, GKInstance> sourceRefMap = new HashMap<String, GKInstance>(sourceReferences.size());
		for (GKInstance instance : sourceReferences)
		{
			String ident = ((String) instance.getAttributeValue(ReactomeJavaConstants.identifier));
			sourceRefMap.put(ident, instance);
		}
		
		// One HMDB could be mapped to multiple UniProts - I haven't actually seen this happen, myself,
		// but supposedly it's possible, based on how I interpret the old AddLinks code...
		for (String uniprotID : mapping.keySet())
		{
			String hmdbAccession = mapping.get(uniprotID);
			//for (String uniprotFromHMDB : mapping.get(hmdbAccession))
			{
				// We want to make sure that the UniProt ID from HMDB is also in our database.
				if (sourceRefMap.containsKey(uniprotID))
				{
					// Check that this HMDB reference hasn't already been created.
					if (!this.checkXRefExists(sourceRefMap.get(uniprotID), hmdbAccession))
					{
						// OK, now we have to create an HMDB reference.
						logger.trace("\tNeed to create a new identifier!");
						sourceIdentifiersWithNewIdentifier++;
						if (!this.testMode)
						{
							refCreator.createIdentifier(hmdbAccession, String.valueOf(sourceRefMap.get(uniprotID).getDBID()), this.targetRefDB, personID, this.getClass().getName());
						}
					}
					else
					{
						sourceIdentifiersWithExistingIdentifier++;
					}
				}
				else
				{
					sourceIdentifiersWithNoMapping++;
				}
				
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
