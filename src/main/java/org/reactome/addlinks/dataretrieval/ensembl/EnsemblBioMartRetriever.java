package org.reactome.addlinks.dataretrieval.ensembl;

import org.apache.http.HttpException;
import org.apache.http.HttpStatus;
import org.reactome.addlinks.EnsemblBioMartUtil;
import org.reactome.release.common.dataretrieval.FileRetriever;

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

    private static final String BIOMART_SPECIES_NAME_PLACEHOLDER = "BIOMART_SPECIES_NAME";
    private static final String BIOMART_QUERY_ID_PLACEHOLDER = "BIOMART_QUERY_ID";
    private static final String baseBioMartURL = "https://www.ensembl.org/biomart/martservice?query=";
    private static final String microarrayTypesBaseQuery = String.join("&",
            "type=listAttributes",
            "mart=ENSEMBL_MART_ENSEMBL",
            "virtualSchema=default",
            "dataset=" + BIOMART_SPECIES_NAME_PLACEHOLDER + "_gene_ensembl",
            "interface=default",
            "attributePage=feature_page",
            "attributeGroup=external",
            "attributeCollection=microarray"
    );
    private static final String MICROARRAY_TYPES = "microarray types";
    private static final String GO_ID_BIOMART_SEARCH_TERM = "go_id";
    private static final String GO_SLIM_BIOMART_SEARCH_TERM = "goslim_goa_accession";
    private static final String UNIPROT_SWISSPROT_BIOMART_SEARCH_TERM = "uniprotswissprot";
    private static final String UNIPROT_TREMBL_BIOMART_SEARCH_TERM = "uniprotsptrembl";
    private static final String NCBI_ENTREZ_BIOMART_SEARCH_TERM = "entrezgene_id";

    /**
     * Downloads Ensembl-Microarray, Ensembl-GO, and Ensembl-Uniprot identifier mapping files for all species, if they exist.
     * @throws IOException - Thrown when file can't be found during writing or by HTTPConnection class.
     * @throws InterruptedException - Thrown if Sleep is interrupted when waiting to retry BioMart query.
     * @throws BioMartQueryException - Thrown if BioMart query doesn't match any existing data in their database.
     * @throws HttpException - Thrown when the Http request to BioMart returns a non-200 response.
     */
    public void downloadData() throws IOException, InterruptedException, BioMartQueryException, HttpException {

        // Create directory where BioMart files will be stored.
        Files.createDirectories(Paths.get(this.destination));
        setProperties(EnsemblBioMartUtil.getProperties());

        // Get names of all organisms we add links and/or microarray data for.
        // Species names are in bioMart format (eg: hsapiens).
        for (String speciesName : EnsemblBioMartUtil.getSpeciesNames()) {
            String speciesBioMartName = EnsemblBioMartUtil.getBioMartSpeciesName(speciesName);
            logger.info("Retrieving BioMart files for " + speciesBioMartName);
            Set<String> biomartOtherIdentifierSearchTerms = new HashSet<>(Arrays.asList(GO_ID_BIOMART_SEARCH_TERM, GO_SLIM_BIOMART_SEARCH_TERM, NCBI_ENTREZ_BIOMART_SEARCH_TERM));
            logger.info("Retrieving microarray data");
            // Query BioMart for existing microarray 'types' (not ids) that exist for this species.
            biomartOtherIdentifierSearchTerms.addAll(queryBioMart(getMicroarrayTypesQuery(speciesBioMartName), MICROARRAY_TYPES));
            // Perform BioMart queries for OtherIdentifiers data. All mappings are stored in a single file, (eg: hsapiens_microarray_go_ncbi_ids).
            queryBioMartForSearchTerms(speciesBioMartName, biomartOtherIdentifierSearchTerms, EnsemblBioMartUtil.OTHER_IDENTIFIERS_SUFFIX);

            // Query Ensembl-Uniprot (swissprot and trembl) identifier mapping data from BioMart and write it to a file (eg: hsapiens_uniprot).
            Set<String> biomartUniProtIdentifierSearchTerms = new HashSet<>(Arrays.asList(UNIPROT_SWISSPROT_BIOMART_SEARCH_TERM, UNIPROT_TREMBL_BIOMART_SEARCH_TERM));
            queryBioMartForSearchTerms(speciesBioMartName, biomartUniProtIdentifierSearchTerms, EnsemblBioMartUtil.UNIPROT_SUFFIX);

            logger.info("Completed BioMart data retrieval for " + speciesBioMartName);
        }
    }

    /**
     * Query BioMart for every term in 'biomartSearchTerms', retrieving Ensembl-identifiers mappings where
     * identifiers can be for microarray, GO, NCBI, and UniProt identifiers.
     * @param speciesBioMartName String -- String of species name in BioMart format (eg: hsapiens).
     * @param biomartSearchTerms Set<String> -- All BioMart search terms that will be queried.
     * @param biomartFileSuffix String -- Suffix of file that will hold the queried BioMart data.
     * @throws IOException -- Can be thrown by 'getBioMartXMLFilePath()' if XML query template file is not found.
     */
    private void queryBioMartForSearchTerms(String speciesBioMartName, Set<String> biomartSearchTerms, String biomartFileSuffix) throws IOException {
        String biomartFilename = this.destination + speciesBioMartName + biomartFileSuffix;
        if (!Files.exists(Paths.get(biomartFilename))) {
            // Iterate through each BioMart search term and retrieve Ensembl-identifier  mappings.
            for (String biomartSearchTerm : biomartSearchTerms) {
                logger.info("Retrieving " + biomartSearchTerm + " mappings from BioMart");
                queryBioMartAndStoreData(speciesBioMartName, getBioMartXMLFilePath(), biomartSearchTerm, biomartFilename);
            }
        } else {
            logFileExistsMessage(biomartFilename);
        }
    }

    // Updates BioMart XML Query with Microarray type information.
    private String getMicroarrayTypesQuery(String bioMartSpeciesName) {
        return this.getDataURL() + microarrayTypesBaseQuery.replace(BIOMART_SPECIES_NAME_PLACEHOLDER, bioMartSpeciesName);
    }

    /**
     * Query BioMart for Microarray 'identifiers' associated with the microarray type and write the data to a file.
     * @param biomartSpeciesName - String of species name in BioMart format (eg: hsapiens)
     * @param biomartQueryFilePath - String, Path to the BioMart XML Query file
     * @param biomartDataType - String, Type of data (either UniProt or Microarray) that will be queried for from BioMart
     * @param biomartFilename - Name of file (ending either with _uniprot or _microarray_go_ncbi_ids) that will hold associated data.
     * @throws IOException - Thrown when unable to write data to file.
     */
    private void queryBioMartAndStoreData(String biomartSpeciesName, String biomartQueryFilePath, String biomartDataType, String biomartFilename) throws IOException {
        logger.info("Querying BioMart for species: {}; data type: {}", biomartSpeciesName, biomartDataType);
        Set<String> biomartResponseLines = new HashSet<>();
        logger.info("Retrieving data associated with query ID: {}", biomartDataType);
        try {
            biomartResponseLines = queryBioMart(
                    getBioMartIdentifierQuery(biomartQueryFilePath, biomartSpeciesName, biomartDataType),
                    biomartDataType
            );
        } catch (Exception e) {
            logger.error("Unable to retrieve data associated with query ID: " + biomartDataType, e);
            e.printStackTrace();
        }
        storeBioMartData(biomartFilename, biomartResponseLines);
    }

    /**
     * Write response from BioMart to file in the format 'BioMartSpeciesName_microarray_ids_and_go_terms' or 'BioMartSpeciesName_uniprot'.
     * @param biomartFilename - String, Data from BioMart is saved to this filename.
     * @param biomartResponseLines - Set<String>, All lines of response from BioMart.
     * @throws IOException - Thrown when unable to write data to file.
     */
    private void storeBioMartData(String biomartFilename, Set<String> biomartResponseLines) throws IOException {

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

    // Checks that the 4th column contains identifiers (of type Microarray, GO, or UniProt).
    private boolean lineContainsIdentifier(List<String> tabSplit) {
        final int IDENTIFIER_COLUMN = 3;
        return tabSplit.size() > IDENTIFIER_COLUMN && !tabSplit.get(IDENTIFIER_COLUMN).trim().isEmpty();
    }


    /**
     * This method queries BioMart using either a URL (see variable 'microarrayTypesBaseQuery) or an XML (see biomart-query.xml in resources) query.
     * It will retry up to 5 times if errors are returned from BioMart instead of data. This initial method sets the initial retry count.
     * There are multiple cases (Both S. cerevisiae & S. pombe yeasts, P. falciparum and D. discoideum) where the data does not exist in BioMart.
     * @param queryString - String, URL/XML string that will be used to query BioMart.
     * @return - Set<String>, All lines of successful BioMart response.
     * @throws IOException - Thrown by HttpURLConnection, BufferedReader, URL classes.
     * @throws InterruptedException - Thrown if Sleep is interrupted when waiting to retry BioMart query.
     * @throws BioMartQueryException - Thrown if BioMart query doesn't match any existing data in their database.
     * @throws HttpException - Thrown when the Http request to BioMart returns a non-200 response.
     */
    private Set<String> queryBioMart(String queryString, String biomartDataType)
            throws InterruptedException, HttpException, BioMartQueryException, IOException {

        int initialRetryCount = 0;
        return queryBioMart(queryString, initialRetryCount, biomartDataType);
    }

    /**
     * This method queries BioMart using either a URL (see variable 'microarrayTypesBaseQuery) or an XML (see biomart-query.xml in resources) query.
     * It will retry up to 5 times if errors are returned from BioMart instead of data. This overloaded method actually performs the query.
     * There are multiple cases (Both S. cerevisiae & S. pombe yeasts, P. falciparum and D. discoideum) where the data does not exist in BioMart.
     * @param queryString - String, URL/XML string that will be used to query BioMart.
     * @param retryCount - int, Denotes how many times this query has been tried with BioMart.
     * @return - Set<String>, All lines of successful BioMart response.
     * @throws IOException - Thrown by HttpURLConnection, BufferedReader, URL classes.
     * @throws InterruptedException - Thrown if Sleep is interrupted when waiting to retry BioMart query.
     * @throws BioMartQueryException - Thrown if BioMart query doesn't match any existing data in their database.
     * If/When this exception is thrown for this species, it can be ignored. Check the logs from previous runs to confirm.
     * @throws HttpException - Thrown when the Http request to BioMart returns a non-200 response.
     */
    private Set<String> queryBioMart(String queryString, int retryCount, String biomartDataType) throws IOException, InterruptedException, BioMartQueryException, HttpException {
        final int MAX_QUERY_RETRIES = 5;
        int numLinesProcessed = 0;
        // Create connection to BioMart URL for each species, retrieving mappings of Ensembl identifiers to microarray, GO, or UniProt identifiers.
        URL biomartUrlWithSpecies = new URL(queryString);
        HttpURLConnection biomartConnection = (HttpURLConnection) biomartUrlWithSpecies.openConnection();
        if (biomartConnection.getResponseCode() == HttpStatus.SC_OK) {
            Set<String> biomartResponseLines = new HashSet<>();
            BufferedReader br = new BufferedReader(new InputStreamReader(biomartConnection.getInputStream()));
            String line;
            while ((line = br.readLine()) != null) {
                numLinesProcessed++;
                // BioMart still responds with a 200, even if no data exists. For now, we handle it by
                // checking the returned content for the string 'ERROR'. It will retry up to 5 times, with a 10 second delay.
                if (line.contains("ERROR") ) {
                    if (retryCount > MAX_QUERY_RETRIES) {
                        // The data does not exist in BioMart for a few species. Frog (X. tropicalis) has UniProt data but not Microarray data;
                        // Yeast (S. cerevisiae) has Uniprot-SwissProt data but not UniProt-TrEMBL data; Yeast (S. pombe), P. falciparum
                        // and D. discoideum don't have UniProt or Microarray data, all at time of writing (January 2020).
                        throw new BioMartQueryException(line +
                                "\nThis can happen without issue for certain species (D. discoideum, S. pombe, S. cerevisiae, P. falciparum) " +
                                "because the data doesn't exist in BioMart");
                    }

                    return retryQuery(queryString, retryCount+1, biomartDataType);

                } else {
                    biomartResponseLines.add(line);
                }

                if (numLinesProcessed % 10000 == 0) {
                    logger.info("Processed " + numLinesProcessed + " for " + biomartDataType);
                }
            }
            return biomartResponseLines;

        } else {
            if (retryCount > MAX_QUERY_RETRIES) {
                throw new HttpException(
                        "Unable to connect to BioMart (" +
                                biomartConnection.getResponseCode() + ": " + biomartConnection.getResponseMessage() +
                                ") with URL: " + queryString
                );
            }
            return retryQuery(queryString, retryCount+1, biomartDataType);
        }
    }

    // Recursive method that retries the BioMart query.
    private Set<String> retryQuery(String queryString, int retryCount, String biomartDataType) throws InterruptedException, IOException, HttpException, BioMartQueryException {
        final long QUERY_SLEEP_DURATION = Duration.ofSeconds(5).toMillis();

        Thread.sleep(QUERY_SLEEP_DURATION);
        logger.warn("BioMart query failed. Trying again...");
        return queryBioMart(queryString, retryCount, biomartDataType);
    }

    // Gets the filepath to BioMart XML file, used to build BioMart query.
    private String getBioMartXMLFilePath() throws IOException {
        // Get species JSON config file location, import as JSON object.
        return this.properties.getProperty("pathToBioMartXML");
    }

    // Modifies default secondary URL with species name and the query type (for example microarray probe type, or uniprotsptrembl).
    private String getBioMartIdentifierQuery(String pathToQueryFile, String biomartSpeciesName, String queryId) throws IOException {
        String biomartQuery = baseBioMartURL + URLEncoder.encode(joinQueryFileLines(pathToQueryFile),"UTF-8");
        return biomartQuery.replace(BIOMART_SPECIES_NAME_PLACEHOLDER, biomartSpeciesName).replace(BIOMART_QUERY_ID_PLACEHOLDER, queryId);
    }

    // Joins lines of XML Query file (biomart-query.xml in resources).
    private String joinQueryFileLines(String pathToFile) throws IOException {
        List<String> fileLines = Files.readAllLines(Paths.get(pathToFile));
        return String.join(System.lineSeparator(), fileLines);
    }

    /**
     * Logs when a file already exists. If it does, the BioMart queries will not be attempted.
     * @param filename String -- Name of file that already exists.
     */
    private void logFileExistsMessage(String filename) {
        logger.info("{} already exists. It is *assumed* to be complete, and will not be downloaded.", filename);
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
