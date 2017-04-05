package org.reactome.addlinks.fileprocessors;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IntActFileProcessor extends FileProcessor<List<String>>
{
	public IntActFileProcessor(String processorName)
	{
		super(processorName);
	}
	
	public IntActFileProcessor()
	{
		super(null);
	}
	
	@Override
	public Map<String, List<String>> getIdMappingsFromFile()
	{
		// Key is the Reactome ID, value is a list of (possibly) multiple EBI IDs that can be used to do a lookup in IntAct's ComplexPortal.
		Map<String, List<String>> reactomeToIntActMapping = new HashMap<String,List<String>>(); 
		try
		{
			Files.readAllLines(this.pathToFile).stream().forEach(line ->
			{
				String[] parts = line.split("\\s+");
				if (reactomeToIntActMapping.containsKey(parts[1]))
				{
					reactomeToIntActMapping.get(parts[1]).add(parts[0]);
				}
				else
				{
					reactomeToIntActMapping.put(parts[0], new ArrayList<String>(Arrays.asList(parts[1])));
				}
			});
		}
		catch (IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		logger.info("Number of IntAct mappings: {}",reactomeToIntActMapping.size());
		return reactomeToIntActMapping;
	}

}
