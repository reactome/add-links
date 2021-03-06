package org.reactome.addlinks.fileprocessors.zinc;

import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import org.reactome.addlinks.fileprocessors.FileProcessor;

public class ZincOrthologsFileProcessor extends FileProcessor<String>
{

	public ZincOrthologsFileProcessor()
	{
		super(null);
	}

	public ZincOrthologsFileProcessor(String processorName)
	{
		super(processorName);
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
			// skip the first line - it is a header line.
			Files.readAllLines(this.pathToFile).stream().skip(1).sequential().forEach(line -> {
				// We want to map from UniProt to ZINC Ortholog
				String[] parts = line.split(",");
				mappings.put(parts[2], parts[0]);
			});
			// NOTE: Should also check http://zinc15.docking.org/orthologs/SRC_CHICK/predictions/subsets/purchasable.csv (example link) and if line count > 1 (meaning: more than 1 header line) the link is valid!
		}
		catch (IOException e)
		{
			e.printStackTrace();
			throw new Error(e);
		}
		this.logger.info("{} values extracted from ZINC Orthologs file.", mappings.keySet().size());
		return mappings;
	}

}
