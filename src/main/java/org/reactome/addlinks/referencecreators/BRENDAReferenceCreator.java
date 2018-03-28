package org.reactome.addlinks.referencecreators;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.GKSchemaAttribute;
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
		AtomicInteger sourceIdentifiersWithNoMapping = new AtomicInteger(0);
		AtomicInteger sourceIdentifiersWithNewIdentifier = new AtomicInteger(0);
		AtomicInteger sourceIdentifiersWithExistingIdentifier = new AtomicInteger(0);
		AtomicInteger totalNumberNewIdentifiers = new AtomicInteger(0);
		List<String> thingsToCreate = Collections.synchronizedList(new ArrayList<String>());
		List<String> brendaDBNames = referenceObjectCache.getRefDbNamesToIds().keySet().stream().filter(refDBName -> refDBName.toUpperCase().contains("BRENDA")).collect(Collectors.toList());
		sourceReferences.parallelStream().forEach( instance ->
		{
			try
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
					
					sourceIdentifiersWithNewIdentifier.incrementAndGet();
					for (String ecNumber : mapping.get(sourceReferenceIdentifier))
					{
						logger.trace("{} ID: {}; {} ID: {}", this.sourceRefDB, sourceReferenceIdentifier, speciesSpecificTargetRefDB, ecNumber);
						// Look for cross-references.
						boolean xrefAlreadyExists = checkXRefExists(instance, ecNumber);
						if (!xrefAlreadyExists)
						{
							logger.trace("\tNeed to create a new identifier!");
							totalNumberNewIdentifiers.incrementAndGet();
							String thingToCreate = ecNumber + ";" + String.valueOf(instance.getDBID()) + ";" + speciesSpecificTargetRefDB + ";" + personID + ";" + this.getClass().getName() + ";" + speciesID;
							thingsToCreate.add(thingToCreate);
						}
						else
						{
							sourceIdentifiersWithExistingIdentifier.incrementAndGet();
						}
					}
				}
				else
				{
					sourceIdentifiersWithNoMapping.incrementAndGet();
					//logger.debug("UniProt ID {} is NOT in the database.", uniprotID);
				}
			}
			catch (Exception e)
			{
				throw new RuntimeException(e);
			}
		});

		logger.info("{} things to create.", thingsToCreate.size());
		for (String thing : thingsToCreate)
		{
			String[] parts = thing.split(";");
			String ecNumber = parts[0];
			String dbID = parts[1];
			String speciesSpecificTargetRefDB = parts[2];
			//String personID = parts[3];
			String creatingClassName = parts[4];
			Long speciesID = Long.valueOf(parts[5]);
			if (!this.testMode)
			{
				this.refCreator.createIdentifier(ecNumber, dbID, speciesSpecificTargetRefDB, personID, creatingClassName, speciesID);
			}
		}
		
		logger.info("{} reference creation summary: \n"
				+ "\t# {} IDs with a new {} identifier (a new {} reference was created): {};\n"
				+ "\t\tTotal # new identifiers: {};\n"
				+ "\t# {} identifiers which already had the same {} reference (nothing new was created): {};\n"
				+ "\t# {} identifiers not in the {} mapping file (no new {} reference was created for them): {} ",
				this.targetRefDB,
				this.sourceRefDB, this.targetRefDB, this.targetRefDB, sourceIdentifiersWithNewIdentifier.get(),
				totalNumberNewIdentifiers.get(),
				this.sourceRefDB, this.targetRefDB, sourceIdentifiersWithExistingIdentifier.get(),
				this.sourceRefDB, this.targetRefDB, this.targetRefDB, sourceIdentifiersWithNoMapping.get());
	}
	
}
