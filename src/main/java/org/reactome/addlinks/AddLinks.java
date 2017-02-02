package org.reactome.addlinks;

import java.io.IOException;
import java.nio.file.Paths;
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
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.InvalidAttributeException;
import org.reactome.addlinks.dataretrieval.FileRetriever;
import org.reactome.addlinks.dataretrieval.UniprotFileRetreiver;
import org.reactome.addlinks.dataretrieval.ensembl.EnsemblBatchLookup;
import org.reactome.addlinks.dataretrieval.ensembl.EnsemblFileRetriever;
import org.reactome.addlinks.db.ReferenceDatabaseCreator;
import org.reactome.addlinks.db.ReferenceObjectCache;
import org.reactome.addlinks.ensembl.EnsemblFileRetrieverExecutor;
import org.reactome.addlinks.fileprocessors.FileProcessor;
import org.reactome.addlinks.fileprocessors.ensembl.EnsemblAggregateFileProcessor;
import org.reactome.addlinks.fileprocessors.ensembl.EnsemblAggregateFileProcessor.EnsemblAggregateProcessingMode;
import org.reactome.addlinks.fileprocessors.ensembl.EnsemblFileAggregator;
import org.reactome.addlinks.referencecreators.BatchReferenceCreator;
import org.reactome.addlinks.referencecreators.ENSMappedIdentifiersReferenceCreator;
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
	
	private Map<String, FileProcessor> fileProcessors;
	
	private Map<String,FileRetriever> fileRetrievers;
	
	private Map<String, Map<String, ?>> referenceDatabasesToCreate;
	
	private Map<String, String> processorCreatorLink;
	
	private Map<String, UPMappedIdentifiersReferenceCreator> uniprotReferenceCreators;
	
	private Map<String, BatchReferenceCreator<?>> referenceCreators;
	
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
		
		executeCreateReferenceDatabases();
		
		executeSimpleFileRetrievers();
		executeUniprotFileRetrievers();
		
		EnsemblFileRetrieverExecutor ensemblFileRetrieverExecutor = new EnsemblFileRetrieverExecutor();
		ensemblFileRetrieverExecutor.setEnsemblBatchLookup(this.ensemblBatchLookup);
		ensemblFileRetrieverExecutor.setEnsemblFileRetrievers(this.ensemblFileRetrievers);
		ensemblFileRetrieverExecutor.setEnsemblFileRetrieversNonCore(this.ensemblFileRetrieversNonCore);
		ensemblFileRetrieverExecutor.setObjectCache(this.objectCache);
		ensemblFileRetrieverExecutor.setDbAdapter(this.dbAdapter);
		ensemblFileRetrieverExecutor.execute();
		
		logger.info("Finished downloading files.");
		
		logger.info("Now processing the files...");
		
		// TODO: Link the file processors to the file retrievers so that if
		// any are filtered, only the appropriate processors will execute. Maybe?
		Map<String, Map<String, ?>> dbMappings = executeFileProcessors();

		// Special extra work for ENSEMBL...
		processENSEMBLFiles(dbMappings);
		
		// Print stats on results of file processing.
		logger.info("{} keys in mapping object.", dbMappings.keySet().size());
		
		for (String k : dbMappings.keySet().stream().sorted().collect(Collectors.toList()))
		{
			logger.info("DB Key: {} has {} submaps.", k, dbMappings.get(k).keySet().size());
			for (String subk : dbMappings.get(k).keySet())
			{
				if (dbMappings.get(k).get(subk) instanceof Map)
				{
					logger.info("    subkey: {} has {} subkeys", subk, ((Map<String, ?>)dbMappings.get(k).get(subk)).keySet().size() );
				}
				
			}
		}
		
		//Before each set of IDs is updated in the database, maybe take a database backup?
		
		//Now we create references.
		createReferences(personID, dbMappings);
		
		logger.info("Process complete.");
		
	}

	private void createReferences(long personID, Map<String, Map<String, ?>> dbMappings) throws IOException, Exception
	{
		for (String refCreatorName : this.referenceCreatorFilter)
		{
			logger.info("Executing reference creator: {}", refCreatorName);
			List<GKInstance> sourceReferences = new ArrayList<GKInstance>();
			// Try to get the processor name, except for E
			Optional<String> fileProcessorName = this.processorCreatorLink.keySet().stream().filter(k -> this.processorCreatorLink.get(k).equals(refCreatorName) ).map( m -> m).findFirst();
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
							((ENSMappedIdentifiersReferenceCreator)refCreator).createIdentifiers(personID, mappings);
						}
					}
					else
					{
						// For ENSEBML, there are many dbmappings
						for(String k : dbMappings.keySet().stream().filter(k -> k.startsWith("ENSEMBL_XREF_")).collect(Collectors.toList()))
						{
							logger.info("Ensembl cross-references: {}", k);
							Map<String, Map<String, List<String>>> mappings = (Map<String, Map<String, List<String>>>) dbMappings.get(k);
							((ENSMappedIdentifiersReferenceCreator)refCreator).createIdentifiers(personID, mappings);
						}
					}
				}
				else
				{
					sourceReferences = this.getIdentifiersList(refCreator.getSourceRefDB(), refCreator.getClassReferringToRefName());
					logger.debug("{} source references", sourceReferences.size());
					refCreator.createIdentifiers(personID, (Map<String, ?>) dbMappings.get(fileProcessorName.get()), sourceReferences);
				}
				
			}
			else if (uniprotReferenceCreators.containsKey(refCreatorName))
			{
				UPMappedIdentifiersReferenceCreator refCreator = uniprotReferenceCreators.get(refCreatorName);
				sourceReferences = this.getIdentifiersList(refCreator.getSourceRefDB(), refCreator.getClassReferringToRefName());
				refCreator.createIdentifiers(personID, (Map<String, Map<String, List<String>>>) dbMappings.get(fileProcessorName.get()), sourceReferences);
			}
		}
	}

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
			
			EnsemblAggregateFileProcessor aggregateProcessor = new EnsemblAggregateFileProcessor();
			aggregateProcessor.setPath(Paths.get("/tmp/addlinks-downloaded-files/ensembl/"+ "ensembl_p2xref_mapping."+speciesID+".csv") );
			aggregateProcessor.setMode(EnsemblAggregateProcessingMode.XREF);
			Map<String, Map<String, List<String>>> xrefMapping = aggregateProcessor.getIdMappingsFromFile();
			dbMappings.put("ENSEMBL_XREF_"+speciesID, xrefMapping);
			
			aggregateProcessor.setMode(EnsemblAggregateProcessingMode.ENSP_TO_ENSG);
			Map<String, Map<String, List<String>>> ensp2EnsgMapping = aggregateProcessor.getIdMappingsFromFile();
			dbMappings.put("ENSEMBL_ENSP_2_ENSG_"+speciesID, ensp2EnsgMapping);
		}
	}

	private List<GKInstance> getENSEMBLIdentifiersList()
	{
		List<GKInstance> identifiers = new ArrayList<GKInstance>();
		
		List<String> ensemblDBNames = objectCache.getRefDbNamesToIds().keySet().stream().filter(k -> k.toUpperCase().contains("ENSEMBL") && k.toUpperCase().contains("PROTEIN")).collect(Collectors.toList());
		
		for (String dbName : ensemblDBNames)
		{
			identifiers.addAll(objectCache.getByRefDb(objectCache.getRefDbNamesToIds().get(dbName).get(0), "ReferenceGeneProduct"));
		}
		
		return identifiers;
	}
	
	private List<GKInstance> getIdentifiersList(String refDb, String className)
	{
		return this.getIdentifiersList(refDb, null, className);
	}
	
	private List<GKInstance> getIdentifiersList(String refDb, String species, String className)
	{
		// Need a list of identifiers.
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
		logger.info("{} file processors to execute.", this.fileProcessors.keySet().size());
		this.fileProcessors.keySet().stream().filter(k -> fileProcessorFilter.contains(k)).forEach( k -> 
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

	private void executeUniprotFileRetrievers()
	{
		UniProtFileRetreiverExecutor executor = new UniProtFileRetreiverExecutor();
		executor.setFileRetrieverFilter(fileRetrieverFilter);
		executor.setObjectCache(objectCache);
		executor.setUniprotFileRetrievers(uniprotFileRetrievers);
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

	public void setProcessorCreatorLink(Map<String, String> processorCreatorLink)
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

