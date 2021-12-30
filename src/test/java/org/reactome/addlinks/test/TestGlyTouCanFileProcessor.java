package org.reactome.addlinks.test;

import org.junit.Test;
import org.reactome.addlinks.fileprocessors.GlyTouCanFileProcessor;

import java.nio.file.Paths;
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
public class TestGlyTouCanFileProcessor {
    private static final String TEST_FILEPATH = "src/test/resources/glytoucan-test.tsv";
    private static final int EXPECTED_TEST_GLYTOUCAN_MAPPING_SIZE = 10;

    @Test
    public void testGlyTouCanFileProcessor() {
        GlyTouCanFileProcessor processor = new GlyTouCanFileProcessor();
        processor.setPath(Paths.get(TEST_FILEPATH));
        Map<String, String> testMapping = processor.getIdMappingsFromFile();

        assertThat(testMapping.size(), is(equalTo(EXPECTED_TEST_GLYTOUCAN_MAPPING_SIZE)));
        System.out.println(testMapping);
        assertThat(testMapping.get("27387"), is(equalTo("G68137PO")));
    }

    @Test
    public void testGlyTouCanFileProcessorNoFileReturnsException() {
        GlyTouCanFileProcessor processor = new GlyTouCanFileProcessor();
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
