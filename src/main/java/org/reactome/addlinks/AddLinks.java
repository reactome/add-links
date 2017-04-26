package org.reactome.addlinks;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.RemoteException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.xml.rpc.ServiceException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.InvalidAttributeException;
import org.gk.schema.SchemaAttribute;
import org.reactome.addlinks.dataretrieval.BRENDAFileRetriever;
import org.reactome.addlinks.dataretrieval.BRENDAFileRetriever.BRENDASoapClient;
import org.reactome.addlinks.dataretrieval.FileRetriever;
import org.reactome.addlinks.dataretrieval.KEGGFileRetriever;
import org.reactome.addlinks.dataretrieval.UniprotFileRetreiver;
import org.reactome.addlinks.dataretrieval.ensembl.EnsemblBatchLookup;
import org.reactome.addlinks.dataretrieval.ensembl.EnsemblFileRetriever;
import org.reactome.addlinks.db.CrossReferenceReporter;
import org.reactome.addlinks.db.ReferenceDatabaseCreator;
import org.reactome.addlinks.db.ReferenceObjectCache;
import org.reactome.addlinks.ensembl.EnsemblFileRetrieverExecutor;
import org.reactome.addlinks.ensembl.EnsemblReferenceDatabaseGenerator;
import org.reactome.addlinks.fileprocessors.FileProcessor;
import org.reactome.addlinks.fileprocessors.ensembl.EnsemblAggregateFileProcessor;
import org.reactome.addlinks.fileprocessors.ensembl.EnsemblAggregateFileProcessor.EnsemblAggregateProcessingMode;
import org.reactome.addlinks.fileprocessors.ensembl.EnsemblFileAggregator;
import org.reactome.addlinks.kegg.KEGGReferenceDatabaseGenerator;
import org.reactome.addlinks.kegg.KEGGSpeciesCache;
import org.reactome.addlinks.referencecreators.BatchReferenceCreator;
import org.reactome.addlinks.referencecreators.ENSMappedIdentifiersReferenceCreator;
import org.reactome.addlinks.referencecreators.IntActReferenceCreator;
import org.reactome.addlinks.referencecreators.OneToOneReferenceCreator;
import org.reactome.addlinks.referencecreators.RHEAReferenceCreator;
import org.reactome.addlinks.referencecreators.UPMappedIdentifiersReferenceCreator;
import org.reactome.addlinks.uniprot.UniProtFileRetreiverExecutor;


public class AddLinks
{
	private static final Logger logger = LogManager.getLogger();
	
	private ReferenceObjectCache objectCache;
	
	private List<String> fileProcessorFilter;
	
	private List<String> fileRetrieverFilter;
	
	private List<String> referenceCreatorFilter;
	
	private Map<String, UniprotFileRetreiver> uniprotFileRetrievers;
	
	private Map<String, EnsemblFileRetriever> ensemblFileRetrievers;
	
	private Map<String, EnsemblFileRetriever> ensemblFileRetrieversNonCore;
	
	private Map<String, FileProcessor<?>> fileProcessors;
	
	private Map<String,FileRetriever> fileRetrievers;
	
	private Map<String, Map<String, ?>> referenceDatabasesToCreate;
	
	private Map<String, Object> processorCreatorLink;
	
	private Map<String, UPMappedIdentifiersReferenceCreator> uniprotReferenceCreators;
	
	private Map<String, BatchReferenceCreator<?>> referenceCreators;
	
	private EnsemblBatchLookup ensemblBatchLookup;
	
	private MySQLAdaptor dbAdapter;

