package org.reactome.addlinks.fileprocessors;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;

import static org.reactome.addlinks.fileprocessors.gtp.Utils.getCSVParser;

public class PharmacoDBFileProcessor extends FileProcessor<String> {

	private Path pharmacodbFilePath;
	private Path guideToPharmacologyFilePath;
	
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
	
	public void setPathToGuideToPharmacologyFile(Path path)
	{
		this.guideToPharmacologyFilePath = path;
	}
	
	@Override
	public Map<String, String> getIdMappingsFromFile() {
		// First, we need to use Guide To Pharmacology file to create Guide To Pharmacology->PubChem mapping.
		// Then we use the PharmacoDB file to create PubChecm->PharmacoDB mapping.
		// The result should be Guide To Pharmacology->PharmacoDB mapping.
		
		// Process Guide To Pharmacology file
		Map<String, String> guideToPharmacology2PubChem = processGuideToPharmacologyFile();
		logger.info("There are {} Guide To Pharmacology->PubChem mappings", guideToPharmacology2PubChem.keySet().size());
		
		// Process PharmacoDB file.
		Map<String, String> pubChem2Pharmacodb = processPharmacoDBFile();
		logger.info("There are {} PubChem->PharmacoDB mappings", pubChem2Pharmacodb.keySet().size());
		
		Map<String, String> guideToPharmacology2PharmacoDB = new HashMap<>(guideToPharmacology2PubChem.keySet().size());
		// Now we need to create the map that goes from Guide To Pharmacology to PharmacoDB.
		for (Entry<String, String> entry : guideToPharmacology2PubChem.entrySet()) {
			String guideToPharmacologyId = entry.getKey();
			String pubChemId = entry.getValue();
			String pharmacoDBId = pubChem2Pharmacodb.get(pubChemId);

			// The regex "PDBC\d+" is because the sample file I got from PharmacoDB sometimes has none-identifiers
			// in the PharmacoDB.uid field (such as "TRUE", "NA", etc...)
			// Hopefully those issues will be resolved by the time they are publishing this file live.
			if (isValidPharmacoDBId(pharmacoDBId)) {
				guideToPharmacology2PharmacoDB.put(guideToPharmacologyId, pharmacoDBId);
				logger.info("{}    {}", guideToPharmacologyId, pharmacoDBId);
			}
		}
		logger.info("There are {} Guide To Pharmacology->PharmacoDB mappings.", guideToPharmacology2PharmacoDB.size());
		return guideToPharmacology2PharmacoDB;
	}

	private Map<String, String> processPharmacoDBFile() {
		Map<String, String> mapping = new HashMap<>();

		try(CSVParser parser = new CSVParser(
			new FileReader(this.pharmacodbFilePath.toString()), CSVFormat.DEFAULT.withFirstRecordAsHeader())
		) {
			parser.forEach(record -> {
				String status = record.get("PharmacoDB.status");
				if (status.equalsIgnoreCase("present")) {
					mapping.put(record.get("cid"), record.get("PharmacoDB.uid"));
				}
			});
		} catch (IOException e) {
			logger.error("IOException was caught: ", e);
		}

		return mapping;
	}

	private Map<String, String> processGuideToPharmacologyFile() {
		Map<String, String> mapping = new HashMap<>();

		try(CSVParser parser = getCSVParser(this.guideToPharmacologyFilePath)) {
			parser.forEach(record -> mapping.put(record.get("Ligand id"), record.get("PubChem CID")));
		} catch (IOException e) {
			logger.error("IOException was caught: ", e);
		}

		return mapping;
	}

	private boolean isValidPharmacoDBId(String pharmacoDBId) {
		return pharmacoDBId != null &&
			!pharmacoDBId.isEmpty() &&
			!pharmacoDBId.trim().isEmpty() &&
			pharmacoDBId.matches("PDBC\\d+");
	}
}
