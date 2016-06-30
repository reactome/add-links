package org.reactome.addlinks;

import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.reactome.addlinks.dataretrieval.FileRetriever;
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
			logger.info("Executing {}",key);
			retriever.fetchData();
		}
		context.close();
		logger.info("Finished downloading files.");
		
//		BatchWebServiceDataRetriever getGenecardsReferenceDatabaseToReferencePeptideSequence = new BatchWebServiceDataRetriever();
//		getGenecardsReferenceDatabaseToReferencePeptideSequence.setDataURL("http://www.genecards.org/cgi-bin/carddisp.pl?id=###ID###&id_type=%22uniprot%22");
//		getGenecardsReferenceDatabaseToReferencePeptideSequence.setDbHost("127.0.0.1");
//		getGenecardsReferenceDatabaseToReferencePeptideSequence.setDbName("test_reactome_57");
//		getGenecardsReferenceDatabaseToReferencePeptideSequence.setDbUser("curator");
//		getGenecardsReferenceDatabaseToReferencePeptideSequence.setDbPassword("");
//		getGenecardsReferenceDatabaseToReferencePeptideSequence.setDbPort(3307);
//		getGenecardsReferenceDatabaseToReferencePeptideSequence.setFetchDestination("/tmp/");
//		getGenecardsReferenceDatabaseToReferencePeptideSequence.setName("GeneCardsPeptideSequence");
//		getGenecardsReferenceDatabaseToReferencePeptideSequence.setMaxAge(Duration.of(0, ChronoUnit.SECONDS));
//		getGenecardsReferenceDatabaseToReferencePeptideSequence.setObjectClassName("ReferenceGeneProduct");
//		try {
//			getGenecardsReferenceDatabaseToReferencePeptideSequence.fetchData();
//		} catch (Exception e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
	}

}

