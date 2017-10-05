package org.reactome.addlinks.fileprocessors.ensembl;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


/**
 * This class will combine the results from:
 * 	 -  ENSP_batch_lookup.$species.xml.transformed.csv
 * 	 -  ENST_batch_lookup.$species.xml.transformed.csv
 * 	 -  ensembl_to_ALL.$species.xml.$dbName.transformed.tsv
 * 
 * It will produce an output file that contains: The ENSP ID, ENST ID, ENSG ID, cross-reference ID, cross-reference datbase name.
 * @author sshorser
 *
 */
public class EnsemblFileAggregator
{
	private static final Logger logger = LogManager.getLogger();
	private String speciesID;
	private List<String> dbNames;
	private String rootPath;
	
	public EnsemblFileAggregator(String speciesID, List<String> dbNames, String rootPath)
	{
		this.speciesID = speciesID;
		this.dbNames = dbNames;
		this.rootPath = rootPath;
	}

	private String[] parseLine(String fileNamePattern, String line) throws Error
	{
		String parts[];
		if (fileNamePattern.toLowerCase().endsWith("csv"))
		{
			parts = line.split(",");
		}
		else if (fileNamePattern.toLowerCase().endsWith("tsv"))
		{
			parts = line.split("\t");
		}
		else
		{
			logger.error("File {} does not end with \"csv\" or \"tsv\", so it cannot be determined how to process it.", fileNamePattern);
			throw new Error ("File \""+fileNamePattern+"\" must be either a tsv or csv.");
		}
		return parts;
	}

	
	private void buildMappingListsFromFile(String fileNamePattern, Map<String, List<String>> mapping)
	{
		if (Files.exists(Paths.get(fileNamePattern)))
		{
			try
			{
				Files.lines(Paths.get(fileNamePattern)).sequential().forEach( line -> {
					String[] parts = parseLine(fileNamePattern, line);
					//mapping.put(parts[0], parts[1]);
					if (mapping.containsKey(parts[0]))
					{
						// no point in having duplicates
						if (!mapping.get(parts[0]).contains(parts[1]))
						{
							mapping.get(parts[0]).add(parts[1]);
							logger.trace("{} maps to multiple cross-references {} ", parts[0],mapping.get(parts[0]));
						}
					}
					else
					{
						mapping.put(parts[0], new ArrayList<String>(Arrays.asList(parts[1])) );
					}
					
				});
			}
			catch (IOException e)
			{
				throw new Error(e);
			}
		}
		else
		{
			logger.trace("File {} does not exist.", fileNamePattern);
		}
	}

	private void buildMappingFromFile(String fileNamePattern, Map<String, String> mapping)
	{
		if (Files.exists(Paths.get(fileNamePattern)))
		{
			try
			{
				Files.lines(Paths.get(fileNamePattern)).sequential().forEach( line -> {
					String[] parts = parseLine(fileNamePattern, line);
					mapping.put(parts[0], parts[1]);
					
				});
			}
			catch (IOException e)
			{
				throw new Error(e);
			}
		}
		else
		{
			logger.trace("File {} does not exist.", fileNamePattern);
		}
	}
	
	public void createAggregateFile()
	{
		logger.info("Aggregating for species: {}", this.speciesID);
		List<EnsemblMapping> mappings = new ArrayList<EnsemblMapping>();
		
		Map<String, String> enspToEnst = new HashMap<String, String>();
		Map<String, String> enstToEnsg = new HashMap<String, String>();
		Map<String,Map<String, List<String>>> ensgToXrefs = new HashMap<String, Map<String, List<String>>>();
		//first, process the ENSP file which will map to ENST.
		buildMappingFromFile(rootPath + "/ENSP_batch_lookup." + this.speciesID + ".xml.transformed.csv", enspToEnst);
		if (enspToEnst.size() > 0)
		{
			logger.info("  {} ENSP->ENST mappings.", enspToEnst.size());
		}
		//second, process the ENST lookup file with ENST -> ENSG mappings.
		buildMappingFromFile(rootPath + "/ENST_batch_lookup." + this.speciesID + ".xml.transformed.csv", enstToEnsg);
		if (enstToEnsg.size() > 0)
		{
			logger.info("  {} ENST->ENSG mappings.", enstToEnsg.size());
		}
		//third, process the file which maps ENSG to cross-references.
		for (String dbName : this.dbNames)
		{
			ensgToXrefs.put(dbName, new HashMap<String, List<String>>());
			
			buildMappingListsFromFile(rootPath + "/ensembl_to_ALL." + this.speciesID + ".xml." + dbName + ".transformed.tsv", ensgToXrefs.get(dbName));
			if (ensgToXrefs.get(dbName).size() > 0)
			{
				logger.info("  for db {}, {} ENSG->Cross-Reference mappings.", dbName, ensgToXrefs.get(dbName).size());
			}
		}
		
		// Now we have to create the ENSP -> ENST -> ENSG -> Xref mappings.
		for (String ensp : enspToEnst.keySet())
		{
			for (String dbName : this.dbNames)
			{
				String enst = enspToEnst.get(ensp);
				String ensg = enstToEnsg.get(enst);
				EnsemblMapping ensemblMapping = null;

				//String xref = ensgToXref.get(ensg);
				//String xref = ensgToXrefs.get(dbName).get(ensg);
				if (ensgToXrefs.get(dbName).get(ensg) != null)
				{
					for (String xref : ensgToXrefs.get(dbName).get(ensg) )
					{
						ensemblMapping = new EnsemblMapping(ensp, enst, ensg, xref, dbName);
						mappings.add(ensemblMapping);
					}
				}
				else
				{
					ensemblMapping = new EnsemblMapping(ensp, enst, ensg, null, dbName);
					mappings.add(ensemblMapping);
				}
			}
		}
		
		Path pathToOutfile = Paths.get(rootPath + "/ensembl_p2xref_mapping." + speciesID + ".csv");

		StringBuilder sb = new StringBuilder();
		for (EnsemblMapping mapping : mappings)
		{
			sb.append(mapping.getEnsp()).append(",")
				.append(mapping.getEnst()).append(",")
				.append(mapping.getEnsg()).append(",")
				.append(mapping.getCrossReferenceDBName()).append(",")
				.append(mapping.getCrossReference()).append("\n");
		}
		
		try
		{
			Files.write(pathToOutfile, sb.toString().getBytes(Charset.forName("UTF-8")));
		}
		catch (IOException e)
		{
			throw new Error(e);
		}
	}
}

