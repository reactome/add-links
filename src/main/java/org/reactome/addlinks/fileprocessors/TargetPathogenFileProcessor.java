/**
 *
 */
package org.reactome.addlinks.fileprocessors;

import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Processes the Target Pathogen files. Currently, this file can be found here: https://target.sbg.qb.fcen.uba.ar/patho/reactome_map <br/>
 * File structure is comma-separate values, with these fields: target_url, genome, target_genes, reactome_id, uniprot
 * <br/>
 * <br/>
 * NOTE: "target_genes" is a pipe-separated list of gene symbols, BUT sometimes also contains commas (and sometimes spaces as well)
 * with pipes such as "|RP-L40e,|RPL40," and "|APU18_05390| pNDM102337_153|groEL| MS6198_A148| CA268_28940| "
 * and in some cases, it doesn't seem to exist at all.
 * @author sshorser
 *
 */
public class TargetPathogenFileProcessor extends FileProcessor<String>
{

	public TargetPathogenFileProcessor(String processorName)
	{
		super(processorName);
	}

	public TargetPathogenFileProcessor()
	{
		super(null);
	}

	@Override
	public Map<String, String> getIdMappingsFromFile()
	{
		AtomicInteger lineCount = new AtomicInteger(0);
		Map<String, String> mapping = new HashMap<>();
		try
		{
			// Skip the fist line because it's the header line.
			Files.lines(this.pathToFile).skip(1).sequential().forEach( line ->
			{
				String[] lineParts = line.split(",");
				// the URL is always the first item. we will need to extract the Target Pathogen identifier from the URL.
				String urlToTargetPathogen = lineParts[0];
				// the UniProt identifier is that LAST item.
				String uniprotID = lineParts[lineParts.length - 1];
				// the Reactome ID is the second last item.
				String reactomeID = lineParts[lineParts.length - 2];
				// not going to bother extracting genes from this file, I don't think we will need them if we already have the UniProt and Reactome identifier.

				// URLs look like this: http://target.sbg.qb.fcen.uba.ar/patho/protein/5642590bbe737e6c7a9fd9a0
				// We need to extract "5642590bbe737e6c7a9fd9a0" as the identifier.
				String[] urlParts = urlToTargetPathogen.split("\\/");
				String targetPathogenIdentifier = urlParts[urlParts.length - 1];
				// Let's pass preserve both the Reactome and UniProt identifiers in the output, in case we need to create references to both types of identifiers.
				if (mapping.containsKey(targetPathogenIdentifier))
				{
					mapping.put(targetPathogenIdentifier, mapping.get(targetPathogenIdentifier) + "|" + reactomeID + "|" + uniprotID);
				}
				else
				{
					mapping.put(targetPathogenIdentifier, reactomeID + "|" + uniprotID);
				}
				lineCount.incrementAndGet();
			});
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		this.logger.info("Lines in mapping file: {}; Mappings from TargetPathogen: {}.", lineCount.get(), mapping.keySet().size());
		for (String tpIdentifier : mapping.keySet())
		{
			this.logger.debug("{}\t{}",tpIdentifier, mapping.get(tpIdentifier));
		}
		return mapping;
	}

}
