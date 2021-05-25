package org.reactome.addlinks.fileprocessors.zinc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.reactome.addlinks.fileprocessors.FileProcessor;

public class ZincMoleculesFileProcessor extends FileProcessor<List<String>>
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
	public Map<String, List<String>> getIdMappingsFromFile()
	{
		Map<String, List<String>> chebiToZincMapping = new HashMap<String, List<String>>() ;
		try
		{
			String pathToUnzippedFile = this.unzipFile(this.pathToFile);
			Files.readAllLines(Paths.get(pathToUnzippedFile)).stream()
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
					chebiToZincMapping.get(chebiId).add(zincId);
				}
				else
				{
					chebiToZincMapping.put(chebiId, new ArrayList<String>(Arrays.asList(zincId)));
				}
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
