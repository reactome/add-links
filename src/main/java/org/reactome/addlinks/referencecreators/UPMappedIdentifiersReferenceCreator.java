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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.InvalidAttributeException;

/*
 * Creates references for identifiers that were mapped from one database (usually UniProt) to another by the UniProt web service.
 * The name *is* pretty terrible, need to come up with something better later.
 */
public class UPMappedIdentifiersReferenceCreator extends SimpleReferenceCreator< Map<String,List<String>> >
{

	public UPMappedIdentifiersReferenceCreator(MySQLAdaptor adapter, String classToCreate, String classReferring, String referringAttribute, String sourceDB, String targetDB)
	{
		super(adapter, classToCreate, classReferring, referringAttribute, sourceDB, targetDB);
	}
	
	//private static final Logger logger = LogManager.getLogger();
	
	public UPMappedIdentifiersReferenceCreator(MySQLAdaptor adapter, String classToCreate, String classReferring, String referringAttribute, String sourceDB, String targetDB, String refCreatorName)
	{
		super(adapter, classToCreate, classReferring, referringAttribute, sourceDB, targetDB, refCreatorName);
	}
	
	/**
	 * Creates identifiers based on the mappings found in files.
	 * @param personID - The ID of the person ID that will be associated with the identifiers that will be created.
	 * @param mappings - First level is species to Uniprot IDs. The next level maps UniProt IDs to Other identifers.
	 * @throws IOException - if an I/O error occurs opening the file
	 */
	
	@SuppressWarnings("unchecked")
	public void createIdentifiers(long personID, Map<String, Map<String, List<String>>> mappings, List<GKInstance> sourceReferences) throws IOException
	{
		AtomicInteger printCounter = new AtomicInteger(0);
		AtomicInteger createdCounter = new AtomicInteger(0);
		AtomicInteger notCreatedCounter = new AtomicInteger(0);
		AtomicInteger xrefAlreadyExistsCounter = new AtomicInteger(0);

		// First, we need a map of sourceReferences.
		Map<String, List<GKInstance>> sourceRefMap = new HashMap<String, List<GKInstance>>(sourceReferences.size());
		
		sourceReferences.stream().forEach(sourceRef -> {
			try
			{
				String identifier = (String) ((GKInstance) sourceRef).getAttributeValue(ReactomeJavaConstants.identifier);
				if (sourceRefMap.containsKey(identifier))
				{
					sourceRefMap.get(identifier).add((GKInstance) sourceRef);
				}
				else
				{
					sourceRefMap.put(identifier, new ArrayList<GKInstance>( Arrays.asList( (GKInstance)sourceRef) ) );
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
		
		if (mappings.keySet().size() > 0)
		{
			for (String speciesID : mappings.keySet())
			{
				
				List<String> thingsToCreate = Collections.synchronizedList(new ArrayList<String>());
				Map<Long,MySQLAdaptor> adapterPool = new HashMap<Long,MySQLAdaptor>();
				
				mappings.get(speciesID).keySet().stream().parallel().forEach(uniprotID -> 
				{

					for (String otherIdentifierID : mappings.get(speciesID).get(uniprotID))
					{
					
						String sourceIdentifier = uniprotID;
						String targetIdentifier = otherIdentifierID;
						
						try
						{
							MySQLAdaptor localAdapter ;
							long threadID = Thread.currentThread().getId();
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
							// Now we need to get the DBID of the pre-existing identifier.

							Collection<GKInstance> sourceInstances = sourceRefMap.get(sourceIdentifier);
							if (sourceInstances != null && sourceInstances.size() > 0)
							{
								if (sourceInstances.size() > 1)
								{
									//Actually, it's OK to have > 1 instances. This just means that the SOURCE ID has multiple entities that will be references, such as a ReferenceGeneProduct and a ReferenceIsoform.
									logger.info("Got {} elements when fetching instances by attribute value: {}.{} {} \"{}\"",sourceInstances.size(),this.classReferringToRefName, this.referringAttributeName, "=", sourceIdentifier);
								}
			
								for (GKInstance inst : sourceInstances)
								{
									if (sourceInstances.size() > 1)
									{
										logger.trace("\tDealing with duplicated instances (in terms of Identifier), instance: {} mapping to {}", inst, targetIdentifier);
									}
									
									logger.trace("Target identifier: {}, source object: {}", targetIdentifier, inst);
									// check and make sure the cross refernces don't already exist.
									Collection<GKInstance> xrefs = inst.getAttributeValuesList(referringAttributeName);
									boolean xrefAlreadyExists = false;
									
									for (GKInstance xref : xrefs)
									{
										logger.trace("\tcross-reference: {}",xref.getAttributeValue(ReactomeJavaConstants.identifier).toString());
										// We won't add a cross-reference if it already exists
										if (xref.getAttributeValue(ReactomeJavaConstants.identifier).toString().equals( targetIdentifier ))
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
										if (!this.testMode)
										{
											// Store the data for future creation as <NewIdentifier>:<DB_ID of the thing that NewIdentifier refers to>:<Species ID>
											thingsToCreate.add(targetIdentifier+":"+String.valueOf(inst.getDBID())+":"+speciesID);
										}
										createdCounter.getAndIncrement();
										
									}
								}
							}
							else
							{
								logger.error("Somehow, there is a mapping file with identifier {} that was originally found in the database, but no longer seems to be there! You might want to investigate this...", sourceIdentifier);
								notCreatedCounter.getAndIncrement();
							}
							if (printCounter.get() >= 99)
							{
								logger.debug("{} ; {} ; {}", createdCounter.get(), xrefAlreadyExistsCounter.get(), notCreatedCounter.get());
								printCounter.set(0);
							}
							else
							{
								printCounter.incrementAndGet();
							}
						}
						catch (Exception e)
						{
							e.printStackTrace();
							throw new RuntimeException(e);
						}
					}
				} );
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
				// Go through the list of references that need to be created, and create them!
				thingsToCreate.stream().sequential().forEach( newIdentifier -> {
					String[] parts = newIdentifier.split(":");
					logger.trace("Creating new identifier {} ", parts[0] );
					try
					{
						// The string had a species-part.
						if (parts[2] != null && !parts[2].trim().equals(""))
						{
							if (!this.testMode)
							{
								this.refCreator.createIdentifier(parts[0], parts[1], this.targetRefDB, personID, this.getClass().getName(), Long.valueOf(parts[2]));
							}
						}
						// The string did NOT have a species-part.
						else
						{
							if (!this.testMode)
							{
								this.refCreator.createIdentifier(parts[0], parts[1], this.targetRefDB, personID, this.getClass().getName());
							}
						}
					}
					catch (Exception e)
					{
						throw new RuntimeException(e);
					}
				} );
			
				
				logger.info("{} Reference creation summary:\n"
						+ "\t# Identifiers created: {}\n"
						+ "\t# Identifiers which already existed: {} \n"
						+ "\t# Identifiers that were not created: {}",
						this.targetRefDB, 
						createdCounter.get(), xrefAlreadyExistsCounter.get(), notCreatedCounter.get());
			}
		}
		else
		{
			logger.info("UniProt mapping is empty for {} to {}", sourceRefDB, targetRefDB);
		}
	}
}