	@SuppressWarnings("unchecked")
	public void doAddLinks() throws Exception
	{
		// The objectCache gets initialized the first time it is referenced, that will happen when Spring tries to instantiate it from the spring config file.
		if (objectCache == null)
		{
			throw new Error("ObjectCache cannot be null.");
		}
		
		Properties applicationProps = new Properties();
		String propertiesLocation = System.getProperty("config.location");
		
		applicationProps.load(new FileInputStream(propertiesLocation));
		
		long personID = Long.valueOf(applicationProps.getProperty("executeAsPersonID"));
		int numUniprotDownloadThreads = Integer.valueOf(applicationProps.getProperty("numberOfUniprotDownloadThreads"));

		boolean filterRetrievers = applicationProps.containsKey("filterFileRetrievers") && applicationProps.getProperty("filterFileRetrievers") != null
									? Boolean.valueOf(applicationProps.getProperty("filterFileRetrievers"))
									: false;
		if (filterRetrievers)
		{
			//fileRetrieverFilter = context.getBean("fileRetrieverFilter",List.class);
			logger.info("Only the specified FileRetrievers will be executed: {}",fileRetrieverFilter);
		}
		// Start by creating ReferenceDatabase objects that we might need later.
		this.executeCreateReferenceDatabases();
		// Now that we've *created* new ref dbs, rebuild any caches that might have dependended on them.
		ReferenceObjectCache.clearAndRebuildAllCaches();
		logger.info("Counts of references to external databases currently in the database ({}), BEFORE running AddLinks", this.dbAdapter.getConnection().getCatalog());
		CrossReferenceReporter reporter = new CrossReferenceReporter(this.dbAdapter);
		Map<String, Map<String,Integer>> preAddLinksReport = reporter.createReportMap();
		logger.info("\n"+(reporter.printReport(preAddLinksReport)));

		ExecutorService execSrvc = Executors.newFixedThreadPool(5);
		
		// We can execute SimpleFileRetrievers at the same time as the UniProt retrievers, and also the ENSEMBL retrievers.
		List<Callable<Boolean>> retrieverJobs = new ArrayList<Callable<Boolean>>();
		// Execute the file retrievers.
		retrieverJobs.add(new Callable<Boolean>()
		{
			@Override
			public Boolean call() throws Exception
			{
				executeSimpleFileRetrievers();
				return true;
			}
		});
		// Execute the UniProt file retrievers separately.
		retrieverJobs.add(new Callable<Boolean>()
		{
			
			@Override
			public Boolean call() throws Exception
			{
				executeUniprotFileRetrievers(numUniprotDownloadThreads);
				return true;
			}
		});
		
		// Check to see if we should do any Ensembl work
		if (fileRetrieverFilter.contains("EnsemblToALL"))
		{
			retrieverJobs.add(new Callable<Boolean>()
			{
				
				@Override
				public Boolean call() throws Exception
				{	
					executeEnsemblFileRetriever();
					return true;
				}
			});
		}
		execSrvc.invokeAll(retrieverJobs);
		
		retrieverJobs = new ArrayList<Callable<Boolean>>();
		// Now that uniprot file retrievers have run, we can run the KEGG file retriever.
		retrieverJobs.add(new Callable<Boolean>()
		{
			
			@Override
			public Boolean call() throws Exception
			{
				executeKeggFileRetriever();
				return true;
			}
		});
		// Run the Brenda file retriever - it is slow and KEGG is slow, so let's run them together!
		retrieverJobs.add(new Callable<Boolean>()
		{
			
			@Override
			public Boolean call() throws Exception
			{
				executeBrendaFileRetriever();
				return true;
			}
		});
		execSrvc.invokeAll(retrieverJobs);
		
		logger.info("Finished downloading files.");
		execSrvc.shutdown();
		logger.info("Now processing the files...");
		
		// TODO: Link the file processors to the file retrievers so that if
		// any are filtered, only the appropriate processors will execute. Maybe?
		Map<String, Map<String, ?>> dbMappings = executeFileProcessors();

		// Special extra work for ENSEMBL...
		if (this.fileProcessorFilter.contains("ENSEMBLFileProcessor") || this.fileProcessorFilter.contains("ENSEMBLNonCoreFileProcessor"))
		{
			this.processENSEMBLFiles(dbMappings);
		}
		
		// Print stats on results of file processing.
		logger.info("{} keys in mapping object.", dbMappings.keySet().size());
		
		for (String k : dbMappings.keySet().stream().sorted().collect(Collectors.toList()))
		{
			logger.info("DB Key: {} has {} submaps.", k, dbMappings.get(k).keySet().size());
			for (String subk : dbMappings.get(k).keySet())
			{
				if (dbMappings.get(k).get(subk) instanceof Map && !k.equals("HmdbMetabolitesFileProcessor")) // No need to print every single HMDB Metabolites ID.
				{
					logger.info("    subkey: {} has {} subkeys", subk, ((Map<String, ?>)dbMappings.get(k).get(subk)).keySet().size() );
				}
				
			}
		}
		//Before each set of IDs is updated in the database, maybe take a database backup?
		
		//Now we create references.
		this.createReferences(personID, dbMappings);

		logger.info("Counts of references to external databases currently in the database ({}), AFTER running AddLinks", this.dbAdapter.getConnection().getCatalog());
		//reporter.printReport();
		Map<String, Map<String,Integer>> postAddLinksReport = reporter.createReportMap();
		logger.info("\n"+reporter.printReport(postAddLinksReport));
		
		logger.info("Differences");
		
		logger.info("\n"+reporter.printReportWithDiffs(preAddLinksReport, postAddLinksReport));
		
		logger.info("Purging unused ReferenceDatabse objects.");
		
		this.purgeUnusedRefDBs();
		
		logger.info("Process complete.");
	}

