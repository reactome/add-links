package org.reactome.addlinks.test;

import org.junit.Test;
import org.reactome.addlinks.dataretrieval.ensembl.EnsemblBioMartRetriever;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class EnsemblBioMartRetrieverIT {

    private static final String TEST_DIRECTORY = "/tmp/test_ensembl_biomart_mapping_service/";
    private static final String EXPECTED_ERROR_MESSAGE = "BioMartQueryException: BioMart query failed with message: Query ERROR";
    private static final String BIOMART_URL = "http://www.ensembl.org/biomart/martservice?";

    @Test
    public void testEnsemblBioMartRetrieverDownloadsAndStoresData() throws Exception {

        EnsemblBioMartRetriever retriever = new EnsemblBioMartRetriever();
        System.setProperty("config.location", "src/test/resources/addlinksTest-btau.properties");
        retriever.setDataURL(new URI(BIOMART_URL));
        retriever.setFetchDestination(TEST_DIRECTORY);
        retriever.downloadData();

        Path fetchDestinationOtherIdentifiers = Paths.get(TEST_DIRECTORY + "btaurus_microarray_ids_and_go_terms");
        boolean otherIdentifiersFileExists = Files.exists(fetchDestinationOtherIdentifiers);
        Files.delete(fetchDestinationOtherIdentifiers);
        Path fetchDestinationUniprot = Paths.get(TEST_DIRECTORY + "btaurus_uniprot");
        boolean uniprotFileExists = Files.exists(fetchDestinationUniprot);
        Files.delete(fetchDestinationUniprot);

        assertThat(otherIdentifiersFileExists, is(equalTo(true)));
        assertThat(uniprotFileExists, is(equalTo(true)));
    }

    /**
     * This test is for cases where one of (multiple) queries fails due to the data not existing. It should throw the
     * Exception message but still continue downloading data. The best example, at time of writing (Jan 2020), is
     * S. cerevisiae for the UniProt download. The Uniprot files are comprised of SwissProt and TrEMBL data, but only
     * SwissProt data exists for S. cerevisiae. This means an Exception should be thrown during the TrEMBL
     * attempt but the UniProt file should still exist after successfully downloading SwissProt data.
     * @throws Exception
     */
    @Test
    public void testEnsemblBioMartRetrieverDownloadsPartiallyAvailableData() throws Exception {
        EnsemblBioMartRetriever retriever = new EnsemblBioMartRetriever();
        System.setProperty("config.location", "src/test/resources/addlinksTest-scer.properties");
        final ByteArrayOutputStream errStream = new ByteArrayOutputStream();
        System.setErr(new PrintStream(errStream));
        retriever.setDataURL(new URI(BIOMART_URL));
        retriever.setFetchDestination(TEST_DIRECTORY);
        retriever.downloadData();

        boolean exceptionHappened = errStream.toString().contains(EXPECTED_ERROR_MESSAGE);

        Path fetchDestinationOtherIdentifiers = Paths.get(TEST_DIRECTORY + "scerevisiae_microarray_ids_and_go_terms");
        boolean otherIdentifiersFileExists = Files.exists(fetchDestinationOtherIdentifiers);
        Files.delete(fetchDestinationOtherIdentifiers);
        Path fetchDestinationUniprot = Paths.get(TEST_DIRECTORY + "scerevisiae_uniprot");
        boolean uniprotFileExists = Files.exists(fetchDestinationUniprot);
        Files.delete(fetchDestinationUniprot);

        assertThat(exceptionHappened, is(equalTo(true)));
        assertThat(otherIdentifiersFileExists, is(equalTo(true)));
        assertThat(uniprotFileExists, is(equalTo(true)));
    }

    /**
     * This test is for cases where the XML query being submitted to BioMArt is improperly formatted.
     * @throws Exception
     */
    @Test
    public void testEnsemblBioMartRetrieverBadQueryDoesNotDownloadDataAndThrowsException() throws Exception {
        EnsemblBioMartRetriever retriever = new EnsemblBioMartRetriever();
        System.setProperty("config.location", "src/test/resources/addlinksTest-badQuery.properties");
        final ByteArrayOutputStream errStream = new ByteArrayOutputStream();
        System.setErr(new PrintStream(errStream));
        retriever.setDataURL(new URI(BIOMART_URL));
        retriever.setFetchDestination(TEST_DIRECTORY);
        retriever.downloadData();

        boolean exceptionHappened = errStream.toString().contains(EXPECTED_ERROR_MESSAGE);

        Path fetchDestinationOtherIdentifiers = Paths.get(TEST_DIRECTORY + "btaurus_microarray_ids_and_go_terms");
        boolean otherIdentifiersFileExists = Files.exists(fetchDestinationOtherIdentifiers);
        Path fetchDestinationUniprot = Paths.get(TEST_DIRECTORY + "btaurus_uniprot");
        boolean uniprotFileExists = Files.exists(fetchDestinationUniprot);
        Files.delete(Paths.get(TEST_DIRECTORY));

        assertThat(exceptionHappened, is(equalTo(true)));
        assertThat(otherIdentifiersFileExists, is(equalTo(false)));
        assertThat(uniprotFileExists, is(equalTo(false)));
    }
}
