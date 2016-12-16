package org.reactome.addlinks.referencecreators;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.reactome.addlinks.fileprocessors.HmdbMetabolitesFileProcessor.HMDBFileMappingKeys;

public class HMDBMoleculeReferenceCreator extends SimpleReferenceCreator<Map<HMDBFileMappingKeys, ? extends Collection<String>>>
{
	private static final Logger logger = LogManager.getLogger();
	public HMDBMoleculeReferenceCreator(MySQLAdaptor adapter, String classToCreate, String classReferring, String referringAttribute, String sourceDB, String targetDB)
	{
		super(adapter, classToCreate, classReferring, referringAttribute, sourceDB, targetDB);
	}
	

	@Override
	public void createIdentifiers(long personID, Map<String,Map<HMDBFileMappingKeys, ? extends Collection<String>>> mapping, List<GKInstance> sourceReferences) throws Exception
	{
		logger.traceEntry();
		
		int sourceUniProtIdentifiersWithNoMapping = 0;
		int sourceUniProtIdentifiersWithNewIdentifier = 0;
		int sourceUniProtIdentifiersWithExistingIdentifier = 0;
		
		int sourceChEBIIdentifiersWithNoMapping = 0;
		int sourceChEBIIdentifiersWithNewIdentifier = 0;
		int sourceChEBIIdentifiersWithExistingIdentifier = 0;
		
		// Need to turn the list of source references into a Map for easier access.
		Map<String, GKInstance> sourceRefMap = new HashMap<String, GKInstance>(sourceReferences.size());
		for (GKInstance instance : sourceReferences)
		{
			String ident = ((String) instance.getAttributeValue(ReactomeJavaConstants.identifier));
			sourceRefMap.put(ident, instance);
		}
		
		//loop on HMDB IDs
		for (String hmdbID : mapping.keySet())
		{
			for (HMDBFileMappingKeys otherDBType : mapping.get(hmdbID).keySet())
			{
				switch (otherDBType)
				{
					case CHEBI:
						String chebiID = (String) (mapping.get(hmdbID).get(otherDBType).toArray())[0];
						if (sourceRefMap.containsKey(chebiID))
						{
							// Check that this HMDB reference hasn't already been created.
							if (!this.checkXRefExists(sourceRefMap.get(chebiID), hmdbID))
							{
								// OK, now we have to create an HMDB reference.
								logger.trace("\tNeed to create a new identifier!");
								sourceChEBIIdentifiersWithNewIdentifier++;
								if (!this.testMode)
								{
									refCreator.createIdentifier(hmdbID, String.valueOf(sourceRefMap.get(chebiID).getDBID()), this.targetRefDB, personID, this.getClass().getName());
								}
							}
							else
							{
								sourceChEBIIdentifiersWithExistingIdentifier++;
							}
						}
						else
						{
							sourceChEBIIdentifiersWithNoMapping++;
						}

						break;
					case UNIPROT:
						for (String uniProtID : mapping.get(hmdbID).get(otherDBType))
						{
							if (sourceRefMap.containsKey(uniProtID))
							{
								// Check that this HMDB reference hasn't already been created.
								if (!this.checkXRefExists(sourceRefMap.get(uniProtID), hmdbID))
								{
									// OK, now we have to create an HMDB reference.
									logger.trace("\tNeed to create a new identifier!");
									sourceUniProtIdentifiersWithNewIdentifier++;
									if (!this.testMode)
									{
										refCreator.createIdentifier(hmdbID, String.valueOf(sourceRefMap.get(uniProtID).getDBID()), this.targetRefDB, personID, this.getClass().getName());
									}
								}
								else
								{
									sourceUniProtIdentifiersWithExistingIdentifier++;
								}
							}
							else
							{
								sourceUniProtIdentifiersWithNoMapping++;
							}
						}
						break;
				}
			}
		}
		
		
		// One HMDB could be mapped to multiple UniProts - I haven't actually seen this happen, myself,
		// but supposedly it's possible, based on how I interpret the old AddLinks code...
//		for (String chebiOrUniProtID : mapping.keySet())
//		{
//			for (String uniprotFromHMDB : mapping.get(chebiOrUniProtID))
//			{
//				// We want to make sure that the UniProt ID from HMDB is also in our database.
//				if (sourceRefMap.containsKey(uniprotFromHMDB))
//				{
//					// Check that this HMDB reference hasn't already been created.
//					if (!this.checkXRefExists(sourceRefMap.get(uniprotFromHMDB), chebiOrUniProtID))
//					{
//						// OK, now we have to create an HMDB reference.
//						logger.trace("\tNeed to create a new identifier!");
//						sourceIdentifiersWithNewIdentifier++;
//						if (!this.testMode)
//						{
//							refCreator.createIdentifier(chebiOrUniProtID, String.valueOf(sourceRefMap.get(uniprotFromHMDB).getDBID()), this.targetRefDB, personID, this.getClass().getName());
//						}
//					}
//					else
//					{
//						sourceIdentifiersWithExistingIdentifier++;
//					}
//				}
//				else
//				{
//					sourceIdentifiersWithNoMapping++;
//				}
//				
//			}
//		}
		logger.info("{} reference creation summary: \n"
				+ "\t# {} IDs with a new {} identifier (a new {} reference was created): {};\n"
				+ "\t# {} identifiers which already had the same {} reference (nothing new was created): {};\n"
				+ "\t# {} identifiers not in the {} mapping file (no new {} reference was created for them): {}\n"
				+ "\t# {} IDs with a new {} identifier (a new {} reference was created): {};\n"
				+ "\t# {} identifiers which already had the same {} reference (nothing new was created): {};\n"
				+ "\t# {} identifiers not in the {} mapping file (no new {} reference was created for them): {} ",
				this.targetRefDB,
				"ChEBI", this.targetRefDB, this.targetRefDB, sourceChEBIIdentifiersWithNewIdentifier,
				"ChEBI", this.targetRefDB, sourceChEBIIdentifiersWithExistingIdentifier,
				"ChEBI", this.targetRefDB, this.targetRefDB, sourceChEBIIdentifiersWithNoMapping,
				"UniProt", this.targetRefDB, this.targetRefDB, sourceUniProtIdentifiersWithNewIdentifier,
				"UniProt", this.targetRefDB, sourceUniProtIdentifiersWithExistingIdentifier,
				"UniProt", this.targetRefDB, this.targetRefDB, sourceUniProtIdentifiersWithNoMapping);
	}
}
