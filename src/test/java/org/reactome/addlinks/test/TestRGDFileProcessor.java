package org.reactome.addlinks.test;

import org.junit.Test;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.reactome.addlinks.fileprocessors.RGDFileProcessor;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.fail;

import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@PrepareForTest({com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderFactoryImpl.class})

public class TestRGDFileProcessor {

    private static final String TEST_FILEPATH = "src/test/resources/rgd-test.tsv";
    private static final int EXPECTED_TEST_RGD_MAPPING_SIZE = 13;

    @Test
    public void testRGDFileProcessor() {
        RGDFileProcessor processor = new RGDFileProcessor();
        processor.setPath(Paths.get(TEST_FILEPATH));
        Map<String, List<String>> testMapping = processor.getIdMappingsFromFile();

        assertThat(testMapping.size(), is(equalTo(EXPECTED_TEST_RGD_MAPPING_SIZE)));
        assertThat(testMapping.get("P06238").get(0), is(equalTo("2004")));
    }

    @Test
    public void testRGDFileProcessorNoFileReturnsException() {
        RGDFileProcessor processor = new RGDFileProcessor();
        processor.setPath(Paths.get("test.tsv"));
        try {
            Map<String, List<String>> testMapping = processor.getIdMappingsFromFile();
            fail();
        } catch (Exception e) {
            e.printStackTrace();
            assertThat(e, is(instanceOf(NullPointerException.class)));

        }
    }
}
