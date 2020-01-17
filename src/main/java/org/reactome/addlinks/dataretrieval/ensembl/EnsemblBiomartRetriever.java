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

    protected void downloadData() throws Exception {

        //TODO: Modulate Biomart Query frequency
        //TODO: Add logging
        //TODO: Add commenting
        //TODO: Add unit tests
        //TODO: Rewrite variable names
        //TODO: Global variables for file names
        //TODO: Functional refactor
        //TODO: Function-level commenting

        // Get names of all organisms we add links and/or microarray data for.
        // Species names are in biomart format (eg: hsapiens).
        for (String biomartSpeciesName : getBiomartSpeciesNames()) {
            System.out.println("Retrieving Biomart files for " + biomartSpeciesName);
            // Create directory where Biomart files will be stored.
            File addlinksDirectories = new File(this.destination);
            addlinksDirectories.mkdirs();
            // Create connection to Biomart URL for each species, retrieving a list of microarray probe types, if available.
            //
            // retrieving Ensembl-Uniprot
            // and Ensembl-Microarray identifier mappings, if available.
            URL microarrayUrlWithSpecies = new URL(this.getDataURL().toString().replace("BIOMART_SPECIES_NAME", biomartSpeciesName));
            HttpURLConnection biomartMicroarrayConnection = (HttpURLConnection) microarrayUrlWithSpecies.openConnection();
            BufferedReader brm = new BufferedReader(new InputStreamReader(biomartMicroarrayConnection.getInputStream()));
            // Iterate through each line of response, storing in a set to remove duplicates.
            String microarrayLine;
            Set<String> microarrayProbeIds = new HashSet<>();
            while ((microarrayLine = brm.readLine()) != null) {
                microarrayProbeIds.add(microarrayLine);
            }
            brm.close();
            // Iterate through each microarray probe type and retrieve Ensembl-Microarray identifier mappings.
            // All mappings are stored in a single file, (eg: hsapiens_microarray_probes);
            for (String probeId : microarrayProbeIds) {
                try {
                    System.out.println("\tRetrieving probes associated with " + probeId);
                    // Create file.
                    String speciesMicroarrayFilepath = this.destination + biomartSpeciesName + "_microarray_probes";
                    File speciesMicroarrayFile = new File(speciesMicroarrayFilepath);
                    // Build URL and query Biomart for data.
                    String biomartMicroarrayQuery = getBiomartQuery(biomartSpeciesName, probeId);
                    URL probeUrlWithSpecies = new URL(biomartMicroarrayQuery);
                    HttpURLConnection biomartProbeConnection = (HttpURLConnection) probeUrlWithSpecies.openConnection();
                    // TODO: Else if not == 200
                    // If proper response, write data to file.
                    if (biomartProbeConnection.getResponseCode() == 200) {
                        BufferedReader brp = new BufferedReader(new InputStreamReader(biomartProbeConnection.getInputStream()));
                        String probeLine;
                        Set<String> probeIdLines = new HashSet<>();
                        // End of response should contain the string '[success]'.
                        // This indicates all data was received that we asked for.
                        boolean isSuccess = false;
                        while ((probeLine = brp.readLine()) != null) {
                            List<String> tabSplit = Arrays.asList(probeLine.split("\t"));
                            if (tabSplit.size() == 4) {
                                probeIdLines.add(probeLine + "\n");
                            }
                            if (probeLine.contains("success")) {
                                isSuccess = true;
                            }
                        }
                        // With successful response that includes data, write it all to file.
                        if (isSuccess && !probeIdLines.isEmpty()) {

                            if (!speciesMicroarrayFile.exists()) {
                                speciesMicroarrayFile.createNewFile();
                            }

                            for (String probeIdLine : probeIdLines) {
                                Files.write(Paths.get(speciesMicroarrayFilepath), probeIdLine.getBytes(), StandardOpenOption.APPEND);
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            // Query Ensembl-Uniprot (swissprot and trembl) identifer mapping data from Biomart
            // and write it to a file (eg: hsapiens_uniprot).
            String speciesUniprotFilepath = this.destination + biomartSpeciesName + "_uniprot";
            // Create file.
            File speciesUniprotFile = new File(speciesUniprotFilepath);
            speciesUniprotFile.createNewFile();
            Set<String> uniprotIdLines = new HashSet<>();
            // TODO: Better way to ensure both queries were success
            int successCount = 0;
            // Query Biomart for both Uniprot mapping types (curated -- SwissProt, generated -- TrEMBL).
            for (String uniprotQueryId : Arrays.asList("uniprotswissprot", "uniprotsptrembl")) {
                // Build URL and query Biomart for data.
                String biomartUniprotQuery = getBiomartQuery(biomartSpeciesName, uniprotQueryId);
                URL biomartUniprotURL = new URL(biomartUniprotQuery);
                HttpURLConnection biomartUniprotConnection = (HttpURLConnection) biomartUniprotURL.openConnection();
                // If proper response, write data to file.
                if (biomartUniprotConnection.getResponseCode() == 200) {
                    BufferedReader bru = new BufferedReader(new InputStreamReader(biomartUniprotConnection.getInputStream()));
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
                        }
                    }
                }
            }

            // If both queries succeeded, write data to file.
            if (successCount == UNIPROT_QUERY_TOTAL)
            for (String uniprotIdLine : uniprotIdLines) {
                Files.write(Paths.get(speciesUniprotFilepath), uniprotIdLine.getBytes(), StandardOpenOption.APPEND);
            }
        }
    }

    //TODO: Ensembl secondary URL needs to go somewhere else. Add additional property to config?

    // Modifies default secondary URL with species name and the query type (for example microarray probe type, or uniprotsptrembl).
    private String getBiomartQuery(String biomartSpeciesName, String queryId) throws IOException {
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
