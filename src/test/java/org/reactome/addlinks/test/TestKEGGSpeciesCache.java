package org.reactome.addlinks.test;

import static org.junit.Assert.*;

import org.junit.Test;
import org.reactome.addlinks.kegg.KEGGSpeciesCache;

public class TestKEGGSpeciesCache
{
	@Test
	public void testKEGGSpeciesMapping()
	{
		try
		{
			System.out.println(KEGGSpeciesCache.getKEGGCodes("Homo sapiens"));
			assertEquals("hsa",KEGGSpeciesCache.getKEGGCodes("Homo sapiens"));
			assertEquals("human",KEGGSpeciesCache.getKEGGCommonNames("Homo sapiens"));
			assertNotNull(KEGGSpeciesCache.getKEGGCodes("Bos taurus"));
			System.out.println(KEGGSpeciesCache.getKEGGCodes("Bos taurus"));
			
			assertNotNull(KEGGSpeciesCache.getKEGGCodes("Mus musculus"));
			System.out.println(KEGGSpeciesCache.getKEGGCodes("Mus musculus"));
		}
		catch (Exception e)
		{
			e.printStackTrace();
			fail();
		}
		
	}
}
