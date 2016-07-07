package org.reactome.addlinks.fileprocessors;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FlyBaseFileProcessor extends FileProcessor
{
	private static final Logger logger = LogManager.getLogger();
	private Path pathToFile;
	@Override
	public void setPath(Path p)
	{
		this.pathToFile = p;
	}

	@Override
	public Map<String, String> getIdMappingsFromFile()
	{
		Map<String,String> mappings = new HashMap<String, String>();
		List<String> unzippedFiles = this.unzipFile(this.pathToFile);
		if (unzippedFiles.size() > 1)
		{
			logger.error("FlyBase file processor was only expecting one file, but the zipped file somehow contained more: {}", String.join(", ", unzippedFiles));
			throw new RuntimeException("Too many input files!");
		}
		else if (unzippedFiles.size() == 0)
		{
			logger.error("File processor received no names of unzipped files!");
			throw new RuntimeException("Not enough input files!");
		}
		unzippedFiles.stream().forEach( s -> 
		{
			try
			{
				Files.lines(Paths.get(s)).filter(p -> !p.startsWith("#")).forEach( line ->
				{
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
							logger.warn("Could not extract the UniProt ID from line #");
						}
					}
				});
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}	
		});
		logger.debug("Number of UniProt IDs in mapping: {}",mappings.keySet().size());
		return mappings;
	}

}
