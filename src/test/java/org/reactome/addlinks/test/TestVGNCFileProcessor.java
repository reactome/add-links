package org.reactome.addlinks.test;

import org.junit.Test;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.reactome.addlinks.fileprocessors.VGNCFileProcessor;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@PrepareForTest({com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderFactoryImpl.class})

public class TestVGNCFileProcessor {

    private static final String testFilepath = "src/test/resources/vgnc-test.tsv.gz";
    private static final int expectedTestVGNCMappingSize = 12;

    @Test
    public void testVGNCFileProcessor() throws IOException {
        VGNCFileProcessor processor = new VGNCFileProcessor("test");
        processor.setPath(Paths.get(testFilepath));
        Map<String, List<String>> testMapping = processor.getIdMappingsFromFile();

        assertThat(testMapping.size(), is(equalTo(expectedTestVGNCMappingSize)));
        assertThat(testMapping.get("A0A1U7SHT6").get(0), is(equalTo("82903")));

        Files.delete(Paths.get(testFilepath.replace(".gz", "")));
    }

    @Test
    public void tesVGNCFileProcessorNoFileReturnsException() {
        VGNCFileProcessor processor = new VGNCFileProcessor();
        processor.setPath(Paths.get("test.tsv.gz"));
        try {
            Map<String, List<String>> testMapping = processor.getIdMappingsFromFile();
            fail();
        } catch (Exception e) {
            e.printStackTrace();
            assertThat(e.toString().contains("NullPointerException"), is(equalTo(true)));

        }
    }
}