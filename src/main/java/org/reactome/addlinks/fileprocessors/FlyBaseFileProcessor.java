package org.reactome.addlinks.fileprocessors;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class FlyBaseFileProcessor extends FileProcessor<String>
{
	public FlyBaseFileProcessor()
	{
		super(null);
	}
	
	public FlyBaseFileProcessor(String processorName)
	{
		super(processorName);
	}

	@Override
	public Map<String, String> getIdMappingsFromFile()
	{
		Map<String,String> mappings = new HashMap<String, String>();
		try
		{
			String unzippedFile = this.unzipFile(this.pathToFile);
			AtomicInteger lineCount = new AtomicInteger(0);
			try
			{
				//I added ".sequential()" because I have line counter and I want to ensure that it works properly.
				Files.lines(Paths.get(unzippedFile)).filter(p -> !p.startsWith("#")).sequential().forEach( line ->
				{
					lineCount.set(lineCount.get() + 1);
					String[] parts = line.split("\\t");
					// UniProt ID is the 6th column, and FlyBase ID is the 3rd columne
					if (parts.length>=6)
					{
						if (!"".equals(parts[5].trim()))
						{
							mappings.put(parts[5], parts[2]);
						}
						else
						{
							logger.warn("Could not extract the UniProt ID from line #{}", lineCount.get());
							// Only print the bad line in TRACE logging mode.
							logger.trace("{}",line);
						}
					}
				});
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}	
			logger.debug("Number of UniProt IDs in mapping: {}; Number of lines processed: {}",mappings.keySet().size(), lineCount.get());
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		return mappings;
	}

}
