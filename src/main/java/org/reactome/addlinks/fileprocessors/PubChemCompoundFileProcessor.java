package org.reactome.addlinks.fileprocessors;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * @author Joel Weiser (joel.weiser@oicr.on.ca)
 *         Created 11/7/2021
 */
public class PubChemCompoundFileProcessor extends FileProcessor<List<String>> {

    public PubChemCompoundFileProcessor() {
        super(null);
    }

    public PubChemCompoundFileProcessor(String processorName) {
        super(processorName);
    }

    @Override
    public Map<String, List<String>> getIdMappingsFromFile() {
        Map<String, List<String>> chEBIIdToPubChemCompoundId = new HashMap<>();

        Stream<String> chEBIReferenceLines;
        try {
            chEBIReferenceLines = Files.lines(this.pathToFile);
        } catch (IOException e) {
            e.printStackTrace();
            return chEBIIdToPubChemCompoundId;
        }

        chEBIReferenceLines.filter(line -> line.contains("PubChem") && line.contains("CID: ")).forEach(line -> {
            String[] columns = line.split("\\t");
            String chEBIId = columns[0];
            String pubChemCompoundId = columns[1].replace("CID: ", "");

            chEBIIdToPubChemCompoundId.computeIfAbsent(chEBIId, k -> new ArrayList<>()).add(pubChemCompoundId);
        });


        return chEBIIdToPubChemCompoundId;
    }
}
