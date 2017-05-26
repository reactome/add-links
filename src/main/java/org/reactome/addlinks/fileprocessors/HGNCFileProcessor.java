package org.reactome.addlinks.fileprocessors;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class HGNCFileProcessor extends FileProcessor<List<String>>
{

	public HGNCFileProcessor(String processorName)
	{
		super(processorName);
	}

	public HGNCFileProcessor()
	{
		super(null);
	}
	
	@Override
	public  Map<String, List<String>> getIdMappingsFromFile()
	{
		// HGNC symbol is in the first column. Uniprot ID is in the 26th column.  Will will map 1:n, UniProt to HGNC
		// A HGNC Id could map to multiple Uniprot IDs, they will be separated by "|".
		Map<String, List<String>> mappings = new HashMap<String, List<String>>();
		int lineCount = 0;
		try
		{
			//Read the file, skipping the first line (which is just column headers).
			for (String line : Files.readAllLines(this.pathToFile).stream().skip(1).collect(Collectors.toList()))
			{
				lineCount++;
				String[] parts = line.split("\t");
				//remove "HGNC:" since that will be a part of the ReferenceDatabase access URL.
				String hgncID = parts[0].replaceAll("HGNC:", "");
				if (parts.length >= 26)
				{
					String uniprotIDLine = parts[25];
					if (uniprotIDLine != null)
					{
						String[] uniprotIDs = uniprotIDLine.split("\\|");
						if (uniprotIDs.length > 0)
						{
							for (String uniprotID : uniprotIDs)
							{
								if (!mappings.containsKey(uniprotID))
								{
									mappings.put(uniprotID, new ArrayList<String>(Arrays.asList(hgncID)));
								}
								else
								{
									mappings.get(uniprotID).add(hgncID);
								}
							}
						}
					}
				}
			}
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		logger.info("{} HGNC IDs in mapping file ; {} had >= 1 UniProt ID.", lineCount, mappings.keySet().size());
		return mappings;
	}

}
