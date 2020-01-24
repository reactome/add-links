package org.reactome.addlinks.dataretrieval.ensembl;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.reactome.addlinks.dataretrieval.FileRetriever;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;

public class EnsemblBiomartRetriever extends FileRetriever {

    public EnsemblBiomartRetriever() { }

    public EnsemblBiomartRetriever(String retrieverName)
    {
        super(retrieverName);
    }

    private static final int UNIPROT_QUERY_TOTAL = 2;
    private static int successCount;
    private static final String  microarrayTypesBaseQuery = "type=listAttributes&mart=ENSEMBL_MART_ENSEMBL&virtualSchema=default&dataset=BIOMART_SPECIES_NAME_gene_ensembl&interface=default&attributePage=feature_page&attributeGroup=external&attributeCollection=microarray";

    /**
     * Downloads Ensembl-Microarray and Ensembl-Uniprot identifier mapping files for all species, if they exist.
     * @throws Exception
     */
    public void downloadData() throws Exception {

        // Get names of all organisms we add links and/or microarray data for.
        // Species names are in biomart format (eg: hsapiens).
        for (String biomartSpeciesName : getBiomartSpeciesNames()) {
            logger.info("Retrieving Biomart files for " + biomartSpeciesName);
            // Create directory where Biomart files will be stored.
            new File(this.destination).mkdirs();

            logger.info("Retrieving microarray data");
            // Query Biomart for existing microarray 'types' (not ids) that exist for this species.
            Set<String> microarrayTypes = queryBiomart(getMicroarrayTypesQuery(biomartSpeciesName), 0);
            // Iterate through each microarray type and retrieve Ensembl-Microarray identifier mappings.
            // All mappings are stored in a single file, (eg: hsapiens_microarray);
            retrieveAndStoreMicroarrayData(biomartSpeciesName, getBiomartXMLPath(), microarrayTypes);

            // Query Ensembl-Uniprot (swissprot and trembl) identifier mapping data from Biomart
            // and write it to a file (eg: hsapiens_uniprot).
            logger.info("Retrieving UniProt data");
            retrieveAndStoreUniprotData(biomartSpeciesName, getBiomartXMLPath());

            logger.info("Completed Biomart data retrieval for " + biomartSpeciesName);
        }
    }

