package org.reactome.addlinks.dataretrieval.ensembl;

import org.apache.http.HttpException;
import org.apache.http.HttpStatus;
import org.reactome.addlinks.EnsemblBiomartUtil;
import org.reactome.addlinks.dataretrieval.FileRetriever;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.*;

public class EnsemblBioMartRetriever extends FileRetriever {

    private Properties properties;

    public EnsemblBioMartRetriever() { }

    public EnsemblBioMartRetriever(String retrieverName)
    {
        super(retrieverName);
    }

    private static final String  microarrayTypesBaseQuery = "type=listAttributes&mart=ENSEMBL_MART_ENSEMBL&virtualSchema=default&dataset=BIOMART_SPECIES_NAME_gene_ensembl&interface=default&attributePage=feature_page&attributeGroup=external&attributeCollection=microarray";


    /**
     * Downloads Ensembl-Microarray and Ensembl-Uniprot identifier mapping files for all species, if they exist.
     * @throws Exception
     */
    public void downloadData() throws Exception {

        // Create directory where BioMart files will be stored.
        Files.createDirectories(Paths.get(this.destination));
        setProperties(EnsemblBiomartUtil.getProperties());

        // Get names of all organisms we add links and/or microarray data for.
        // Species names are in BioMart format (eg: hsapiens).
        for (String speciesName : EnsemblBiomartUtil.getSpeciesNames()) {
            System.out.println(speciesName);
            String speciesBioMartName = EnsemblBiomartUtil.getBiomartSpeciesName(speciesName);
            logger.info("Retrieving BioMart files for " + speciesBioMartName);

            logger.info("Retrieving microarray data");
            // Query BioMart for existing microarray 'types' (not ids) that exist for this species.
            Set<String> microarrayTypes = queryBiomart(getMicroarrayTypesQuery(speciesBioMartName), 0);
            // Iterate through each microarray type and retrieve Ensembl-Microarray identifier mappings.
            // All mappings are stored in a single file, (eg: hsapiens_microarray);
            for (String microarrayType : microarrayTypes) {
                queryBiomartAndStoreData(speciesBioMartName, getBiomartXMLFilePath(), microarrayType, "_microarray");
            }

            // Query Ensembl-Uniprot (swissprot and trembl) identifier mapping data from Biomart
            // and write it to a file (eg: hsapiens_uniprot).
            logger.info("Retrieving UniProt data");
            for (String uniprotQueryId : Arrays.asList("uniprotswissprot", "uniprotsptrembl")) {
                queryBiomartAndStoreData(speciesBioMartName, getBiomartXMLFilePath(), uniprotQueryId, "_uniprot");
            }

            logger.info("Completed BioMart data retrieval for " + speciesBioMartName);
        }
    }

    private String getMicroarrayTypesQuery(String biomartSpeciesName) {
        return this.getDataURL() + microarrayTypesBaseQuery.replace("BIOMART_SPECIES_NAME", biomartSpeciesName);
    }

    /**
     * Query Biomart for Microarray 'identifiers' associated with the microarray type
     * @param biomartSpeciesName -- String of species name in Biomart format (eg: hsapiens)
     * @param biomartQueryFilePath -- String, Path to the Biomart XML Query file
     * @param biomartDataType -- String, Type of data (either UniProt or Microarray) that will be queried for from BioMart
     * @param fileSuffix -- String that will be used to make filename (either _uniprot or _microarray) that will hold associated data.
     * @throws Exception
     */
    private void queryBiomartAndStoreData(String biomartSpeciesName, String biomartQueryFilePath, String biomartDataType, String fileSuffix) throws IOException {

        // Query Biomart for microarray identifiers associated with microarray type.
        Set<String> biomartResponseLines = new HashSet<>();
        try {
            logger.info("Retrieving data associated with microarray type " + biomartDataType);
            biomartResponseLines = queryBiomart(getBiomartIdentifierQuery(biomartQueryFilePath, biomartSpeciesName, biomartDataType), 0);
        } catch (Exception e) {
            e.printStackTrace();
        }

        String biomartFilename = this.destination + biomartSpeciesName + fileSuffix;
        storeBiomartData(biomartFilename, biomartResponseLines);

    }

    private void storeBiomartData(String biomartFilename, Set<String> biomartResponseLines) throws IOException {

        Set<String> biomartIdentifierLines = new HashSet<>();
        for (String biomartResponseLine : biomartResponseLines) {
            List<String> tabSplit = Arrays.asList(biomartResponseLine.split("\t"));
            if (lineContainsIdentifier(tabSplit)) {
                biomartIdentifierLines.add(biomartResponseLine + "\n");
            }
        }

        // Write data to file.
        for (String biomartIdentifierLine : biomartIdentifierLines) {
            Files.write(
                    Paths.get(biomartFilename),
                    biomartIdentifierLine.getBytes(),
                    StandardOpenOption.APPEND, StandardOpenOption.CREATE
            );

        }
    }

