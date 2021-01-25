package org.reactome.addlinks.fileprocessors;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;

public class PharmacodbFileProcessor extends FileProcessor<String>
{

	private String pharmacodbFilePath;
	private String IUPHARFilePath;
	
	public void setPharmacoDBFilePath(String path)
	{
		this.pharmacodbFilePath = path;
	}
	
	public void setIUPHARFilePath(String path)
	{
		this.IUPHARFilePath = path;
	}
	
	@Override
	public Map<String, String> getIdMappingsFromFile()
	{
		Map<String, String> iuphar2PubChem = new HashMap<>();
		Map<String, String> pubChem2Pharmacodb = new HashMap<>();
		
		// First, we need to use IUPHAR file to create IUPHAR->PubChem mapping.
		// Then we use the PharmacoDB file to create PubChecm->PharmacoDB mapping.
		// The result should be IUPHAR->PharmacoDB mapping.
		try(CSVParser iupharParser = new CSVParser(new FileReader(this.IUPHARFilePath), CSVFormat.DEFAULT.withFirstRecordAsHeader()))
		{
			iupharParser.forEach(record -> {
				iuphar2PubChem.put(record.get("Ligand id"), record.get("PubChem CID"));
			});
		}
		catch (FileNotFoundException e)
		{
			logger.error("The file {} was not found.", this.IUPHARFilePath);
		}
		catch (IOException e)
		{
			logger.error("IOException was caught: ", e);
		}
		
		try(CSVParser pharmacodbParser = new CSVParser(new FileReader(this.pharmacodbFilePath), CSVFormat.DEFAULT.withFirstRecordAsHeader()))
		{
			pharmacodbParser.forEach(record -> {
				pubChem2Pharmacodb.put(record.get("cid"), record.get("PharmacoDB.uid"));
			});
		}
		catch (FileNotFoundException e)
		{
			logger.error("The file {} was not found.", this.pharmacodbFilePath);
		}
		catch (IOException e)
		{
			logger.error("IOException was caught: ", e);
		}
		
		Map<String, String> iuphar2PharmacoDB = new HashMap<>(iuphar2PubChem.keySet().size());
		// Now we need to create the map that goes from IUPHAR to PharmacoDB.
		for (Entry<String, String> entry : iuphar2PubChem.entrySet())
		{
			String iuphar = entry.getKey();
			String pubChem = entry.getValue();
			String pharmacoDB = pubChem2Pharmacodb.get(pubChem);
			if (pharmacoDB != null && !pharmacoDB.isEmpty() && !pharmacoDB.isBlank())
			{
				iuphar2PharmacoDB.put(iuphar, pharmacoDB);
			}
		}
		
		return iuphar2PharmacoDB;
	}

}
