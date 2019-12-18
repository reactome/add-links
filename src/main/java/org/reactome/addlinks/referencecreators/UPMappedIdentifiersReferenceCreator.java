package org.reactome.addlinks.referencecreators;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.InvalidAttributeException;
import org.reactome.addlinks.db.ReferenceObjectCache;
import org.reactome.addlinks.kegg.KEGGSpeciesCache;

/*
 * Creates references for identifiers that were mapped from one database (usually UniProt) to another by the UniProt web service.
 * The name *is* pretty terrible, need to come up with something better later.
 */
public class UPMappedIdentifiersReferenceCreator extends NCBIGeneBasedReferenceCreator
{

	ReferenceObjectCache refObjectCache = new ReferenceObjectCache(this.adapter, true);

	public UPMappedIdentifiersReferenceCreator(MySQLAdaptor adapter, String classToCreate, String classReferring, String referringAttribute, String sourceDB, String targetDB)
	{
		super(adapter, classToCreate, classReferring, referringAttribute, sourceDB, targetDB);
	}

	public UPMappedIdentifiersReferenceCreator(MySQLAdaptor adapter, String classToCreate, String classReferring, String referringAttribute, String sourceDB, String targetDB, String refCreatorName)
	{
		super(adapter, classToCreate, classReferring, referringAttribute, sourceDB, targetDB, refCreatorName);
	}

	public UPMappedIdentifiersReferenceCreator(MySQLAdaptor adapter, String classToCreate, String classReferring, String referringAttribute, String sourceDB, String targetDB, String refCreatorName, List<EntrezGeneBasedReferenceCreator> entrezGeneRefCreators)
	{
		super(adapter, classToCreate, classReferring, referringAttribute, sourceDB, targetDB, refCreatorName);
		this.entrezGeneReferenceCreators = entrezGeneRefCreators;
	}

	/**
	 * Creates identifiers based on the mappings found in files.
	 * @param personID - The ID of the person ID that will be associated with the identifiers that will be created.
	 * @param mappings - First level is species to Uniprot IDs. The next level maps UniProt IDs to Other identifers.
	 * @throws IOException - if an I/O error occurs opening the file
	 */

