package org.reactome.addlinks.test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.reactome.addlinks.db.ReferenceObjectCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration("/db-adapter-config.xml")
@RunWith(org.springframework.test.context.junit4.SpringJUnit4ClassRunner.class)
public class TestReferenceObjectCache
{

	@Autowired
	MySQLAdaptor dbAdapter;
	
	@Test
	public void testObjectReferenceCache()
	{
		ReferenceObjectCache cache = new ReferenceObjectCache(dbAdapter);

		assertTrue(cache.getRefDBMappings().size() > 0);
		assertTrue(cache.getRefDbNamesToIds().size() > 0);
		assertTrue(cache.getSpeciesMappings().size() > 0);
		assertTrue(cache.getSpeciesNamesToIds().size() > 0);
		
		assertTrue(cache.getByRefDbAndSpecies("2", "48887", ReactomeJavaConstants.ReferenceGeneProduct).size() > 0);
	}

	
	@Test
	public void testObjectReferenceCacheLazyLoad()
	{
		ReferenceObjectCache cache = new ReferenceObjectCache(dbAdapter, true);
		

		cache.getByRefDbAndSpecies("2", "48887", ReactomeJavaConstants.ReferenceRNASequence);
		
		assertTrue(cache.getRefDBMappings().size() > 0);
		assertTrue(cache.getRefDbNamesToIds().size() > 0);
		assertTrue(cache.getSpeciesMappings().size() > 0);
		assertTrue(cache.getSpeciesNamesToIds().size() > 0);
		
		cache.getByRefDbAndSpecies("2", "48887", ReactomeJavaConstants.ReferenceDNASequence);
		
		assertTrue(cache.getRefDBMappings().size() > 0);
		assertTrue(cache.getRefDbNamesToIds().size() > 0);
		assertTrue(cache.getSpeciesMappings().size() > 0);
		assertTrue(cache.getSpeciesNamesToIds().size() > 0);
		String refDBID = cache.getRefDbNamesToIds().get("Wormbase").get(0);
		assertNotNull(refDBID);
		String speciesID = cache.getSpeciesNamesToIds().get("Caenorhabditis elegans").get(0);
		assertNotNull(speciesID);
		assertTrue(cache.getByRefDbAndSpecies(refDBID, speciesID, ReactomeJavaConstants.ReferenceDNASequence).size() > 0);

	}
}
