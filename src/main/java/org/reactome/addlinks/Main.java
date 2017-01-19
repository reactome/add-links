package org.reactome.addlinks;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;

public class Main
{
	private static final Logger logger = LogManager.getLogger();
	
	/**
	 * Main method for AddLinks.
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception
	{
		
		String springConfigFile = args[0];
		logger.info("Spring context will be loaded from {}", springConfigFile);
		ConfigurableApplicationContext context = new FileSystemXmlApplicationContext(springConfigFile);
		AddLinks addLinks =  context.getBean("addLinks",AddLinks.class);
		addLinks.doAddLinks();
		context.close();
	}

}
