package org.reactome.addlinks.test.referencecreators;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.reactome.addlinks.dataretrieval.FileRetriever;
import org.reactome.addlinks.db.ReferenceObjectCache;
import org.reactome.addlinks.fileprocessors.DOCKBlasterFileProcessor;
import org.reactome.addlinks.referencecreators.DOCKBlasterReferenceCreator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration("/test-dockblaster-refcreator.xml")
@RunWith(org.springframework.test.context.junit4.SpringJUnit4ClassRunner.class)
/**
 * @deprecated DOCKBlaster data is not used anymore, this class will be removed in the future.
 * @author sshorser
 *
 */
public class TestDOCKBlasterReferenceCreator
{
	@Autowired
	ReferenceObjectCache objectCache;
	
	// DOCKBlaster returns a Uniprot-to-PDB mapping.
	
	@Autowired
	DOCKBlasterReferenceCreator pdbRefCreator;
	
	@Autowired
	FileRetriever UniProtToPDBRetriever;
	
	@Autowired
	DOCKBlasterFileProcessor DOCKBlasterFileProcessor;
	
	@Test
	public void testUniProtToPDBReferenceCreator() throws Exception
	{
		UniProtToPDBRetriever.fetchData();
		
		Map<String, ArrayList<String>> uniprotToPDBBaseMap = DOCKBlasterFileProcessor.getIdMappingsFromFile();
		
		assertNotNull(uniprotToPDBBaseMap);
		assertTrue(uniprotToPDBBaseMap.size() > 0);
		//ReferenceObjectCache.setAdapter(adapter);
		// 2 == UniProt
		List<GKInstance> uniprotReferences = objectCache.getByRefDb("2", ReactomeJavaConstants.ReferenceGeneProduct);
		assertTrue(uniprotReferences.size() > 0);
		pdbRefCreator.createIdentifiers(123456, uniprotToPDBBaseMap, uniprotReferences);
		//TODO: Assert the creation worked. Maybe do this by intercepting the actual call with a mock class...
	}
}
