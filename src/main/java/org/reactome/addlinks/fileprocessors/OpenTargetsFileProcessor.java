package org.reactome.addlinks.fileprocessors;

import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Processes TSV files for mapping to OpenTargets.
 * Files will be formatted like this: ${UniProt ID}\t${ENSEMBL ID}
 * @author sshorser
 *
 */
public class OpenTargetsFileProcessor extends FileProcessor<String>
{
	public OpenTargetsFileProcessor()
	{
		super();
	}
	
	public OpenTargetsFileProcessor(String processorName)
	{
		super(processorName);
	}
	
	@Override
	public Map<String, String> getIdMappingsFromFile()
	{
		Map<String, String> uniprotToEnsemblForOpenTargetsMap = new HashMap<String, String>();
		AtomicInteger lineCount = new AtomicInteger(0);
		try
		{
			Files.readAllLines(this.pathToFile).forEach( line -> {
				String[] parts = line.split("\t");
				lineCount.incrementAndGet();
				if (parts.length == 2)
				{
					String uniprotID = parts[0];
					String ensemblID = parts[1];
					uniprotToEnsemblForOpenTargetsMap.put(uniprotID, ensemblID);
				}
				else
				{
					logger.warn("File from OpenTargets could not be split to exactly two parts: \"{}\"", line);
				}
			} );
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		logger.info("{} lines processed, {} keys added to map.", lineCount.get(), uniprotToEnsemblForOpenTargetsMap.keySet().size());
		return uniprotToEnsemblForOpenTargetsMap;
	}

}
