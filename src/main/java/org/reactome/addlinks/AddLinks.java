package org.reactome.addlinks;

import static org.hamcrest.CoreMatchers.instanceOf;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
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
import org.gk.schema.InvalidAttributeException;
import org.reactome.addlinks.dataretrieval.FileRetriever;
import org.reactome.addlinks.dataretrieval.UniprotFileRetreiver;
import org.reactome.addlinks.dataretrieval.UniprotFileRetreiver.UniprotDB;
import org.reactome.addlinks.dataretrieval.ensembl.EnsemblFileRetriever;
import org.reactome.addlinks.dataretrieval.ensembl.EnsemblFileRetriever.EnsemblDB;
import org.reactome.addlinks.db.ReferenceDatabaseCreator;
import org.reactome.addlinks.db.ReferenceObjectCache;
import org.reactome.addlinks.fileprocessors.FileProcessor;
import org.springframework.beans.factory.annotation.Autowired;

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
		
		executeCreateReferenceDatabases();
		
		//executeSimpleFileRetrievers();
		//executeUniprotFileRetrievers();
		//executeEnsemblFileRetrievers();
		
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
		for (String key : ensemblFileRetrievers.keySet().stream().filter(p -> fileRetrieverFilter.contains(p)).collect(Collectors.toList()))
		{
			logger.info("Executing Downloader: {}", key);
			EnsemblFileRetriever retriever = ensemblFileRetrievers.get(key);
			
			EnsemblDB toDb = EnsemblDB.ensemblDBFromEnsemblName(retriever.getMapToDb());
			EnsemblDB fromDb = EnsemblDB.ensemblDBFromEnsemblName(retriever.getMapFromDb());
			String originalFileDestinationName = retriever.getFetchDestination();
			List<String> refDbIds = new ArrayList<String>();
			//ENSEMBL Protein is special because the lookup DB ID is "ENSEMBL_PRO_ID", but in the Reactome database, it is "ENSEMBL_<species name>_PROTEIN".
			if (fromDb == EnsemblDB.ENSEMBLProtein)
			{
				refDbIds = objectCache.getRefDbNamesToIds().keySet().stream().filter(p -> p.startsWith("ENSEMBL") && p.endsWith("PROTEIN")).collect(Collectors.toList());
			}
			else if (fromDb == EnsemblDB.ENSEMBLGene)
			{
				refDbIds = objectCache.getRefDbNamesToIds().keySet().stream().filter(p -> p.startsWith("ENSEMBL") && p.endsWith("GENE")).collect(Collectors.toList());
			}
			else if (fromDb == EnsemblDB.ENSEMBLTranscript)
			{
				refDbIds = objectCache.getRefDbNamesToIds().keySet().stream().filter(p -> p.startsWith("ENSEMBL") && p.endsWith("TRANSCRIPT")).collect(Collectors.toList());
			}
			else
			{
				refDbIds = objectCache.getRefDbNamesToIds().get(fromDb.toString() );
			}
			if (refDbIds != null && refDbIds.size() > 0 )
			{
				logger.info("Number of Reference Database IDs to process: {}",refDbIds.size());
				for (String refDb : refDbIds)
				{
					Set<String> speciesList = objectCache.getListOfSpeciesNames();
					for (String speciesId : speciesList)
					{
						logger.info("Number of species IDs to process: {}", speciesList.size() );
						
						List<String> possibleSpecies = objectCache.getSpeciesNamesByID().get(speciesId);
						
						if (possibleSpecies.size() > 1)
						{
							logger.info("Trying to do a mapping with species ID {} but there are {} multiple names associated with id: {}. I'm just going to use the first one in the list: {}", speciesId, possibleSpecies.size(), possibleSpecies, possibleSpecies.get(0));
						}
						
						String speciesName = possibleSpecies.get(0).replace(" ", "_");
						retriever.setSpecies(speciesName);
						
						List<GKInstance> refGenes = objectCache.getByRefDbAndSpecies(refDb,speciesId,ReactomeJavaConstants.ReferenceGeneProduct);
						
						if (refGenes != null && refGenes.size() > 0)
						{
							logger.info("Number of identifiers that we will attempt to map TO {} FROM db_id: {}/{} (species: {}/{} ) is: {}", toDb.toString(), refDb,fromDb.toString() , speciesId, speciesName, refGenes.size());
							List<String> identifiersList = refGenes.stream().map(refGeneProduct -> {
								try
								{
									return (String)(refGeneProduct.getAttributeValue(ReactomeJavaConstants.identifier));
								}
								catch (InvalidAttributeException e)
								{
									e.printStackTrace();
									throw new RuntimeException(e);
								}
								catch (Exception e)
								{
									e.printStackTrace();
									throw new RuntimeException(e);
								}
							}).collect(Collectors.toList());
							//Inject the refdb in, for cases where there are multiple ref db IDs mapping to the same name.
							
							retriever.setFetchDestination(originalFileDestinationName.replace(".txt","." + speciesId + "." + refDb + ".txt"));
							retriever.setIdentifiers(identifiersList);
							retriever.fetchData();
						}
						else
						{
							logger.info("Could not find any RefefenceGeneProducts for reference database ID {}/{} for species {}/{}", refDb, fromDb.toString(), speciesId, speciesName);
						}
					}
				}
			}
			else
			{
				logger.info("Could not find Reference Database IDs for reference database named: {}",toDb.toString());
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

}