    /**
     * Query Biomart for both Uniprot mapping types (curated -- SwissProt, generated -- TrEMBL).
     * @param biomartSpeciesName
     * @throws Exception
     */
    private void retrieveAndStoreUniprotData(String biomartSpeciesName, String biomartXMLPath) throws IOException {
        Set<String> uniprotIdLines = new HashSet<>();
        try {
            for (String uniprotQueryId : Arrays.asList("uniprotswissprot", "uniprotsptrembl")) {
                // If proper response, write data to file.
                Set<String> uniprotResponseLines = queryBiomart(getBiomartIdentifierQuery(biomartXMLPath, biomartSpeciesName, uniprotQueryId), 0);
                for (String uniprotResponseLine : uniprotResponseLines) {
                    List<String> tabSplit = Arrays.asList(uniprotResponseLine.split("\t"));
                    if (tabSplit.size() == 4) {
                        uniprotIdLines.add(uniprotResponseLine + "\n");
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        // Write data to file.
        if (!uniprotIdLines.isEmpty()) {
            File speciesUniprotFile = createNewFile(this.destination + biomartSpeciesName + "_uniprot");
            for (String uniprotIdLine : uniprotIdLines) {
                Files.write(Paths.get(speciesUniprotFile.toString()), uniprotIdLine.getBytes(), StandardOpenOption.APPEND);
            }
        }
    }

    private String getMicroarrayTypesQuery(String biomartSpeciesName) {
        return this.getDataURL() + microarrayTypesBaseQuery.replace("BIOMART_SPECIES_NAME", biomartSpeciesName);
    }

    /**
     * Query Biomart for Microarray 'identifiers' associated with the microarray type
     * @param biomartSpeciesName -- String of species name in Biomart format (eg: hsapiens)
     * @param microarrayTypes -- String, type of microarray identifiers being queried for.
     * @throws Exception
     */
    private void retrieveAndStoreMicroarrayData(String biomartSpeciesName, String biomartXMLPath, Set<String> microarrayTypes) {

        // Query Biomart for microarray identifiers associated with microarray type.
        for (String microarrayType : microarrayTypes) {
            try {
                logger.info("Retrieving data associated with microarray type " + microarrayType);
                Set<String> microarrayResponseLines = queryBiomart(getBiomartIdentifierQuery(biomartXMLPath, biomartSpeciesName, microarrayType), 0);
                Set<String> microarrayIdentifierLines = new HashSet<>();
                for (String microarrayResponseLine : microarrayResponseLines) {
                    List<String> tabSplit = Arrays.asList(microarrayResponseLine.split("\t"));
                    // Microarray ID is 4th column, and sometimes it isn't in a line.
                    // Since we need the ID, we require the List to have 4 elements.
                    if (tabSplit.size() == 4) {
                        microarrayIdentifierLines.add(microarrayResponseLine + "\n");
                    }
                }

                // Write data to file.
                if (!microarrayIdentifierLines.isEmpty()) {
                    File speciesMicroarrayFile = createNewFile(this.destination + biomartSpeciesName + "_microarray");
                    for (String probeIdLine : microarrayIdentifierLines) {
                        Files.write(Paths.get(speciesMicroarrayFile.toString()), probeIdLine.getBytes(), StandardOpenOption.APPEND);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Attempts to create a connection to Biomart with the supplied query
     * @param queryString String of Biomart query
     * @return Set<String> Biomart response data.
     * @throws Exception Can be caused by the query URL not being valid, or the data not existing for the species.
     */
    private Set<String> queryBiomart(String queryString, int retryCount) throws Exception {
        // Create connection to Biomart URL for each species, retrieving a list of microarray probe types, if available.
        URL biomartUrlWithSpecies = new URL(queryString);
        HttpURLConnection biomartConnection = (HttpURLConnection) biomartUrlWithSpecies.openConnection();
        if (biomartConnection.getResponseCode() == 200) {
            Set<String> biomartResponseLines = new HashSet<>();
            BufferedReader br = new BufferedReader(new InputStreamReader(biomartConnection.getInputStream()));
            String line;
            while ((line = br.readLine()) != null) {
                // Biomart still responds with a 200, even if no data exists. For now, we handle it by
                // checking the returned content for the string 'ERROR'. It will retry up to 5 times, with a 10 second delay.
                if (line.contains("ERROR") ) {
                    retryCount++;
                    if (retryCount < 5) {
                        Thread.sleep(10000);
                        logger.warn("Biomart query failed. Trying again...");
                        return queryBiomart(queryString, retryCount);
                    }
                    throw new Exception("Biomart query failed with message: " + line);
                } else {
                    biomartResponseLines.add(line);
                }
            }
            return biomartResponseLines;

        } else {
            retryCount++;
            if (retryCount < 5) {
                Thread.sleep(10000);
                logger.warn("Unable to connect to Biomart. Trying again...");
                return queryBiomart(queryString, retryCount);
            }
            throw new Exception("Unable to connect to Biomart with URL: " + queryString);
        }
    }

    /**
     * Function that takes species name attribute from config file (eg: Homo sapiens) and modifies it to
     * match Biomart formatting (first letter from primary species name + secondary name, all lowercase -- eg: hsapiens).
     * @return List<String> of species names in Biomart format (eg: hsapiens).
     * @throws IOException
     * @throws ParseException
     */
    private List<String> getBiomartSpeciesNames() throws IOException, ParseException {
        // Read properties file.
        Properties applicationProps = new Properties();
        String propertiesLocation = System.getProperty("config.location");
        try(FileInputStream fis = new FileInputStream(propertiesLocation))
        {
            applicationProps.load(fis);
        }
        // Get species JSON config file location, import as JSON object.
        String pathToSpeciesConfig = applicationProps.getProperty("pathToSpeciesConfig");
        JSONParser parser = new JSONParser();
        Object obj = parser.parse(new FileReader(pathToSpeciesConfig));
        JSONObject jsonObject = (JSONObject) obj;

        // For each species in config file, retrieve name, modify it and then add to list.
        List<String> biomartNames = new ArrayList<>();
        for (Object speciesKey : jsonObject.keySet()) {
            JSONObject speciesJson = (JSONObject) jsonObject.get(speciesKey);
            JSONArray speciesNames = (JSONArray) speciesJson.get("name");
            String speciesName = (String) speciesNames.get(0);
            String biomartName = speciesName.substring(0,1).toLowerCase() + speciesName.split(" ")[1];
            biomartNames.add(biomartName);
        }
        return biomartNames;
    }

    // Gets the filepath to biomart XML file, used to build biomart query.
    private String getBiomartXMLPath() throws IOException {
        // Read properties file.
        Properties applicationProps = new Properties();
        String propertiesLocation = System.getProperty("config.location");
        try(FileInputStream fis = new FileInputStream(propertiesLocation))
        {
            applicationProps.load(fis);
        }
        // Get species JSON config file location, import as JSON object.
        return applicationProps.getProperty("pathToBiomartXML");
    }

    // Modifies default secondary URL with species name and the query type (for example microarray probe type, or uniprotsptrembl).
    private String getBiomartIdentifierQuery(String pathToXMLQuery, String biomartSpeciesName, String queryId) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(pathToXMLQuery));
        String biomartQuery = "http://www.ensembl.org/biomart/martservice?query=" + URLEncoder.encode(br.readLine(), "UTF-8");
        return biomartQuery.replace("BIOMART_SPECIES_NAME", biomartSpeciesName).replace("BIOMART_QUERY_ID", queryId);
    }

    // Creates file from filepath
    private File createNewFile(String filepath) throws IOException {
        File file = new File(filepath);
        file.createNewFile();
        return file;
    }
}
