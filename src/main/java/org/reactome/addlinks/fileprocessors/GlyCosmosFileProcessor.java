package org.reactome.addlinks.fileprocessors;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;

import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Joel Weiser (joel.weiser@oicr.on.ca)
 *         Created 10/20/2021
 */
public class GlyCosmosFileProcessor extends FileProcessor<String> {

    public GlyCosmosFileProcessor() {
        super(null);
    }

    public GlyCosmosFileProcessor(String processorName) {
        super(processorName);
    }

    @Override
    public Map<String, String> getIdMappingsFromFile() {
        Map<String, String> mapping = new HashMap<>();

        try (CSVParser parser = new CSVParser(Files.newBufferedReader(this.pathToFile), CSVFormat.DEFAULT.withDelimiter('\t').withFirstRecordAsHeader())) {
            parser.forEach(line -> {
                String reactomeStableId = line.get(0);

                // GlyCosmos uses Reactome Stable Identifiers (i.e. R-HSA-XXXXXX) to display pathway data
                mapping.put(reactomeStableId, reactomeStableId);
            });
        } catch (IOException e) {
            logger.error(e);
        }

        return mapping;
    }
}
