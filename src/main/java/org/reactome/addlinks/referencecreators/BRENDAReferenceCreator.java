package org.reactome.addlinks.referencecreators;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.GKSchemaAttribute;
import org.reactome.addlinks.brenda.BRENDAReferenceDatabaseGenerator;
import org.reactome.addlinks.db.ReferenceObjectCache;

public class BRENDAReferenceCreator extends SimpleReferenceCreator<List<String>>
{
	ReferenceObjectCache referenceObjectCache = new ReferenceObjectCache(this.adapter);
	public BRENDAReferenceCreator(MySQLAdaptor adapter, String classToCreate, String classReferring, String referringAttribute, String sourceDB, String targetDB)
	{
		super(adapter, classToCreate, classReferring, referringAttribute, sourceDB, targetDB, null);
	}
	
	public BRENDAReferenceCreator(MySQLAdaptor adapter, String classToCreate, String classReferring, String referringAttribute, String sourceDB, String targetDB,String refCreatorName)
	{
		super(adapter, classToCreate, classReferring, referringAttribute, sourceDB, targetDB, refCreatorName);
	}

	@Override
	public void createIdentifiers(long personID, Map<String, List<String>> mapping, List<GKInstance> sourceReferences) throws Exception
	{
		int sourceIdentifiersWithNoMapping = 0;
		int sourceIdentifiersWithNewIdentifier = 0;
		int sourceIdentifiersWithExistingIdentifier = 0;
		int totalNumberNewIdentifiers = 0;
		List<String> brendaDBNames = referenceObjectCache.getRefDbNamesToIds().keySet().stream().filter(refDBName -> refDBName.toUpperCase().contains("BRENDA")).collect(Collectors.toList());
		for (GKInstance instance : sourceReferences)
		{
			String sourceReferenceIdentifier = (String) instance.getAttributeValue(ReactomeJavaConstants.identifier);
			Long speciesID = null;
			// We know the species name so we should be able to find a BRENDA refdb that matches it.
			String speciesSpecificTargetRefDB = "";
			@SuppressWarnings("unchecked")
			Collection<GKSchemaAttribute> attributes = (Collection<GKSchemaAttribute>) instance.getSchemClass().getAttributes();
			if ( attributes.stream().filter(attr -> attr.getName().equals(ReactomeJavaConstants.species)).findFirst().isPresent())
			{
				GKInstance speciesInst = (GKInstance) instance.getAttributeValue(ReactomeJavaConstants.species);
				if (speciesInst != null)
				{
					speciesID = new Long(speciesInst.getDBID());
				}
			}
			if (speciesID != null)
			{
				// Find the species name based on the species ID.
				String speciesNameForBrenda = referenceObjectCache.getSpeciesNamesByID().get(String.valueOf(speciesID))
																	.stream()
																	.filter( speciesName -> brendaDBNames.stream().anyMatch( brendaName -> brendaName.toUpperCase().contains(speciesName.toUpperCase()) ) )
																	.findFirst().orElse("");
				// Find the species-specific BREDNA ReferenceDatabase.
				speciesSpecificTargetRefDB = brendaDBNames.stream().filter(dbName -> dbName.contains(speciesNameForBrenda)).findFirst().orElse("");
				
			}
			if (mapping.containsKey(sourceReferenceIdentifier))
			{
				
				sourceIdentifiersWithNewIdentifier ++;
				for (String ecNumber : mapping.get(sourceReferenceIdentifier))
				{
					logger.trace("{} ID: {}; {} ID: {}", this.sourceRefDB, sourceReferenceIdentifier, speciesSpecificTargetRefDB, ecNumber);
					// Look for cross-references.
					boolean xrefAlreadyExists = checkXRefExists(instance, ecNumber);
					if (!xrefAlreadyExists)
					{
						logger.trace("\tNeed to create a new identifier!");
						totalNumberNewIdentifiers++;
						if (!this.testMode)
						{
							this.refCreator.createIdentifier(ecNumber, String.valueOf(instance.getDBID()), speciesSpecificTargetRefDB, personID, this.getClass().getName(), speciesID);
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
