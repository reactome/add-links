package org.reactome.addlinks.test.referencecreators;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.reactome.addlinks.referencecreators.EnsemblBioMartOtherIdentifierPopulator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.util.*;

@ContextConfiguration("/test-application-context.xml")
@RunWith(org.springframework.test.context.junit4.SpringJUnit4ClassRunner.class)

public class TestEnsemblBioMartOtherIdentifiersPopulator {

    @Autowired
    EnsemblBioMartOtherIdentifierPopulator otherIdentifiersPopulator;

    @Mock
    GKInstance mockRGPInst;

    @Mock
    GKInstance mockRGPInst2;

    @Mock
    GKInstance mockIdentifierString;

    @Mock
    Map<String, List<String>> mockMap;

    @Mock
    GKInstance mockRefDBInst;

    Map<String, Map<String, List<String>>> mappings = new HashMap<>();

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        Map<String, List<String>> mockProteinToTranscripts = new HashMap<>();
        mockProteinToTranscripts.put("ENSP12345", Arrays.asList("ENST12345"));
        Map<String, List<String>> mockTranscriptToOtherIdentifiers = new HashMap<>();
        mockTranscriptToOtherIdentifiers.put("ENST12345", Arrays.asList("M12345"));
        Map<String, List<String>> mockUniprotToProteins = new HashMap<>();
        mockUniprotToProteins.put("A7MBE8", Arrays.asList("ENSP12345"));
        mappings.put("btaurus_proteinToTranscripts", mockProteinToTranscripts);
        mappings.put("btaurus_transcriptToOtherIdentifiers", mockTranscriptToOtherIdentifiers);
        mappings.put("btaurus_uniprotToProteins", mockUniprotToProteins);
    }

    @Test
    public void testEnsemblBioMartOtherIdentifiersPopulator() {

        List<GKInstance> mockSourceReferences = Arrays.asList(mockRGPInst);
        System.setProperty("config.location", "src/test/resources/addlinksTest-btau.properties");
        try {
            otherIdentifiersPopulator.setTestMode(true);
            otherIdentifiersPopulator.createIdentifiers(12345L, mappings, mockSourceReferences);
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        }
    }

    @Test
    public void testMapRGPIdentifiersToRGPInstances() throws Exception {

        List<GKInstance> mockRGPInstances = Arrays.asList(mockRGPInst, mockRGPInst2);
        Mockito.when(mockRGPInst.getAttributeValue(ReactomeJavaConstants.identifier)).thenReturn("12345");
        Mockito.when(mockRGPInst2.getAttributeValue(ReactomeJavaConstants.identifier)).thenReturn("09876");

        Map<String, List<GKInstance>> testRGPIdentifierMap = otherIdentifiersPopulator.mapRGPIdentifiersToRGPInstances(mockRGPInstances);

        assertThat(testRGPIdentifierMap.get("12345").contains(mockRGPInst), is(equalTo(true)));
        assertThat(testRGPIdentifierMap.get("09876").contains(mockRGPInst2), is(equalTo(true)));
    }

    @Test
    public void testAllDataStructuresPopulated() {
        boolean allDataStructuresPopulated = otherIdentifiersPopulator.allDataStructuresPopulated(mockMap, mockMap, mockMap);
        assertThat(allDataStructuresPopulated, is(equalTo(true)));
        boolean notAllDataStructuresPopulated = otherIdentifiersPopulator.allDataStructuresPopulated(mockMap, null, mockMap);
        assertThat(notAllDataStructuresPopulated, is(equalTo(false)));
    }
}
