package org.reactome.addlinks.fileprocessors.ensembl;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.reactome.addlinks.fileprocessors.FileProcessor;

/**
 * For processing the output of the Ensembl aggregate files, which contain ENSP, ENST, ENSG, XRef
 * @author sshorser
 *
 */
public class EnsemblAggregateFileProcessor extends FileProcessor<Map<String, List<String>>>
{

	public EnsemblAggregateFileProcessor(String processorName)
	{
		super(processorName);
	}

	public EnsemblAggregateFileProcessor()
	{
		super();
	}

	public enum EnsemblAggregateProcessingMode
	{
		ENSP_TO_ENSG,
		XREF
	}
	
	private EnsemblAggregateProcessingMode mode;
	
	@Override
	public Map<String, Map<String, List<String>>> getIdMappingsFromFile()
	{
		Map<String, Map<String, List<String>>> mappings = new HashMap<String, Map<String, List<String>>>();
		try
		{
			Files.lines(this.pathToFile).sequential()
			// If we are doing cross-references, we want to ignore lines ending in NULL because those lines did not have a cross-reference mapping.
			.filter(line -> (this.mode == EnsemblAggregateProcessingMode.XREF && !line.trim().endsWith("null")) || this.mode == EnsemblAggregateProcessingMode.ENSP_TO_ENSG)
			.forEach( line ->
			{
				String parts[] = line.split(",");
				String ensp = parts[0];
				// String enst = parts[1]; // this won't be used for anything.
				String ensg = parts[2];
				String dbName = parts[3];
				String xref = parts[4];
				
				String targetValue;
				
				// force dbName to always be Ensembl Gene when mapping Ensembl Protein to Ensembl Gene.
				if (this.mode == EnsemblAggregateProcessingMode.ENSP_TO_ENSG)
				{
					dbName = "ENSG";
					targetValue = ensg;
				}
				else
				{
					targetValue = xref;
				}
				
				// Do we need to create a key for this db?
				if (!mappings.containsKey(dbName))
				{
					mappings.put(dbName, new HashMap<String, List<String>>());
				}
				
				if (mappings.get(dbName).containsKey(ensp))
				{
					// prevent duplicates. Could happen when mode == ENSP_TO_ENSG 
					// because there could be multiple entries in an aggregate file
					// when the same identifier maps to multiple XREF identifiers.
					// But for ENSP -> ENSG, we only want a SINGLE mapping.
					if (!mappings.get(dbName).get(ensp).contains(targetValue) )
					{
						mappings.get(dbName).get(ensp).add(targetValue);
					}
				}
				else
				{
					List<String> xrefList = new ArrayList<String>();
					xrefList.add(targetValue);
					mappings.get(dbName).put(ensp, xrefList);
				}
				
			});
		}
		catch (IOException e)
		{
			// IOException - can't read/open the file - no way to recover so just throw an Error.
			throw new Error(e);
		}
		
		for (String dbName : mappings.keySet())
		{
			logger.info("For {}, {} records.", dbName, mappings.get(dbName).keySet().size());
		}
		
		return mappings;
	}

	public void setMode(EnsemblAggregateProcessingMode mode)
	{
		this.mode = mode;
	}
	
}
