package org.reactome.addlinks.test.referencecreators;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.reactome.addlinks.referencecreators.EnsemblReferenceCreator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.fail;

import java.util.*;

@ContextConfiguration("/test-application-context.xml")
@RunWith(org.springframework.test.context.junit4.SpringJUnit4ClassRunner.class)

public class TestEnsemblReferenceCreator {

    @Autowired
    EnsemblReferenceCreator ensemblRefCreator;

    @Mock
    GKInstance mockRGPInst;

    @Mock
    GKInstance mockSpeciesInst;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testEnsemblReferenceCreator() {

        try {
            Map<String, Map<String, List<String>>> mappings = new HashMap<>();
            Map<String, List<String>> mockProteinToTranscripts = new HashMap<>();
            mockProteinToTranscripts.put("ENSP12345", Arrays.asList("ENST12345"));
            Map<String, List<String>> mockProteinToGenes = new HashMap<>();
            mockProteinToGenes.put("ENSP12345", Arrays.asList("ENSG12345"));
            Map<String, List<String>> mockUniprotToProteins = new HashMap<>();
            mockUniprotToProteins.put("U12345", Arrays.asList("ENSP12345"));
            mappings.put("hsapiens_proteinToTranscripts", mockProteinToTranscripts);
            mappings.put("hsapiens_proteinToGenes", mockProteinToGenes);
            mappings.put("hsapiens_uniprotToProteins", mockUniprotToProteins);

            Mockito.when(mockRGPInst.getAttributeValue(ReactomeJavaConstants.identifier)).thenReturn("U12345");
            Mockito.when(mockRGPInst.getAttributeValue(ReactomeJavaConstants.species)).thenReturn(mockSpeciesInst);
            Mockito.when(mockSpeciesInst.getDisplayName()).thenReturn("Homo sapiens");

            List<GKInstance> mockSourceReferences = Arrays.asList(mockRGPInst);

            ensemblRefCreator.setTestMode(true);
            ensemblRefCreator.createIdentifiers(12345L, mappings, mockSourceReferences);

        } catch (Exception e) {
            e.printStackTrace();
            fail();
        }
    }

    @Test
    public void testCollectEnsemblIdentifiers() {
        List<String> testIdentifiersList = Arrays.asList("ENSG12345", "ENST12345", "ENSP12345", "123456789");
        Collection<String> testEnsemblIdentifiersList = ensemblRefCreator.collectEnsemblIdentifiers(testIdentifiersList);
        assertThat(testEnsemblIdentifiersList.size(), is(equalTo(3)));
    }

}
