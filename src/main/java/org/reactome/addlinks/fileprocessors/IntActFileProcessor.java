package org.reactome.addlinks.fileprocessors;

import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class IntActFileProcessor extends FileProcessor<String>
{
	public IntActFileProcessor(String processorName)
	{
		super(processorName);
	}
	//private static final Logger logger = LogManager.getLogger();
	@Override
	public Map<String, String> getIdMappingsFromFile()
	{
		Map<String,String> intactToReactomeMapping = new HashMap<String,String>(); 
		try
		{
			Files.readAllLines(this.pathToFile).stream().forEach(line ->
			{
				String[] parts = line.split("\\s+");
				intactToReactomeMapping.put(parts[0], parts[1]);
			});
		}
		catch (IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		logger.info("Number of IntAct mappings: {}",intactToReactomeMapping.size());
		return intactToReactomeMapping;
	}

}
