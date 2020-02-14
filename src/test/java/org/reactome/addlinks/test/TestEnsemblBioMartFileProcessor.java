package org.reactome.addlinks.test;

import org.junit.Before;
import org.junit.Test;
import org.reactome.addlinks.fileprocessors.ensembl.EnsemblBioMartFileProcessor;

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

public class TestEnsemblBioMartFileProcessor {

    private static final String UNIPROT_TO_PROTEINS_KEY = "hsapiens_uniprotToProteins";
    private static final String TRANSCRIPT_TO_OTHER_IDENTIFIERS_KEY = "hsapiens_transcriptToOtherIdentifiers";
    private static final String PROTEIN_TO_GENES_KEY = "hsapiens_proteinToGenes";
    private static final String PROTEIN_TO_TRANSCRIPTS_KEY = "hsapiens_proteinToTranscripts";
    private static final int EXPECTED_RESULTS_LENGTH = 50;
    private static final String TEST_DIRECTORY = "/tmp/test_ensembl_biomart_mapping_service/";
    private static final Path IMPROPERLY_FORMATTED_FILEPATH = Paths.get(TEST_DIRECTORY + "hsapiens_improperly_formatted_other_identifiers");
    private static final List<String> IMPROPERLY_FORMATTED_LINES = new ArrayList<>(Arrays.asList("ENSG00000174953ENST00000308361ENSP00000309296ILMN_1738272\n", "ENSG00000230791ENST00000414519ENSP00000400138ILMN_1717127\n"));

    private EnsemblBioMartFileProcessor ensemblBioMartFileProcessor;

    @Before
    public void createEnsemblBioMartFileProcessor() {
        ensemblBioMartFileProcessor = new EnsemblBioMartFileProcessor();
    }

    @Test
    public void testEnsemblBioMartFileProcessorMapHasUniProtToProteinsKey() {
        ensemblBioMartFileProcessor.setPath(Paths.get("src/test/resources/"));
        Map<String, Map<String, List<String>>> testMapping = ensemblBioMartFileProcessor.getIdMappingsFromFile();

        assertThat(testMapping, hasKey(UNIPROT_TO_PROTEINS_KEY));
        assertThat(testMapping.get(UNIPROT_TO_PROTEINS_KEY), is(aMapWithSize(EXPECTED_RESULTS_LENGTH)));
    }

    @Test
    public void testEnsemblBioMartFileProcessorHasTranscriptToOtherIdentifiersKey() {
        ensemblBioMartFileProcessor.setPath(Paths.get("src/test/resources/"));
        Map<String, Map<String, List<String>>> testMapping = ensemblBioMartFileProcessor.getIdMappingsFromFile();

        assertThat(testMapping, hasKey(TRANSCRIPT_TO_OTHER_IDENTIFIERS_KEY));
        assertThat(testMapping.get(TRANSCRIPT_TO_OTHER_IDENTIFIERS_KEY), is(aMapWithSize(EXPECTED_RESULTS_LENGTH)));
    }

    @Test
    public void testEnsemblBioMartFileProcessorHasProteinToGenesKey() {
        ensemblBioMartFileProcessor.setPath(Paths.get("src/test/resources/"));
        Map<String, Map<String, List<String>>> testMapping = ensemblBioMartFileProcessor.getIdMappingsFromFile();

        assertThat(testMapping, hasKey(PROTEIN_TO_GENES_KEY));
        assertThat(testMapping.get(PROTEIN_TO_GENES_KEY), is(aMapWithSize(EXPECTED_RESULTS_LENGTH)));
    }

    @Test
    public void testEnsemblBioMartFileProcessorHasProteinToTranscriptsKey() {
        ensemblBioMartFileProcessor.setPath(Paths.get("src/test/resources/"));
        Map<String, Map<String, List<String>>> testMapping = ensemblBioMartFileProcessor.getIdMappingsFromFile();

        assertThat(testMapping, hasKey(PROTEIN_TO_TRANSCRIPTS_KEY));
        assertThat(testMapping.get(PROTEIN_TO_TRANSCRIPTS_KEY), is(aMapWithSize(EXPECTED_RESULTS_LENGTH)));
    }

    /**
     * This tests that an improperly formatted file will result in the returned Map being empty. Since EnsemblBioMartFileProcessor
     * evaluates the entire directory for files ending with 'uniprot' and 'microarray_ids_and_go_terms', the test directory can't hold both a
     * valid and invalid file type. To get around this the improperly formatted file is just generated within the test.
     * @throws IOException
     */
    @Test
    public void testEnsemblBioMartFileProcessorReturnsEmptyMapWithImproperlyFormattedFile() throws IOException {
        ensemblBioMartFileProcessor.setPath(Paths.get(TEST_DIRECTORY));
        Files.createDirectories(Paths.get(TEST_DIRECTORY));
        Files.createFile(IMPROPERLY_FORMATTED_FILEPATH);
        for (String line : IMPROPERLY_FORMATTED_LINES) {
            Files.write(IMPROPERLY_FORMATTED_FILEPATH, line.getBytes(), StandardOpenOption.APPEND);
        }

        Map<String, Map<String, List<String>>> testMapping = ensemblBioMartFileProcessor.getIdMappingsFromFile();
        Files.delete(IMPROPERLY_FORMATTED_FILEPATH);
        Files.delete(Paths.get(TEST_DIRECTORY));

        assertThat(testMapping.isEmpty(), is(equalTo(true)));
    }
}
