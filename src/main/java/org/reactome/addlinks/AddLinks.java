package org.reactome.addlinks;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.reactome.addlinks.dataretrieval.FileRetriever;
import org.reactome.addlinks.dataretrieval.UniprotFileRetreiver;
import org.reactome.addlinks.dataretrieval.UniprotFileRetreiver.UniprotDB;
import org.reactome.addlinks.db.ReferenceGeneProductCache;
import org.reactome.addlinks.db.ReferenceGeneProductCache.ReferenceGeneProductShell;
import org.reactome.addlinks.fileprocessors.FlyBaseFileProcessor;
import org.reactome.addlinks.fileprocessors.HmdbMetabolitesFileProcessor;
import org.reactome.addlinks.fileprocessors.IntActFileProcessor;
import org.reactome.addlinks.fileprocessors.OrphanetFileProcessor;
import org.reactome.addlinks.fileprocessors.PROFileProcessor;
import org.reactome.addlinks.fileprocessors.ZincMoleculesFileProcessor;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class AddLinks {
	private static final Logger logger = LogManager.getLogger();
	public static void main(String[] args) throws Exception {
		
		ConfigurableApplicationContext context = new ClassPathXmlApplicationContext("application-context.xml");
		//TODO: Add the ability to filter so that only certain beans are selected and run.
		@SuppressWarnings("unchecked")
		Map<String,FileRetriever> fileRetrievers = context.getBean("FileRetrievers", Map.class);
		//Execute the file retreivers in parallel
		fileRetrievers.keySet().stream().parallel().forEach(k -> {
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
		});
		
//		for (String key : fileRetrievers.keySet())
//		{
//			FileRetriever retriever = fileRetrievers.get(key);
//			logger.info("Executing downloader: {}",key);
//			try
//			{
//				retriever.fetchData();
//			}
//			catch (Exception e)
//			{
//				//TODO: The decision to continue after a failure should be a configurable option. 
//				logger.info("Exception caught while processing {}, message is: {}. Will continue with next file retriever.",key,e.getMessage());
//			}
//		}
		
		//Now download mapping data from Uniprot.
		ReferenceGeneProductCache.setDbParams("127.0.0.1", "test_reactome_57", "curator", "",3307);
		//TODO: Add the ability to filter so that only certain beans are selected and run.
		@SuppressWarnings("unchecked")
		Map<String,UniprotFileRetreiver> uniprotFileRetrievers = context.getBean("UniProtFileRetrievers", Map.class);
		for (String key : uniprotFileRetrievers.keySet())
		{
			logger.info("Executing Downloader: {}", key);
			UniprotFileRetreiver retriever = uniprotFileRetrievers.get(key);
			
			UniprotDB toDb = UniprotDB.uniprotDBFromUniprotName(retriever.getMapToDb());
			UniprotDB fromDb = UniprotDB.uniprotDBFromUniprotName(retriever.getMapFromDb());
			//String toDb = retriever.getMapToDb();
			String originalFileDestinationName = retriever.getFetchDestination();
			List<String> refDbIds = ReferenceGeneProductCache.getInstance().getRefDbNamesToIds().get(fromDb.toString() );
			int downloadCounter = 0;
			if (refDbIds != null && refDbIds.size() > 0 )
			{
				logger.info("Number of Reference Database IDs to process: {}",refDbIds.size());
				for (String refDb : refDbIds)
				{
					Set<String> speciesList = ReferenceGeneProductCache.getInstance().getListOfSpecies();
					for (String speciesId : speciesList)
					{
						logger.info("Number of species IDs to process: {}", speciesList.size() );
						
						List<ReferenceGeneProductShell> refGenes = ReferenceGeneProductCache.getInstance().getByRefDbAndSpecies(refDb,speciesId);
						
						if (refGenes != null && refGenes.size() > 0)
						{
							logger.info("Number of identifiers that we will attempt to map from UniProt to {} (db_id: {}, species: {} ) is: {}",toDb.toString(),refDb, speciesId,refGenes.size());
							String identifiersList = refGenes.stream().map(refGeneProduct -> refGeneProduct.getIdentifier()).collect(Collectors.joining("\n"));
							InputStream inStream = new ByteArrayInputStream(identifiersList.getBytes());
							//Inject the refdb in, for cases where there are multiple ref db IDs mapping to the same name.
							
							retriever.setFetchDestination(originalFileDestinationName.replace(".txt","." + speciesId + "." + refDb + ".txt"));
							retriever.setDataInputStream(inStream);
							retriever.fetchData();
							downloadCounter ++ ;
							//Let's sleep a bit after 5 downloads, so we don't get blocked! In the future this could all be parameterized.
							if (downloadCounter % 5 ==0)
							{
								Duration sleepDelay = Duration.ofSeconds(15);
								logger.info("Sleeping for {} to be nice. We don't want to flood their service!", sleepDelay);
								Thread.sleep(sleepDelay.toMillis());
							}
						}
						else
						{
							logger.info("Could not find any RefefenceGeneProducts for reference database ID {} for species {}", refDb, speciesId);
						}
					}
				}
			}
			else
			{
				logger.info("Could not find Reference Database IDs for reference database named: {}",toDb.toString());
			}
		}
		
		logger.info("Finished downloading files.");
		
		logger.info("Now processing the files...");

		Map<String,Map<String,String>> dbMappings = new HashMap<String, Map<String,String>>();
		
		PROFileProcessor proFileProcessor = new PROFileProcessor();
		proFileProcessor.setPath(Paths.get("/tmp/uniprotmapping.txt"));
		Map<String,String> proMappings = proFileProcessor.getIdMappingsFromFile();
		dbMappings.put("PRO", proMappings);

		FlyBaseFileProcessor flyBaseFileProcessor = new FlyBaseFileProcessor();
		flyBaseFileProcessor.setPath(Paths.get("/tmp/FlyBase.tsv.gz"));
		Map<String,String> flyBaseMappings = flyBaseFileProcessor.getIdMappingsFromFile();
		dbMappings.put("flyBase", flyBaseMappings);
		
		HmdbMetabolitesFileProcessor hmdbMetabolitesProcessor = new HmdbMetabolitesFileProcessor();
		hmdbMetabolitesProcessor.setPath(Paths.get("/tmp/hmdb_metabolites.zip"));
		Map<String,String> hmdbMetabolitesMappings = hmdbMetabolitesProcessor.getIdMappingsFromFile();
		dbMappings.put("hmdbMetabolites", hmdbMetabolitesMappings);
		
		//...because it's the same code, just a different input file. right?
//		HmdbMetabolitesFileProcessor hmdbProteinsProcessor = new HmdbMetabolitesFileProcessor();
//		hmdbProteinsProcessor.setPath(Paths.get("/tmp/hmdb_proteins.zip"));
//		Map<String,String> hmdbProteinsMappings = hmdbProteinsProcessor.getIdMappingsFromFile();
//		dbMappings.put("hmdbProteins", hmdbProteinsMappings);
		
		OrphanetFileProcessor orphanetFileProcessor = new OrphanetFileProcessor();
		orphanetFileProcessor.setPath(Paths.get("/tmp/genes_diseases_external_references.xml"));
		Map<String,String> orphanetMappings = orphanetFileProcessor.getIdMappingsFromFile();
		dbMappings.put("orphanet", orphanetMappings);
		
		IntActFileProcessor intactFileProcessor = new IntActFileProcessor();
		intactFileProcessor.setPath(Paths.get("/tmp/reactome.dat"));
		Map<String,String> intactFileMappings = intactFileProcessor.getIdMappingsFromFile();
		dbMappings.put("intact", intactFileMappings);

		//Only ZincMolecules needs a file processor. ZincProteins can be read straight from the file because it just a listing of IDs, one per line.
		ZincMoleculesFileProcessor zincMoleculesFileProcessor = new ZincMoleculesFileProcessor();
		zincMoleculesFileProcessor.setPath(Paths.get("/tmp/zinc_chebi_purch.xls"));
		Map<String,String> zincMappings = zincMoleculesFileProcessor.getIdMappingsFromFile();
		dbMappings.put("zinc", zincMappings);

		logger.info("Process complete.");
		context.close();
	}

}

