package org.reactome.addlinks.dataretrieval.ensembl;

import org.apache.http.HttpException;
import org.apache.http.HttpStatus;
import org.json.simple.parser.ParseException;
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

public class EnsemblBiomartRetriever extends FileRetriever {

    private Properties properties;

    public EnsemblBiomartRetriever() { }

    public EnsemblBiomartRetriever(String retrieverName)
    {
        super(retrieverName);
    }

    private static final String  microarrayTypesBaseQuery = "type=listAttributes&mart=ENSEMBL_MART_ENSEMBL&virtualSchema=default&dataset=BIOMART_SPECIES_NAME_gene_ensembl&interface=default&attributePage=feature_page&attributeGroup=external&attributeCollection=microarray";

    /**
     * Downloads Ensembl-Microarray and Ensembl-Uniprot identifier mapping files for all species, if they exist.
     * @throws IOException - Thrown when file can't be found when during writing and by HTTPConnection class.
     * @throws ParseException - Thrown by JSONParser when reading Species config file.
     * @throws InterruptedException - Thrown if Sleep is interrupted when waiting to retry BioMart query.
     * @throws BioMartQueryException - Thrown if BioMart query doesn't match any existing data in their database.
     * @throws HttpException - Thrown when the Http request to BioMart returns a non-200 response.
     */
    public void downloadData() throws IOException, ParseException, InterruptedException, BioMartQueryException, HttpException {

        // Create directory where Biomart files will be stored.
        Files.createDirectories(Paths.get(this.destination));
        setProperties(EnsemblBiomartUtil.getProperties());

        // Get names of all organisms we add links and/or microarray data for.
        // Species names are in biomart format (eg: hsapiens).
        for (String speciesName : EnsemblBiomartUtil.getSpeciesNames()) {
            String speciesBiomartName = EnsemblBiomartUtil.getBiomartSpeciesName(speciesName);
            logger.info("Retrieving Biomart files for " + speciesBiomartName);

            logger.info("Retrieving microarray data");
            // Query Biomart for existing microarray 'types' (not ids) that exist for this species.
            Set<String> microarrayTypes = queryBiomart(getMicroarrayTypesQuery(speciesBiomartName), 0);
            // Iterate through each microarray type and retrieve Ensembl-Microarray identifier mappings.
            // All mappings are stored in a single file, (eg: hsapiens_microarray);
            for (String microarrayType : microarrayTypes) {
                queryBiomartAndStoreData(speciesBiomartName, getBiomartXMLFilePath(), microarrayType, "_microarray");
            }

            // Query Ensembl-Uniprot (swissprot and trembl) identifier mapping data from Biomart
            // and write it to a file (eg: hsapiens_uniprot).
            logger.info("Retrieving UniProt data");
            for (String uniprotQueryId : Arrays.asList("uniprotswissprot", "uniprotsptrembl")) {
                queryBiomartAndStoreData(speciesBiomartName, getBiomartXMLFilePath(), uniprotQueryId, "_uniprot");
            }

            logger.info("Completed Biomart data retrieval for " + speciesBiomartName);
        }
    }

    // Updates Biomart XML Query with Microarray type information.
    private String getMicroarrayTypesQuery(String biomartSpeciesName) {
        return this.getDataURL() + microarrayTypesBaseQuery.replace("BIOMART_SPECIES_NAME", biomartSpeciesName);
    }

    /**
     * Query Biomart for Microarray 'identifiers' associated with the microarray type and write the data to a file.
     * @param biomartSpeciesName - String of species name in Biomart format (eg: hsapiens)
     * @param biomartQueryFilePath - String, Path to the Biomart XML Query file
     * @param biomartDataType - String, Type of data (either UniProt or Microarray) that will be queried for from BioMart
     * @param fileSuffix - String that will be used to make filename (either _uniprot or _microarray) that will hold associated data.
     * @throws IOException - Thrown when unable to write data to file.
     */
    private void queryBiomartAndStoreData(String biomartSpeciesName, String biomartQueryFilePath, String biomartDataType, String fileSuffix) throws IOException {

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

    /**
     * Write response from BioMart to file in the format 'biomartSpeciesName_microarray' or 'biomartSpeciesName_uniprot'.
     * @param biomartFilename - String, Data from BioMart is saved to this filename.
     * @param biomartResponseLines - Set<String>, All lines of response from BioMart.
     * @throws IOException - Thrown when unable to write data to file.
     */
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

    // Checks that the 4th column contains identifiers (either Microarray or UniProt ).
    private boolean lineContainsIdentifier(List<String> tabSplit) {
        final int IDENTIFIER_COLUMN = 3;
        return tabSplit.size() > IDENTIFIER_COLUMN && !tabSplit.get(IDENTIFIER_COLUMN).trim().isEmpty();
    }


    /**
     * This method queries BioMart using either a URL (see variable 'microarrayTypesBaseQuery) or an XML (see biomart-query.xml in resources) query.
     * It will retry up to 5 times if errors are returned from BioMart instead of data.
     * There are multiple cases (Frog, Both Yeasts, P. falciparum and D. discoideum) where the data does not exist in BioMart.
     * If/When this exception is thrown for this species, it can be ignored. Check the logs from previous runs to confirm.
     * @param queryString - String, URL/XML string that will be used to query BioMart.
     * @param retryCount - int, Denotes how many times this query has been tried with BioMart.
     * @return - Set<String>, All lines of successful BioMart response.
     * @throws IOException - Thrown by HttpURLConnection, BufferedReader, URL classes.
     * @throws InterruptedException - Thrown if Sleep is interrupted when waiting to retry BioMart query.
     * @throws BioMartQueryException - Thrown if BioMart query doesn't match any existing data in their database.
     * @throws HttpException - Thrown when the Http request to BioMart returns a non-200 response.
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

    // Recursive method that retries the BioMart query.
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
        String biomartQuery = "http://www.ensembl.org/biomart/martservice?query=" + URLEncoder.encode(joinQueryFileLines(pathToQueryFile),"UTF-8");
        return biomartQuery.replace("BIOMART_SPECIES_NAME", biomartSpeciesName).replace("BIOMART_QUERY_ID", queryId);
    }

    // Joins lines of XML Query file (biomart-query.xml in resources).
    private String joinQueryFileLines(String pathToFile) throws IOException {
        List<String> fileLines = Files.readAllLines(Paths.get(pathToFile));
        return String.join(System.lineSeparator(), fileLines);
    }

    /**
     * Custom exception that is thrown when a BioMart query throws an error in the response message.
     * At time of writing, they will still return a 200 despite the error. This exception describes that behaviour.
     */
    public static class BioMartQueryException extends Exception {
        BioMartQueryException(String message) {
            super("BioMart query failed with message: " + message);
        }
    }

    // Set properties file to be used in the class.
    private void setProperties(Properties applicationProps) {
        this.properties = applicationProps;
    }

}
