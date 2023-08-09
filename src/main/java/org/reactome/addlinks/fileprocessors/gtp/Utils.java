package org.reactome.addlinks.fileprocessors.gtp;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @author Joel Weiser (joel.weiser@oicr.on.ca)
 *         Created 6/28/2023
 */
public class Utils {
    public static CSVParser getCSVParser(Path csvFilePath) throws IOException {
        return new CSVParser(
            getReaderAfterVersionHeader(Files.newBufferedReader(csvFilePath)),
            CSVFormat.DEFAULT.withFirstRecordAsHeader()
        );
    }

    private static Reader getReaderAfterVersionHeader(BufferedReader reader) throws IOException {
        String header = reader.readLine();
        if (!header.contains("GtoPdb Version")) {
            reader.reset(); // Reset to beginning to include first line if not version header
        }
        return reader; // Return reader at proper position after version header, if exists
    }
}