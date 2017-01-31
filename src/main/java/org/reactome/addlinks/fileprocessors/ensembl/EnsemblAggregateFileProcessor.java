package org.reactome.addlinks.fileprocessors.ensembl;

import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.reactome.addlinks.fileprocessors.FileProcessor;

/**
 * For processing the output of the Ensembl aggregate files, which contain ENSP, ENST, ENSG, XRef
 * @author sshorser
 *
 */
public class EnsemblAggregateFileProcessor extends FileProcessor<Map<String, String>>
{

	private static final Logger logger = LogManager.getLogger();
	
	public enum EnsemblAggregateProcessingMode
	{
		ENSP_TO_ENSG,
		XREF
	}
	
	
	private EnsemblAggregateProcessingMode mode;
	
	@Override
	public Map<String, Map<String, String>> getIdMappingsFromFile()
	{
		Map<String, Map<String, String>> mappings = new HashMap<String, Map<String,String>>();
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
					mappings.put(dbName, new HashMap<String,String>());
				}
				
				mappings.get(dbName).put(ensp, targetValue);
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
