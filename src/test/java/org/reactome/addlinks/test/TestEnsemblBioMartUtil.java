package org.reactome.addlinks.test;

import org.junit.Test;
import org.reactome.addlinks.EnsemblBioMartUtil;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;


public class TestEnsemblBioMartUtil {

    private static final int EXPECTED_LINES_FROM_FILE_COUNT = 10;

    @Test
    public void testGetSpeciesName() throws IOException {
        System.setProperty("config.location", "src/test/resources/addlinksTest-btau.properties");
        List<String> speciesNames = EnsemblBioMartUtil.getSpeciesNames();

        assertThat(speciesNames.contains("Bos taurus"), is(equalTo(true)));
    }

    @Test
    public void testGetBioMartSpeciesName() {
        String biomartSpeciesName = EnsemblBioMartUtil.getBioMartSpeciesName("Homo sapiens");

        assertThat(biomartSpeciesName, is(equalTo("hsapiens")));
    }

    @Test
    public void testGetLinesFromFile() {
        List<String> linesFromFile = EnsemblBioMartUtil.getLinesFromFile(Paths.get("src/test/resources/mgi-test.tsv"), false);

        assertThat(linesFromFile, hasSize(EXPECTED_LINES_FROM_FILE_COUNT));
    }

    @Test
    public void testNecessaryColumnsPresent() {
        List<String> testArrayOfFileColumns = Arrays.asList("First", "Second", "Three");
        boolean testColumnPresent = EnsemblBioMartUtil.necessaryColumnPresent(testArrayOfFileColumns, 2);

        assertThat(testColumnPresent, is(equalTo(true)));
    }

    @Test
    public void testGetIdentifierWithoutPrefix() {
        String testIdentifierWithPrefix = "Before:After";
        String testIdentifierWithoutPrefix = EnsemblBioMartUtil.getIdentifierWithoutPrefix(testIdentifierWithPrefix);

        assertThat(testIdentifierWithoutPrefix, is(equalTo("After")));
    }
}
