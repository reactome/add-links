package org.reactome.addlinks.fileprocessors;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

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
		Map<String,String> mappings = new ConcurrentHashMap<>();
		try
		{
			String unzippedFile = this.unzipFile(this.pathToFile);
			AtomicInteger lineCount = new AtomicInteger(0);

			try(Stream<String> lineStream = Files.lines(Paths.get(unzippedFile), StandardCharsets.ISO_8859_1))
			{
				// Use parallel streams at your own peril! I kept running out of RAM and
				// crashing on my OICR laptop - the server or the office-based workstation can
				// *probably* handle it but since I can't successfully test, I will leave this
				// as a sequential stream, for now. But honestly, it took my laptop ~7 minutes
				// to process the file. I can't imagine a parallel stream would save more
				// than 3 or 4 minutes...
				lineStream.filter(p -> !p.startsWith("#")).sequential().forEach( line ->
				{
					lineCount.incrementAndGet();
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