	@SuppressWarnings("unchecked")
	private void purgeUnusedRefDBs()
	{
		try
		{
			@SuppressWarnings("unchecked")
			Collection<GKInstance> refDBs = (Collection<GKInstance>) this.dbAdapter.fetchInstancesByClass(ReactomeJavaConstants.ReferenceDatabase);
			for (GKInstance refDB : refDBs)
			{
				this.dbAdapter.loadInstanceAttributeValues(refDB);
				@SuppressWarnings("unchecked")
				List<String> names = (List<String>) refDB.getAttributeValuesList(ReactomeJavaConstants.name);
				@SuppressWarnings("unchecked")
				Collection<GKInstance> refMap = new ArrayList<GKInstance> ();
				refMap = (Collection<GKInstance>) refDB.getReferers(ReactomeJavaConstants.referenceDatabase);
//				refMap.putAll((Map<? extends SchemaAttribute, ? extends Collection<GKInstance>>) refDB.getReferers(ReactomeJavaConstants.crossReference));
//				refMap.putAll((Map<? extends SchemaAttribute, ? extends Collection<GKInstance>>) refDB.getReferers(ReactomeJavaConstants.refer));
				
				int refCount = 0;
				if (refMap != null)
				{
					refCount = refMap.size();
				}
				logger.info("ReferenceDatabase: {} ({}); # referrers: {}", refDB.getDBID(), names.toString(), refCount);
				if (refCount == 0)
				{
					logger.info("NOTHING refers to ReferenceDatabase DB ID {} ({}) so it will now be deleted.", refDB.getDBID(), names.toString());
					this.dbAdapter.deleteByDBID(refDB.getDBID());
				}
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
			throw new Error(e);
		}
		
	}

	private void executeEnsemblFileRetriever() throws Exception
	{
		if (fileRetrieverFilter.contains("EnsemblToALL"))
		{
			logger.info("Executing ENSEMBL file retrievers");
			EnsemblFileRetrieverExecutor ensemblFileRetrieverExecutor = new EnsemblFileRetrieverExecutor();
			ensemblFileRetrieverExecutor.setEnsemblBatchLookup(ensemblBatchLookup);
			ensemblFileRetrieverExecutor.setEnsemblFileRetrievers(ensemblFileRetrievers);
			ensemblFileRetrieverExecutor.setEnsemblFileRetrieversNonCore(ensemblFileRetrieversNonCore);
			ensemblFileRetrieverExecutor.setObjectCache(objectCache);
			ensemblFileRetrieverExecutor.setDbAdapter(dbAdapter);
			ensemblFileRetrieverExecutor.execute();
		}
	}
	
	private void executeBrendaFileRetriever()
	{
		if (this.fileRetrieverFilter.contains("BrendaRetriever"))
		{
			logger.info("Executing BRENDA file retrievers");
			BRENDAFileRetriever brendaRetriever = (BRENDAFileRetriever) this.fileRetrievers.get("BrendaRetriever");
			BRENDASoapClient client = brendaRetriever.new BRENDASoapClient(brendaRetriever.getUserName(), brendaRetriever.getPassword());
			
			// TODO: Maybe move this out to a BRENDASpeciesCache class. 
			String speciesResult;
			try
			{
				speciesResult = client.callBrendaService(brendaRetriever.getDataURL().toString(), "getOrganismsFromOrganism", "");
			}
			catch (MalformedURLException | NoSuchAlgorithmException | RemoteException |ServiceException e)
			{
				logger.error("Exception caught while trying to get BRENDA species list: {}",e.getMessage());
				e.printStackTrace();
				throw new Error(e);
			}
			//Normalize the list.
			List<String> brendaSpecies = Arrays.asList(speciesResult.split("!")).stream().map(species -> species.replace("'", "").replaceAll("\"", "").trim().toUpperCase() ).collect(Collectors.toList());
			logger.debug(brendaSpecies.size() + " species known to BRENDA");

			List<String> identifiers = new ArrayList<String>();
			String originalDestination = brendaRetriever.getFetchDestination();
			for (String speciesName : objectCache.getListOfSpeciesNames().stream().sorted().collect(Collectors.toList() ) )
			{
				String speciesId = objectCache.getSpeciesNamesToIds().get(speciesName).get(0);
				if (brendaSpecies.contains(speciesName.trim().toUpperCase()))
				{
					List<String> uniprotIdentifiers = objectCache.getByRefDbAndSpecies("2", speciesId, ReactomeJavaConstants.ReferenceGeneProduct).stream().map(instance -> {
						try
						{
							return (String)instance.getAttributeValue(ReactomeJavaConstants.identifier);
						}
						catch (InvalidAttributeException e)
						{
							e.printStackTrace();
						}
						catch (Exception e)
						{
							e.printStackTrace();
						}
						return null;
					}).collect(Collectors.toList());
					
					logger.debug("Species: "+speciesId+"/"+speciesName);
					identifiers.addAll(uniprotIdentifiers);
					
					if (uniprotIdentifiers != null && uniprotIdentifiers.size() > 0)
					{
						brendaRetriever.setSpeciesName(speciesName);
						brendaRetriever.setIdentifiers(uniprotIdentifiers);
						brendaRetriever.setFetchDestination(originalDestination.replace(".csv","."+speciesName.replace(" ", "_")+".csv"));
						try
						{
							brendaRetriever.fetchData();
						} catch (Exception e)
						{
							logger.error("Error occurred while trying to fetch Brenda data: {}. File may not have been downloaded.", e.getMessage());
							e.printStackTrace();
						}
					}
					else
					{
						logger.debug("No uniprot identifiers for " + speciesName);
					}
				}
				else
				{
					logger.debug("Species " + speciesName + " is not in the list of species known to BRENDA.");
				}
			}
		}
		else
		{
			logger.info("Skipping BrendaRetriever");
		}
	}
	
	private void executeKeggFileRetriever()
	{
		if (this.fileRetrieverFilter.contains("KEGGRetriever"))
		{
			logger.info("Executing KEGG retriever");
			UniprotFileRetreiver uniprotToKeggRetriever = this.uniprotFileRetrievers.get("UniProtToKEGG");
			KEGGFileRetriever keggFileRetriever = (KEGGFileRetriever) this.fileRetrievers.get("KEGGRetriever");
			
			// Now we need to loop through the species.
			String downloadDestination = keggFileRetriever.getFetchDestination();

			List<Callable<Boolean>> keggJobs = new ArrayList<Callable<Boolean>>();
			
			for (String speciesName : objectCache.getListOfSpeciesNames().stream().sequential()
												.filter(speciesName -> KEGGSpeciesCache.getKEGGCode(speciesName)!=null)
												.collect(Collectors.toList()))
			{
				String speciesCode = objectCache.getSpeciesNamesToIds().get(speciesName).get(0);
				logger.debug("Species Name: {} Species Code: {}", speciesName, speciesCode);
				
				Predicate<String> isValidKEGGmappingFile = new Predicate<String>()
				{
					@Override
					public boolean test(String fileName)
					{
						try
						{
							return !fileName.contains(".notMapped")
									&& fileName.contains("KEGG")
									&& Files.exists(Paths.get(fileName))
									&& objectCache.getSpeciesNamesToIds().get(speciesName).stream().anyMatch(s -> fileName.contains(s))
									&& Files.lines(Paths.get(fileName)).count() > 1;
						}
						catch (IOException e1)
						{
							e1.printStackTrace();
							return false;
						}
					}
				};
				
				List<Path> uniProtToKeggFiles = uniprotToKeggRetriever.getActualFetchDestinations().stream()
																			.filter(fileName -> isValidKEGGmappingFile.test(fileName))
																			.map(fileName -> Paths.get(fileName))
																			.collect(Collectors.toList());
				// This could happen if the UniProt files were already downloaded. In that case, uniprotToKeggRetriever.getActualFetchDestinations() will return 
				// NULL because nothing was downloaded this time.
				if (uniProtToKeggFiles == null || uniProtToKeggFiles.isEmpty())
				{
					// Since the uniprotToKeggRetriever didn't download anything, maybe we can check in the directory and see if there are any other files there.
					String uniProtToKeggDestination = uniprotToKeggRetriever.getFetchDestination();
					
					try
					{
						// We'll try to search for everything in the uniprotToKeggRetriever's destination's directory.
						uniProtToKeggFiles = Files.list(Paths.get(uniProtToKeggDestination).getParent())
													.filter(path -> isValidKEGGmappingFile.test(path.toString()) )
													.collect(Collectors.toList());
					}
					catch (IOException e)
					{
						e.printStackTrace();
					}
					
				}

				if (uniProtToKeggFiles != null && uniProtToKeggFiles.size() > 0)
				{
					List<Path> files = uniProtToKeggFiles;
					Callable<Boolean> job = new Callable<Boolean>()
					{

						@Override
						public Boolean call() throws Exception
						{
							KEGGFileRetriever retriever = new KEGGFileRetriever(keggFileRetriever.getRetrieverName());
							retriever.setAdapter(keggFileRetriever.getAdapter());
							retriever.setDataURL(keggFileRetriever.getDataURL());
							retriever.setUniprotToKEGGFiles(files);
							retriever.setMaxAge(keggFileRetriever.getMaxAge());
							// the ".2" is for the ReferenceDatabase - in this case it is UniProt whose DB_ID is 2.
							retriever.setFetchDestination(downloadDestination.replaceAll(".txt", "." + speciesCode + ".2.txt"));
							try
							{
								retriever.fetchData();
							}
							catch (Exception e)
							{
								e.printStackTrace();
								throw new Error(e);
							}
							return true;
						}
					};
					keggJobs.add(job);

				}
				else
				{
					logger.info("Sorry, No uniprot-to-kegg mappings found for species {} / {}", speciesName, speciesCode);
				}
			}
			// These jobs are not very CPU intense so it is probably not too serious to them ALL in parallel.
			ForkJoinPool pool = new ForkJoinPool(keggJobs.size());
			pool.invokeAll(keggJobs);
		}
		else
		{
			logger.info("Skipping KEGGRetriever");
		}
	}

	/**
	 * Create references.
	 * @param personID - the ID of the Person entity which these new references will be attributed to.
	 * @param dbMappings - A mapping from source identifier to target identifier.
	 * @throws IOException
	 * @throws Exception
	 */
	@SuppressWarnings("unchecked")
	private void createReferences(long personID, Map<String, Map<String, ?>> dbMappings) throws IOException, Exception
	{
		for (String refCreatorName : this.referenceCreatorFilter)
		{
			logger.info("Executing reference creator: {}", refCreatorName);
			List<GKInstance> sourceReferences = new ArrayList<GKInstance>();
			// Try to get the processor name, except for E
			Optional<?> fileProcessorName = this.processorCreatorLink.keySet().stream().filter(k -> {
				if (this.processorCreatorLink.get(k) instanceof String)
				{
					return this.processorCreatorLink.get(k).equals(refCreatorName);
				}
				else if (this.processorCreatorLink.get(k) instanceof List)
				{
					List<String> sublist = ((List<String>)this.processorCreatorLink.get(k)); 
					return sublist.stream().filter( element -> element.equals(refCreatorName) ).findFirst().isPresent();
				}
				else // if not a list and not a string, something is wrong.
				{
					return false;
				}
					
			} ).map( m -> m ).findFirst();
			if (referenceCreators.containsKey(refCreatorName))
			{
				@SuppressWarnings("rawtypes")
				BatchReferenceCreator refCreator = referenceCreators.get(refCreatorName);
				if (refCreator instanceof ENSMappedIdentifiersReferenceCreator)
				{
					sourceReferences = getENSEMBLIdentifiersList();
					logger.debug("{} ENSEMBL source references", sourceReferences.size());
					// This is for ENSP -> ENSG mappings.
					if (refCreator.getSourceRefDB().equals(((ENSMappedIdentifiersReferenceCreator) refCreator).getTargetRefDB()))
					{
						for(String k : dbMappings.keySet().stream().filter(k -> k.startsWith("ENSEMBL_ENSP_2_ENSG_")).collect(Collectors.toList()))
						{
							logger.info("Ensembl cross-references: {}", k);
							Map<String, Map<String, List<String>>> mappings = (Map<String, Map<String, List<String>>>) dbMappings.get(k);
							((ENSMappedIdentifiersReferenceCreator)refCreator).createIdentifiers(personID, mappings, sourceReferences);
						}
					}
					else
					{
						// For ENSEBML, there are many dbmappings
						for(String k : dbMappings.keySet().stream().filter(k -> k.startsWith("ENSEMBL_XREF_")).collect(Collectors.toList()))
						{
							logger.info("Ensembl cross-references: {}", k);
							Map<String, Map<String, List<String>>> mappings = (Map<String, Map<String, List<String>>>) dbMappings.get(k);
							((ENSMappedIdentifiersReferenceCreator)refCreator).createIdentifiers(personID, mappings, sourceReferences);
						}
					}
				}
				else
				{
					// Rhea reference creator is special - its source references is a simple list of all Reactions.
					if (refCreator instanceof RHEAReferenceCreator)
					{
						sourceReferences = objectCache.getReactionsByID().values().stream().collect(Collectors.toList());
					}
					else if (refCreator instanceof IntActReferenceCreator)
					{
						// The IntActReferenceCreator does not *need* a list of source references since the mapping it gets is sufficient.
						sourceReferences = new ArrayList<GKInstance>();
					}
					else
					{
						sourceReferences = this.getIdentifiersList(refCreator.getSourceRefDB(), refCreator.getClassReferringToRefName());
					}
					logger.debug("{} source references", sourceReferences.size());
					if (refCreator instanceof OneToOneReferenceCreator)
					{
						// OneToOne Reference Creators do not take an input of mappings. They just create a 1:1 mapping from the source references.
						refCreator.createIdentifiers(personID, null, sourceReferences);
					}
					else
					{
						if (fileProcessorName.isPresent())
						{
							if (fileProcessorName.get() instanceof String)
							{
								refCreator.createIdentifiers(personID, (Map<String, ?>) dbMappings.get(fileProcessorName.get()), sourceReferences);
							}
							// For all the extra ZINC references, they all use the same file processor so it's a string-to-list mapping.
							else if (fileProcessorName.get() instanceof List<?>)
							{
								for (String fpName : (List<String>)fileProcessorName.get())
								{
									refCreator.createIdentifiers(personID, (Map<String, ?>) dbMappings.get(fpName), sourceReferences);
								}
							}
						}
						else
						{
							logger.warn("Reference Creator name \"{}\" could not be found it mapping between file processors and reference creators, so it will not be executed and references will not be created.", refCreatorName);
						}
					}
				}
				
			}
			// There is a separate list of reference creators to create UniProt references.
			else if (uniprotReferenceCreators.containsKey(refCreatorName))
			{
				UPMappedIdentifiersReferenceCreator refCreator = uniprotReferenceCreators.get(refCreatorName);
				sourceReferences = this.getIdentifiersList(refCreator.getSourceRefDB(), refCreator.getClassReferringToRefName());
				refCreator.createIdentifiers(personID, (Map<String, Map<String, List<String>>>) dbMappings.get(fileProcessorName.get()), sourceReferences);
			}
		}
	}

	/**
	 * Process ENSEMBL files. ENSEMBL files need special processing - you can't do it in a single step.
	 * This function will actually run EnsemblFileAggregators and EnsemblFileAggregatorProcessors.
	 * These two classes are used to produce an aggregate file containing ALL Ensembl mappings: each line will have the following identifiers:
	 *  - ENSP, ENST, ENSG.
	 *  Each line also contains the Name of an external database that the ENSG maps to, and the identifier value from that external database (or "null" if there was no mapping).
	 * @param dbMappings - this mapping will be updated by this function.
	 * @throws Exception
	 * @throws InvalidAttributeException
	 */
	private void processENSEMBLFiles(Map<String, Map<String, ?>> dbMappings) throws Exception, InvalidAttributeException
	{
		@SuppressWarnings("unchecked")
		Collection<GKInstance> enspDatabases = dbAdapter.fetchInstanceByAttribute(ReactomeJavaConstants.ReferenceDatabase, ReactomeJavaConstants.name, " LIKE ", "ENSEMBL%PROTEIN");
		Set<String> species = new HashSet<String>();
		for (GKInstance inst : enspDatabases)
		{	
			List<GKInstance> refGeneProds = objectCache.getByRefDb(inst.getDBID().toString(), "ReferenceGeneProduct");
			for (GKInstance refGeneProd : refGeneProds)
			{
				species.add(((GKInstance)refGeneProd.getAttributeValue(ReactomeJavaConstants.species)).getDBID().toString());
			}
		}
		
		for (String speciesID : species/*objectCache.getSpeciesNamesByID().keySet()*/)
		{
			List<String> dbNames = new ArrayList<String>(Arrays.asList("EntrezGene", "Wormbase")/*objectCache.getRefDbNamesToIds().keySet()*/);
			EnsemblFileAggregator ensemblAggregator = new EnsemblFileAggregator(speciesID, dbNames, "/tmp/addlinks-downloaded-files/ensembl/");
			ensemblAggregator.createAggregateFile();
			
			EnsemblAggregateFileProcessor aggregateProcessor = new EnsemblAggregateFileProcessor("file-processors/EnsemblAggregateFileProcessor");
			aggregateProcessor.setPath(Paths.get("/tmp/addlinks-downloaded-files/ensembl/"+ "ensembl_p2xref_mapping."+speciesID+".csv") );
			aggregateProcessor.setMode(EnsemblAggregateProcessingMode.XREF);
			Map<String, Map<String, List<String>>> xrefMapping = aggregateProcessor.getIdMappingsFromFile();
			dbMappings.put("ENSEMBL_XREF_"+speciesID, xrefMapping);
			
			aggregateProcessor.setMode(EnsemblAggregateProcessingMode.ENSP_TO_ENSG);
			Map<String, Map<String, List<String>>> ensp2EnsgMapping = aggregateProcessor.getIdMappingsFromFile();
			dbMappings.put("ENSEMBL_ENSP_2_ENSG_"+speciesID, ensp2EnsgMapping);
		}
	}

	/**
	 * This function will get a list of ENSEMBL identifiers. Each GKInstance will be a ReferenceGeneProduct from an ENSEMBL_*_PROTEIN database. 
	 * 
	 * @return
	 */
	private List<GKInstance> getENSEMBLIdentifiersList()
	{
		List<GKInstance> identifiers = new ArrayList<GKInstance>();
		
		List<String> ensemblDBNames = objectCache.getRefDbNamesToIds().keySet().stream().filter(k -> k.toUpperCase().startsWith("ENSEMBL") && k.toUpperCase().contains("PROTEIN")).collect(Collectors.toList());
		
		for (String dbName : ensemblDBNames)
		{
			for (String s : objectCache.getRefDbNamesToIds().get(dbName))
			{
				identifiers.addAll(objectCache.getByRefDb(s, "ReferenceGeneProduct"));
			}
		}
		
		return identifiers;
	}
	
	/**
	 * Gets a list of identifiers for a given reference database and type. All relevant instances will be returned, regardless of species.
	 * @param refDb - the reference database. 
	 * @param className - the type, such as ReferenceGeneProduct.
	 * @return
	 */
	private List<GKInstance> getIdentifiersList(String refDb, String className)
	{
		return this.getIdentifiersList(refDb, null, className);
	}
	
	/**
	 * Gets a list of identifiers for a given reference database, species, and type.
	 * @param refDb
	 * @param species
	 * @param className
	 * @return
	 */
	private List<GKInstance> getIdentifiersList(String refDb, String species, String className)
	{
		// Need a list of identifiers.
		if (objectCache.getRefDbNamesToIds().get(refDb) == null)
		{
			throw new Error("Could not find a reference database for name: " + refDb);
		}
		String refDBID = objectCache.getRefDbNamesToIds().get(refDb).get(0);
		List<GKInstance> identifiers;
		if (species!=null)
		{
			String speciesDBID = objectCache.getSpeciesNamesToIds().get(species).get(0);
			identifiers = objectCache.getByRefDbAndSpecies(refDBID, speciesDBID, className);
			logger.debug(refDb + " " + refDBID + " ; " + species + " " + speciesDBID);
		}
		else
		{
			identifiers = objectCache.getByRefDb(refDBID, className);
			logger.debug(refDb + " " + refDBID + " ; " );
		}
		
		return identifiers;
	}
	
	/**
	 * Create ReferenceDatabase objects, in case they done yet exist in this database. 
	 */
	@SuppressWarnings("unchecked")
	private void executeCreateReferenceDatabases()
	{
		ReferenceDatabaseCreator creator = new ReferenceDatabaseCreator(dbAdapter);
		for (String key : this.referenceDatabasesToCreate.keySet())
		{
			logger.info("Creating ReferenceDatabase {}", key);
			
			Map<String, ?> refDB = this.referenceDatabasesToCreate.get(key);
			String url = null, accessUrl = null;
			List<String> aliases = new ArrayList<String>();
			String primaryName = null;
			for(String attributeKey : refDB.keySet())
			{
				switch (attributeKey)
				{
					case "PrimaryName":
						if (refDB.get(attributeKey) instanceof String )
						{
							primaryName = (String) refDB.get(attributeKey);
						}
						else
						{
							logger.error("Found a \"Name\" of an invalid type: {}", refDB.get(attributeKey).getClass().getName() );
						}
						break;
					case "Aliases":
						if (refDB.get(attributeKey) instanceof List )
						{
							aliases.addAll((Collection<? extends String>) refDB.get(attributeKey));
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
				creator.createReferenceDatabaseWithAliases(url, accessUrl, primaryName, (String[]) aliases.toArray(new String[aliases.size()]) );
			}
			catch (Exception e)
			{
				logger.error("Error while trying to create ReferenceDatabase record: {}", e.getMessage());
				e.printStackTrace();
			}
		}
		EnsemblReferenceDatabaseGenerator.setDbCreator(creator);
		KEGGReferenceDatabaseGenerator.setDBCreator(creator);
		try
		{
			EnsemblReferenceDatabaseGenerator.generateSpeciesSpecificReferenceDatabases(objectCache);
			KEGGReferenceDatabaseGenerator.generateSpeciesSpecificReferenceDatabases(objectCache);
		}
		catch (Exception e)
		{
			logger.error("Error while creating ENSEMBL species-specific ReferenceDatabase objects: {}", e.getMessage());
			e.printStackTrace();
			throw new Error(e);
		}
	}

	/**
	 * Execute the file processors.
	 * @return Mappings, keyed by the *name* of the file processor. The values of this mapping are Map<String,?> - see the specific processor to know what it returns for "?".
	 */
	private Map<String, Map<String, ?>> executeFileProcessors()
	{
		Map<String,Map<String,?>> dbMappings = new HashMap<String, Map<String,?>>();
		logger.info("{} file processors to execute.", this.fileProcessorFilter.size());
		this.fileProcessors.keySet().stream().filter(k -> fileProcessorFilter.contains(k)).forEach( k -> 
			{
				logger.info("Executing file processor: {}", k);
				dbMappings.put(k, fileProcessors.get(k).getIdMappingsFromFile() );
			}
		);
		return dbMappings;
	}

	/**
	 * Execute file retrievers. Covers pretty much everything, except for ENSEMBL and UniProt retrievers. 
	 */
	private void executeSimpleFileRetrievers()
	{
		fileRetrievers.keySet().stream().parallel()
										.filter(k -> !k.equals("KEGGRetriever") && !k.equals("BrendaRetriever"))
										.forEach(k ->
		{
			// KEGGRetreiver is special: it depends on the result of the uniprotToKegg retriever as an input, so we can't execute it here.
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
					logger.warn("Exception caught while processing {}, message is: \"{}\". Will continue with next file retriever.",k,e.getMessage());
				}
			}
			else
			{
				logger.info("Skipping {}",k);
			}
		});
	}

	/**
	 * Execute the UniProt retrievers.
	 * @param numberOfUniprotDownloadThreads
	 */
	private void executeUniprotFileRetrievers(int numberOfUniprotDownloadThreads)
	{
		logger.info("Executing UniProt file retrievers");
		UniProtFileRetreiverExecutor executor = new UniProtFileRetreiverExecutor();
		executor.setFileRetrieverFilter(fileRetrieverFilter);
		executor.setObjectCache(objectCache);
		executor.setUniprotFileRetrievers(uniprotFileRetrievers);
		executor.setNumberOfUniprotDownloadThreads(numberOfUniprotDownloadThreads);
		executor.execute();
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
	
	public void setEnsemblFileRetrieversNonCore(Map<String, EnsemblFileRetriever> ensemblFileRetrievers)
	{
		this.ensemblFileRetrieversNonCore = ensemblFileRetrievers;
	}

	public void setFileProcessors(Map<String, FileProcessor<?>> fileProcessors)
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

	public void setProcessorCreatorLink(Map<String, Object> processorCreatorLink)
	{
		this.processorCreatorLink = processorCreatorLink;
	}

	public void setUniprotReferenceCreators(Map<String, UPMappedIdentifiersReferenceCreator> uniprotReferenceCreators)
	{
		this.uniprotReferenceCreators = uniprotReferenceCreators;
	}

	public void setReferenceCreators(Map<String, BatchReferenceCreator<?>> referenceCreators)
	{
		this.referenceCreators = referenceCreators;
	}

	public void setReferenceCreatorFilter(List<String> referenceCreatorFilter)
	{
		this.referenceCreatorFilter = referenceCreatorFilter;
	}
}
