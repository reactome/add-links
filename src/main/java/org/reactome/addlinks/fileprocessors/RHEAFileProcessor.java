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


public class RHEAFileProcessor extends FileProcessor<List<String>>
{
	// Process this file: ftp://ftp.ebi.ac.uk/pub/databases/rhea/tsv/rhea2reactome.tsv
	// It's much simpler: It has a Rhea ID, and a Reactome ID on each line.
	
	private static final Logger logger = LogManager.getLogger();
	
	/**
	 * Will return RHEA IDs that map to a list of Reactome IDs. 
	 */
	@Override
	public Map<String, List<String>> getIdMappingsFromFile()
	{
		Map<String, List<String>> mappings = new HashMap<String, List<String>>();
		
		try
		{
			Files.lines(this.pathToFile).sequential()
				.filter(line -> !line.startsWith("RHEA_ID") && !line.trim().equals(""))
				.forEach( line ->
			{
				String parts[] = line.split("\t");
				/* RHEA mapping file looks like this:
				 * RHEA_ID	DIRECTION	MASTER_ID	ID
				 * 10041	LR	10040	R-HSA-176606.2
				 */
				String rheaID = parts[0];
				String reactomeID = parts[3];
				
				if (mappings.containsKey(rheaID))
				{
					mappings.get(rheaID).add(reactomeID);
				}
				else
				{
					mappings.put(rheaID, new ArrayList<String>(Arrays.asList(reactomeID)));
				}
			});
		} 
		catch (IOException e)
		{
			e.printStackTrace();
			throw new Error(e);
		}
		logger.info("Number of Rhea IDs in mapping: {}", mappings.keySet());
		return mappings;
	}

}
