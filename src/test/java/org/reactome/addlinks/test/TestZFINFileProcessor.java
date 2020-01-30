package org.reactome.addlinks.test;

import org.junit.Test;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.reactome.addlinks.fileprocessors.ZFINFileProcessor;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.fail;

import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@PrepareForTest({com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderFactoryImpl.class})

public class TestZFINFileProcessor {

    private static final String testFilepath = "src/test/resources/zfin-test.tsv";
    private static final int expectedZFINTestMappingSize = 9;

    @Test
    public void testZFINFileProcessor() {
        ZFINFileProcessor processor = new ZFINFileProcessor();
        processor.setPath(Paths.get(testFilepath));
        Map<String, List<String>> testMapping = processor.getIdMappingsFromFile();

        assertThat(testMapping.size(), is(equalTo(expectedZFINTestMappingSize)));
        assertThat(testMapping.get("F1Q650").get(0), is(equalTo("ZDB-GENE-060528-1")));
    }

    @Test
    public void testZFINFileProcessorNoFileReturnsException() {
        ZFINFileProcessor processor = new ZFINFileProcessor();
        processor.setPath(Paths.get("test.tsv"));
        try {
            Map<String, List<String>> testMapping = processor.getIdMappingsFromFile();
            fail();
        } catch (Exception e) {
            e.printStackTrace();
            assertThat(e.toString().contains("NullPointerException"), is(equalTo(true)));
        }
    }
}
