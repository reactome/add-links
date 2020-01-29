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

public class EnsemblBiomartUtil {


    public static final String proteinToGenesSuffix = "_proteinToGenes";
    public static final String proteinToTranscriptsSuffix = "_proteinToTranscripts";
    public static final String transcriptToMicroarraysSuffix = "_transcriptToMicroarrays";
    public static final String uniprotToProteinsSuffix = "_uniprotToProteins";

    /**
     * Function that takes species name attribute from config file (eg: Homo sapiens) and modifies it to
     * match Biomart formatting (first letter from primary species name + secondary name, all lowercase -- eg: hsapiens).
     * @return List<String> of species names in Biomart format (eg: hsapiens).
     * @throws IOException
     * @throws ParseException
     */
    public static List<String> getSpeciesNames() throws IOException, ParseException {
        // Read properties file.
        Properties applicationProps = getProperties();
        // Get species JSON config file location, import as JSON object.
        List<String> speciesNames = getSpeciesNamesFromJSON(applicationProps.getProperty("pathToSpeciesConfig"));
        return speciesNames;
    }

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

    public static String getBiomartSpeciesName(String speciesName) {
        return speciesName.substring(0,1).toLowerCase() + speciesName.split(" ")[1];
    }

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

    public static Map<String, List<String>> getUniprotToProteinsMappings(String speciesBiomartName, Map<String, Map<String, List<String>>> mappings) {
        String uniprotToProteinsKey = speciesBiomartName + uniprotToProteinsSuffix;
        return mappings.get(uniprotToProteinsKey);
    }

    public static Map<String, List<String>> getTranscriptToMicroarraysMappings(String speciesBiomartName, Map<String, Map<String, List<String>>> mappings) {
        String transcriptToMicroarraysKey = speciesBiomartName + transcriptToMicroarraysSuffix;
        return mappings.get(transcriptToMicroarraysKey);
    }

    public static Map<String, List<String>> getProteinToTranscriptsMappings(String speciesBiomartName, Map<String, Map<String, List<String>>> mappings) {
        String proteinToTranscriptsKey = speciesBiomartName + proteinToTranscriptsSuffix;
        return mappings.get(proteinToTranscriptsKey);
    }

    public static List<String>  getLinesFromFile(Path inputFilePath, boolean skipHeader) throws IOException {
        if (skipHeader) {
            return Files.readAllLines(inputFilePath).stream().skip(1).collect((Collectors.toList()));
        } else {
            return Files.readAllLines(inputFilePath);
        }
    }

    public static boolean necessaryColumnPresent(List<String> arrayOfFileColumns, int requiredColumnIndex) {
        return arrayOfFileColumns.size() > requiredColumnIndex;
    }
}
