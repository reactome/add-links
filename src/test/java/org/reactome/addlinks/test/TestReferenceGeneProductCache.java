package org.reactome.addlinks.test;


import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.schema.InvalidAttributeException;
import org.junit.Test;
import org.reactome.addlinks.db.ReferenceObjectCache;

public class TestReferenceGeneProductCache {
	
	@Test
	public void testCacheBySpecies()
	{
		ReferenceObjectCache cache;
		
		//TODO: all test classes really should get their adapter froma spring file or something.
		ReferenceObjectCache.setDbParams("127.0.0.1", "test_reactome_57", "curator", "",3307);
		
		cache = ReferenceObjectCache.getInstance();
		
		List <GKInstance> items = cache.getBySpecies("48887");
		
		assertNotNull(items);
		assertTrue(items.size()>0);
		System.out.println("# Items for species 48887: "+items.size());
	}
	
	
	@Test
	public void testCacheById() throws InvalidAttributeException, Exception
	{
		ReferenceObjectCache cache;
		
		ReferenceObjectCache.setDbParams("127.0.0.1", "test_reactome_57", "curator", "",3307);
		
		cache = ReferenceObjectCache.getInstance();
		
		GKInstance shell = cache.getById("5618411");
		
		assertNotNull(shell);
		assertTrue("5618411".equals(shell.getDBID()));
		assertTrue("2".equals(shell.getAttributeValue(ReactomeJavaConstants.referenceDatabase)));
		assertTrue("48887".equals(shell.getAttributeValue(ReactomeJavaConstants.species)));
		assertTrue("P0DML2".equals(shell.getAttributeValue(ReactomeJavaConstants.identifier)));
		assertTrue("UniProt:P0DML2 CSH1".equals(shell.getDisplayName()));
	}
	
	@Test
	public void testCacheByRefDbId()
	{
		ReferenceObjectCache cache;
		ReferenceObjectCache.setDbParams("127.0.0.1", "test_reactome_57", "curator", "",3307);
		cache = ReferenceObjectCache.getInstance();
		List <GKInstance> items = cache.getByRefDb("427877");
		
		assertNotNull(items);
		assertTrue(items.size()>0);
		
		System.out.println("# Items for ref db 427877: "+items.size());
	}
	
	@Test
	public void testCacheByRefDbAndId()
	{
		ReferenceObjectCache cache;
		ReferenceObjectCache.setDbParams("127.0.0.1", "test_reactome_57", "curator", "",3307);
		cache = ReferenceObjectCache.getInstance();
		List <GKInstance> items = cache.getByRefDbAndSpecies("2", "48898");
		
		assertNotNull(items);
		assertTrue(items.size()>0);
		System.out.println("# Items for refdb/species 2/48898: "+items.size());
	}
}
