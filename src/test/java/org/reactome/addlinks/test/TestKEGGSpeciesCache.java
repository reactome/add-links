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
			System.out.println(KEGGSpeciesCache.getKEGGCode("Homo sapiens"));
			assertEquals("hsa",KEGGSpeciesCache.getKEGGCode("Homo sapiens"));
			assertEquals("human",KEGGSpeciesCache.getKEGGCommonName("Homo sapiens"));
			assertNotNull(KEGGSpeciesCache.getKEGGCode("Bos taurus"));
			System.out.println(KEGGSpeciesCache.getKEGGCode("Bos taurus"));
			
			assertNotNull(KEGGSpeciesCache.getKEGGCode("Mus musculus"));
			System.out.println(KEGGSpeciesCache.getKEGGCode("Mus musculus"));
		}
		catch (Exception e)
		{
			e.printStackTrace();
			fail();
		}
		
	}
}
