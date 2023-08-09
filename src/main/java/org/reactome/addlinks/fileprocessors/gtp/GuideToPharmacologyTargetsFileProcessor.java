package org.reactome.addlinks.fileprocessors.gtp;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.csv.CSVParser;
import org.reactome.addlinks.fileprocessors.FileProcessor;

import static org.reactome.addlinks.fileprocessors.gtp.Utils.getCSVParser;

public class GuideToPharmacologyTargetsFileProcessor extends FileProcessor<String> {

	public GuideToPharmacologyTargetsFileProcessor()
	{
		super(null);
	}

	public GuideToPharmacologyTargetsFileProcessor(String processorName)
	{
		super(processorName);
	}

	@Override
	public Map<String, String> getIdMappingsFromFile() {
		Map<String, String> mapping = new HashMap<>();
		try(CSVParser parser = getCSVParser(this.pathToFile)) {
			parser.forEach(line -> {
				String targetID = line.get("Target id");
				String uniprotID = line.get("Human SwissProt");
				mapping.put(uniprotID, targetID);
			});
		} catch (IOException e) {
			logger.error("There was a problem opening/reading the file " + this.pathToFile.toString(), e);
		}
		return mapping;
	}
}
