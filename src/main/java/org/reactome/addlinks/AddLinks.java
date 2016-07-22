package org.reactome.addlinks;

import java.nio.file.Paths;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.reactome.addlinks.dataretrieval.FileRetriever;
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
		Map<String,FileRetriever> beans = context.getBeansOfType(FileRetriever.class);
		
		for (String key : beans.keySet())
		{
			FileRetriever retriever = beans.get(key);
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

		ZincMoleculesFileProcessor zincMoleculesFileProcessor = new ZincMoleculesFileProcessor();
		zincMoleculesFileProcessor.setPath(Paths.get("/tmp/zinc_chebi_purch.xls"));
		Map<String,String> zincMappings = zincMoleculesFileProcessor.getIdMappingsFromFile();
		
		logger.info("Process complete.");
		context.close();
	}

}

