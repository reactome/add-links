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
	
	@Autowired
	ReferenceObjectCache objectCache;
	
	@Test
	public void testCacheBySpecies()
	{
		List <GKInstance> items = objectCache.getBySpecies("48887",ReactomeJavaConstants.ReferenceGeneProduct);
		
		assertNotNull(items);
		assertTrue(items.size()>0);
		System.out.println("# Items for species 48887: "+items.size());
	}
	
	
	@Test
	public void testCacheById() throws InvalidAttributeException, Exception
	{
		GKInstance shell = objectCache.getById("5618411");

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
		List <GKInstance> items = objectCache.getByRefDb("427877",ReactomeJavaConstants.ReferenceGeneProduct);
		
		assertNotNull(items);
		assertTrue(items.size()>0);
		
		System.out.println("# Items for ref db 427877: "+items.size());
	}
	
	@Test
	public void testCacheByRefDbAndId()
	{
		List <GKInstance> items = objectCache.getByRefDbAndSpecies("2", "48898",ReactomeJavaConstants.ReferenceGeneProduct);
		
		assertNotNull(items);
		assertTrue(items.size()>0);
		System.out.println("# Items for refdb/species 2/48898: "+items.size());
		
		items = objectCache.getByRefDbAndSpecies("9214213", "48895" ,ReactomeJavaConstants.ReferenceDNASequence);
		
		assertNotNull(items);
		assertTrue(items.size()>0);
		System.out.println("# Items for refdb/species 9214213/48895: "+items.size());
	}
}
