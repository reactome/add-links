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
 * File structure is tab-separate values, with these fields: target_url, reactome_id, uniprot
 * <br/>
 * <br/>
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
		super(TargetPathogenFileProcessor.class.getName());
	}

	@Override
	public Map<String, String> getIdMappingsFromFile()
	{
		AtomicInteger lineCount = new AtomicInteger(0);
		Map<String, String> mapping = new HashMap<>();
		try
		{
			Files.lines(this.pathToFile).sequential().forEach( line ->
			{
				String[] lineParts = line.split("\\t");

				String urlToTargetPathogen = lineParts[0];
				String reactomeID = lineParts[1];
				String uniprotID = lineParts[2];

				// URLs look like this: http://target.sbg.qb.fcen.uba.ar/patho/protein/5642590bbe737e6c7a9fd9a0
				// We need to extract "5642590bbe737e6c7a9fd9a0" as the identifier.
				String[] urlParts = urlToTargetPathogen.split("\\/");
				String targetPathogenIdentifier = urlParts[urlParts.length - 1];
				// Let's preserve both the Reactome and UniProt identifiers in the output, in case we need to create references to both types of identifiers.
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
