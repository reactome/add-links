package org.reactome.addlinks.fileprocessors;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class DOCKBlasterFileProcessor extends FileProcessor<ArrayList<String>>
{
	public DOCKBlasterFileProcessor(String processorName)
	{
		super(processorName);
	}

	public DOCKBlasterFileProcessor()
	{}

	@Override
	public Map<String, ArrayList<String>> getIdMappingsFromFile()
	{
		AtomicInteger lineCount = new AtomicInteger(0);
		Map<String, ArrayList<String>> uniprotsToPdbs = new HashMap<String,ArrayList<String>>();
		try
		{
			Files.lines(this.pathToFile).sequential().forEach( line ->
			{
				lineCount.incrementAndGet();
				String[] parts = line.split("\\t");
				String pdbID = parts[0];
				String uniProtID = parts[1];
				// The mappings are not 1:1. The left column is PDB ID, the right column is UniProt ID. 
				if (uniprotsToPdbs.containsKey(pdbID))
				{
					uniprotsToPdbs.get(pdbID).add(uniProtID);
				}
				else
				{
					ArrayList<String> values = new ArrayList<String>(); 
					values.add(uniProtID);
					uniprotsToPdbs.put(pdbID, values);
				}
			});
			logger.info("Processed {} lines.", lineCount.get());
			logger.info("Uniprot-to-PDB (for DOCKBlaster) map has {} unique keys.", uniprotsToPdbs.keySet().size());
		}
		catch (IOException e)
		{
			e.printStackTrace();
			throw new Error(e);
		}
		return uniprotsToPdbs;
	}

}
