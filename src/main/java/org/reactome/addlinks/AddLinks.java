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
			logger.info("Executing downloader: {}",key);
			try
			{
				retriever.fetchData();
			}
			catch (Exception e)
			{
				logger.info("Exception caught while processing {}, message is: {}. Will continue with next file retriever.",key,e.getMessage());
			}
		}
		logger.info("Finished downloading files.");
		context.close();
	}

}

