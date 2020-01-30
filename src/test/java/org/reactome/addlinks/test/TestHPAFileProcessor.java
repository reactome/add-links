package org.reactome.addlinks.test;

import org.junit.Test;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.reactome.addlinks.fileprocessors.HPAFileProcessor;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@PrepareForTest({com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderFactoryImpl.class})

public class TestHPAFileProcessor {

    private static final String testDirectory = "src/test/resources/";
    private static final String testFile = "hpa-test.tsv";
    private static final int expectedTestHPAMappingSize = 9;

    @Test
    public void testHPAFileProcessor() throws IOException {

        HPAFileProcessor processor = new HPAFileProcessor("test");
        processor.setPath(Paths.get(testDirectory, testFile + ".zip"));
        Map<String, List<String>> testMapping = processor.getIdMappingsFromFile();

        assertThat(testMapping.size(), is(equalTo(expectedTestHPAMappingSize)));
        assertThat(testMapping.get("P08603").get(0), is(equalTo("ENSG00000000971-CFH")));

        Files.delete(Paths.get(testDirectory, testFile, testFile));
        Files.delete(Paths.get(testDirectory, testFile));
    }

    @Test
    public void testHPAFileProcessorNoFileReturnsEmptyMap() {

        HPAFileProcessor processor = new HPAFileProcessor("test");
        processor.setPath(Paths.get("test.tsv.zip"));
        Map<String, List<String>> testMapping = processor.getIdMappingsFromFile();

        assertThat(testMapping.isEmpty(), is(equalTo(true)));
    }
}
