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
public class GlyTouCanFileProcessor extends FileProcessor<String> {

    public GlyTouCanFileProcessor() {
        super(null);
    }

    public GlyTouCanFileProcessor(String processorName) {
        super(processorName);
    }

    @Override
    public Map<String, String> getIdMappingsFromFile() {
        Map<String, String> mapping = new HashMap<>();

        try (CSVParser parser = new CSVParser(Files.newBufferedReader(this.pathToFile), CSVFormat.DEFAULT.withDelimiter('\t'))) {
            parser.forEach(line -> {
                String chebiId = line.get(1);
                String glytoucanId = line.get(4);

                mapping.put(chebiId, glytoucanId);
            });
        } catch (IOException e) {
            logger.error(e);
        }

        return mapping;
    }
}
