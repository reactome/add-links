package org.reactome.addlinks.fileprocessors;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Returns a mapping of EC numbers, and the UniProt accessions that are associated with them.
 * @author sshorser
 *
 */
public class IntEnzFileProcessor extends FileProcessor<List<String>>
{
	private static final Logger logger = LogManager.getLogger();
	
	@Override
	public Map<String, List<String>> getIdMappingsFromFile()
	{
		Map<String, List<String>> mappings = new HashMap<String, List<String>>();
		try
		{
			String ecNum = new String();
			List<String> uniprots = new ArrayList<String>();

			for (String line : Files.readAllLines(this.pathToFile))
			{
				// "ID" is the prefix for a line with an EC number.
				if (line.startsWith("//"))
				{
					// The "//" means a new record is begining,
					// so update the main mapping.
					// Then clear (or re-initialize) all fields.
					mappings.put(ecNum, uniprots);
					//logger.trace("{} : {}", ecNum, uniprots);
					//ecNum = new String();
					uniprots.clear();// = new ArrayList<String>();
				}
				else if (line.startsWith("ID"))
				{
					// split on space. 
					line = line.replaceAll("^ID\\W*", "");
					ecNum = line.trim();
				}
				else if (line.startsWith("DR"))
				{
					// remove the leading DR and whitespace.
					line = line.replaceFirst("^DR\\W*", "");
					// There will be UP TO 3 mappings per line.
					String[] parts = line.split(";");
					for (String part : parts)
					{
						// each mapping is of the form <UNIPROT_ACCESSION> , <UNIPROT_NAME??> - we only want the accession.
						String[] moreParts = part.split(",");
						uniprots.add(moreParts[0].trim());
					}
				}
			}
		}
		catch (IOException e)
		{
			throw new Error(e);
		}
		
		logger.info("{} EC numbers in mapping.", mappings.keySet().size());
		return mappings;
	}

}
