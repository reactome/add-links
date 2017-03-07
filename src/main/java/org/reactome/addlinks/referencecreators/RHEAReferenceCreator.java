package org.reactome.addlinks.referencecreators;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.InvalidAttributeException;

public class RHEAReferenceCreator extends SimpleReferenceCreator<List <String>>
{
	
	public RHEAReferenceCreator(MySQLAdaptor adapter, String classToCreate, String classReferring, String referringAttribute, String sourceDB, String targetDB, String refCreatorName)
	{
		super(adapter, classToCreate, classReferring, referringAttribute, sourceDB, targetDB, refCreatorName);
	}

	public RHEAReferenceCreator(MySQLAdaptor adapter, String classToCreate, String classReferring, String referringAttribute, String sourceDB, String targetDB)
	{
		super(adapter, classToCreate, classReferring, referringAttribute, sourceDB, targetDB);
	}
	
	/**
	 * "mapping" is a map of Rhea IDs to lists of Reactome Stable IDs (for reactions).
	 */
	@Override
	public void createIdentifiers(long personID, Map<String, List<String>> mapping, List<GKInstance> sourceReferences) throws Exception
	{
		int sourceIdentifiersWithNoMapping = 0;
		int sourceIdentifiersWithNewIdentifier = 0;
		int sourceIdentifiersWithExistingIdentifier = 0;
		
		for (String rheaID : mapping.keySet())
		{
			for (String reactomeID : mapping.get(rheaID))
			{
				// We have to check each of these reactions to make sure it is: 1) in sourceReferences 2) doesn't already have a xref to rheaID
				List<GKInstance> instances = getIdentifiersInList(sourceReferences, reactomeID);
				// Really, I don't expect more than 1 instance, but it *could* return multiple instances, so...
				if (instances.size() > 0)
				{
					for (GKInstance reaction : instances)
					{
						if (!this.checkXRefExists(reaction, rheaID))
						{
							sourceIdentifiersWithNewIdentifier++;
							if (!this.testMode)
							{
								this.refCreator.createIdentifier(rheaID, reactomeID,  this.targetRefDB, personID, this.getClass().getName());
							}
						}
						else
						{
							sourceIdentifiersWithExistingIdentifier++;
						}
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
	
	private List<GKInstance> getIdentifiersInList(List<GKInstance> instances, String reactomeID)
	{
		List<GKInstance> matchingInstances = instances.parallelStream().filter( instance -> {
			try
			{
				return ((GKInstance)instance.getAttributeValue(ReactomeJavaConstants.stableIdentifier)).getAttributeValue(ReactomeJavaConstants._displayName).toString().equals(reactomeID);
			}
			catch (InvalidAttributeException e)
			{
				e.printStackTrace();
				return false;
			}
			catch (Exception e)
			{
				e.printStackTrace();
				return false;
			}
		} ).map(instance -> instance).collect(Collectors.toList());
		
		return matchingInstances;
	}
}
