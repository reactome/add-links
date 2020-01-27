package org.reactome.addlinks.test;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.reactome.addlinks.fileprocessors.ensembl.EnsemblBiomartFileProcessor;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@PowerMockIgnore({"javax.management.*","javax.net.ssl.*", "javax.security.*"})
@RunWith(PowerMockRunner.class)
@PrepareForTest({ java.net.URI.class,
        org.apache.commons.net.ftp.FTPClient.class,
        org.reactome.addlinks.dataretrieval.FileRetriever.class,
        org.apache.http.impl.client.HttpClients.class })

public class TestEnsemblBiomartFileProcessor {

    private static final String uniprotToProteinsKey = "hsapiens_uniprotToProteins";
    private static final String transcriptToMicroarraysKey = "hsapiens_transcriptToMicroarrays";
    private static final String proteinToGenesKey = "hsapiens_proteinToGenes";
    private static final String proteinToTranscriptsKey = "hsapiens_proteinToTranscripts";
    private static final int EXPECTED_RESULTS_LENGTH = 50;
    private static final String testDirectory = "/tmp/test_ensembl_biomart_mapping_service/";
    private static final Path improperlyFormattedFilepath = Paths.get(testDirectory + "hsapiens_improperly_formatted_microarray");
    private static final List<String> improperlyFormattedLines = new ArrayList<>(Arrays.asList("ENSG00000174953ENST00000308361ENSP00000309296ILMN_1738272\n", "ENSG00000230791ENST00000414519ENSP00000400138ILMN_1717127\n"));


    @Test
    public void testEnsemblBiomartFileProcessor() throws IOException {
        EnsemblBiomartFileProcessor processor = new EnsemblBiomartFileProcessor();
        processor.setPath(Paths.get("src/test/resources/"));
        Map<String, Map<String, List<String>>> testMapping = processor.getIdMappingsFromFile();

        boolean uniprotToProteinsPopulated = testMapping.containsKey(uniprotToProteinsKey) && testMapping.get(uniprotToProteinsKey).size() == EXPECTED_RESULTS_LENGTH;
        assertThat(uniprotToProteinsPopulated, is(equalTo(true)));
        boolean transcriptToMicroarraysPopulated = testMapping.containsKey(transcriptToMicroarraysKey) && testMapping.get(transcriptToMicroarraysKey).size() == EXPECTED_RESULTS_LENGTH;
        assertThat(transcriptToMicroarraysPopulated, is(equalTo(true)));
        boolean proteinToGenesPopulated = testMapping.containsKey(proteinToGenesKey) && testMapping.get(proteinToGenesKey).size() == EXPECTED_RESULTS_LENGTH;
        assertThat(proteinToGenesPopulated, is(equalTo(true)));
        boolean proteinToTranscriptsPopulated = testMapping.containsKey(proteinToTranscriptsKey) && testMapping.get(proteinToTranscriptsKey).size() == EXPECTED_RESULTS_LENGTH;
        assertThat(proteinToTranscriptsPopulated, is(equalTo(true)));
    }

    /**
     * This tests that an improperly formatted file will result in the returned Map being empty. Since EnsemblBiomartFileProcessor
     * evaluates the entire directory for files ending with 'uniprot' and 'microarray', the test directory can't hold both a
     * valid and invalid file type. To get around this the improperly formatted file is just generated within the test.
     * @throws IOException
     */
    @Test
    public void testEnsemblBiomartFileProcessorReturnsEmptyMapWithImproperlyFormattedFile() throws IOException {

        EnsemblBiomartFileProcessor processor = new EnsemblBiomartFileProcessor();
        processor.setPath(Paths.get(testDirectory));
        Files.createDirectories(Paths.get(testDirectory));
        Files.createFile(improperlyFormattedFilepath);
        for (String line : improperlyFormattedLines) {
            Files.write(improperlyFormattedFilepath, line.getBytes(), StandardOpenOption.APPEND);
        }

        Map<String, Map<String, List<String>>> testMapping = processor.getIdMappingsFromFile();
        Files.delete(improperlyFormattedFilepath);

        assertThat(testMapping.isEmpty(), is(equalTo(true)));
    }
}
