package org.reactome.addlinks;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.persistence.MySQLAdaptor.AttributeQueryRequest;
import org.gk.schema.InvalidAttributeException;
import org.reactome.addlinks.dataretrieval.FileRetriever;
import org.reactome.addlinks.dataretrieval.UniprotFileRetreiver;
import org.reactome.addlinks.dataretrieval.UniprotFileRetreiver.UniprotDB;
import org.reactome.addlinks.dataretrieval.ensembl.EnsemblBatchLookup;
import org.reactome.addlinks.dataretrieval.ensembl.EnsemblFileRetriever;
import org.reactome.addlinks.dataretrieval.ensembl.EnsemblFileRetriever.EnsemblDB;
import org.reactome.addlinks.db.ReferenceDatabaseCreator;
import org.reactome.addlinks.db.ReferenceObjectCache;
import org.reactome.addlinks.fileprocessors.FileProcessor;
import org.reactome.addlinks.fileprocessors.ensembl.EnsemblBatchLookupFileProcessor;

public class AddLinks
{
	private static final Logger logger = LogManager.getLogger();
	
	private ReferenceObjectCache objectCache;
	
	private List<String> fileProcessorFilter;
	
	private List<String> fileRetrieverFilter;
	
	private Map<String, UniprotFileRetreiver> uniprotFileRetrievers;
	
	private Map<String, EnsemblFileRetriever> ensemblFileRetrievers;
	
	private Map<String, FileProcessor> fileProcessors;
	
	private Map<String,FileRetriever> fileRetrievers;
	
	private Map<String, Map<String, ?>> referenceDatabasesToCreate;
	
	private EnsemblBatchLookup ensemblBatchLookup;
	
	private MySQLAdaptor dbAdapter;

	public void doAddLinks() throws Exception
	{
		if (objectCache == null)
		{
			throw new Error("ObjectCache cannot be null.");
		}
		
		//TODO: Command line arguments:
		// - paths to spring config and addlinks.properties files.
		
		Properties applicationProps = new Properties();
		applicationProps.load(AddLinks.class.getClassLoader().getResourceAsStream("addlinks.properties"));
		
		long personID = Long.valueOf(applicationProps.getProperty("executeAsPersonID"));

		boolean filterRetrievers = applicationProps.containsKey("filterFileRetrievers") && applicationProps.getProperty("filterFileRetrievers") != null ? Boolean.valueOf(applicationProps.getProperty("filterFileRetrievers")) : false;		
		if (filterRetrievers)
		{
			//fileRetrieverFilter = context.getBean("fileRetrieverFilter",List.class);
			logger.info("Only the specified FileRetrievers will be executed: {}",fileRetrieverFilter);
		}
		
		//executeCreateReferenceDatabases();
		
		//executeSimpleFileRetrievers();
		//executeUniprotFileRetrievers();
		executeEnsemblFileRetrievers();
		
		logger.info("Finished downloading files.");
		
		logger.info("Now processing the files...");
		// TODO: Link the file processors to the file retrievers so that if
		// any are filtered, only the appropriate processors will execute.
//		Map<String, Map<String, ?>> dbMappings = executeFileProcessors();
//		logger.info("{} keys in mapping object.", dbMappings.keySet().size());
		
		//Before each set of IDs is updated in the database, maybe take a database backup?
		
		//Now we create references.

		
		logger.info("Process complete.");
		
	}

	private void executeCreateReferenceDatabases()
	{
		ReferenceDatabaseCreator creator = new ReferenceDatabaseCreator(dbAdapter);
		for (String key : this.referenceDatabasesToCreate.keySet())
		{
			logger.info("Creating ReferenceDatabase {}", key);
			
			Map<String, ?> refDB = this.referenceDatabasesToCreate.get(key);
			String url = null, accessUrl = null;
			List<String> names = new ArrayList<String>();
			for(String attributeKey : refDB.keySet())
			{
				switch (attributeKey)
				{
					case "Name":
						if (refDB.get(attributeKey) instanceof String )
						{
							names.add((String) refDB.get(attributeKey));
						}
						else if (refDB.get(attributeKey) instanceof List )
						{
							names.addAll((Collection<? extends String>) refDB.get(attributeKey));
						}
						else
						{
							logger.error("Found a \"Name\" of an invalid type: {}", refDB.get(attributeKey).getClass().getName() );
						}
						break;
	
					case "AccessURL":
						accessUrl = (String) refDB.get(attributeKey) ;
						break;
						
					case "URL":
						url = (String) refDB.get(attributeKey) ;
						break;
				}
				
			}
			try
			{
				creator.createReferenceDatabase(url, accessUrl, (String[]) names.toArray(new String[names.size()]) );
			}
			catch (Exception e)
			{
				logger.error("Error while trying to create ReferenceDatabase record: {}", e.getMessage());
				e.printStackTrace();
			}
		}
	}

