package org.reactome.addlinks.test;


import static org.junit.Assert.*;

import java.util.List;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.InvalidAttributeException;
import org.junit.AfterClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.reactome.addlinks.db.ReferenceObjectCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;


@ContextConfiguration("/test-application-context.xml")
@RunWith(org.springframework.test.context.junit4.SpringJUnit4ClassRunner.class)
public class TestReferenceGeneProductCache {
	
	static MySQLAdaptor adapter;

	// This adapter will be populated with the adapter in the Spring config file.
	@Autowired
	public void setAdapter(MySQLAdaptor a)
	{
		TestReferenceGeneProductCache.adapter = a;
	}
	
	@Test
	public void testCacheBySpecies()
	{
		
		
		//TODO: all test classes really should get their adapter froma spring file or something.
		//ReferenceObjectCache.setDbParams("127.0.0.1", "test_reactome_57", "curator", "",3307);
		ReferenceObjectCache.setAdapter(adapter);
		
		ReferenceObjectCache cache = ReferenceObjectCache.getInstance();
		
		List <GKInstance> items = cache.getBySpecies("48887");
		
		assertNotNull(items);
		assertTrue(items.size()>0);
		System.out.println("# Items for species 48887: "+items.size());
	}
	
	
	@Test
	public void testCacheById() throws InvalidAttributeException, Exception
	{
		//ReferenceObjectCache cache;
		
		//ReferenceObjectCache.setDbParams("127.0.0.1", "test_reactome_57", "curator", "",3307);
		ReferenceObjectCache.setAdapter(adapter);
		
		ReferenceObjectCache cache = ReferenceObjectCache.getInstance();
		
		GKInstance shell = cache.getById("5618411");

		assertNotNull(shell);
		assertTrue(5618411 == shell.getDBID());
		assertTrue(2 == ((GKInstance)shell.getAttributeValue(ReactomeJavaConstants.referenceDatabase)).getDBID()  );
		assertTrue(48887 == ((GKInstance)shell.getAttributeValue(ReactomeJavaConstants.species)).getDBID() );
		assertEquals("UniProt:P0DML2 CSH1",(shell.getDisplayName()));
		assertEquals("P0DML2",(  shell.getAttributeValue(ReactomeJavaConstants.identifier) ));
	}
	
	@Test
	public void testCacheByRefDbId()
	{
		//ReferenceObjectCache cache;
		//ReferenceObjectCache.setDbParams("127.0.0.1", "test_reactome_57", "curator", "",3307);
		ReferenceObjectCache.setAdapter(adapter);
		ReferenceObjectCache cache = ReferenceObjectCache.getInstance();
		List <GKInstance> items = cache.getByRefDb("427877");
		
		assertNotNull(items);
		assertTrue(items.size()>0);
		
		System.out.println("# Items for ref db 427877: "+items.size());
	}
	
	@Test
	public void testCacheByRefDbAndId()
	{
		//ReferenceObjectCache cache;
		//ReferenceObjectCache.setDbParams("127.0.0.1", "test_reactome_57", "curator", "",3307);
		ReferenceObjectCache.setAdapter(adapter);
		ReferenceObjectCache cache = ReferenceObjectCache.getInstance();
		List <GKInstance> items = cache.getByRefDbAndSpecies("2", "48898");
		
		assertNotNull(items);
		assertTrue(items.size()>0);
		System.out.println("# Items for refdb/species 2/48898: "+items.size());
	}
	
	@AfterClass
	public static void finished() throws Exception
	{
		TestReferenceGeneProductCache.adapter.cleanUp();
	}
}
