package org.reactome.addlinks.test.referencecreators;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.reactome.addlinks.dataretrieval.FileRetriever;
import org.reactome.addlinks.db.ReferenceObjectCache;
import org.reactome.addlinks.fileprocessors.PROFileProcessor;
import org.reactome.addlinks.referencecreators.SimpleReferenceCreator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

/**
 * This is an INTEGRATION test of the PRO file retriever and file processor but mostly of the PROReferenceCreator.
 * @author sshorser
 *
 */
@ContextConfiguration("/test-application-context.xml")
@RunWith(org.springframework.test.context.junit4.SpringJUnit4ClassRunner.class)
public class TestPROReferenceCreator
{


	@Autowired
	FileRetriever PROToReferencePeptideSequence;
	
	@Autowired
	PROFileProcessor proFileProcessor; 
	
	@Autowired
	SimpleReferenceCreator proRefCreator;
	
	@Autowired
	ReferenceObjectCache objectCache;
	
	
	@Test
	public void testPROReferenceCreator() throws Exception
	{
		PROToReferencePeptideSequence.fetchData();
		
		Map<String, String> uniprotToProMap = proFileProcessor.getIdMappingsFromFile();
		
		assertNotNull(uniprotToProMap);
		assertTrue(uniprotToProMap.size() > 0);
		//ReferenceObjectCache.setAdapter(adapter);
		// 2 == UniProt
		List<GKInstance> uniprotReferences = objectCache.getByRefDb("2", ReactomeJavaConstants.ReferenceGeneProduct);
		proRefCreator.createIdentifiers(123456, uniprotToProMap, uniprotReferences);
		//TODO: Assert the creation worked. Maybe do this by intercepting the actual call with a mock class...
	};
	

}