	private Map<String, Map<String, ?>> executeFileProcessors()
	{
		Map<String,Map<String,?>> dbMappings = new HashMap<String, Map<String,?>>();

		fileProcessors.keySet().stream().filter(k -> fileProcessorFilter.contains(k)).forEach( k -> 
			{
				logger.info("Executing file processor: {}", k);
				dbMappings.put(k, fileProcessors.get(k).getIdMappingsFromFile() );
			}
		);
		return dbMappings;
	}

	private void executeSimpleFileRetrievers()
	{
		fileRetrievers.keySet().stream().parallel().forEach(k -> {
			if (fileRetrieverFilter.contains(k))
			{
				FileRetriever retriever = fileRetrievers.get(k);
				logger.info("Executing downloader: {}",k);
				try
				{
					retriever.fetchData();
				}
				catch (Exception e)
				{
					//TODO: The decision to continue after a failure should be a configurable option. 
					logger.info("Exception caught while processing {}, message is: {}. Will continue with next file retriever.",k,e.getMessage());
				}
			}
			else
			{
				logger.info("Skipping {}",k);
			}
		});
	}

	private void executeEnsemblFileRetrievers() throws Exception
	{
		// Getting cross-references from ENSEMBL requires first getting doing a batch mapping from ENSP to ENST, then batch mapping ENST to ENSG.
		// Then, individual xref lookups on ENSG.
		// To do the batch lookups, you will need the species, and also the species-specific ENSEMBL db_id.
		// To do the xref lookup, you will need the target database.
		
		// Input to this algorithm should be: species and external db. You can give in a list of retrievers that contain the external db they want
		// to do a cross-ref lookup on. Filtering by species should also be allowed. 
		// Also: maybe allow PROTEIN, GENE, TRANSCRIPT as inputs, to start with different ENSEMBL_${species}_PROTEIN/GENE/TRANSCRIPT Ensembl IDs.
		// 
		// Ok, here's what to do: 
		// 1) for all species, generate ReferenceDatabase names for ENSEMBL with the pattern ENSEMBL_${species}_PROTEIN/GENE/TRANSCRIPT
		// 2) check to see if that Name is a valid name in the database.
		// 3) if it is, then do batch lookups on everything in the database for that Ensembl ReferenceDatabase.
		// 4) do batch lookups to get ENSG ENSEMBL IDs, where necessary.
		// 5) Use list of ensemblFileRetrievers to do xref lookups.
		// Consider refactoring all of this into a separate class.
		
		//for (String speciesName : objectCache.getListOfSpeciesNames())
		{
			//now, generate an ENSEMBL database name ending in _GENE, _PROTEIN, _TRANSCRIPT
			//for (String suffix : new String[]{/*"_GENE", "_TRANSCRIPT", */"_PROTEIN"})
			{
				// replace any spaces with "_". Remember: MySQL will match "_" with any single character when you try to match using the LIKE operator.
				// this is necessary because some databases have a space in the species name and some have an underscore.
				//String dbName = "ENSEMBL_" + speciesName.replace(" ", "_") + suffix;
				String dbName = "ENSEMBL_%_PROTEIN";
				logger.debug("Trying to find database with name {}", dbName);
				//List<GKInstance> databases = (List<GKInstance>) dbAdapter.fetchInstanceByAttribute("ReferenceDatabase", "name", "LIKE", dbName);
				List<AttributeQueryRequest> aqrList = new ArrayList<AttributeQueryRequest>();
				AttributeQueryRequest dbNameARQ = dbAdapter.new AttributeQueryRequest("ReferenceDatabase", "name", " LIKE ", dbName);
				AttributeQueryRequest accessUrlARQ = dbAdapter.new AttributeQueryRequest("ReferenceDatabase", "accessUrl", " LIKE ", "%www.ensembl.org%");
				aqrList.add(dbNameARQ);
				aqrList.add(accessUrlARQ);
				Set<GKInstance> databases = (Set<GKInstance>) dbAdapter._fetchInstance(aqrList);
				if (databases.size() > 0)
				{
					logger.debug("Database {} exists ({} matches), now trying to find entities that reference it.", dbName, databases.size());
					List<GKInstance> refGeneProducts = new ArrayList<GKInstance>();
					for (GKInstance database : databases)
					{
						for (String name : ((List<String>) database.getAttributeValuesList(ReactomeJavaConstants.name)).stream()
											.filter(n -> !n.toUpperCase().equals("ENSEMBL")).collect(Collectors.toList()) )
						{
							logger.debug("Trying {}", name);
							List<GKInstance> results = objectCache.getByRefDb(String.valueOf(database.getDBID()) , "ReferenceGeneProduct");
							refGeneProducts.addAll(results);
							logger.debug("{} results found in cache", results.size());
							
						}
					}
					logger.debug("{} ReferenceGeneProducts found", refGeneProducts.size());
					
					// generate list of ENSP identifiers. This code would look prettier if getAttributeValue didn't throw Exception ;)
					Map<String, List<String>> refGeneProdsBySpecies = new HashMap<String, List<String>>();
					refGeneProducts.stream().forEach(instance -> {
						try
						{
							String species = String.valueOf(((GKInstance)instance.getAttributeValue(ReactomeJavaConstants.species)).getDBID());
							if (refGeneProdsBySpecies.get(species) == null)
							{
								refGeneProdsBySpecies.put(species, new ArrayList<String>( Arrays.asList((String)instance.getAttributeValue(ReactomeJavaConstants.identifier) ) ) );
							}
							else
							{
								refGeneProdsBySpecies.get(species).add((String)instance.getAttributeValue(ReactomeJavaConstants.identifier));
							}
						}
						catch (Exception e)
						{
							e.printStackTrace();
						}
						
					});
					
					// now, do batch look-ups by species. This will perform Protein-to-Transcript mappings.
					String baseFetchDestination = ensemblBatchLookup.getFetchDestination();
					for (String species : refGeneProdsBySpecies.keySet())
					{
						String speciesName = objectCache.getSpeciesNamesByID().get(species).get(0).replaceAll(" ", "_");
						
						ensemblBatchLookup.setFetchDestination(baseFetchDestination+"ENSP_batch_lookup."+species+".xml");
						ensemblBatchLookup.setSpecies(speciesName);
						ensemblBatchLookup.setIdentifiers(refGeneProdsBySpecies.get(species));
						ensemblBatchLookup.downloadData();
						
						EnsemblBatchLookupFileProcessor enspProcessor = new EnsemblBatchLookupFileProcessor();
						enspProcessor.setPath(Paths.get(baseFetchDestination+"ENSP_batch_lookup."+species+".xml"));
						Map<String, String> enspToEnstMap = enspProcessor.getIdMappingsFromFile();
						
						if (!enspToEnstMap.isEmpty())
						{
							ensemblBatchLookup.setFetchDestination(baseFetchDestination+"ENST_batch_lookup."+species+".xml");
							ensemblBatchLookup.setSpecies(speciesName);
							ensemblBatchLookup.setIdentifiers(new ArrayList<String>(enspToEnstMap.values()));
							ensemblBatchLookup.downloadData();
							
							enspProcessor.setPath(Paths.get(baseFetchDestination+"ENST_batch_lookup."+species+".xml"));
							Map<String, String> enstToEnsgMap = enspProcessor.getIdMappingsFromFile();
	
							if (!enstToEnsgMap.isEmpty())
							{
								// Ok, now we have RefGeneProd for ENSEMBL_%_PROTEIN. Now we can map these identifiers to some external database.
								for (String ensemblRetrieverName : ensemblFileRetrievers.keySet())
								{
									EnsemblFileRetriever retriever = ensemblFileRetrievers.get(ensemblRetrieverName);
									retriever.setFetchDestination(retriever.getFetchDestination().replaceAll(".[0-9]*.xml", "." + species + ".xml"));
									retriever.setSpecies(speciesName);
									retriever.setIdentifiers(new ArrayList<String>(enstToEnsgMap.values()));
									retriever.downloadData();
								}
							}
							else
							{
								logger.debug("ENST to ENSG mapping is empty. No identifiers to do xref lookup for species {}/{}", species, speciesName);
							}
						}
						else
						{
							logger.debug("ENSP to ENST mapping returned no results for species {}/{}", species, speciesName);
						}
					}

				}
			}
		}
	}

