package org.reactome.addlinks.fileprocessors;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Returns a mapping of UniProt accessions mapped to EC numbers.
 * @author sshorser
 *
 */
public class IntEnzFileProcessor extends FileProcessor<List<String>>
{
	private static final Logger logger = LogManager.getLogger();
	
	@Override
	public Map<String, List<String>> getIdMappingsFromFile()
	{
		int accessionsWithMultipleECs = 0;
		Map<String, List<String>> mappings = new HashMap<String, List<String>>();
		try
		{
			String ecNum = new String();

			for (String line : Files.readAllLines(this.pathToFile))
			{
				// "ID" is the prefix for a line with an EC number.
				if (line.startsWith("//"))
				{
					// The "//" means a new record is begining,
					//logger.trace("{} : {}", ecNum, uniprots);
					ecNum = new String();
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
						//uniprots.add(moreParts[0].trim());
						if (!mappings.containsKey(moreParts[0]))
						{
							mappings.put(moreParts[0], new ArrayList<String>(Arrays.asList(ecNum)));
						}
						else
						{
							mappings.get(moreParts[0]).add(ecNum);
							accessionsWithMultipleECs++;
						}
					}
				}
			}
		}
		catch (IOException e)
		{
			throw new Error(e);
		}
		
		logger.info("{} UniProt accessions in mapping, {} with > 1 EC.", mappings.keySet().size(), accessionsWithMultipleECs);
		return mappings;
	}

}
