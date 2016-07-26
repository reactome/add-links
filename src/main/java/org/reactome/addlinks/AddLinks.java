package org.reactome.addlinks;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
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
		Map<String,FileRetriever> fileRetrievers = context.getBean("FileRetrievers", Map.class);
		
		for (String key : fileRetrievers.keySet())
		{
			FileRetriever retriever = fileRetrievers.get(key);
			logger.info("Executing downloader: {}",key);
			try
			{
				retriever.fetchData();
			}
			catch (Exception e)
			{
				//TODO: The decision to continue after a failure should be a configurable option. 
				logger.info("Exception caught while processing {}, message is: {}. Will continue with next file retriever.",key,e.getMessage());
			}
		}
		logger.info("Finished downloading files.");
		
		logger.info("Now processing the files...");

		PROFileProcessor proFileProcessor = new PROFileProcessor();
		proFileProcessor.setPath(Paths.get("/tmp/uniprotmapping.txt"));
		Map<String,String> proMappings = proFileProcessor.getIdMappingsFromFile();

		FlyBaseFileProcessor flyBaseFileProcessor = new FlyBaseFileProcessor();
		flyBaseFileProcessor.setPath(Paths.get("/tmp/FlyBase.tsv.gz"));
		Map<String,String> flyBaseMappings = flyBaseFileProcessor.getIdMappingsFromFile();		
		
		HmdbMetabolitesFileProcessor hmdbMetabolitesProcessor = new HmdbMetabolitesFileProcessor();
		hmdbMetabolitesProcessor.setPath(Paths.get("/tmp/hmdb_metabolites.zip"));
		Map<String,String> hmdbMetabolitesMappings = hmdbMetabolitesProcessor.getIdMappingsFromFile();
		
		//...because it's the same code, just a different input file. right?
//		HmdbMetabolitesFileProcessor hmdbProteinsProcessor = new HmdbMetabolitesFileProcessor();
//		hmdbProteinsProcessor.setPath(Paths.get("/tmp/hmdb_proteins.zip"));
//		Map<String,String> hmdbProteinsMappings = hmdbProteinsProcessor.getIdMappingsFromFile();
		
		OrphanetFileProcessor orphanetFileProcessor = new OrphanetFileProcessor();
		orphanetFileProcessor.setPath(Paths.get("/tmp/genes_diseases_external_references.xml"));
		Map<String,String> orphanetMappings = orphanetFileProcessor.getIdMappingsFromFile();
		
		IntActFileProcessor intactFileProcessor = new IntActFileProcessor();
		intactFileProcessor.setPath(Paths.get("/tmp/reactome.dat"));
		Map<String,String> intactFileMappings = intactFileProcessor.getIdMappingsFromFile();

		//Only ZincMolecules needs a file processor. ZincProteins can be read straight from the file because it just a listing of IDs, one per line.
		ZincMoleculesFileProcessor zincMoleculesFileProcessor = new ZincMoleculesFileProcessor();
		zincMoleculesFileProcessor.setPath(Paths.get("/tmp/zinc_chebi_purch.xls"));
		Map<String,String> zincMappings = zincMoleculesFileProcessor.getIdMappingsFromFile();

		ReferenceGeneProductCache.setDbParams("127.0.0.1", "test_reactome_57", "curator", "",3307);
		
		Map<String,UniprotFileRetreiver> uniprotFileRetrievers = context.getBean("UniProtFileRetrievers", Map.class);
		for (String key : uniprotFileRetrievers.keySet())
		{
			logger.info("Executing Downloader: {}", key);
			UniprotFileRetreiver retriever = uniprotFileRetrievers.get(key);
			
			UniprotDB toDb = UniprotDB.uniprotDBFromUniprotName(retriever.getMapToDb());
			//String toDb = retriever.getMapToDb();
			
			List<String> refDbIds = ReferenceGeneProductCache.getInstance().getRefDbNamesToIds().get(toDb.toString() );
			if (refDbIds != null && refDbIds.size() > 0 )
			{
				for (String refDb : refDbIds)
				{
					List<ReferenceGeneProductShell> refGenes = ReferenceGeneProductCache.getInstance().getByRefDb(refDb);
					
					if (refGenes != null && refGenes.size() > 0)
					{
						logger.info("Number of identifiers that we will attempt to map from UniProt to {} is: {}",toDb.toString(),refGenes.size());
						String identifiersList = refGenes.stream().map(refGeneProduct -> refGeneProduct.getIdentifier()).collect(Collectors.joining("\n"));
						InputStream inStream = new ByteArrayInputStream(identifiersList.getBytes());
						
						retriever.setDataInputStream(inStream);
						retriever.downloadData();
					}
					else
					{
						logger.info("Could not find any RefefenceGeneProducts for reference database ID: {}",refDb);
					}
					//
				}
				
			}
			else
			{
				logger.info("Could not find Reference Database IDs for reference database named: {}",toDb.toString());
			}
		}
		
		logger.info("Process complete.");
		context.close();
	}

}

