package org.reactome.addlinks.test.referencecreators;

import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.InvalidAttributeException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.reactome.addlinks.db.ReferenceObjectCache;
import org.reactome.addlinks.fileprocessors.KEGGFileProcessor.KEGGKeys;
import org.reactome.addlinks.referencecreators.KEGGReferenceCreator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration("/test-application-context.xml")
@RunWith(org.springframework.test.context.junit4.SpringJUnit4ClassRunner.class)
public class TestKEGGReferenceCreator
{

	@Autowired
	ReferenceObjectCache objectCache;
	
//	@Mock(name = "refCreator")
//	ReferenceCreator referenceCreator;
	
	@Mock
	MySQLAdaptor dbAdaptor;
	
//	@InjectMocks
	@Autowired
	KEGGReferenceCreator keggRefCreator;// = new KEGGReferenceCreator(dbAdaptor, ReactomeJavaConstants.ReferenceDNASequence, ReactomeJavaConstants.ReferenceGeneProduct, ReactomeJavaConstants.referenceGene, "UniProt", "KEGG");
	
	@Mock
	GKInstance inst1;
	
	@Mock
	GKInstance inst2;
	
	@Mock
	GKInstance inst3;
	
	@Mock
	GKInstance inst4;
	
	@Before
	public void setup() throws InvalidAttributeException, Exception
	{
		MockitoAnnotations.initMocks(this);
		
		Mockito.when(inst1.getAttributeValue(ReactomeJavaConstants.identifier)).thenReturn("UNIPROT1");
		Mockito.when(inst1.getDisplayName()).thenReturn("[Dummy:UNIPROT1]");
		
		Mockito.when(inst2.getAttributeValue(ReactomeJavaConstants.identifier)).thenReturn("UNIPROT2");
		Mockito.when(inst2.getDisplayName()).thenReturn("[Dummy:UNIPROT2]");

		Mockito.when(inst3.getAttributeValue(ReactomeJavaConstants.identifier)).thenReturn("UNIPROT3");
		Mockito.when(inst3.getDisplayName()).thenReturn("[Dummy:UNIPROT3]");

		Mockito.when(inst4.getAttributeValue(ReactomeJavaConstants.identifier)).thenReturn("UNIPROT4");
		Mockito.when(inst4.getDisplayName()).thenReturn("[Dummy:UNIPROT4]");
	}

	
	@SuppressWarnings("serial")
	@Test
	public void testCreateKeggIdentifiers()
	{
		try
		{
			
			Map<String, List<Map<KEGGKeys, String>>> mappings = new HashMap<String, List<Map<KEGGKeys,String>>>();
			
			mappings.put("UNIPROT1", Arrays.asList( new HashMap<KEGGKeys, String>(){ { 
//				put(KEGGKeys.KEGG_IDENTIFIER, "hsa:12355");
				put(KEGGKeys.KEGG_DEFINITION, "TEST TEST");
				put(KEGGKeys.KEGG_SPECIES, "hsa");
				put(KEGGKeys.EC_NUMBERS, "1.2.3.4");
				put(KEGGKeys.KEGG_GENE_ID, "hsa:12355");
			} } ));
			
			mappings.put("UNIPROT2", Arrays.asList( new HashMap<KEGGKeys, String>(){ { 
				put(KEGGKeys.KEGG_IDENTIFIER, "hsa:6789");
				put(KEGGKeys.KEGG_DEFINITION, "TEST TEST TEST!!");
				put(KEGGKeys.KEGG_SPECIES, "hsa");
				put(KEGGKeys.EC_NUMBERS, "3.3.1.1");
				put(KEGGKeys.KEGG_GENE_ID, "hsa:6789");
			} } ));
			
			mappings.put("UNIPROT3", Arrays.asList( new HashMap<KEGGKeys, String>(){ { 
				put(KEGGKeys.KEGG_IDENTIFIER, "dre:si:ch73-368j24.13");
				put(KEGGKeys.KEGG_DEFINITION, "BLARG");
				put(KEGGKeys.KEGG_SPECIES, "hsa");
				put(KEGGKeys.EC_NUMBERS, "3.3.1.1");
				put(KEGGKeys.KEGG_GENE_ID, null);
			} } ));
			

			mappings.put("UNIPROT4", Arrays.asList( new HashMap<KEGGKeys, String>(){ { 
				put(KEGGKeys.KEGG_IDENTIFIER, null);
				put(KEGGKeys.KEGG_DEFINITION, "sdfag");
				put(KEGGKeys.KEGG_SPECIES, "hsa");
				put(KEGGKeys.EC_NUMBERS, "3.3.1.1");
				put(KEGGKeys.KEGG_GENE_ID, "hsa:si:1451313");
			} } ));
			
			List<GKInstance> sourceReferences = Arrays.asList(inst1, inst2, inst3, inst4);
			long personID = 123456L;
			
			keggRefCreator.createIdentifiers(personID, mappings, sourceReferences);
		}
		catch (Exception e)
		{
			e.printStackTrace();
			fail();
		}
	}
}