	@Override
	public void createIdentifiers(long personID, Map<String, Map<String, List<String>>> mappings, List<GKInstance> sourceReferences) throws IOException
	{
		AtomicInteger createdCounter = new AtomicInteger(0);
		AtomicInteger notCreatedCounter = new AtomicInteger(0);
		AtomicInteger xrefAlreadyExistsCounter = new AtomicInteger(0);

		Function<String, String> generateENSEMBLRefDBName = (String speciesName) ->
		{
			return "ENSEMBL_"+speciesName.replaceAll(" ", "_").toLowerCase() + "_" + (this.classToCreateName.equals(ReactomeJavaConstants.ReferenceGeneProduct) ? "PROTEIN" : "GENE");
		};

		// First, we need a map of sourceReferences.
		Map<String, List<GKInstance>> sourceRefMap = Collections.synchronizedMap(new HashMap<String, List<GKInstance>>(sourceReferences.size()));

		sourceReferences.parallelStream().forEach(sourceRef -> {
			try
			{
				String identifier = (String) ((GKInstance) sourceRef).getAttributeValue(ReactomeJavaConstants.identifier);
				if (sourceRefMap.containsKey(identifier))
				{
					sourceRefMap.get(identifier).add((GKInstance) sourceRef);
				}
				else
				{
					sourceRefMap.put(identifier, new ArrayList<>( Arrays.asList( (GKInstance)sourceRef) ) );
				}
			}
			catch (InvalidAttributeException e1)
			{
				e1.printStackTrace();
				throw new Error(e1);
			}
			catch (Exception e1)
			{
				e1.printStackTrace();
				throw new Error(e1);
			}
		});

		if (mappings != null && mappings.keySet() != null && mappings.keySet().size() > 0)
		{
			List<String> thingsToCreate = Collections.synchronizedList(new ArrayList<String>());
			for (String speciesID : mappings.keySet())
			{
				if (mappings.get(speciesID).keySet().size() > 0)
				{
					this.logger.info("Creating (up to) {} references for species {}", mappings.get(speciesID).keySet().size(), speciesID);
				}
				else
				{
					this.logger.info("No references to create for species {}", speciesID);
				}

				mappings.get(speciesID).keySet().parallelStream().forEach(uniprotID ->
				{

					for (String otherIdentifierID : mappings.get(speciesID).get(uniprotID))
					{
						String sourceIdentifier = uniprotID;
						String targetIdentifier = otherIdentifierID;
						String keggPrefix = null;
						try
						{
							// Special case for KEGG - prune species code prefix.
							boolean targetIsKEGG = this.targetRefDB.toUpperCase().trim().contains("KEGG");
							if (targetIsKEGG)
							{
								keggPrefix = KEGGSpeciesCache.extractKEGGSpeciesCode(targetIdentifier);
								//TODO find a way to skip to next mapping if KEGG prefix is "forbidden" (such as mtv).
								targetIdentifier = KEGGSpeciesCache.pruneKEGGSpeciesCode(targetIdentifier);
							}
							boolean forbiddenKEGGPrefix = "mtv".equals(keggPrefix);
							// Now we need to get the DBID of the pre-existing identifier.
							Collection<GKInstance> sourceInstances = sourceRefMap.get(sourceIdentifier);
							if (sourceInstances != null && sourceInstances.size() > 0)
							{
								if (sourceInstances.size() > 1)
								{
									//Actually, it's OK to have > 1 instances. This just means that the SOURCE ID has multiple entities that will be references, such as a ReferenceGeneProduct and a ReferenceIsoform.
									this.logger.info("Fetch instance by attribute ({}.{}={})yields {} items",this.classReferringToRefName, this.referringAttributeName, sourceIdentifier,sourceInstances.size());
								}

								for (GKInstance inst : sourceInstances)
								{
									String targetDB = this.targetRefDB;
									if (sourceInstances.size() > 1)
									{
										this.logger.trace("\tDealing with duplicate instances (w.r.t. Identifier), instance: {} mapping to {}", inst, targetIdentifier);
									}

									this.logger.trace("Target identifier: {}, source object: {}", targetIdentifier, inst);

									if (this.targetRefDB.contains("UCSC"))
									{
										// UCSC - the target identifier IS the UniProt identifier,
										// since we are using a UCSC feature which takes in
										// UniProt IDs and then redirects the use to the correct page.
										targetIdentifier = uniprotID;
									}
									else if (targetIsKEGG && !forbiddenKEGGPrefix)
									{
										synchronized (this)
										{
											targetDB = null;
											KEGGReferenceCreatorHelper referenceCreatorHelper = new KEGGReferenceCreatorHelper(this.refObjectCache, this.logger);
											String[] parts = referenceCreatorHelper.determineKeggReferenceDatabase(targetIdentifier, keggPrefix);
											targetDB = parts[0];
											targetIdentifier = parts[1];
										}

									}
									else if (this.targetRefDB.toUpperCase().contains("ENSEMBL"))
									{
										targetDB = setTargetDBForENSEMBL(generateENSEMBLRefDBName, speciesID);
									}
									if (!targetIsKEGG || !forbiddenKEGGPrefix)
									{
										boolean xrefAlreadyExists = checkXRefExists(inst, targetIdentifier, targetDB);
										String thingToCreate = targetIdentifier+","+String.valueOf(inst.getDBID())+","+speciesID+","+targetDB;
										if (!xrefAlreadyExists && !thingsToCreate.contains(thingToCreate) && targetDB != null)
										{
											//logger.info("Need to create {} references", thingsToCreate.size());
											if (!this.testMode)
											{
												// Store the data for future creation as <NewIdentifier>:<DB_ID of the thing that NewIdentifier refers to>:<Species ID>
												thingsToCreate.add(thingToCreate);
											}
											createdCounter.getAndIncrement();
										}
									}
								}
							}
							else
							{
								this.logger.error("Somehow, there is a mapping file with identifier {} that was originally found in the database, but no longer seems to be there! You might want to investigate this...", sourceIdentifier);
								notCreatedCounter.getAndIncrement();
							}
						}
						catch (Exception e)
						{
							e.printStackTrace();
							throw new RuntimeException(e);
						}
					}
				} );
			}
			// Go through the list of references that need to be created, and create them!
			thingsToCreate.stream().sequential().forEach( newIdentifier -> {
				String[] newIdentifierParts = newIdentifier.split(",");
				String identifierValue = newIdentifierParts[0];
				String referenceToValue = newIdentifierParts[1];
				String species = newIdentifierParts[2];
				String targetDB = newIdentifierParts[3];
				//logger.trace("Creating new identifier {} ", identifierValue );
				try
				{

					// The string had a species-part.
					if (species != null && !species.trim().equals(""))
					{
						if (!this.testMode)
						{
							this.refCreator.createIdentifier(identifierValue, referenceToValue, targetDB, personID, this.getClass().getName(), Long.valueOf(species));
						}
						// If target is EntrezGene, there are references to other databases that need to be created using the EntrezGene ID: BioGPS, CTD, DbSNP, Monarch
						// NOTE: "EntrezGene" should really be referred to now as "NCBI Gene".
						if (this.targetRefDB.toUpperCase().contains("ENTREZGENE") || this.targetRefDB.toUpperCase().contains("ENTREZ GENE") || this.targetRefDB.toUpperCase().contains("NCBI GENE"))
						{
							runNCBIGeneRefCreators(personID, identifierValue, referenceToValue, species, this.refObjectCache);
						}
					}
					// The string did NOT have a species-part.
					else
					{
						if (!this.testMode)
						{
							this.refCreator.createIdentifier(identifierValue, referenceToValue, this.targetRefDB, personID, this.getClass().getName());
						}
					}
				}
				catch (Exception e)
				{
					throw new RuntimeException(e);
				}
			} );
			this.logger.info("{} Reference creation summary:\n"
							+ "\t# Identifiers created: {}\n"
							+ "\t# Identifiers which already existed: {} \n"
							+ "\t# Identifiers that were not created: {}",
					this.targetRefDB,
					createdCounter.get(), xrefAlreadyExistsCounter.get(), notCreatedCounter.get());
		}
		else
		{
			this.logger.info("UniProt mapping is empty for {} to {}", this.sourceRefDB, this.targetRefDB);
		}
	}

