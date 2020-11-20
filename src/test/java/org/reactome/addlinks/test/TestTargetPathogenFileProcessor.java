package org.reactome.addlinks.test;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.reactome.addlinks.fileprocessors.TargetPathogenFileProcessor;

public class TestTargetPathogenFileProcessor
{
	private Path pathToFile;
	private final static String URL_PREFIX = "http://target.sbg.qb.fcen.uba.ar/patho/protein";
	private final static String TEST_URL_1 = URL_PREFIX + "/5787f63fbe737e0acf790d69";
	private final static String TEST_URL_2 = URL_PREFIX + "/5787f654be737e0acf7919ab";
	private final static String fileContent = String.join("\n",
			TEST_URL_1 + "	R-DME-983157	P0CG69",
			TEST_URL_1 + "	R-NUL-9604648	P0CG50",
			TEST_URL_1 + "	R-NUL-9011324	P0CG48",
			TEST_URL_2 + "	R-DME-9619376	P62152",
			TEST_URL_2 + "	R-CEL-9619376	O16305");

	// There are 2 Target Pathogen identifiers in the sample file, even though there are 7 lines, because some map to multiple Reactome/UniProt identifiers.
	private final static int numberOfExpectedMappings = 2;

	@Before
	public void setup() throws IOException
	{
		this.pathToFile = Files.createTempFile("testTargetPathogenFileContent", null);
		Files.write(this.pathToFile, fileContent.getBytes());
		System.out.println("Temp content file is: " + this.pathToFile.toString());
	}

	@Test
	public void testTargetPathogenFileProcessor()
	{
		TargetPathogenFileProcessor processor = new TargetPathogenFileProcessor("TargetPathogen");
		processor.setPath(this.pathToFile);
		Map<String, String> mappings = processor.getIdMappingsFromFile();
		System.out.println(mappings.keySet().size() + " Mappings:\n\t"+ mappings);
		assertTrue(mappings.keySet().size() == numberOfExpectedMappings);
	}

	@After
	public void cleanup() throws IOException
	{
		System.out.print("Removing: " + this.pathToFile.toString());
		Files.delete(this.pathToFile);
	}
}
