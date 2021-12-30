package org.reactome.addlinks.test;

import org.junit.Test;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.reactome.addlinks.fileprocessors.GlyCosmosFileProcessor;
import org.reactome.addlinks.fileprocessors.XenbaseFileProcessor;

import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.fail;

/**
 * @author Joel Weiser (joel.weiser@oicr.on.ca)
 *         Created 10/20/2021
 */

@PrepareForTest({com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderFactoryImpl.class})

public class TestGlyCosmosFileProcessor {
    private static final String TEST_FILEPATH = "src/test/resources/glycosmos-test.tsv";
    private static final int EXPECTED_TEST_GLYCOSMOS_MAPPING_SIZE = 10;

    @Test
    public void testGlyCosmosFileProcessor() {
        GlyCosmosFileProcessor processor = new GlyCosmosFileProcessor();
        processor.setPath(Paths.get(TEST_FILEPATH));
        Map<String, String> testMapping = processor.getIdMappingsFromFile();

        assertThat(testMapping.size(), is(equalTo(EXPECTED_TEST_GLYCOSMOS_MAPPING_SIZE)));
        assertThat(testMapping.get("R-HSA-428559"), is(equalTo("R-HSA-428559")));
    }

    @Test
    public void testGlyCosmosFileProcessorNoFileReturnsException() {
        GlyCosmosFileProcessor processor = new GlyCosmosFileProcessor();
        processor.setPath(Paths.get("test.tsv"));
        try {
            Map<String, String> testMapping = processor.getIdMappingsFromFile();
            fail();
        } catch (Exception e) {
            e.printStackTrace();
            assertThat(e, is(instanceOf(NullPointerException.class)));
        }
    }
}
