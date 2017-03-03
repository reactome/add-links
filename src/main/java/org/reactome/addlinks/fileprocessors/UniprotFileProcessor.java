package org.reactome.addlinks.fileprocessors;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class UniprotFileProcessor extends GlobbedFileProcessor<Map<String,List<String>>>
{
	private static final Logger logger = LogManager.getLogger();
	
	public UniprotFileProcessor()
	{
	}
	
	public UniprotFileProcessor(String processorName)
	{
		super(processorName);
		this.pattern = Pattern.compile("[^.]+\\.([0-9]+)\\.[0-9]+\\.txt");
		//this.fileProcessor = this::processFile;
	}
	
	@Override
	protected void processFile(Path file, Map<String, Map<String,List<String>>> mapping)
	{
		//The returned structure looks like this:
		/*
		 * ROOT MAP
		 *   |
		 *   |
		 *   +--Species 1 (Key)
		 *   |    |
		 *   |    +--UniProt ID 1 (Key)
		 *   |    |     | (List)
		 *   |    |     +- Other DB ID 1
		 *   |    |     +- Other DB ID 2
		 *   |    |     +- Other DB ID 3
		 *   |    |
		 *   |    +--UniProt ID 2 (Key)
		 *   |          | (List)
		 *   |          +- Other DB ID 1
		 *   |          +- Other DB ID 2
		 *   |          +- Other DB ID 3
		 *   |
		 *   +--Species 2
		 *   |    |
		 *   |    +--UniProt ID 1 (Key)
		 *   |    |     | (List)
		 *   |    |     +- Other DB ID 1
		 *   |    |     +- Other DB ID 2
		 *   |    |     +- Other DB ID 3
		 *   ...
		 */
		Matcher matcher = this.pattern.matcher(file.getFileName().toString());
		matcher.matches();
		logger.info(matcher.groupCount());
		String speciesId = matcher.group(1);
		if (mapping.containsKey(speciesId))
		{
			logger.warn("You already have an entry for {}. You should only have ONE file for each species for each refDB. If you have more, something may have gone wrong...", speciesId);
		}
		else
		{
			Map<String,List<String>> submappings = new HashMap<String, List<String>>();
			mapping.put(speciesId, submappings);
		}
		logger.debug("Processing file: {}",file.getFileName());
		//Process the file.
		try
		{
			Files.readAllLines(file).stream().filter(p -> !p.equals("From\tTo")).forEach( line ->
			{
				String[] parts = line.split("\\t");
				String uniProtId = parts[0];
				String otherId = parts[1];
				if (mapping.get(speciesId).containsKey(uniProtId))
				{
					List<String> otherIds = mapping.get(speciesId).get(uniProtId);
					otherIds.add(otherId);
					mapping.get(speciesId).put(uniProtId, otherIds);
				}
				else
				{
					List<String> otherIds = new ArrayList<String>();
					otherIds.add(otherId);
					mapping.get(speciesId).put(uniProtId, otherIds);
				}
			});
		}
		catch (IOException e1)
		{
			e1.printStackTrace();
		}
	}

}
