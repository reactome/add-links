package org.reactome.addlinks;

import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.apache.logging.log4j.Logger;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonValue;

public class EnsemblBioMartUtil {

    // Private no-argument constructor to prevent instantiation of whole utilities class.
    private EnsemblBioMartUtil() {}

    protected static Logger logger;
    public static final String UNIPROT_SUFFIX = "_uniprot";
    public static final String MICROARRAY_SUFFIX = "_microarray";
    public static final String PROTEIN_TO_GENES_SUFFIX = "_proteinToGenes";
    public static final String PROTEIN_TO_TRANSCRIPTS_SUFFIX = "_proteinToTranscripts";
    public static final String TRANSCRIPT_TO_MICROARRAYS_SUFFIX = "_transcriptToMicroarrays";
    public static final String UNIPROT_TO_PROTEINS_SUFFIX = "_uniprotToProteins";
    /**
     * Function that takes species name attribute from config file (eg: Homo sapiens) and modifies it to
     * match Biomart formatting (first letter from genus + full species name, all lowercase -- eg: hsapiens).
     * @return List<String> of species names in Biomart format (eg: hsapiens).
     * @throws IOException - Thrown when unable to read file.
     */
    public static List<String> getSpeciesNames() throws IOException {
        // Read properties file.
        Properties applicationProps = getProperties();
        // Get species JSON config file location, import as JSON object.
        List<String> speciesNames = getSpeciesNamesFromJSON(applicationProps.getProperty("pathToSpeciesConfig"));
        return speciesNames;
    }

    /**
     * Reads the Species.json file and parses out the species 'name' attribute.
     * @param pathToSpeciesConfig - String, Filepath to Species.json file
     * @return List<String> of species names
     * @throws IOException - Thrown when unable to read file.
     */
    private static List<String> getSpeciesNamesFromJSON(String pathToSpeciesConfig) throws IOException {
        JsonReader reader = Json.createReader(new FileReader(pathToSpeciesConfig));
        JsonObject jsonObject = reader.readObject();
        // For each species in config file, retrieve name, modify it and then add to list.
        List<String> speciesNames = new ArrayList<>();
        for (Object speciesKey : jsonObject.keySet()) {
            JsonValue speciesJson = jsonObject.get(speciesKey);
            String speciesName = speciesJson.asJsonObject().getJsonArray("name").getString(0);
            speciesNames.add(speciesName);
        }
        return speciesNames;
    }

    /**
     * Formats species scientific name to BioMart format (eg: hsapiens).
     * @param speciesName - String, scientific species name (eg: Homo sapiens).
     * @return String, BioMart-formatted string.
     */
    public static String getBioMartSpeciesName(String speciesName) {
        return speciesName.substring(0,1).toLowerCase() + speciesName.split(" ")[1];
    }

    /**
     * Read properties file set to 'config.location' in the system.
     * @return Loaded properties file
     * @throws IOException - Thrown if file does not exist.
     */
    public static Properties getProperties() throws IOException {
        // Read properties file.
        Properties applicationProps = new Properties();
        String propertiesLocation = System.getProperty("config.location");
        try(FileInputStream fis = new FileInputStream(propertiesLocation))
        {
            applicationProps.load(fis);
        }
        return applicationProps;
    }

    /**
     * Returns data corresponding to 'species_uniprotToProteins' in mappings.
     * @param speciesBiomartName - String, BioMart-formatted species name
     * @param mappings- Map<String, Map<String, List<String>>>, Mapping generated from EnsemblBioMartFileProcessor.
     * @return Map<String, List<String>>, corresponding mappings for species.
     */
    public static Map<String, List<String>> getUniprotToProteinsMappings(String speciesBiomartName, Map<String, Map<String, List<String>>> mappings) {
        String uniprotToProteinsKey = speciesBiomartName + UNIPROT_TO_PROTEINS_SUFFIX;
        return mappings.computeIfAbsent(uniprotToProteinsKey, k -> new HashMap<>());
    }

    /**
     * Returns data corresponding to 'species_transcriptToMicroarrays' in mappings.
     * @param speciesBiomartName - String, BioMart-formatted species name
     * @param mappings- Map<String, Map<String, List<String>>>, Mapping generated from EnsemblBioMartFileProcessor.
     * @return Map<String, List<String>>, corresponding mappings for species.
     */
    public static Map<String, List<String>> getTranscriptToMicroarraysMappings(String speciesBiomartName, Map<String, Map<String, List<String>>> mappings) {
        String transcriptToMicroarraysKey = speciesBiomartName + TRANSCRIPT_TO_MICROARRAYS_SUFFIX;
        return mappings.computeIfAbsent(transcriptToMicroarraysKey, k -> new HashMap<>());
    }

    /**
     * Returns data corresponding to 'species__proteinToTranscripts' in mappings.
     * @param speciesBiomartName - String, BioMart-formatted species name
     * @param mappings- Map<String, Map<String, List<String>>>, Mapping generated from EnsemblBioMartFileProcessor.
     * @return Map<String, List<String>>, corresponding mappings for species.
     */
    public static Map<String, List<String>> getProteinToTranscriptsMappings(String speciesBiomartName, Map<String, Map<String, List<String>>> mappings) {
        String proteinToTranscriptsKey = speciesBiomartName + PROTEIN_TO_TRANSCRIPTS_SUFFIX;
        return mappings.computeIfAbsent(proteinToTranscriptsKey, k -> new HashMap<>());
    }

    /**
     * Returns all lines in the file. If the file contains a single-line header, it is filtered out.
     * @param inputFilePath - Path, location of file to be read.
     * @param skipHeader - boolean, determines if first line of file should be filtered out.
     * @return List<String>, contents of file in a List.
     * @throws IOException - Thrown if file does not exist.
     */
    public static List<String>  getLinesFromFile(Path inputFilePath, boolean skipHeader) {
        try {
            List<String> fileLines = Files.readAllLines(inputFilePath, StandardCharsets.ISO_8859_1);
            if (skipHeader) {
                fileLines.remove(0);
            }
            return fileLines;

        } catch (IOException e) {
            logger.error("Error reading file ({}): {}", inputFilePath, e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    /**
     * Checks if necessary columns exist in file. Not all columns are guaranteed to contain the data needed, so this method checks that.
     * @param arrayOfFileColumns - List<String>, All columns of a line separated into a list.
     * @param requiredColumnIndex - int, Index of column required to proceed.
     * @return boolean that confirms if the necessary data exists or not.
     */
    public static boolean necessaryColumnPresent(List<String> arrayOfFileColumns, int requiredColumnIndex) {
        return arrayOfFileColumns.size() > requiredColumnIndex;
    }

    /**
     * This splits identifiers that contain a prefix. Currently this pertains to MGI and VGNC.
     * @param identifierWithPrefix String - Example: VGNC:12345
     * @return String - IdentifierWithoutPrefix (ie. 12345)
     */
    public static String getIdentifierWithoutPrefix(String identifierWithPrefix) {
        return identifierWithPrefix.split(":")[1];
    }
}
