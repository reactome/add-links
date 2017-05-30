package org.reactome.addlinks.referencecreators;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.GKSchemaAttribute;
import org.reactome.addlinks.db.ReferenceObjectCache;

public class ENSMappedIdentifiersReferenceCreator extends SimpleReferenceCreator<Map<String,List<String>>>
{
	private List<EntrezGeneBasedReferenceCreator> entrezGeneReferenceCreators;
	
	private static ReferenceObjectCache objectCache;
	
	public ENSMappedIdentifiersReferenceCreator(MySQLAdaptor adapter, String classToCreate, String classReferring, String referringAttribute, String sourceDB, String targetDB)
	{
		super(adapter, classToCreate, classReferring, referringAttribute, sourceDB, targetDB);
	}
	
	public ENSMappedIdentifiersReferenceCreator(MySQLAdaptor adapter, String classToCreate, String classReferring, String referringAttribute, String sourceDB, String targetDB, String refCreatorName)
	{
		super(adapter, classToCreate, classReferring, referringAttribute, sourceDB, targetDB, refCreatorName);
	}
	
	public ENSMappedIdentifiersReferenceCreator(MySQLAdaptor adapter, String classToCreate, String classReferring, String referringAttribute, String sourceDB, String targetDB, String refCreatorName, List<EntrezGeneBasedReferenceCreator> entrezGeneRefCreators)
	{
		super(adapter, classToCreate, classReferring, referringAttribute, sourceDB, targetDB, refCreatorName);
		this.entrezGeneReferenceCreators = entrezGeneRefCreators;
	}
	
