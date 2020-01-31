package org.reactome.addlinks;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

public class EnsemblBioMartUtil {


    public static final String PROTEIN_TO_GENES_SUFFIX = "_proteinToGenes";
    public static final String PROTEIN_TO_TRANSCRIPTS_SUFFIX = "_proteinToTranscripts";
    public static final String TRANSCRIPT_TO_MICROARRAYS_SUFFIX = "_transcriptToMicroarrays";
    public static final String UNIPROT_TO_PROTEINS_SUFFIX = "_uniprotToProteins";

    /**
     * Function that takes species name attribute from config file (eg: Homo sapiens) and modifies it to
     * match Biomart formatting (first letter from genus + full species name, all lowercase -- eg: hsapiens).
     * @return List<String> of species names in Biomart format (eg: hsapiens).
     * @throws IOException - Thrown when unable to read file.
     * @throws ParseException - Thrown when unable to parse JSON data.
     */
    public static List<String> getSpeciesNames() throws IOException, ParseException {
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
     * @throws ParseException - Thrown when unable to parse JSON data.
     */
    private static List<String> getSpeciesNamesFromJSON(String pathToSpeciesConfig) throws IOException, ParseException {
        JSONParser parser = new JSONParser();
        Object obj = parser.parse(new FileReader(pathToSpeciesConfig));
        JSONObject jsonObject = (JSONObject) obj;
        // For each species in config file, retrieve name, modify it and then add to list.
        List<String> speciesNames = new ArrayList<>();
        for (Object speciesKey : jsonObject.keySet()) {
            JSONObject speciesJson = (JSONObject) jsonObject.get(speciesKey);
            JSONArray speciesNamesJSON = (JSONArray) speciesJson.get("name");
            String speciesName = (String) speciesNamesJSON.get(0);
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
        return mappings.get(uniprotToProteinsKey);
    }

    /**
     * Returns data corresponding to 'species_transcriptToMicroarrays' in mappings.
     * @param speciesBiomartName - String, BioMart-formatted species name
     * @param mappings- Map<String, Map<String, List<String>>>, Mapping generated from EnsemblBioMartFileProcessor.
     * @return Map<String, List<String>>, corresponding mappings for species.
     */
    public static Map<String, List<String>> getTranscriptToMicroarraysMappings(String speciesBiomartName, Map<String, Map<String, List<String>>> mappings) {
        String transcriptToMicroarraysKey = speciesBiomartName + TRANSCRIPT_TO_MICROARRAYS_SUFFIX;
        return mappings.get(transcriptToMicroarraysKey);
    }

    /**
     * Returns data corresponding to 'species__proteinToTranscripts' in mappings.
     * @param speciesBiomartName - String, BioMart-formatted species name
     * @param mappings- Map<String, Map<String, List<String>>>, Mapping generated from EnsemblBioMartFileProcessor.
     * @return Map<String, List<String>>, corresponding mappings for species.
     */
    public static Map<String, List<String>> getProteinToTranscriptsMappings(String speciesBiomartName, Map<String, Map<String, List<String>>> mappings) {
        String proteinToTranscriptsKey = speciesBiomartName + PROTEIN_TO_TRANSCRIPTS_SUFFIX;
        return mappings.get(proteinToTranscriptsKey);
    }

    /**
     * Returns all lines in the file. If the file contains a single-line header, it is filtered out.
     * @param inputFilePath - Path, location of file to be read.
     * @param skipHeader - boolean, determines if first line of file should be filtered out.
     * @return List<String>, contents of file in a List.
     * @throws IOException - Thrown if file does not exist.
     */
    public static List<String>  getLinesFromFile(Path inputFilePath, boolean skipHeader) throws IOException {
        if (skipHeader) {
            return Files.readAllLines(inputFilePath).stream().skip(1).collect((Collectors.toList()));
        } else {
            return Files.readAllLines(inputFilePath);
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
}
