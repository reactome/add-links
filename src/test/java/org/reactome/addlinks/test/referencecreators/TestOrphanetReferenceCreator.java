package org.reactome.addlinks.test.referencecreators;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.reactome.addlinks.dataretrieval.FileRetriever;
import org.reactome.addlinks.db.ReferenceObjectCache;
import org.reactome.addlinks.fileprocessors.OrphanetFileProcessor;
import org.reactome.addlinks.referencecreators.SimpleReferenceCreator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

/**
 * INTEGRATION test of Orphanet functionality: file retreival, file processing, reference creation.
 * @author sshorser
 *
 */
@ContextConfiguration("/test-application-context.xml")
@RunWith(org.springframework.test.context.junit4.SpringJUnit4ClassRunner.class)
public class TestOrphanetReferenceCreator
{
	static MySQLAdaptor adapter;


	@Autowired
	FileRetriever OrphanetToUniprotReferenceDNASequence;
	
	@Autowired
	OrphanetFileProcessor orphanetFileProcessor; 
	
	@Autowired
	SimpleReferenceCreator OrphanetReferenceCreator;
	
	@Autowired
	ReferenceObjectCache objectCache;
	
	@Test
	public void testOrphanetReferenceCreator() throws Exception
	{
		OrphanetToUniprotReferenceDNASequence.fetchData();
		
		Map<String, String> uniprotToOrphanetMap = orphanetFileProcessor.getIdMappingsFromFile();
		
		assertNotNull(uniprotToOrphanetMap);
		assertTrue(uniprotToOrphanetMap.size() > 0);
		//ReferenceObjectCache.setAdapter(adapter);
		// 2 == UniProt
		List<GKInstance> uniProtReferences = objectCache.getByRefDb("2", ReactomeJavaConstants.ReferenceGeneProduct);
		OrphanetReferenceCreator.createIdentifiers(123456, uniprotToOrphanetMap, uniProtReferences);
		//TODO: Assert the creation worked. Maybe do this by intercepting the actual call with a mock class...
	};
	

}
