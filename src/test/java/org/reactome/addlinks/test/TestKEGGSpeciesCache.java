package org.reactome.addlinks.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.reactome.addlinks.kegg.KEGGSpeciesCache;

public class TestKEGGSpeciesCache
{
	@Test
	public void testKEGGSpeciesMapping()
	{
		try
		{
			KEGGSpeciesCache.generateSpeciesMapping();
			System.out.println(KEGGSpeciesCache.getKEGGCode("Homo sapiens"));
			assertEquals("hsa",KEGGSpeciesCache.getKEGGCode("Homo sapiens"));
			assertEquals("human",KEGGSpeciesCache.getKEGGCommonName("Homo sapiens"));
		}
		catch (Exception e)
		{
			e.printStackTrace();
			fail();
		}
		
	}
}