    private boolean lineContainsIdentifier(List<String> tabSplit) {
        final int IDENTIFIER_COLUMN = 3;
        return tabSplit.size() > IDENTIFIER_COLUMN && !tabSplit.get(IDENTIFIER_COLUMN).trim().isEmpty();
    }

    /**
     * Attempts to create a connection to Biomart with the supplied query
     * @param queryString String of Biomart query
     * @return Set<String> Biomart response data.
     * @throws Exception Can be caused by the query URL not being valid, or the data not existing for the species.
     */
    private Set<String> queryBiomart(String queryString, int retryCount) throws IOException, InterruptedException, BioMartQueryException, HttpException {
        final int MAX_QUERY_RETRIES = 5;
        // Create connection to Biomart URL for each species, retrieving a list of microarray probe types, if available.
        URL biomartUrlWithSpecies = new URL(queryString);
        HttpURLConnection biomartConnection = (HttpURLConnection) biomartUrlWithSpecies.openConnection();
        if (biomartConnection.getResponseCode() == HttpStatus.SC_OK) {
            Set<String> biomartResponseLines = new HashSet<>();
            BufferedReader br = new BufferedReader(new InputStreamReader(biomartConnection.getInputStream()));
            String line;
            while ((line = br.readLine()) != null) {
                // Biomart still responds with a 200, even if no data exists. For now, we handle it by
                // checking the returned content for the string 'ERROR'. It will retry up to 5 times, with a 10 second delay.
                if (line.contains("ERROR") ) {
                    retryCount++;
                    if (retryCount < MAX_QUERY_RETRIES) {
                        return retryQuery(queryString, retryCount);
                    }
                    // The data does not exist in BioMart for a few species. Frog (X. tropicalis) has UniProt data but not Microarray data;
                    // Yeast (S. cerevisiae) has Uniprot-SwissProt data but not UniProt-TrEMBL data; Yeast (S. pombe), P. falciparum
                    // and D. discoideum don't have UniProt or Microarray data, all at time of writing (January 2020).
//                    throw new Exception("Biomart query failed with message: " + line + "\nThis can happen without issue for certain species (X. tropicalis, S. pombe, S. cerevisiae, P. falciparum) because the data doesn't exist in BioMart");
                throw new BioMartQueryException(line +
                        "\nThis can happen without issue for certain species (X. tropicalis, S. pombe, S. cerevisiae, P. falciparum) " +
                        "because the data doesn't exist in BioMart");
                } else {
                    biomartResponseLines.add(line);
                }
            }
            return biomartResponseLines;

        } else {
            retryCount++;
            if (retryCount < MAX_QUERY_RETRIES) {
                return retryQuery(queryString, retryCount);
            }
            throw new HttpException(
                    "Unable to connect to Biomart (" +
                            biomartConnection.getResponseCode() + ": " + biomartConnection.getResponseMessage() +
                            ") with URL: " + queryString
            );
        }
    }

    private Set<String> retryQuery(String queryString, int retryCount) throws InterruptedException, IOException, HttpException, BioMartQueryException {
        final long QUERY_SLEEP_DURATION = Duration.ofSeconds(5).toMillis();

        Thread.sleep(QUERY_SLEEP_DURATION);
        logger.warn("Biomart query failed. Trying again...");
        return queryBiomart(queryString, retryCount);
    }

    // Gets the filepath to biomart XML file, used to build biomart query.
    private String getBiomartXMLFilePath() throws IOException {
        // Get species JSON config file location, import as JSON object.
        return this.properties.getProperty("pathToBiomartXML");
    }

    // Modifies default secondary URL with species name and the query type (for example microarray probe type, or uniprotsptrembl).
    private String getBiomartIdentifierQuery(String pathToQueryFile, String biomartSpeciesName, String queryId) throws IOException {
        String biomartQuery = "http://www.ensembl.org/biomart/martservice?query=" + URLEncoder.encode(joinFileLines(pathToQueryFile),"UTF-8");
        return biomartQuery.replace("BIOMART_SPECIES_NAME", biomartSpeciesName).replace("BIOMART_QUERY_ID", queryId);
    }

    private String joinFileLines(String pathToFile) throws IOException {
        List<String> fileLines = Files.readAllLines(Paths.get(pathToFile));
        return String.join(System.lineSeparator(), fileLines);
    }

    public static class BioMartQueryException extends Exception {
        BioMartQueryException(String message) {
            super("BioMart query failed with message: " + message);
        }
    }

    private void setProperties(Properties applicationProps) {
        this.properties = applicationProps;
    }

}
