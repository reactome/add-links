package org.reactome.addlinks.fileprocessors;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DOCKBlasterFileProcessor extends FileProcessor
{
	private static final Logger logger = LogManager.getLogger();

	@Override
	public Map<String, ArrayList<String>> getIdMappingsFromFile()
	{
		AtomicInteger lineCount = new AtomicInteger(0);
		Map<String, ArrayList<String>> uniprotsToPdbs = new HashMap<String,ArrayList<String>>();
		try
		{
			Files.lines(this.pathToFile).sequential().forEach( line ->
			{
				lineCount.incrementAndGet();
				String[] parts = line.split("\\t");
				String key = parts[0];
				String value = parts[1];
				// The mappings are not 1:1. 
				if (uniprotsToPdbs.containsKey(key))
				{
					uniprotsToPdbs.get(key).add(value);
				}
				else
				{
					ArrayList<String> values = new ArrayList<String>(); 
					values.add(value);
					uniprotsToPdbs.put(key, values);
				}
			});
			logger.info("Processed {} lines.", lineCount.get());
			logger.info("Uniprot-to-PDB (for DOCKBlaster) map has {} unique keys.", uniprotsToPdbs.keySet().size());
		} catch (IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return uniprotsToPdbs;
	}

}