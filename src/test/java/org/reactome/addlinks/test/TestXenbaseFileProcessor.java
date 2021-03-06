package org.reactome.addlinks.test;

import org.junit.Test;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.reactome.addlinks.fileprocessors.XenbaseFileProcessor;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.fail;

import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@PrepareForTest({com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderFactoryImpl.class})

public class TestXenbaseFileProcessor {

    private static final String TEST_FILEPATH = "src/test/resources/xenbase-test.tsv";
    private static final int EXPECTED_TEST_XENBASE_MAPPING_SIZE = 10;

    @Test
    public void testXenbaseFileProcessor() {

        XenbaseFileProcessor processor = new XenbaseFileProcessor();
        processor.setPath(Paths.get(TEST_FILEPATH));
        Map<String, List<String>> testMapping = processor.getIdMappingsFromFile();

        assertThat(testMapping.size(), is(equalTo(EXPECTED_TEST_XENBASE_MAPPING_SIZE)));
        assertThat(testMapping.get("F6QJR9").get(0), is(equalTo("XB-GENE-478064")));
    }

    @Test
    public void testXenbaseFileProcessorNoFileReturnsException() {
        XenbaseFileProcessor processor = new XenbaseFileProcessor();
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
