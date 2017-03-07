package org.reactome.addlinks.fileprocessors;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class ZincMoleculesFileProcessor extends FileProcessor<String>
{

	public ZincMoleculesFileProcessor()
	{
		super(null);
	}
	public ZincMoleculesFileProcessor(String processorName)
	{
		super(processorName);
	}

	@Override
	public Map<String, String> getIdMappingsFromFile()
	{
		Map<String, String> chebiToZincMapping = new HashMap<String, String>() ;
		try
		{
			String pathToFile = this.unzipFile(this.pathToFile);
			Files.readAllLines(Paths.get(pathToFile + "/chebi.info.txt")).stream()
				// Filter so that only ChEBI lines are processed.
				.filter(line -> line.matches("CHEBI:\\d+\\tZINC.*"))
				.forEach( line ->
			{
				String[] parts = line.split("\t");
				String chebiId = parts[0];
				chebiId = chebiId.replace("CHEBI:", "");
				String zincId = parts[1];
				zincId = zincId.replace("ZINC", "");
				//if the key is already in the map, append it.
				if (chebiToZincMapping.containsKey(chebiId))
				{
					zincId = chebiToZincMapping.get(chebiId) + "," + zincId ;
				}
				chebiToZincMapping.put(chebiId, zincId);
			});
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		logger.info("Number of ChEBI IDs in mapping: {}", chebiToZincMapping.size());
		return chebiToZincMapping;
	}

}
