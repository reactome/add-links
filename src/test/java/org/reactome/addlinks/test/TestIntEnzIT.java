package org.reactome.addlinks.test;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Map;

import org.gk.model.GKInstance;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.reactome.release.common.dataretrieval.FileRetriever;
import org.reactome.addlinks.db.ReferenceObjectCache;
import org.reactome.addlinks.fileprocessors.IntEnzFileProcessor;
import org.reactome.addlinks.referencecreators.IntEnzReferenceCreator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration("/test-application-context.xml")
@RunWith(org.springframework.test.context.junit4.SpringJUnit4ClassRunner.class)
public class TestIntEnzIT
{

	@Autowired
	FileRetriever intEnzFileRetriever;

	@Autowired
	IntEnzFileProcessor intEnzFileProcessor;

	@Autowired
	IntEnzReferenceCreator intEnzRefCreator;

	@Autowired
	ReferenceObjectCache objectCache;

	@Test
	public void testGetIntEnzFile() throws Exception
	{
		intEnzFileRetriever.fetchData();
	}


	@Test
	public void testProcessIntEnzFile() throws Exception
	{
		intEnzFileRetriever.fetchData();

		Map<String, List<String>> mappings = intEnzFileProcessor.getIdMappingsFromFile();

		assertNotNull(mappings);
		assertTrue(mappings.keySet().size() > 0);
	}

	@Test
	public void testCreateIntEnzReferences() throws Exception
	{
		intEnzFileRetriever.fetchData();

		Map<String, List<String>> mappings = intEnzFileProcessor.getIdMappingsFromFile();

		assertNotNull(mappings);
		assertTrue(mappings.keySet().size() > 0);

		List<GKInstance> sourceReferences = objectCache.getByRefDb(objectCache.getRefDbNamesToIds().get("UniProt").get(0), "ReferenceGeneProduct");

		intEnzRefCreator.createIdentifiers(123456789, mappings, sourceReferences);
	}
}
