package org.reactome.addlinks.test;

import org.junit.Test;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.reactome.addlinks.fileprocessors.HPAFileProcessor;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.fail;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@PrepareForTest({com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderFactoryImpl.class})

public class TestHPAFileProcessor {

    private static final String TEST_DIRECTORY = "src/test/resources/";
    private static final String TEST_FILE = "hpa-test.tsv";
    private static final int EXPECTED_TEST_HPA_MAPPING_SIZE = 9;

    @Test
    public void testHPAFileProcessor() throws IOException {

        HPAFileProcessor processor = new HPAFileProcessor("test");
        processor.setPath(Paths.get(TEST_DIRECTORY, TEST_FILE + ".zip"));
        Map<String, List<String>> testMapping = processor.getIdMappingsFromFile();

        assertThat(testMapping.size(), is(equalTo(EXPECTED_TEST_HPA_MAPPING_SIZE)));
        assertThat(testMapping.get("P08603").get(0), is(equalTo("ENSG00000000971-CFH")));

        Files.delete(Paths.get(TEST_DIRECTORY, TEST_FILE, TEST_FILE));
        Files.delete(Paths.get(TEST_DIRECTORY, TEST_FILE));
    }

    @Test
    public void testHPAFileProcessorNoFileReturnsException() {

        HPAFileProcessor processor = new HPAFileProcessor("test");
        processor.setPath(Paths.get("test.tsv.zip"));
        try {
            Map<String, List<String>> testMapping = processor.getIdMappingsFromFile();
            fail();
        } catch (Exception e) {
            e.printStackTrace();
            assertThat(e, is(instanceOf(NullPointerException.class)));

        }
    }
}
