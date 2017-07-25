package org.reactome.addlinks.fileprocessors;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class CTDFileProcessor extends FileProcessor<String>
{

	
	public CTDFileProcessor()
	{
		super(null);
	}
	
	public CTDFileProcessor(String processorName)
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
					String[] parts = line.split(",");
					// NCBI Gene ID is in column #5 
					if (parts.length > 5)
					{
						if (!"".equals(parts[4].trim()))
						{
							// We're mapping the NCBI Gene ID to itself because that's the only part we're interested in, which we will use to filter later.
							mappings.put(parts[4], parts[4]);
						}
						else
						{
							logger.warn("Could not extract the NCBI Gene ID from line #{}", lineCount.get());
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
			logger.debug("Number of NCBI Gene IDs in mapping: {}; Number of lines processed: {}",mappings.keySet().size(), lineCount.get());
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		return mappings;

	}

}
