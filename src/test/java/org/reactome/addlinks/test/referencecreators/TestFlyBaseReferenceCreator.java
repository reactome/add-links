package org.reactome.addlinks.test.referencecreators;

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
import org.reactome.addlinks.fileprocessors.FlyBaseFileProcessor;
import org.reactome.addlinks.referencecreators.FlyBaseReferenceCreator;
import org.reactome.addlinks.referencecreators.SimpleReferenceCreator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

/**
 * INTEGRATION test of FlyBase functionality: file retreival, file processing, reference creation.
 * @author sshorser
 *
 */
@ContextConfiguration("/test-application-context.xml")
@RunWith(org.springframework.test.context.junit4.SpringJUnit4ClassRunner.class)

public class TestFlyBaseReferenceCreator
{
	static MySQLAdaptor adapter;

	// This adapter will be populated with the adapter in the Spring config file.
	@Autowired
	public void setAdapter(MySQLAdaptor a)
	{
		TestFlyBaseReferenceCreator.adapter = a;
	}

	@Autowired
	FileRetriever FlyBaseToUniprotReferenceDNASequence;
	
	@Autowired
	FlyBaseFileProcessor flyBaseFileProcessor; 
	
	@Autowired
	SimpleReferenceCreator FlyBaseReferenceCreator;
	
	@Test
	public void testFlyBaseReferenceCreator() throws Exception
	{
		FlyBaseToUniprotReferenceDNASequence.fetchData();
		
		Map<String, String> uniprotToFlyBaseMap = flyBaseFileProcessor.getIdMappingsFromFile();
		
		assertNotNull(uniprotToFlyBaseMap);
		assertTrue(uniprotToFlyBaseMap.size() > 0);
		ReferenceObjectCache.setAdapter(adapter);
		// 2 == UniProt
		List<GKInstance> uniprotReferences = ReferenceObjectCache.getInstance().getByRefDb("2", ReactomeJavaConstants.ReferenceGeneProduct);
		FlyBaseReferenceCreator.createIdentifiers(123456, uniprotToFlyBaseMap, uniprotReferences);
		//TODO: Assert the creation worked. Maybe do this by intercepting the actual call with a mock class...
	};
	
	@AfterClass
	public static void finished() throws Exception
	{
		TestFlyBaseReferenceCreator.adapter.cleanUp();
	}
}
