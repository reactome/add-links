package org.reactome.addlinks.fileprocessors;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class COSMICFileProcessor extends FileProcessor<String>
{

	public COSMICFileProcessor()
	{
		super(null);
	}
	
	public COSMICFileProcessor(String processorName)
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
					if (parts.length > 0)
					{
						String geneName = parts[0];
						String hgncId = parts[3];
						// Could this file have multiple entries for a single gene name? Need to find out...
						mappings.put(geneName, hgncId);
					}
				});
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
			logger.debug("Number of COSMIC Gene Names in mapping: {}; Number of lines processed: {}",mappings.keySet().size(), lineCount.get());
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		
		return mappings;
	}

}
