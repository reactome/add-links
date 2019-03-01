package org.reactome.addlinks.fileprocessors;

import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.csv.CSVFormat;

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
				// Use the Apache CSVFormat class. The CTD file now contains quoted (single *AND* double) strings which contain commans and that confuses the old process which simply
				// split each line on commas. I don't really feel like writing a parser for quoted commas.
				CSVFormat.DEFAULT.withCommentMarker('#').parse(new FileReader(unzippedFile)).forEach(line -> 
				{
					lineCount.set(lineCount.get() + 1);
					// NCBI Gene ID is in column #5 
					String ncbiGeneID = line.get(4);
					if (ncbiGeneID != null && !"".equals(ncbiGeneID.trim()))
					{
						// We're mapping the NCBI Gene ID to itself because that's the only part we're interested in, which we will use to filter later.
						mappings.put(ncbiGeneID, ncbiGeneID);
					}
					else
					{
						logger.warn("Could not extract the NCBI Gene ID from line #{}", lineCount.get());
						// Only print the bad line in TRACE logging mode.
						logger.trace("{}",line);
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