	/**
	 * Creates identifiers based on the mappings found in files.
	 * @param personID - The ID of the person ID that will be associated with the identifiers that will be created.
	 * @param mappings - A map of maps of strings.
	 * @throws IOException - if an I/O error occurs opening the file
	 */
	@Override
	public void createIdentifiers(long personID, Map<String,Map<String,List<String>>> mappings, List<GKInstance> sourceReferences) throws IOException
	{
		AtomicInteger createdCounter = new AtomicInteger(0);
		AtomicInteger notCreatedCounter = new AtomicInteger(0);
		AtomicInteger xrefAlreadyExistsCounter = new AtomicInteger(0);
	
		if (objectCache == null)
		{
			objectCache = new ReferenceObjectCache(this.adapter, true);
		}
		
		List<String> thingsToCreate = Collections.synchronizedList(new ArrayList<String>());
		Map<Long,MySQLAdaptor> adapterPool = Collections.synchronizedMap( new HashMap<Long,MySQLAdaptor>() );

		// Loop for each database
		mappings.keySet().stream().sequential().forEach( dbName -> {
			logger.debug("DB: {}", dbName);
			// Loop for all ENS identifiers under the named DB.
			Set<String> ensemblIdentifiers = mappings.get(dbName).keySet();
			logger.debug("{} identifiers to map.", ensemblIdentifiers.size());
			ensemblIdentifiers.stream().parallel().forEach( ensemblIdentifier -> {
				String sourceIdentifier = ensemblIdentifier;
				List<String> targetIdentifiers = mappings.get(dbName).get(ensemblIdentifier);

				try
				{
					MySQLAdaptor localAdapter ;
					long threadID = Thread.currentThread().getId();
					//logger.debug("Thread ID: {}",threadID);
					if (adapterPool.containsKey(threadID))
					{
						localAdapter = adapterPool.get(threadID);
					}
					else
					{
						logger.debug("Creating new SQL Adaptor for thread {}", Thread.currentThread().getId());
						localAdapter = new MySQLAdaptor(this.adapter.getDBHost(), this.adapter.getDBName(), this.adapter.getDBUser(),this.adapter.getDBPwd(), this.adapter.getDBPort());
						adapterPool.put(threadID, localAdapter);
					}
					// Filter the the input list of source reference objects by everything whose identifier matches the *current* sourceIdentifier.
					Collection<GKInstance> sourceInstances = Collections.synchronizedCollection( sourceReferences.stream().filter(sourceRef -> {
						try
						{
							return sourceRef.getAttributeValue(ReactomeJavaConstants.identifier).equals(sourceIdentifier);
						}
						catch (Exception e)
						{
							e.printStackTrace();
							return false;
						}
					}).collect(Collectors.toList()) );
					// I really wouldn't expect more than one instance, BUT the API function festInstanceByAttribute used here returns a Collection, so we should still loop.
					if (sourceInstances.size() > 0)
					{
						if (sourceInstances.size() > 1)
						{
							//Actually, it's OK to have > 1 instances. This just means that the SOURCE ID has multiple entities that will be references, such as a ReferenceGeneProduct and a ReferenceIsoform.
							logger.info("Got {} elements when fetching instances by attribute value: {}.{} {} \"{}\"",sourceInstances.size(),this.classReferringToRefName, this.referringAttributeName, "=", sourceIdentifier);
						}
						
						for (GKInstance inst : sourceInstances)
						{
							for (String targetIdentifier : targetIdentifiers)
							{
								if (sourceInstances.size() > 1)
								{
									logger.debug("\tDealing with duplicated instances (in terms of Identifier), instance: {} mapping to {}", inst, targetIdentifier);
								}
								
								// It's possible that we could get a list of things from some third-party that contains mappings for multiple species.
								// So we need to get the species for EACH thing we iterate on. I worry this will slow it down, but  it needs to be done
								// if we want new identifiers to have the same species of the thing which they refer to.
								Long speciesID = null;
								@SuppressWarnings("unchecked")
								Collection<GKSchemaAttribute> attributes = (Collection<GKSchemaAttribute>) inst.getSchemClass().getAttributes();
								if ( attributes.stream().filter(attr -> attr.getName().equals(ReactomeJavaConstants.species)).findFirst().isPresent())
								{
									GKInstance speciesInst = (GKInstance) inst.getAttributeValue(ReactomeJavaConstants.species);
									if (speciesInst != null)
									{
										speciesID = new Long(speciesInst.getDBID());
									}
								}
								logger.trace("Target identifier: {}, source object: {}", targetIdentifier, inst);
								// check and make sure the cross refernces don't already exist.
								@SuppressWarnings("unchecked")
								List<GKInstance> xrefs = (List<GKInstance>) inst.getAttributeValuesList(referringAttributeName);
								boolean xrefAlreadyExists = false;
								
								for (GKInstance xref : xrefs)
								{
									String xrefIdentifier = xref.getAttributeValue(ReactomeJavaConstants.identifier) != null
															? xref.getAttributeValue(ReactomeJavaConstants.identifier).toString()
															: null;
									logger.trace("\tcross-reference: {}",xrefIdentifier);
									// We won't add a cross-reference if it already exists
									if (targetIdentifier.equals(xrefIdentifier))
									{
										xrefAlreadyExistsCounter.incrementAndGet();
										xrefAlreadyExists = true;
										// Break out of the xrefs loop - we found an existing cross-reference that matches so there's no point 
										// in letting the loop run longer.
										// TODO: rewrite into a while-loop condition (I don't like breaks that much).
										break;
									}
								}
								if (!xrefAlreadyExists)
								{
									thingsToCreate.add(targetIdentifier+":"+String.valueOf(inst.getDBID())+":"+speciesID);
									createdCounter.getAndIncrement();
								}
							}
						}
					}
					else
					{
						logger.error("Somehow, there is a mapping file with identifier {} that was originally found in the database, but no longer seems to be there! You might want to investigate this...", sourceIdentifier);
						notCreatedCounter.getAndIncrement();
					}
				}
				catch (Exception e)
				{
					throw new RuntimeException(e);
				}
			});
		});
		
		// empty the pool.
		for (Long k : adapterPool.keySet())
		{
			try
			{
				adapterPool.get(k).cleanUp();
			} 
			catch (Exception e)
			{
				logger.error("Could not clean up the database adapter: {}",e.getMessage());
				throw new Error(e);
			}
		}
		
		thingsToCreate.stream().sequential().forEach( newIdentifier -> {
			if (newIdentifier != null)
			{
				String[] parts = newIdentifier.split(":");
				logger.trace("Creating new identifier {} ", parts[0]);
				try
				{
					if (!this.testMode)
					{
						// The string had a species-part.
						if (parts[2] != null && !parts[2].trim().equals(""))
						{
							String targetRefDBName = this.targetRefDB;
							// If target is ENSEMBL, we need to figure out *which* ENSEMBL target database to use.
							if (this.targetRefDB.toUpperCase().contains("ENSEMBL"))
							{
								String speciesName = objectCache.getSpeciesNamesByID().get(parts[2]).get(0);
								// ReactomeJavaConstants.ReferenceGeneProduct should be under ENSEMBL*PROTEIN and others should be under ENSEMBL*GENE
								// Since we're not mapping to Transcript, we don't need to worry about that here.
								targetRefDBName = "ENSEMBL_"+speciesName.replaceAll(" ", "_").toLowerCase()
													+ "_" + (this.classToCreateName.equals(ReactomeJavaConstants.ReferenceGeneProduct) ? "PROTEIN" : "GENE");
							}
							this.refCreator.createIdentifier(parts[0], parts[1], targetRefDBName, personID, this.getClass().getName(), Long.valueOf(parts[2]));
							// If target is EntrezGene, there are references to other databases that need to be created using the EntrezGene ID: BioGPS, CTD, DbSNP
							if (this.targetRefDB.toUpperCase().contains("ENTREZGENE") || this.targetRefDB.toUpperCase().contains("ENTREZ GENE"))
							{
								for (EntrezGeneBasedReferenceCreator entrezGeneCreator : this.entrezGeneReferenceCreators)
								{
									entrezGeneCreator.createEntrezGeneReference(parts[0], parts[1], parts[2], personID);
								}
							}
						}
						// The string did NOT have a species-part.
						else
						{
							this.refCreator.createIdentifier(parts[0], parts[1], this.targetRefDB, personID, this.getClass().getName());
						}
					}
				}
				catch (Exception e)
				{
					throw new RuntimeException(e);
				}
			}
			else
			{
				logger.error("newIdentifier is null. How does that even happen?!?! Here's the list of things to create: {}", thingsToCreate);
			}
		} );
		if (createdCounter.get() != thingsToCreate.size())
		{
			logger.warn("The \"created\" counter says: {} but the size of the thingsToCreate list is: {}",createdCounter.get(), thingsToCreate.size());
		}
		logger.info("{} Reference creation summary:\n"
				+ "\t# Identifiers created: {}\n"
				+ "\t# Identifiers which already existed: {} \n"
				+ "\t# Identifiers that were not created: {}",
				this.targetRefDB, 
				createdCounter.get(), xrefAlreadyExistsCounter.get(), notCreatedCounter.get());
	
	}
}
