package org.reactome.addlinks.uniprot;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.schema.InvalidAttributeException;
import org.reactome.addlinks.CustomLoggable;
import org.reactome.addlinks.dataretrieval.UniprotFileRetreiver;
import org.reactome.addlinks.dataretrieval.UniprotFileRetreiver.UniprotDB;
import org.reactome.addlinks.db.ReferenceObjectCache;

public class UniProtFileRetreiverExecutor implements CustomLoggable
{
	private Logger logger;// = LogManager.getLogger();
	private Map<String, UniprotFileRetreiver> uniprotFileRetrievers;
	private List<String> fileRetrieverFilter;
	private ReferenceObjectCache objectCache;
	private int numberOfUniprotDownloadThreads = 10;
	
	public UniProtFileRetreiverExecutor()
	{
		this.logger = this.createLogger("retrievers/Uniprot", "RollingRandomAccessFile", this.getClass().getName(), true, Level.DEBUG, this.logger, "UniProtFileRetreiverExecutor");
	}
	
	public void execute()
	{
		//Now download mapping data from Uniprot.
		for (String key : this.uniprotFileRetrievers.keySet().stream().sequential().filter(p -> this.fileRetrieverFilter.contains(p)).collect(Collectors.toList()))
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
					List<String> speciesList = new ArrayList<String>( objectCache.getSpeciesNamesByID().keySet() );
					logger.debug("Degree of parallelism in the Common Pool: {}", ForkJoinPool.getCommonPoolParallelism());
					int numRequestedThreads = numberOfUniprotDownloadThreads;
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
											UniprotFileRetreiver innerRetriever = new UniprotFileRetreiver(retriever.getRetrieverName());
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
										return false;
									}
								};
								tasks.add(task);
							}
						}
						// now that the pool is full of jobs, run them!
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
					// try to get rid of any lingering threads that haven't been cleaned up yet.
					pool.shutdown();
				}
			}
			else
			{
				logger.info("Could not find Reference Database IDs for reference database named: {}",toDb.toString());
			}
		}
	}

	public void setUniprotFileRetrievers(Map<String, UniprotFileRetreiver> uniprotFileRetrievers)
	{
		this.uniprotFileRetrievers = uniprotFileRetrievers;
	}

	public void setFileRetrieverFilter(List<String> fileRetrieverFilter)
	{
		this.fileRetrieverFilter = fileRetrieverFilter;
	}

	public void setObjectCache(ReferenceObjectCache objectCache)
	{
		this.objectCache = objectCache;
	}

	public void setNumberOfUniprotDownloadThreads(int numberOfUniprotDownloadThreads)
	{
		this.numberOfUniprotDownloadThreads = numberOfUniprotDownloadThreads;
	}
}
