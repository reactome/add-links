package org.reactome.addlinks.fileprocessors;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;

public class PharmacoDBFileProcessor extends FileProcessor<String>
{

	private Path pharmacodbFilePath;
	private Path IUPHARFilePath;
	
	public PharmacoDBFileProcessor()
	{
		super(null);
	}
	
	public PharmacoDBFileProcessor(String processorName)
	{
		super(processorName);
	}
	
	public void setPathToPharmacoDBFile(Path path)
	{
		this.pharmacodbFilePath = path;
	}
	
	public void setPathToIUPHARFile(Path path)
	{
		this.IUPHARFilePath = path;
	}
	
	private Map<String, String> processFile(Path path, String mappingSourceField, String mappingTargetField)
	{
		Map<String, String> mapping = new HashMap<>();
		
		try(CSVParser parser = new CSVParser(new FileReader(path.toString()), CSVFormat.DEFAULT.withFirstRecordAsHeader()))
		{
			parser.forEach(record -> mapping.put(record.get(mappingSourceField), record.get(mappingTargetField)) );
		}
		catch (FileNotFoundException e)
		{
			logger.error("The file {} was not found.", path);
		}
		catch (IOException e)
		{
			logger.error("IOException was caught: ", e);
		}
		
		return mapping;
	}
	
	@Override
	public Map<String, String> getIdMappingsFromFile()
	{
		Map<String, String> iuphar2PubChem = new HashMap<>();
		Map<String, String> pubChem2Pharmacodb = new HashMap<>();
		
		// First, we need to use IUPHAR file to create IUPHAR->PubChem mapping.
		// Then we use the PharmacoDB file to create PubChecm->PharmacoDB mapping.
		// The result should be IUPHAR->PharmacoDB mapping.
		
		// Process IUPHAR file
		iuphar2PubChem = this.processFile(this.IUPHARFilePath, "Ligand id", "PubChem CID");
		logger.info("There are {} IUPHAR->PubChem mappings", iuphar2PubChem.keySet().size());
		
		// Process PharmacoDB file.
		pubChem2Pharmacodb = this.processFile(this.pharmacodbFilePath, "cid", "PharmacoDB.uid");
		logger.info("There are {} PubChem->PharmacoDB mappings", pubChem2Pharmacodb.keySet().size());
		
		Map<String, String> iuphar2PharmacoDB = new HashMap<>(iuphar2PubChem.keySet().size());
		// Now we need to create the map that goes from IUPHAR to PharmacoDB.
		for (Entry<String, String> entry : iuphar2PubChem.entrySet())
		{
			String iuphar = entry.getKey();
			String pubChem = entry.getValue();
			String pharmacoDB = pubChem2Pharmacodb.get(pubChem);

			// The regex "PDBC\d+" is because the sample file I got from PharmacoDB sometimes has none-identifiers in the PharmacoDB.uid field (such as "TRUE", "NA", etc...)
			// Hopefully those issues will be resolved by the time they are publishing this file live.
			if (pharmacoDB != null && !pharmacoDB.isEmpty() && !pharmacoDB.isBlank() && pharmacoDB.matches("PDBC\\d+"))
			{
				iuphar2PharmacoDB.put(iuphar, pharmacoDB);
			}
		}
		logger.info("There are {} IUPHAR->PharmacoDB mappings.", iuphar2PharmacoDB.size());
		return iuphar2PharmacoDB;
	}

}
