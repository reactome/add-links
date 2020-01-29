package org.reactome.addlinks.test.referencecreators;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.reactome.addlinks.referencecreators.EnsemblBiomartMicroarrayPopulator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ContextConfiguration("/test-application-context.xml")
@RunWith(org.springframework.test.context.junit4.SpringJUnit4ClassRunner.class)

public class TestEnsemblBiomartMicroarrayPopulator {

    @Autowired
    EnsemblBiomartMicroarrayPopulator microarrayPopulator;

    @Mock
    GKInstance mockRGPInst;

    @Mock
    GKInstance mockRGPInst2;

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
        Map<String, List<String>> mockTranscriptToMicroarrays = new HashMap<>();
        mockTranscriptToMicroarrays.put("ENST12345", Arrays.asList("M12345"));
        Map<String, List<String>> mockUniprotToProteins = new HashMap<>();
        mockUniprotToProteins.put("A7MBE8", Arrays.asList("ENSP12345"));
        mappings.put("btaurus_proteinToTranscripts", mockProteinToTranscripts);
        mappings.put("btaurus_transcriptToMicroarrays", mockTranscriptToMicroarrays);
        mappings.put("btaurus_uniprotToProteins", mockUniprotToProteins);
    }

    @Test
    public void testEnsemblBiomartMicroarrayPopulator() {

        List<GKInstance> mockSourceReferences = Arrays.asList(mockRGPInst);
        System.setProperty("config.location", "src/test/resources/addlinksTest-btau.properties");
        try {
            microarrayPopulator.setTestMode(true);
            microarrayPopulator.createIdentifiers(12345L, mappings, mockSourceReferences);
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        }
    }

    @Test
    public void testFindRGPProteinsReturnsSet() throws Exception {

        Mockito.when(mockRGPInst.getAttributeValue(ReactomeJavaConstants.referenceDatabase)).thenReturn(mockRefDBInst);
        Mockito.when(mockRefDBInst.getDisplayName()).thenReturn("UniProt");
        microarrayPopulator.findRGPProteins("12345", mockRGPInst, "species", mappings);
    }

    @Test
    public void testMapRGPIdentifiersToRGPInstances() throws Exception {

        List<GKInstance> mockRGPInstances = Arrays.asList(mockRGPInst, mockRGPInst2);
        Mockito.when(mockRGPInst.getAttributeValue(ReactomeJavaConstants.identifier)).thenReturn("12345");
        Mockito.when(mockRGPInst2.getAttributeValue(ReactomeJavaConstants.identifier)).thenReturn("09876");

        Map<String, List<GKInstance>> testRGPIdentifierMap = microarrayPopulator.mapRGPIdentifiersToRGPInstances(mockRGPInstances);

        assertThat(testRGPIdentifierMap.get("12345").contains(mockRGPInst), is(equalTo(true)));
        assertThat(testRGPIdentifierMap.get("09876").contains(mockRGPInst2), is(equalTo(true)));
    }

    @Test
    public void testAllDataStructuresPopulated() {
        boolean allDataStructuresPopulated = microarrayPopulator.allDataStructuresPopulated(mockMap, mockMap, mockMap);
        assertThat(allDataStructuresPopulated, is(equalTo(true)));
        boolean notAllDataStructuresPopulated = microarrayPopulator.allDataStructuresPopulated(mockMap, null, mockMap);
        assertThat(notAllDataStructuresPopulated, is(equalTo(false)));
    }
}
