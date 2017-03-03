package org.reactome.addlinks.fileprocessors;

import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ZincProteinsFileProcessor extends FileProcessor<String>
{
	public ZincProteinsFileProcessor()
	{
		super(null);
	}
	
	public ZincProteinsFileProcessor(String processorName)
	{
		super(processorName);
	}
	//private static final Logger logger = LogManager.getLogger();
	@Override
	public Map<String,String> getIdMappingsFromFile()
	{
		Map<String, String> mapping = new HashMap<String, String>();
		try
		{
			// The ZINC Proteins file will only contain a single column of UniProt IDs.
			// We'll put those into the map as key AND value, to make it easier for other classes
			// to conform to standard interfaces.
			Files.readAllLines(this.pathToFile).forEach(line -> {mapping.put(line, line);});
		}
		catch (IOException e)
		{
			e.printStackTrace();
			throw new Error(e);
		}
		logger.info("{} values extracted from ZINC Protein file.", mapping.keySet().size());
		return mapping;
	}

}
