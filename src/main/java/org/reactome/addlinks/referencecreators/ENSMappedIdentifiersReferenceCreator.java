package org.reactome.addlinks.referencecreators;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.GKSchemaAttribute;
import org.gk.schema.InvalidAttributeException;
import org.gk.schema.SchemaClass;
import org.reactome.addlinks.db.ReferenceCreator;

public class ENSMappedIdentifiersReferenceCreator
{
	private MySQLAdaptor adapter;
	
	protected String classToCreateName ;
	protected String classReferringToRefName ;
	protected String referringAttributeName ;
	protected String targetRefDB ;
	protected String sourceRefDB ;
	protected boolean testMode;
	
	private static final Logger logger = LogManager.getLogger();
	protected ReferenceCreator refCreator;

	public ENSMappedIdentifiersReferenceCreator(MySQLAdaptor adapter, String classToCreate, String classReferring, String referringAttribute, String sourceDB, String targetDB)
	{
		this.adapter = adapter;
		
		this.classToCreateName = classToCreate;
		this.classReferringToRefName = classReferring;
		this.referringAttributeName = referringAttribute;
		this.sourceRefDB = sourceDB;
		this.targetRefDB = targetDB;
		
		// Y'know, this code was lifted straight from OrphanetReferenceCreator and is pretty much unchanged. Perhaps these two (and others to follow) could pull
		// this code up into a common parent class/interface...
		this.adapter = adapter;
		SchemaClass schemaClass = this.adapter.getSchema().getClassByName(this.classToCreateName);

		SchemaClass referringSchemaClass = adapter.getSchema().getClassByName(this.classReferringToRefName);
		
		GKSchemaAttribute referringSchemaAttribute = null;
		try
		{
			// This should never fail, but we still need to handle the exception.
			referringSchemaAttribute = (GKSchemaAttribute) referringSchemaClass.getAttribute(referringAttributeName);
		}
		catch (InvalidAttributeException e)
		{
			logger.error("Failed to get GKSchemaAttribute with name {} from class {}. This shouldn't have happened, but somehow it did."
						+ " Check that the classes/attributes you have chosen match the data model in the database.",
						referringSchemaAttribute, referringSchemaClass );
			e.printStackTrace();
			// Can't recover if there is no valid attribute object, throw it up the stack. 
			throw new RuntimeException (e);
		}
		 
		refCreator = new ReferenceCreator(schemaClass , referringSchemaClass, referringSchemaAttribute, this.adapter);
	}
	
	/**
	 * Creates identifiers based on the mappings found in files.
	 * @param personID - The ID of the person ID that will be associated with the identifiers that will be created.
	 * @param mappingFile - The path to the file that contains the mappings that should be created as references.
	 * @throws IOException - if an I/O error occurs opening the file
	 */
	public void createIdentifiers(long personID, Map<String,Map<String,List<String>>> mappings) throws IOException
	{
		//logger.debug("Processing file: {}", mappingFile.toString());
		AtomicInteger printCounter = new AtomicInteger(0);
		AtomicInteger createdCounter = new AtomicInteger(0);
		AtomicInteger notCreatedCounter = new AtomicInteger(0);
		AtomicInteger xrefAlreadyExistsCounter = new AtomicInteger(0);
		StringBuilder sb = new StringBuilder();
//		Files.lines(mappingFile).sequential().filter(line -> !line.startsWith("From")).forEach( line -> {
//			sb.append(line).append("\n");
//		});

		//if (sb.toString().length() > 0)
		{
			String[] lines = sb.toString().split("\n");
			List<String> thingsToCreate = new ArrayList<String>();
			Map<Long,MySQLAdaptor> adapterPool = new HashMap<Long,MySQLAdaptor>();
			// Loop for each database
			mappings.keySet().stream().sequential().forEach( dbName -> {
				
				// Loop for all ENS identifiers under the named DB.
				Set<String> ensemblIdentifiers = mappings.get(dbName).keySet();
				
				ensemblIdentifiers.stream().parallel().forEach( ensemblIdentifier -> {
					String sourceIdentifier = ensemblIdentifier;
					List<String> targetIdentifiers = mappings.get(dbName).get(ensemblIdentifier);
/*
			});
			Arrays.stream(lines).parallel().forEach(line -> {
				String[] parts = line.split("\t");
				String sourceIdentifier = parts[0];
				String targetIdentifier = parts[1];
*/				
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
						// Now we need to get the DBID of the pre-existing identifier.
						@SuppressWarnings("unchecked")
						
						Collection<GKInstance> sourceInstances = (Collection<GKInstance>) localAdapter.fetchInstanceByAttribute(this.classReferringToRefName, ReactomeJavaConstants.identifier, "=", sourceIdentifier);
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
									for (GKSchemaAttribute attrib : (Collection<GKSchemaAttribute>) inst.getSchemaAttributes())
									{
										if (attrib.getName().equals(ReactomeJavaConstants.species) )
										{
											GKInstance speciesInst = (GKInstance) inst.getAttributeValue(ReactomeJavaConstants.species);
											if (speciesInst != null)
											{
												speciesID = new Long(speciesInst.getDBID());
											}
										}
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
											// Store the data for future creation as <NewIdentifier>:<DB_ID of the thing that NewIdentifier refers to>
											thingsToCreate.add(targetIdentifier+":"+String.valueOf(inst.getDBID())+":"+speciesID);
										}
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
						throw new RuntimeException(e);
					}
				});
			});
			if (!this.testMode)
			{
				thingsToCreate.stream().sequential().forEach( newIdentifier -> {
					String[] parts = newIdentifier.split(":");
					logger.trace("Creating new identifier {} ", parts[0]);
					try
					{
						if (parts[2] != null && !parts[2].trim().equals(""))
						{
							// The string had a species-part.
							this.refCreator.createIdentifier(parts[0], parts[1], this.targetRefDB, personID, this.getClass().getName(), Long.valueOf(parts[2]));
						}
						else
						{
							// The string did NOT have a species-part.
							this.refCreator.createIdentifier(parts[0], parts[1], this.targetRefDB, personID, this.getClass().getName());
						}
					}
					catch (Exception e)
					{
						throw new RuntimeException(e);
					}
				} );
			}
			
			logger.info("{} Reference creation summary:\n"
					+ "\t# Identifiers created: {}\n"
					+ "\t# Identifiers which already existed: {} \n"
					+ "\t# Identifiers that were not created: {}",
					this.targetRefDB, 
					createdCounter.get(), xrefAlreadyExistsCounter.get(), notCreatedCounter.get());
		}
//		else
//		{
//			logger.info("ENSEMBL mapping file {} is empty for {} to {}", mappingFile.toString(), sourceRefDB, targetRefDB);
//		}
	}
	
	public boolean isTestMode()
	{
		return this.testMode;
	}

	public void setTestMode(boolean testMode)
	{
		this.testMode = testMode;
	}
}