	private void executeUniprotFileRetrievers()
	{
		//Now download mapping data from Uniprot.
		for (String key : this.uniprotFileRetrievers.keySet().stream().sequential().filter(p -> this.fileRetrieverFilter.contains(p)).collect(Collectors.toList()))
		//uniprotFileRetrievers.keySet().stream().filter(p -> retrieversToExecute.contains(p)).parallel().forEach(key -> 
		{
			logger.info("Executing Downloader: {}", key);
			UniprotFileRetreiver retriever = this.uniprotFileRetrievers.get(key);
			
			UniprotDB toDb = UniprotDB.uniprotDBFromUniprotName(retriever.getMapToDb());
			UniprotDB fromDb = UniprotDB.uniprotDBFromUniprotName(retriever.getMapFromDb());
			//String toDb = retriever.getMapToDb();
			String originalFileDestinationName = retriever.getFetchDestination();
			
			List<String> refDbIds;
			//ENSEMBL Protein is special because the lookup DB ID is "ENSEMBL_PRO_ID", but in the Reactome database, it is "ENSEMBL_<species name>_PROTEIN".
			if (fromDb == UniprotDB.ENSEMBLProtein)
			{
				refDbIds = Collections.unmodifiableList(objectCache.getRefDbNamesToIds().keySet().stream().filter(p -> p.startsWith("ENSEMBL") && p.endsWith("PROTEIN")).collect(Collectors.toList()));
			}
			else if (fromDb == UniprotDB.ENSEMBLGene)
			{
				refDbIds = Collections.unmodifiableList(objectCache.getRefDbNamesToIds().keySet().stream().filter(p -> p.startsWith("ENSEMBL") && p.endsWith("GENE")).collect(Collectors.toList()));
			}
			else if (fromDb == UniprotDB.ENSEMBLTranscript)
			{
				refDbIds = Collections.unmodifiableList(objectCache.getRefDbNamesToIds().keySet().stream().filter(p -> p.startsWith("ENSEMBL") && p.endsWith("TRANSCRIPT")).collect(Collectors.toList()));
			}
			else
			{
				refDbIds = Collections.unmodifiableList(objectCache.getRefDbNamesToIds().get(fromDb.toString() ));
			}
			
			if (refDbIds != null && refDbIds.size() > 0 )
			{
				AtomicInteger uniprotRequestcounter = new AtomicInteger(0);
				
				logger.info("Number of Reference Database IDs to process: {}",refDbIds.size());
				for (String refDb : refDbIds)
				{
					//Set<String> speciesList = ReferenceObjectCache.getInstance().getListOfSpecies();
					List<String> speciesList = new ArrayList<String>( objectCache.getSpeciesNamesByID().keySet() );
					//for (String speciesId : speciesList)
					logger.debug("Degree of parallelism in the Common Pool: {}", ForkJoinPool.getCommonPoolParallelism());
					// TODO: Parameterise this in the config file, call it "uniprotDownloaderNumThreads". If not set, then just fall back to default parallelism. 
					int numRequestedThreads = 10;
					ForkJoinPool pool = new ForkJoinPool(numRequestedThreads);
					int stepSize = pool.getParallelism();
					logger.debug("Degree of parallelism in the pool: {}", stepSize);
					
					
					for (int i = 0; i < speciesList.size(); i+= stepSize)
					{
						List<Callable<Boolean>> tasks = new ArrayList<Callable<Boolean>>();
						
						//do n tasks at a time, where n is the degree of parallelism.
						for (int j = 0; j < stepSize; j++)
						{
							int speciesIndex = uniprotRequestcounter.getAndIncrement();
							if (speciesIndex < speciesList.size())
							{
								String speciesId = speciesList.get(speciesIndex);
								List<GKInstance> refGenes = objectCache.getByRefDbAndSpecies(refDb,speciesId,ReactomeJavaConstants.ReferenceGeneProduct);
								
								String speciesName = objectCache.getSpeciesNamesByID().get(speciesId).get(0);
								
								Callable<Boolean> task = new Callable<Boolean>()
								{
									@Override
									public Boolean call()
									{
										//if (speciesIndex < speciesList.size())
//										{
											if (refGenes != null && refGenes.size() > 0)
											{
												
												logger.info("Number of identifiers that we will attempt to map from UniProt to {} (db_id: {}, species: {}/{} ) is: {}",toDb.toString(),refDb, speciesId, speciesName, refGenes.size());
												String identifiersList = refGenes.stream().map(refGeneProduct -> {
													try
													{
														return (String)(refGeneProduct.getAttributeValue(ReactomeJavaConstants.identifier));
													}
													catch (InvalidAttributeException e1)
													{
														e1.printStackTrace();
														throw new RuntimeException(e1);
													} catch (Exception e1)
													{
														e1.printStackTrace();
														throw new RuntimeException(e1);
													}
												}).collect(Collectors.joining("\n"));
												
												BufferedInputStream inStream = new BufferedInputStream(new ByteArrayInputStream(identifiersList.getBytes()));
												// if we want to execute multiple retrievers in parallel, we need to create a 
												// NEW retriever and pass in the relevant values from the retriever that came from the original Uniprot file retriever
												// defined in the spring config file.
												UniprotFileRetreiver innerRetriever = new UniprotFileRetreiver();
												innerRetriever.setMapFromDb(retriever.getMapFromDb());
												innerRetriever.setMapToDb(retriever.getMapToDb());
												innerRetriever.setDataURL(retriever.getDataURL());
												innerRetriever.setMaxAge(retriever.getMaxAge());
												//Inject the refdb in, for cases where there are multiple ref db IDs mapping to the same name.
												innerRetriever.setFetchDestination(originalFileDestinationName.replace(".txt","." + speciesId + "." + refDb + ".txt"));
												innerRetriever.setDataInputStream(inStream);
												try
												{
													innerRetriever.fetchData();
													return true;
												}
												catch (Exception e)
												{
													logger.error("Error getting data for speciesId {}: {}", speciesId, e.getMessage());
													e.printStackTrace();
												}
											}
											else
											{
												logger.info("Could not find any RefefenceGeneProducts for reference database ID {} for species {}/{}", refDb, speciesId, speciesName);
											}
//										}
	
										return false;
									}
								};
								tasks.add(task);
							}
						}
						
						
						pool.invokeAll(tasks);
						try
						{
							Duration sleepDelay = Duration.ofSeconds(5);
							logger.info("Sleeping for {} to be nice. We don't want to flood their service!", sleepDelay);
							Thread.sleep(sleepDelay.toMillis());
						}
						catch (InterruptedException e)
						{
							e.printStackTrace();
						}
						catch (Exception e)
						{
							e.printStackTrace();
						}
					}
				}
			}
			else
			{
				logger.info("Could not find Reference Database IDs for reference database named: {}",toDb.toString());
			}
//		});
		}
	}

