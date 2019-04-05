package org.reactome.addlinks;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import org.apache.http.HttpStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.reactome.addlinks.brenda.BRENDAReferenceDatabaseGenerator;
import org.reactome.addlinks.dataretrieval.FileRetriever;
import org.reactome.addlinks.dataretrieval.UniprotFileRetriever;
import org.reactome.addlinks.dataretrieval.brenda.BRENDAFileRetriever;
import org.reactome.addlinks.dataretrieval.brenda.BRENDASoapClient;
import org.reactome.addlinks.dataretrieval.ensembl.EnsemblBatchLookup;
import org.reactome.addlinks.dataretrieval.ensembl.EnsemblFileRetriever;
import org.reactome.addlinks.dataretrieval.executor.BrendaFileRetrieverExecutor;
import org.reactome.addlinks.dataretrieval.executor.KeggFileRetrieverExecutor;
import org.reactome.addlinks.dataretrieval.executor.SimpleFileRetrieverExecutor;
import org.reactome.addlinks.dataretrieval.executor.UniprotFileRetrieverExecutor;
import org.reactome.addlinks.db.CrossReferenceReporter;
import org.reactome.addlinks.db.DuplicateIdentifierReporter;
import org.reactome.addlinks.db.DuplicateIdentifierReporter.REPORT_KEYS;
import org.reactome.addlinks.db.ReferenceDatabaseCreator;
import org.reactome.addlinks.db.ReferenceObjectCache;
import org.reactome.addlinks.ensembl.EnsemblFileRetrieverExecutor;
import org.reactome.addlinks.ensembl.EnsemblReferenceDatabaseGenerator;
import org.reactome.addlinks.fileprocessors.FileProcessor;
import org.reactome.addlinks.fileprocessors.ensembl.EnsemblFileProcessorExecutor;
import org.reactome.addlinks.kegg.KEGGReferenceDatabaseGenerator;
import org.reactome.addlinks.linkchecking.LinkCheckInfo;
import org.reactome.addlinks.linkchecking.LinkCheckManager;
import org.reactome.addlinks.linkchecking.LinksToCheckCache;
import org.reactome.addlinks.referencecreators.BatchReferenceCreator;
import org.reactome.addlinks.referencecreators.COSMICReferenceCreator;
import org.reactome.addlinks.referencecreators.ComplexPortalReferenceCreator;
import org.reactome.addlinks.referencecreators.ENSMappedIdentifiersReferenceCreator;
import org.reactome.addlinks.referencecreators.NCBIGeneBasedReferenceCreator;
import org.reactome.addlinks.referencecreators.OneToOneReferenceCreator;
import org.reactome.addlinks.referencecreators.RHEAReferenceCreator;
import org.reactome.addlinks.referencecreators.UPMappedIdentifiersReferenceCreator;


public class AddLinks
{
	private static final String DATE_PATTERN_FOR_FILENAMES = "yyyy-MM-dd_HHmmss";

	private static final Logger logger = LogManager.getLogger();
	
	private ReferenceObjectCache objectCache;
	
	private List<String> fileProcessorFilter;
	
	private List<String> fileRetrieverFilter;
	
	private List<String> referenceCreatorFilter;
	
	private Map<String, UniprotFileRetriever> uniprotFileRetrievers;
	
	private Map<String, EnsemblFileRetriever> ensemblFileRetrievers;
	
	private Map<String, EnsemblFileRetriever> ensemblFileRetrieversNonCore;
	
	private Map<String, FileProcessor<?>> fileProcessors;
	
	private Map<String,FileRetriever> fileRetrievers;
	
	private Map<String, Map<String, ?>> referenceDatabasesToCreate;
	
	private Map<String, Object> processorCreatorLink;
	
	private Map<String, UPMappedIdentifiersReferenceCreator> uniprotReferenceCreators;
	
	private Map<String, BatchReferenceCreator<?>> referenceCreators;
	
