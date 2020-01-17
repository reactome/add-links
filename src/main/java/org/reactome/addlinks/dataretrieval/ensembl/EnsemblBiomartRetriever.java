package org.reactome.addlinks.dataretrieval.ensembl;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.reactome.addlinks.dataretrieval.FileRetriever;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
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

    protected void downloadData() throws Exception {

        //TODO: Modulate Biomart Query frequency
        //TODO: Add logging
        //TODO: Add commenting
        //TODO: Add unit tests
        //TODO: Rewrite variable names
        //TODO: Global variables for file names and numerical values
        //TODO: Function-level commenting

        // Get names of all organisms we add links and/or microarray data for.
        // Species names are in biomart format (eg: hsapiens).
        for (String biomartSpeciesName : getBiomartSpeciesNames()) {
            System.out.println("Retrieving Biomart files for " + biomartSpeciesName);
            // Create directory where Biomart files will be stored.
            createBiomartDirectory();
            // Query Biomart for existing microarray 'types' (not ids) that exist for this species.
            BufferedReader br = queryBiomart(this.getDataURL().toString().replace("BIOMART_SPECIES_NAME", biomartSpeciesName));
            // Parse response from Biomart
            Set<String> microarrayTypes = getBiomartResponse(br);
            // Iterate through each microarray type and retrieve Ensembl-Microarray identifier mappings.
            // All mappings are stored in a single file, (eg: hsapiens_microarray);
            for (String microarrayType : microarrayTypes) {
                System.out.println("\tRetrieving data associated with microarray type " + microarrayType);
                retrieveAndStoreMicroarrayData(biomartSpeciesName, microarrayType);
            }

            // Query Ensembl-Uniprot (swissprot and trembl) identifier mapping data from Biomart
            // and write it to a file (eg: hsapiens_uniprot).
            successCount = 0;
            File speciesUniprotFile = createNewFile(this.destination + biomartSpeciesName + "_uniprot");
            Set<String> uniprotIdLines = retrieveAndStoreUniprotData(biomartSpeciesName);
            // TODO: Better way to ensure both queries were success

            // If both queries succeeded, write data to file.
            if (successCount == UNIPROT_QUERY_TOTAL) {
                for (String uniprotIdLine : uniprotIdLines) {
                    Files.write(Paths.get(speciesUniprotFile.toString()), uniprotIdLine.getBytes(), StandardOpenOption.APPEND);
                }
            }
        }
    }

    // Query Biomart for both Uniprot mapping types (curated -- SwissProt, generated -- TrEMBL).
    private Set<String> retrieveAndStoreUniprotData(String biomartSpeciesName) throws Exception {
        File speciesMicroarrayFile = createNewFile(this.destination + biomartSpeciesName + "_uniprot");
        Set<String> uniprotIdLines = new HashSet<>();
        for (String uniprotQueryId : Arrays.asList("uniprotswissprot", "uniprotsptrembl")) {
            // If proper response, write data to file.
            BufferedReader bru = queryBiomart(getBiomartIdentifierQuery(biomartSpeciesName, uniprotQueryId));
            String uniprotLine;
            while ((uniprotLine = bru.readLine()) != null) {
                List<String> tabSplit = Arrays.asList(uniprotLine.split("\t"));
                if (tabSplit.size() == 4) {
                    uniprotIdLines.add(uniprotLine + "\n");
                }
                // With '[success]' string at end of query, increment the successCount integer.
                // We need both queries to succeed (regardless of if there is data or not) in order to continue.
                // TODO: Retry queries that fail
                if (uniprotLine.contains("success")) {
                    successCount++;
//                } else {
//                    //TODO: Retry
//                    throw new Exception("Incomplete UniProt data retrieval from Biomart");
                }
            }
        }
        return uniprotIdLines;
    }

    private void retrieveAndStoreMicroarrayData(String biomartSpeciesName, String microarrayType) throws Exception {
        File speciesMicroarrayFile = createNewFile(this.destination + biomartSpeciesName + "_microarray");

        BufferedReader brp = queryBiomart(getBiomartIdentifierQuery(biomartSpeciesName, microarrayType));

        // Iterate through lines of response, writing data to file.
        String microarrayIdLine;
        Set<String> microarrayIdLines = new HashSet<>();
        while ((microarrayIdLine = brp.readLine()) != null) {
            List<String> tabSplit = Arrays.asList(microarrayIdLine.split("\t"));
            if (tabSplit.size() == 4) {
                microarrayIdLines.add(microarrayIdLine + "\n");
            }
            // End of response should contain the string '[success]'. This indicates all data was received that we asked for.
            if (microarrayIdLine.contains("success")) {
                if (!microarrayIdLines.isEmpty()) {
                    for (String probeIdLine : microarrayIdLines) {
                        Files.write(Paths.get(speciesMicroarrayFile.toString()), probeIdLine.getBytes(), StandardOpenOption.APPEND);
                    }
                }
//            } else {
//                //TODO: Retry
//                throw new Exception("Incomplete Microarray data retrieval from Biomart");
            }
        }
    }

    private Set<String> getBiomartResponse(BufferedReader br) throws IOException {

        String microarrayTypesLine;
        Set<String> microarrayTypes = new HashSet<>();
        while ((microarrayTypesLine = br.readLine()) != null) {
            microarrayTypes.add(microarrayTypesLine);
        }
        br.close();
        return microarrayTypes;
    }

    private File createNewFile(String filepath) throws IOException {
        File file = new File(filepath);
        file.createNewFile();
        return file;
    }

    private BufferedReader queryBiomart(String queryString) throws Exception {
        // Create connection to Biomart URL for each species, retrieving a list of microarray probe types, if available.
        URL microarrayUrlWithSpecies = new URL(queryString);
        HttpURLConnection biomartMicroarrayConnection = (HttpURLConnection) microarrayUrlWithSpecies.openConnection();
        if (biomartMicroarrayConnection.getResponseCode() == 200) {
            return new BufferedReader(new InputStreamReader(biomartMicroarrayConnection.getInputStream()));
        } else {
            //TODO: Retry
            throw new Exception("Unable to connect to " + queryString);
        }
    }

    private void createBiomartDirectory() {
        File addlinksDirectories = new File(this.destination);
        addlinksDirectories.mkdirs();
    }

    //TODO: Ensembl secondary URL needs to go somewhere else. Add additional property to config?

    // Modifies default secondary URL with species name and the query type (for example microarray probe type, or uniprotsptrembl).
    private String getBiomartIdentifierQuery(String biomartSpeciesName, String queryId) throws IOException {
        String defaultBiomartQuery = "http://www.ensembl.org/biomart/martservice?query=%3C?xml%20version=%221.0%22%20encoding=%22UTF-8%22?%3E%3C!DOCTYPE%20Query%3E%3CQuery%20%20virtualSchemaName%20=%20%22default%22%20formatter%20=%20%22TSV%22%20header%20=%20%220%22%20uniqueRows%20=%20%220%22%20count%20=%20%22%22%20completionStamp%20=%20%221%22%3E%3CDataset%20name%20=%20%22BIOMART_SPECIES_NAME_gene_ensembl%22%20interface%20=%20%22default%22%20%3E%3CAttribute%20name%20=%20%22ensembl_gene_id%22%20/%3E%3CAttribute%20name%20=%20%22ensembl_transcript_id%22%20/%3E%3CAttribute%20name%20=%20%22ensembl_peptide_id%22%20/%3E%3CAttribute%20name%20=%20%22BIOMART_QUERY_ID%22%20/%3E%3C/Dataset%3E%3C/Query%3E";
        String updatedBiomartQuery = defaultBiomartQuery.replace("BIOMART_SPECIES_NAME", biomartSpeciesName).replace("BIOMART_QUERY_ID", queryId);

        return updatedBiomartQuery;
    }

    // Function that takes species name attribute from config file (eg: Homo sapiens) and modifies it to
    // match Biomart formatting (first letter from primary species name + secondary name, all lowercase -- eg: hsapiens).
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
}