	public void setObjectCache(ReferenceObjectCache objectCache)
	{
		this.objectCache = objectCache;
	}

	public void setFileProcessorFilter(List<String> fileProcessorFilter)
	{
		this.fileProcessorFilter = fileProcessorFilter;
	}

	public void setFileRetrieverFilter(List<String> fileRetrieverFilter)
	{
		this.fileRetrieverFilter = fileRetrieverFilter;
	}

	public void setUniprotFileRetrievers(Map<String, UniprotFileRetreiver> uniprotFileRetrievers)
	{
		this.uniprotFileRetrievers = uniprotFileRetrievers;
	}

	public void setEnsemblFileRetrievers(Map<String, EnsemblFileRetriever> ensemblFileRetrievers)
	{
		this.ensemblFileRetrievers = ensemblFileRetrievers;
	}

	public void setFileProcessors(Map<String, FileProcessor> fileProcessors)
	{
		this.fileProcessors = fileProcessors;
	}

	public void setFileRetrievers(Map<String, FileRetriever> fileRetrievers)
	{
		this.fileRetrievers = fileRetrievers;
	}

	public void setReferenceDatabasesToCreate(Map<String, Map<String, ?>> referenceDatabasesToCreate)
	{
		this.referenceDatabasesToCreate = referenceDatabasesToCreate;
	}

	public void setDbAdapter(MySQLAdaptor dbAdapter)
	{
		this.dbAdapter = dbAdapter;
	}

	public void setEnsemblBatchLookup(EnsemblBatchLookup ensemblBatchLookup)
	{
		this.ensemblBatchLookup = ensemblBatchLookup;
	}
}

