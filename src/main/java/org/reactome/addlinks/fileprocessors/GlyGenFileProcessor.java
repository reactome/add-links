package org.reactome.addlinks.fileprocessors;

import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;

public class GlyGenFileProcessor extends FileProcessor<String>
{

	public GlyGenFileProcessor()
	{
		super(null);
	}

	public GlyGenFileProcessor(String processorName)
	{
		super(processorName);
	}

	@Override
	public Map<String, String> getIdMappingsFromFile()
	{
		Map<String, String> mapping = new HashMap<>();

		try(CSVParser parser = new CSVParser(Files.newBufferedReader(this.pathToFile), CSVFormat.DEFAULT.withDelimiter('\t').withFirstRecordAsHeader()))
		{
			parser.forEach( line -> {
				String uniprotIdentifier = line.get("uniprotkb_primary_accession");
				// GlyGen uses UniProt identiifers as their primary identifier but we need to return a MAP so we'll just map the key to the value.
				mapping.put(uniprotIdentifier, uniprotIdentifier);
			});
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}

		return mapping;
	}

}