	private List<String> referenceDatabasesToLinkCheck;
	
	private float proportionToLinkCheck = 0.1f;
	
	private int maxNumberLinksToCheck = 100;
	
	private EnsemblBatchLookup ensemblBatchLookup;
	
	private MySQLAdaptor dbAdapter;

	@SuppressWarnings("unchecked")
	public void doAddLinks() throws Exception
	{
		// This list will be used at the very end when we are checking links but we need to
		// seed it in the LinksToCheckCache now, because species-specific reference databases will only 
		// be created at run-time and we can't anticipate them now. They will be added to
		// LinksToCheckCache's list of reference DBs as they are created.
		LinksToCheckCache.setRefDBsToCheck(this.referenceDatabasesToLinkCheck);
		
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
			logger.info("Only the specified FileRetrievers will be executed: {}",fileRetrieverFilter);
		}
		// Start by creating ReferenceDatabase objects that we might need later.
		this.executeCreateReferenceDatabases(personID);
		// Now that we've *created* new ref dbs, rebuild any caches that might have dependended on them.
		ReferenceObjectCache.clearAndRebuildAllCaches();
		CrossReferenceReporter xrefReporter = new CrossReferenceReporter(this.dbAdapter);
		DuplicateIdentifierReporter duplicateIdentifierReporter = new DuplicateIdentifierReporter(this.dbAdapter);
		Map<String, Map<String, Integer>> preAddLinksReport = this.reportsBeforeAddLinks(xrefReporter, duplicateIdentifierReporter);
		
		ExecutorService execSrvc = Executors.newFixedThreadPool(5);
		List<Callable<Boolean>> retrieverJobs = createRetrieverJobs(numUniprotDownloadThreads);
		// Execute the file retrievers.
		execSrvc.invokeAll(retrieverJobs);
		
		retrieverJobs = new ArrayList<Callable<Boolean>>();
		// Now that uniprot file retrievers have run, we can run the KEGG file retriever.
		retrieverJobs.add(new KeggFileRetrieverExecutor(fileRetrievers, uniprotFileRetrievers, fileRetrieverFilter, objectCache));
		// Run the Brenda file retriever - it is slow and KEGG is slow, so let's run them together!
		retrieverJobs.add( new BrendaFileRetrieverExecutor(fileRetrievers, fileRetrieverFilter, objectCache));
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
			EnsemblFileProcessorExecutor ensemblFileProcessorExecutor = new EnsemblFileProcessorExecutor(this.dbAdapter, this.objectCache);
			ensemblFileProcessorExecutor.processENSEMBLFiles(dbMappings);
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
		this.reportsAfterAddLinks(xrefReporter, duplicateIdentifierReporter, preAddLinksReport);
		logger.info("Purging unused ReferenceDatabse objects.");
		this.purgeUnusedRefDBs();
		
		logger.info("Now checking links.");
		
		this.checkLinks();
		
		// Now... we need to clean up some of the species-specific Reference Database names. Specifically, BRENDA was causing problems for some external team
		// that was using a file which contained strings of the form "BRENDA (Species Name)". So we will change name[0] for BRENDA ReferenceDatabase objects
		// to "BRENDA" but leave the _displayName as "BRENDA (Species Name)".
		// This *CANNOT* be done earlier in the process as the species name in the ReferenceDatabase name is used to do lookups to get the correct species-specific
		// ReferenceDatabase object. Another option would be to modify the data model by adding a new "species" attribute to the ReferenceDatabase type, but
		// I'm not sure anyone else will go along with that...
		fixBrendaRefDBNames();
		
