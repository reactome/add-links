package org.reactome.addlinks.fileprocessors;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FlyBaseFileProcessor extends FileProcessor
{
	private static final Logger logger = LogManager.getLogger();

	@Override
	public Map<String, String> getIdMappingsFromFile()
	{
		Map<String,String> mappings = new HashMap<String, String>();
		try
		{
			String unzippedFile = this.unzipFile(this.pathToFile);
	//		if (unzippedFiles.size() > 1)
	//		{
	//			logger.error("FlyBase file processor was only expecting one file, but the zipped file somehow contained more: {}", String.join(", ", unzippedFiles));
	//			throw new RuntimeException("Too many input files!");
	//		}
	//		else if (unzippedFiles.size() == 0)
	//		{
	//			logger.error("File processor received no names of unzipped files!");
	//			throw new RuntimeException("Not enough input files!");
	//		}
			
	//		unzippedFiles.stream().forEach( s -> 
	//		{
				AtomicInteger lineCount = new AtomicInteger(0);
				try
				{
					//I added ".sequential()" because I have line counter and I want to ensure that it works properly.
					Files.lines(Paths.get(unzippedFile)).filter(p -> !p.startsWith("#")).sequential().forEach( line ->
					{
						lineCount.set(lineCount.get() + 1);
						String[] parts = line.split("\\t");
						// UniProt ID is the last column - if split returns 5 things than there is a UniProt ID. 
						if (parts.length==5)
						{
							if (!"".equals(parts[4].trim()))
							{
								mappings.put(parts[4], parts[1]);
							}
							else
							{
								logger.warn("Could not extract the UniProt ID from line #{}", lineCount.get());
							}
						}
					});
				}
				catch (IOException e)
				{
					e.printStackTrace();
				}	
	//		});
			logger.debug("Number of UniProt IDs in mapping: {}; Number of lines processed: {}",mappings.keySet().size(), lineCount.get());
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		return mappings;
	}

}