	private String setTargetDBForENSEMBL(Function<String, String> generateENSEMBLRefDBName, String speciesID)
	{
		String targetDB;
		List<String> speciesNames = this.refObjectCache.getSpeciesNamesByID().get(speciesID);
		String speciesName = speciesNames.stream().filter(s -> null!=generateENSEMBLRefDBName.apply(s) ).findFirst().orElse(null);
		if (speciesName != null)
		{
			//special case for hamsters - ENSEMBL doesn't have an *exact*
			//match for Cricetulus griseus, but "cricetulus_griseus_crigri"
			//is what should be used.
			if (speciesName.equals("Cricetulus griseus")) {
				speciesName = "cricetulus_griseus_crigri";
			}

			// ENSEMBL species-specific database.
			// ReactomeJavaConstants.ReferenceGeneProduct should be under ENSEMBL*PROTEIN and others should be under ENSEMBL*GENE
			// Since we're not mapping to Transcript, we don't need to worry about that here.
			targetDB = generateENSEMBLRefDBName.apply(speciesName);
			// Ok, now let's check that that the db we want actually exists
			if (this.refObjectCache.getRefDbNamesToIds().get(targetDB) == null
				|| this.refObjectCache.getRefDbNamesToIds().get(targetDB).size() == 0)
			{
				this.logger.error("You wanted the database with the name {} but that does not exist.", targetDB);
				throw new RuntimeException("Requested ENSEMBL ReferenceDatabase \""+targetDB+"\" does not exists.");
			}
		}
		else
		{
			// If we couldn't generate a potential database name from the species code, just use the targetRefDB.
			// Not ideal, but what else can you do here?
			targetDB = this.targetRefDB;
			// ...also, let's issue a warning.
			this.logger.warn("No ENSEMBL species-specific database found for species ID: {}, so Ref DB {} will be used", speciesID, this.targetRefDB);
		}
		return targetDB;
	}
}
