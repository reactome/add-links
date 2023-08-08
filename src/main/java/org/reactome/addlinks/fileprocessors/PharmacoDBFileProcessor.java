package org.reactome.addlinks.fileprocessors;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;

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
//			this.processFile(this.guideToPharmacologyFilePath, "Ligand id", "PubChem CID");
		logger.info("There are {} Guide To Pharmacology->PubChem mappings", guideToPharmacology2PubChem.keySet().size());
		
		// Process PharmacoDB file.
		Map<String, String> pubChem2Pharmacodb = processPharmacoDBFile();
//			this.processFile(this.pharmacodbFilePath, "cid", "PharmacoDB.uid");
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
			if (pharmacoDBId != null &&
				!pharmacoDBId.isEmpty() &&
				!pharmacoDBId.trim().isEmpty() &&
				pharmacoDBId.matches("PDBC\\d+")) {

				guideToPharmacology2PharmacoDB.put(guideToPharmacologyId, pharmacoDBId);
				logger.info("{}    {}", guideToPharmacologyId, pharmacoDBId);
			}
		}
		logger.info("There are {} Guide To Pharmacology->PharmacoDB mappings.", guideToPharmacology2PharmacoDB.size());
		return guideToPharmacology2PharmacoDB;
	}

//	/**
//	 * Processes a CSV file, assumed to have the first row as the Header. A map will be returned, keyed by
//	 * <code>mappingSourceField</code> with the values coming from <code>mappingTargetField</code>.
//	 * @param path - the path to the file to process.
//	 * @param mappingSourceField - the NAME of the source mapping field.
//	 * @param mappingTargetField - the NAME of the target mapping field.
//	 * @return A map, which maps <code>mappingSourceField</code> to <code>mappingTargetField</code>.
//	 */
//	private Map<String, String> processFile(Path path, String mappingSourceField, String mappingTargetField) {
//		Map<String, String> mapping = new HashMap<>();
//
//		try(CSVParser parser =
//				new CSVParser(new FileReader(path.toString()), CSVFormat.DEFAULT.withFirstRecordAsHeader())) {
//			parser.forEach(record -> mapping.put(record.get(mappingSourceField), record.get(mappingTargetField)));
//		} catch (IOException e) {
//			logger.error("IOException was caught: ", e);
//		}
//
//		return mapping;
//	}

	private Map<String, String> processPharmacoDBFile() {
        Map<String, String> mapping = new HashMap<>();

        try(CSVParser parser = new CSVParser(
            new FileReader(this.pharmacodbFilePath.toString()), CSVFormat.DEFAULT.withFirstRecordAsHeader())
        ) {
            parser.forEach(record -> mapping.put(record.get("cid"), record.get("PharmacoDB.uid")));
        } catch (IOException e) {
            logger.error("IOException was caught: ", e);
        }

        return mapping;
    }

    private Map<String, String> processGuideToPharmacologyFile() {
        Map<String, String> mapping = new HashMap<>();

        try(CSVParser parser = new CSVParser(
            new FileReader(this.guideToPharmacologyFilePath.toString()), CSVFormat.DEFAULT.withFirstRecordAsHeader())
        ) {
            parser.forEach(record -> mapping.put(record.get("Ligand id"), record.get("PubChem CID")));
        } catch (IOException e) {
            logger.error("IOException was caught: ", e);
        }

        return mapping;
    }
}
