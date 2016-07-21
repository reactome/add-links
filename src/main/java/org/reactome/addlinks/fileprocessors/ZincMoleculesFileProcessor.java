package org.reactome.addlinks.fileprocessors;

import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ZincMoleculesFileProcessor extends FileProcessor
{

	private static final Logger logger = LogManager.getLogger();
	@Override
	public Map<String, String> getIdMappingsFromFile()
	{
		Map<String, String> chebiToZincMapping = new HashMap<String, String>() ;
		try
		{
			Files.readAllLines(this.pathToFile).stream()
				.filter(line -> line.matches("ZINC\\d+\\tChEBI.*"))
				.forEach( line ->
			{
				String[] parts = line.split("\t");
				String chebiId = parts[2];
				chebiId = chebiId.replace("CHEBI:", "");
				String zincId = parts[0];
				zincId = zincId.replace("ZINC", "");
				//if the key is already in the map, append it.
				if (chebiToZincMapping.containsKey(chebiId))
				{
					zincId = chebiToZincMapping.get(chebiId) + "," + zincId ;
				}
				chebiToZincMapping.put(chebiId, zincId);
			});
		} catch (IOException e)
		{
			e.printStackTrace();
		}
		logger.info("Number of ChEBI IDs in mapping: {}", chebiToZincMapping.size());
		return chebiToZincMapping;
	}

}
