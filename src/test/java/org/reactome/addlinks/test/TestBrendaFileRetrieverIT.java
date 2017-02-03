package org.reactome.addlinks.test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.reactome.addlinks.dataretrieval.BRENDAFileRetriever;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration("/test-application-context.xml")
@RunWith(org.springframework.test.context.junit4.SpringJUnit4ClassRunner.class)
public class TestBrendaFileRetrieverIT
{

	@Autowired
	BRENDAFileRetriever brendaRetriever;
	
	@Test
	public void testBrendaRetriever() throws Exception
	{
		
		Map<String, List<String>> identifiers = new HashMap<String, List<String>>();
		
		identifiers.put("Homo sapiens", Arrays.asList("3uzd", "4d18"));
		
		brendaRetriever.setIdentifiers(identifiers);
		
		brendaRetriever.fetchData();
	}
	
}
