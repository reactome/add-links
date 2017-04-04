package org.reactome.addlinks.fileprocessors;

import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

public class ZincOrthologsFileProcessor extends FileProcessor<String>
{

	public ZincOrthologsFileProcessor()
	{
		super(null);
	}
	
	public ZincOrthologsFileProcessor(String processorName)
	{
		this.processorName = processorName;
	}
	
	@Override
	public Map<String, String> getIdMappingsFromFile()
	{
		Map<String, String> mappings = new HashMap<String, String>();
		// Record structures are:
		/*
		name,	description,	uniprot,	chembl,	gene_name
		MGA_HUMAN,	Maltase-glucoamylase,	O43451,	CHEMBL2074,	MGAM
		*/
		// We need name (as ZINC Ortholog) and uniprot.
		try
		{
			Files.readAllLines(this.pathToFile).stream().sequential().forEach(line -> {
				// We want to map from UniProt to ZINC Ortholog
				String[] parts = line.split(",");
				mappings.put(parts[2], parts[0]);
			});
		}
		catch (IOException e)
		{
			e.printStackTrace();
			throw new Error(e);
		}
		logger.info("{} values extracted from ZINC Orthologs file.", mappings.keySet().size());
		return mappings;
	}

}
