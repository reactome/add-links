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

	static MySQLAdaptor adapter;

	// This adapter will be populated with the adapter in the Spring config file.
	@Autowired
	public void setAdapter(MySQLAdaptor a)
	{
		TestPROReferenceCreator.adapter = a;
	}

	@Autowired
	FileRetriever PROToReferencePeptideSequence;
	
	@Autowired
	PROFileProcessor proFileProcessor; 
	
	@Test
	public void testPROReferenceCreator() throws Exception
	{
		PROToReferencePeptideSequence.fetchData();
		
		Map<String, String> uniprotToProMap = proFileProcessor.getIdMappingsFromFile();
		
		assertNotNull(uniprotToProMap);
		assertTrue(uniprotToProMap.size() > 0);
		ReferenceObjectCache.setAdapter(adapter);
		SimpleReferenceCreator proRefCreator = new SimpleReferenceCreator(adapter, ReactomeJavaConstants.DatabaseIdentifier, ReactomeJavaConstants.ReferenceGeneProduct,
																					ReactomeJavaConstants.crossReference,
																					"UniProt", "PRO");
		proRefCreator.setTestMode(true);
		// 2 == UniProt
		List<GKInstance> uniprotReferences = ReferenceObjectCache.getInstance().getByRefDb("2", ReactomeJavaConstants.ReferenceGeneProduct);
		proRefCreator.createIdentifiers(123456, uniprotToProMap, uniprotReferences);
		//TODO: Assert the creation worked. Maybe do this by intercepting the actual call with a mock class...
	};
	
	@AfterClass
	public static void finished() throws Exception
	{
		TestPROReferenceCreator.adapter.cleanUp();
	}
}