		logger.info("Process complete.");
	}

	/**
	 * Checks the links to external resources.
	 */
	private void checkLinks()
	{
		// Now, check the links that were created to ensure that they are all valid.
		LinkCheckManager linkCheckManager = new LinkCheckManager();
		linkCheckManager.setDbAdaptor(dbAdapter);
		// Filter by references database name.
		for (GKInstance refDBInst : LinksToCheckCache.getCache().keySet() )
		{
			int numLinkOK = 0;
			int numLinkNotOK = 0;
			// LinksToCheckCache.getRefDBsToCheck() should return a list that contains everything
			// from the Spring file AND all of the ENSEMBL and KEGG species-specific reference database names.
			if (LinksToCheckCache.getRefDBsToCheck().contains(refDBInst.getDisplayName())
					|| (refDBInst.getDisplayName().toUpperCase().contains("ENSEMBL") && LinksToCheckCache.getRefDBsToCheck().contains("ENSEMBL"))
					|| (refDBInst.getDisplayName().toUpperCase().contains("BRENDA") && LinksToCheckCache.getRefDBsToCheck().contains("Brenda"))
					|| (refDBInst.getDisplayName().toUpperCase().contains("KEGG") && LinksToCheckCache.getRefDBsToCheck().contains("KEGG"))
				)
			{
				if (LinksToCheckCache.getCache().get(refDBInst).size() > 0)
				{
					logger.info("Link-checking for database: {}", refDBInst.getDisplayName());
					Map<String, LinkCheckInfo> results = linkCheckManager.checkLinks(refDBInst, new ArrayList<GKInstance>(LinksToCheckCache.getCache().get(refDBInst)), this.proportionToLinkCheck, this.maxNumberLinksToCheck);
					// "results" is a map of DB IDs mapped to link-checking results, for each identifier.
					for (String k : results.keySet())
					{
						if (!results.get(k).isKeywordFound())
						{
							if (results.get(k).getStatusCode() == HttpStatus.SC_OK)
							{
								logger.warn("Link-checking error: Identifier {} was not found when querying the URL {}", results.get(k).getIdentifier(), results.get(k).getURI());
							}
							else
							{
								logger.warn("Link-checking error: Identifier {} returned a non-200 status code: {}", results.get(k).getIdentifier(), results.get(k).getStatusCode());
							}
							numLinkNotOK++;
						}
						else
						{
							numLinkOK++;
						}
					}
					logger.info("{} links were OK, {} links were NOT ok.", numLinkOK, numLinkNotOK);
				}
				else
				{
					logger.info("Could not check links for {} because there no *new* links for this reference database.", refDBInst.getDisplayName());
				}
			}
			else
			{
				logger.info("ReferenceDatabase with name \"{}\" will *not* be link-checked because it was not in the list.", refDBInst.getDisplayName());
			}
			
		}
	}

	/**
	 * Reports on counts and duplicates after running the main AddLinks process. It also reports on differences of counts for the databases.
	 * @param xrefReporter - cross-reference reporter.
	 * @param duplicateIdentifierReporter - duplicate reporter.
	 * @param preAddLinksReport - The report from before the main AddLinks process was run.
	 * @throws SQLException
	 * @throws IOException
	 * @throws Exception
	 */
	private void reportsAfterAddLinks(CrossReferenceReporter xrefReporter, DuplicateIdentifierReporter duplicateIdentifierReporter, Map<String, Map<String, Integer>> preAddLinksReport) throws SQLException, IOException, Exception
	{
		logger.info("Counts of references to external databases currently in the database ({}), AFTER running AddLinks", this.dbAdapter.getConnection().getCatalog());
		//reporter.printReport();
		Map<String, Map<String,Integer>> postAddLinksReport = xrefReporter.createReportMap();
		logger.info("\n"+xrefReporter.printReport(postAddLinksReport));
		
		logger.info("Differences");
		String diffReport = xrefReporter.printReportWithDiffs(preAddLinksReport, postAddLinksReport);
		// Save the diff report to a file for future reference.uinm
		String diffReportName = "reports/diffReports/diffReport" + DateTimeFormatter.ofPattern(DATE_PATTERN_FOR_FILENAMES).format(LocalDateTime.now()) + ".txt";
		Files.write(Paths.get(diffReportName), diffReport.getBytes() );
		logger.info("\n"+diffReport);
		logger.info("(Differences report can also be found in the file: " + diffReportName);
		
		logger.info("Querying for duplicated identifiers in the database, AFTER running AddLinks...");
		List<Map<REPORT_KEYS, String>> postAddLinksdataRows = duplicateIdentifierReporter.createReport();
		StringBuilder postAddLinksduplicateSB = duplicateIdentifierReporter.generatePrintableReport(postAddLinksdataRows);
		String postAddLinksDuplicateIdentifierReportFileName = "reports/duplicateReports/postAddLinksDuplicatedIdentifiers_" + DateTimeFormatter.ofPattern(DATE_PATTERN_FOR_FILENAMES).format(LocalDateTime.now()) + ".txt";
		logger.info("Report can be found in {}", postAddLinksDuplicateIdentifierReportFileName);
		Files.write(Paths.get(postAddLinksDuplicateIdentifierReportFileName), postAddLinksduplicateSB.toString().getBytes());
	}

	/**
	 * Reports on counts and duplicates before running the main AddLinks process.
	 * @param xrefReporter - a cross-reference reporter.
	 * @param duplicateIdentifierReporter - a duplicate reporter.
	 * @return A mapping of databases and counts. This gets used AFTER AddLinks to determine how much changed during the process. It is 
	 * used to create the "diff" report.
	 * @throws SQLException
	 * @throws Exception
	 * @throws IOException
	 */
	private Map<String, Map<String, Integer>> reportsBeforeAddLinks(CrossReferenceReporter xrefReporter, DuplicateIdentifierReporter duplicateIdentifierReporter) throws SQLException, Exception, IOException
	{
		logger.info("Counts of references to external databases currently in the database ({}), BEFORE running AddLinks", this.dbAdapter.getConnection().getCatalog());
		Map<String, Map<String,Integer>> preAddLinksReport = xrefReporter.createReportMap();
		logger.info("\n"+(xrefReporter.printReport(preAddLinksReport)));
		logger.info("Querying for Duplicated identifiers in the database, BEFORE running AddLinks...");
		List<Map<REPORT_KEYS, String>> dataRows = duplicateIdentifierReporter.createReport();
		StringBuilder duplicateSB = duplicateIdentifierReporter.generatePrintableReport(dataRows);
		String preAddLinksDuplicateIdentifierReportFileName = "reports/duplicateReports/preAddLinksDuplicatedIdentifiers_" + DateTimeFormatter.ofPattern(DATE_PATTERN_FOR_FILENAMES).format(LocalDateTime.now()) + ".txt";
		logger.info("Report can be found in {}", preAddLinksDuplicateIdentifierReportFileName);
		Files.write(Paths.get(preAddLinksDuplicateIdentifierReportFileName), duplicateSB.toString().getBytes());
		return preAddLinksReport;
	}

	/**
	 * @param numUniprotDownloadThreads
	 * @param retrieverJobs
	 */
	private List<Callable<Boolean>> createRetrieverJobs(int numUniprotDownloadThreads)
	{
		// We can execute SimpleFileRetrievers at the same time as the UniProt retrievers, and also the ENSEMBL retrievers.
		
		List<Callable<Boolean>> retrieverJobs = new ArrayList<>();
		retrieverJobs.add(new SimpleFileRetrieverExecutor(fileRetrievers, fileRetrieverFilter));
		// Execute the UniProt file retrievers separately.
		retrieverJobs.add(new UniprotFileRetrieverExecutor(uniprotFileRetrievers, fileRetrieverFilter, numUniprotDownloadThreads, objectCache));
		
		// Check to see if we should do any Ensembl work
		if (fileRetrieverFilter.contains("EnsemblToALL"))
		{
			retrieverJobs.add(new EnsemblFileRetrieverExecutor(this.ensemblFileRetrievers, this.ensemblFileRetrieversNonCore , this.fileRetrieverFilter, this.ensemblBatchLookup, this.objectCache, this.dbAdapter));
		}
		return retrieverJobs;
	}
	
	/**
	 * Renames all of the BRENDA databases to simply be "BRENDA".
	 * The names of the BRENDA ReferenceDatabases were getting into a downstream file,
	 * and the primary consumer of that file had problems processing multiple species-specific databases,
	 * so now they are all simply renamed to "BRENDA".
	 * @throws Exception
	 */
	private void fixBrendaRefDBNames() throws Exception
	{
		logger.info("Fixing BRENDA reference database names.");
		@SuppressWarnings("unchecked")
		Set<GKInstance> brendaRefDBs = (Set<GKInstance>) this.dbAdapter.fetchInstanceByAttribute(ReactomeJavaConstants.ReferenceDatabase, ReactomeJavaConstants.name, "LIKE", "%BRENDA%");
		for (GKInstance brendaRefDB : brendaRefDBs)
		{
			@SuppressWarnings("unchecked")
			List<String> names = (List<String>) brendaRefDB.getAttributeValuesList(ReactomeJavaConstants.name);
			names.set(0, "BRENDA");
			brendaRefDB.setAttributeValue(ReactomeJavaConstants.name, names);
			this.dbAdapter.updateInstanceAttribute(brendaRefDB, ReactomeJavaConstants.name);
			logger.info("BRENDA RefDB {} now has names: {}", brendaRefDB.toString(), brendaRefDB.getAttributeValue(ReactomeJavaConstants.name));
		}
	}
	
	/**
	 * Finds all ReferenceDatabase objects that have no referrers, and purges them from the database.
	 */
	@SuppressWarnings("unchecked")
	private void purgeUnusedRefDBs()
	{
		try
		{
			//@SuppressWarnings("unchecked")
			Collection<GKInstance> refDBs = (Collection<GKInstance>) this.dbAdapter.fetchInstancesByClass(ReactomeJavaConstants.ReferenceDatabase);
			for (GKInstance refDB : refDBs)
			{
				this.dbAdapter.loadInstanceAttributeValues(refDB);
				//@SuppressWarnings("unchecked")
				List<String> names = (List<String>) refDB.getAttributeValuesList(ReactomeJavaConstants.name);
				//@SuppressWarnings("unchecked")
				Collection<GKInstance> refMap = new ArrayList<GKInstance> ();
				refMap = (Collection<GKInstance>) refDB.getReferers(ReactomeJavaConstants.referenceDatabase);
				
				int refCount = 0;
				if (refMap != null)
				{
					refCount = refMap.size();
				}
				logger.trace("ReferenceDatabase: {} ({}); # referrers: {}", refDB.getDBID(), names.toString(), refCount);
				if (refCount == 0)
				{
					logger.debug("NOTHING refers to ReferenceDatabase DB ID {} ({}) so it will now be deleted.", refDB.getDBID(), names.toString());
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
				
				if (refCreator instanceof NCBIGeneBasedReferenceCreator)
				{
					((NCBIGeneBasedReferenceCreator) refCreator).setCTDGenes( (Map<String, String>) dbMappings.get("CTDProcessor") );
				}
				
				if (refCreator instanceof ENSMappedIdentifiersReferenceCreator)
				{
					createEnsemblReferences(personID, dbMappings, refCreator);
				}
				else
				{
					// Rhea reference creator is special - its source references is a simple list of all Reactions.
					if (refCreator instanceof RHEAReferenceCreator)
					{
						sourceReferences = objectCache.getReactionsByID().values().stream().collect(Collectors.toList());
					}
					else if (refCreator instanceof ComplexPortalReferenceCreator)
					{
						// The ComplexPortalReferenceCreator does not *need* a list of source references since the mapping it gets is sufficient.
						sourceReferences = new ArrayList<GKInstance>();
					}
					else if ( refCreator instanceof COSMICReferenceCreator)
					{
						// COSMIC should use ALL human ReferenceGeneProduct, regarless of source database.
						sourceReferences = this.objectCache.getBySpecies("48887", "ReferenceGeneProduct");
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
				if (refCreator instanceof NCBIGeneBasedReferenceCreator)
				{
					((NCBIGeneBasedReferenceCreator) refCreator).setCTDGenes( (Map<String, String>) dbMappings.get("CTDProcessor") );
				}
				sourceReferences = this.getIdentifiersList(refCreator.getSourceRefDB(), refCreator.getClassReferringToRefName());
				refCreator.createIdentifiers(personID, (Map<String, Map<String, List<String>>>) dbMappings.get(fileProcessorName.get()), sourceReferences);
			}
		}
	}

	/**
	 * Create ENSEMBL references.
	 * @param personID - the personID to us when creating references.
	 * @param dbMappings - dbMappings is a mapping from source identifier to target identifier.
	 * @param refCreator - the object that will create the references.
	 * @throws IOException
	 */
	private void createEnsemblReferences(long personID, Map<String, Map<String, ?>> dbMappings, BatchReferenceCreator refCreator) throws IOException
	{
		List<GKInstance> sourceReferences;
		sourceReferences = getENSEMBLIdentifiersList();
		logger.debug("{} ENSEMBL source references", sourceReferences.size());
		// This is for ENSP -> ENSG mappings.
		if (refCreator.getSourceRefDB().equals(((ENSMappedIdentifiersReferenceCreator) refCreator).getTargetRefDB()))
		{
			for(String k : dbMappings.keySet().stream().filter(k -> k.startsWith("ENSEMBL_ENSP_2_ENSG_")).collect(Collectors.toList()))
			{
				logger.info("Ensembl cross-references: {}", k);
				@SuppressWarnings("unchecked")
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
				@SuppressWarnings("unchecked")
				Map<String, Map<String, List<String>>> mappings = (Map<String, Map<String, List<String>>>) dbMappings.get(k);
				((ENSMappedIdentifiersReferenceCreator)refCreator).createIdentifiers(personID, mappings, sourceReferences);
			}
		}
	}

	/**
	 * This function will get a list of ENSEMBL identifiers. Each GKInstance will be a ReferenceGeneProduct from an ENSEMBL_*_PROTEIN database. 
	 * 
	 * @return A list of instances.
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
	private void executeCreateReferenceDatabases(long personID)
	{
		ReferenceDatabaseCreator creator = new ReferenceDatabaseCreator(dbAdapter, personID);
		for (String key : this.referenceDatabasesToCreate.keySet())
		{
			logger.info("Creating ReferenceDatabase {}", key);
			
			Map<String, ?> refDB = this.referenceDatabasesToCreate.get(key);
			String url = null, accessUrl = null, resourceIdentifier = null;
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
					case "resourceIdentifier":
						resourceIdentifier = (String) refDB.get(attributeKey);
						break;
				}
				// If a resourceIdentifier was present, we will need to query identifiers.org to ensure we have the most up-to-date access URL.
				if (resourceIdentifier != null && !"".equals(resourceIdentifier.trim()))
				{
					accessUrl = getUpToDateAccessURL(resourceIdentifier, accessUrl);
				}
			}
			try
			{
				if (primaryName == null || "".equals(primaryName.trim()))
				{
					throw new RuntimeException("You attempted to create a ReferenceDatabase with a NULL primary name! This is not allowed. The other attributes for this reference database are: " + refDB.toString());
				}
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
		KEGGReferenceDatabaseGenerator.setDBAdaptor(this.dbAdapter);
		BRENDAReferenceDatabaseGenerator.setDBCreator(creator);
		try
		{
			EnsemblReferenceDatabaseGenerator.generateSpeciesSpecificReferenceDatabases(objectCache);
			KEGGReferenceDatabaseGenerator.generateSpeciesSpecificReferenceDatabases(objectCache);
			BRENDAFileRetriever brendaRetriever = (BRENDAFileRetriever) this.fileRetrievers.get("BrendaRetriever");
			BRENDASoapClient client = new BRENDASoapClient(brendaRetriever.getUserName(), brendaRetriever.getPassword());
			BRENDAReferenceDatabaseGenerator.createReferenceDatabases(client, brendaRetriever.getDataURL().toString(), objectCache, dbAdapter, personID);
		}
		catch (Exception e)
		{
			logger.error("Error while creating ENSEMBL species-specific ReferenceDatabase objects: {}", e.getMessage());
			e.printStackTrace();
			throw new Error(e);
		}
	}

	/**
	 * Gets an up-to-date URL for an external database ("resource"), identified by an identifiers.org resource identifier.
	 * If the URL from identifiers.org is different from the one that is given into this function, the URL from identifiers.org
	 * will be returned, with the Reactome-format identifier token ("###ID###") as a replacement for the identifiers.org token ("{$id}").
	 * @param resourceIdentifier - the resourceIdentifier.
	 * @param accessURL - the current accessURL, from reference-databases.xml.
	 * @return The most up-to-date accessURL for the resource, with the Reactome identifier token.
	 */
	private String getUpToDateAccessURL(String resourceIdentifier, String accessURL)
	{
		// start off by assuming that the updated URL will be the same as the URL that is given as input here (hopefully, this will usually be the case).
		String updatedAccessURL = accessURL;
		// Call identifiers.org web service to get the most up-to-date accessUrl, and compare with the one in the file.
		// The WS URL is: https://identifiers.org/rest/resources/${resourceIdentifier}
		// The response will be in JSON-format, look for the key "accessURL"
		
		String urlFromIdentifiersDotOrg = IdentifiersDotOrgUtil.getAccessUrlForResource(resourceIdentifier);
		// If we got a URL back from identifiers.org...
		if (urlFromIdentifiersDotOrg != null && !urlFromIdentifiersDotOrg.trim().equals(""))
		{
			if (!urlFromIdentifiersDotOrg.replace("{$id}", "").equals(accessURL.replace("###ID###", "")))
			{
				// If replacing the Identifier tokens cause the two strings to mis-match, we should
				// use the new accessURL from identifiers.org, and log a message so someone will
				// know to update reference-databases.xml
				updatedAccessURL = urlFromIdentifiersDotOrg.replace("{$id}", "###ID###");
				logger.info("The resource with resourceIdentifier={} got a new accessURL from identifiers.org: {} ; You might want to update reference-databases.xml to contain this new URL.", resourceIdentifier, updatedAccessURL);
			}
			// else, the URL in reference-databases.xml matches the URL from identifiers.org so just return the input URL.
		}
		else
		{
			logger.warn("No accessUrl came back from identifiers.org for the resourceIdentifier {}, so the original accessUrl will be used.", resourceIdentifier);
		}
		return updatedAccessURL;
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

	public void setUniprotFileRetrievers(Map<String, UniprotFileRetriever> uniprotFileRetrievers)
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
	
	public void setReferenceDatabasesToLinkCheck(List<String> referenceDatabasesToLinkCheck)
	{
		this.referenceDatabasesToLinkCheck = referenceDatabasesToLinkCheck;
	}

	public void setProportionToLinkCheck(float proportionToLinkCheck)
	{
		this.proportionToLinkCheck = proportionToLinkCheck;
	}

	public void setMaxNumberLinksToCheck(int maxNumberLinksToCheck)
	{
		this.maxNumberLinksToCheck = maxNumberLinksToCheck;
	}
}
