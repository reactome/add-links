package org.reactome.addlinks.test;

import org.json.simple.parser.ParseException;
import org.junit.Test;
import org.reactome.addlinks.EnsemblBiomartUtil;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;


public class TestEnsemblBiomartUtil {

    private static final int expectedLinesFromFileCount = 10;

    @Test
    public void testGetSpeciesName() throws IOException, ParseException {
        System.setProperty("config.location", "src/test/resources/addlinksTest-btau.properties");
        List<String> speciesNames = EnsemblBiomartUtil.getSpeciesNames();

        assertThat(speciesNames.contains("Bos taurus"), is(equalTo(true)));
    }

    @Test
    public void testGetBiomartSpeciesName() {
        String biomartSpeciesName = EnsemblBiomartUtil.getBiomartSpeciesName("Homo sapiens");

        assertThat(biomartSpeciesName, is(equalTo("hsapiens")));
    }

    @Test
    public void testGetLinesFromFile() throws IOException {
        List<String> linesFromFile = EnsemblBiomartUtil.getLinesFromFile(Paths.get("src/test/resources/mgi-test.tsv"), false);

        assertThat(linesFromFile.size(), is(equalTo(expectedLinesFromFileCount)));
    }

    @Test
    public void testNecessaryColumnsPresent() {
        List<String> testArrayOfFileColumns = Arrays.asList("First", "Second", "Three");
        boolean testColumnPresent = EnsemblBiomartUtil.necessaryColumnPresent(testArrayOfFileColumns, 2);

        assertThat(testColumnPresent, is(equalTo(true)));
    }
}
