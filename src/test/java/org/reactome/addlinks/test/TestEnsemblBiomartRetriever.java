package org.reactome.addlinks.test;

import org.junit.Test;
import org.reactome.addlinks.dataretrieval.ensembl.EnsemblBiomartRetriever;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class TestEnsemblBiomartRetriever {

    private static final String testDirectory = "/tmp/test_ensembl_biomart_mapping_service/";
    private static final String expectedErrorMessage = "java.lang.Exception: Biomart query failed with message: Query ERROR";

    //TODO Move these to IT?
    @Test
    public void testEnsemblBiomartRetrieverDownloadsAndStoresData() throws Exception {

        EnsemblBiomartRetriever retriever = new EnsemblBiomartRetriever();
        System.setProperty("config.location", "src/test/resources/addlinksTest-btau.properties");
        retriever.setDataURL(new URI("http://www.ensembl.org/biomart/martservice?"));
        retriever.setFetchDestination(testDirectory);
        retriever.downloadData();

        Path fetchDestinationMicroarray = Paths.get(testDirectory + "btaurus_microarray");
        boolean microarrayFileExists = Files.exists(fetchDestinationMicroarray);
        Files.delete(fetchDestinationMicroarray);
        Path fetchDestinationUniprot = Paths.get(testDirectory + "btaurus_uniprot");
        boolean uniprotFileExists = Files.exists(fetchDestinationUniprot);
        Files.delete(fetchDestinationUniprot);

        assertThat(microarrayFileExists, is(equalTo(true)));
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
    public void testEnsemblBiomartRetrieverDownloadsPartiallyAvailableData() throws Exception {
        EnsemblBiomartRetriever retriever = new EnsemblBiomartRetriever();
        System.setProperty("config.location", "src/test/resources/addlinksTest-scer.properties");
        final ByteArrayOutputStream errStream = new ByteArrayOutputStream();
        System.setErr(new PrintStream(errStream));
        retriever.setDataURL(new URI("http://www.ensembl.org/biomart/martservice?"));
        retriever.setFetchDestination(testDirectory);
        retriever.downloadData();

        boolean exceptionHappened = errStream.toString().contains(expectedErrorMessage);

        Path fetchDestinationMicroarray = Paths.get(testDirectory + "scerevisiae_microarray");
        boolean microarrayFileExists = Files.exists(fetchDestinationMicroarray);
        Files.delete(fetchDestinationMicroarray);
        Path fetchDestinationUniprot = Paths.get(testDirectory + "scerevisiae_uniprot");
        boolean uniprotFileExists = Files.exists(fetchDestinationUniprot);
        Files.delete(fetchDestinationUniprot);

        assertThat(exceptionHappened, is(equalTo(true)));
        assertThat(microarrayFileExists, is(equalTo(true)));
        assertThat(uniprotFileExists, is(equalTo(true)));
    }

    @Test
    public void testEnsemblBiomartRetrieverBadQueryDoesNotDownloadDataAndThrowsException() throws Exception {
        EnsemblBiomartRetriever retriever = new EnsemblBiomartRetriever();
        System.setProperty("config.location", "src/test/resources/addlinksTest-badQuery.properties");
        final ByteArrayOutputStream errStream = new ByteArrayOutputStream();
        System.setErr(new PrintStream(errStream));
        retriever.setDataURL(new URI("http://www.ensembl.org/biomart/martservice?"));
        retriever.setFetchDestination(testDirectory);
        retriever.downloadData();

        boolean exceptionHappened = errStream.toString().contains(expectedErrorMessage);

        Path fetchDestinationMicroarray = Paths.get(testDirectory + "btaurus_microarray");
        boolean microarrayFileExists = Files.exists(fetchDestinationMicroarray);
        Files.delete(fetchDestinationMicroarray);
        Path fetchDestinationUniprot = Paths.get(testDirectory + "btaurus_uniprot");
        boolean uniprotFileExists = Files.exists(fetchDestinationUniprot);
        Files.delete(fetchDestinationUniprot);

        assertThat(exceptionHappened, is(equalTo(true)));
        assertThat(microarrayFileExists, is(equalTo(false)));
        assertThat(uniprotFileExists, is(equalTo(false)));
    }
}
