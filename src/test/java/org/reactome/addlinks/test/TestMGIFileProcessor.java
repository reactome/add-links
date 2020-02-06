package org.reactome.addlinks.test;

import org.junit.Test;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.reactome.addlinks.fileprocessors.MGIFileProcessor;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.fail;

import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@PrepareForTest({com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderFactoryImpl.class})

public class TestMGIFileProcessor {

    private static final String TEST_FILEPATH = "src/test/resources/mgi-test.tsv";
    private static final int EXPECTED_TEST_MGI_MAPPING_SIZE = 28;

    @Test
    public void testMGIFileProcessor() {
        MGIFileProcessor processor = new MGIFileProcessor();
        processor.setPath(Paths.get(TEST_FILEPATH));
        Map<String, List<String>> testMapping =  processor.getIdMappingsFromFile();

        assertThat(testMapping.size(), is(equalTo(EXPECTED_TEST_MGI_MAPPING_SIZE)));
        assertThat(testMapping.get("Q9CW84").get(0), is(equalTo("1913301")));
    }

    @Test
    public void testMGIFileProcessorNoFileReturnsException() {
        MGIFileProcessor processor = new MGIFileProcessor();
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
