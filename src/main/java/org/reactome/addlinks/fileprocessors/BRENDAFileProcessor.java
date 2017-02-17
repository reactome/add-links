package org.reactome.addlinks.fileprocessors;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BRENDAFileProcessor extends GlobbedFileProcessor<List<String>>
{
	private Pattern brendaResultPattern = Pattern.compile("ecNumber\\*([0-9\\.]+)#sequence\\*[A-Z]*#noOfAminoAcids\\*\\d*#firstAccessionCode\\*([^#]+)#");
	
	private static final Logger logger = LogManager.getLogger();
	
	public BRENDAFileProcessor()
	{
		this.pattern = Pattern.compile("BRENDA\\.[^\\.]+\\.csv");
	}
	
	@Override
	protected void processFile(Path file, Map<String, List<String>> mappings)
	{
		try
		{
			Files.lines(file).sequential().forEach( line -> {
				String parts[] = line.split("\t");
				if (parts.length > 1)
				{
					int matchCount = 0;
					Matcher matcher = brendaResultPattern.matcher(parts[1]);
					
					while (matcher.find())
					{
						matchCount++;
						String ecNumber = matcher.group(1);
						String uniProtID = matcher.group(2);
						
						if (mappings.containsKey(uniProtID))
						{
							mappings.get(uniProtID).add(ecNumber);
						}
						else
						{
							mappings.put(uniProtID, new ArrayList<String>(Arrays.asList(ecNumber)));
						}
					}
					if (matchCount == 0)
					{
						logger.warn("The line {} did not have ANY matches for the pattern {}",line, brendaResultPattern.toString());
					}
				}
			});
		}
		catch (IOException e)
		{
			throw new Error(e);
		}
	}

}
