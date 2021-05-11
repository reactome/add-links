package org.reactome.addlinks.fileprocessors.gtp;

import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.reactome.addlinks.fileprocessors.FileProcessor;

public class GuideToPharmacologyLigandsFileProcessor extends FileProcessor<String>
{

	public GuideToPharmacologyLigandsFileProcessor()
	{
		super(null);
	}

	public GuideToPharmacologyLigandsFileProcessor(String processorName)
	{
		super(processorName);
	}

	@Override
	public Map<String, String> getIdMappingsFromFile()
	{
		Map<String, String> mapping = new HashMap<>();
		try(CSVParser parser = new CSVParser(Files.newBufferedReader(this.pathToFile), CSVFormat.DEFAULT.withFirstRecordAsHeader()))
		{
			parser.forEach(line -> {
				// We need the ligand ID
				String ligandID = line.get("Ligand id");
				// we will try to map with ChEBI ID
				String chebiID = line.get("Chebi ID");
				// but for future reference, some entities don't have ChEBI IDs but they may have CHEMBL, PubChem, or UniProt
				// identifiers so you could also extract those mappings. It will make reference creation more complicated,
				// and it may be better to have a different file-process/reference-creator pair for the different sources that map to GtP
				// in the GtP Ligands identifiers mapping file. Or you could try to do it all in this file processor.
				mapping.put(chebiID, ligandID);
			});
		}
		catch (IOException e)
		{
			logger.error("There was a problem opening/reading the file " + this.pathToFile.toString(), e);
		}
		return mapping;
	}

}
