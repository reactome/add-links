package org.reactome.addlinks.test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.junit.AfterClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.reactome.addlinks.dataretrieval.FileRetriever;
import org.reactome.addlinks.db.ReferenceObjectCache;
import org.reactome.addlinks.fileprocessors.OrphanetFileProcessor;
import org.reactome.addlinks.referencecreators.OrphanetReferenceCreator;

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

	// This adapter will be populated with the adapter in the Spring config file.
	@Autowired
	public void setAdapter(MySQLAdaptor a)
	{
		TestOrphanetReferenceCreator.adapter = a;
	}

	@Autowired
	FileRetriever OrphanetToUniprotReferenceDNASequence;
	
	@Autowired
	OrphanetFileProcessor orphanetFileProcessor; 
	
	@Test
	public void testOrphanetReferenceCreator() throws Exception
	{
		OrphanetToUniprotReferenceDNASequence.fetchData();
		
		Map<String, String> uniprotToOrphanetMap = orphanetFileProcessor.getIdMappingsFromFile();
		
		assertNotNull(uniprotToOrphanetMap);
		assertTrue(uniprotToOrphanetMap.size() > 0);
		ReferenceObjectCache.setAdapter(adapter);
		OrphanetReferenceCreator orphanetRefCreator = new OrphanetReferenceCreator(adapter);
		orphanetRefCreator.setTestMode(true);
		// 2 == UniProt
		List<GKInstance> uniProtReferences = ReferenceObjectCache.getInstance().getByRefDb("2", ReactomeJavaConstants.ReferenceGeneProduct);
		orphanetRefCreator.createIdentifiers(123456, uniprotToOrphanetMap, uniProtReferences);
		//TODO: Assert the creation worked. Maybe do this by intercepting the actual call with a mock class...
	};
	
	@AfterClass
	public static void finished() throws Exception
	{
		TestOrphanetReferenceCreator.adapter.cleanUp();
	}
}
