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
	private static final String URL_PREFIX = "http://target.sbg.qb.fcen.uba.ar/patho/protein/";
	private final static String fileContent = "#HEADER LINE\n" + URL_PREFIX+"56425908be737e6c7a9fd4d0,H37Rv,Rv0046c|ino1,R-MTU-879331,P9WKI1\n" +
			URL_PREFIX+"56425908be737e6c7a9fd525,H37Rv,Rv0126|treS,R-MTU-868709,P9WQ19\n" +
			URL_PREFIX+"5b2801b3be737e35a6df1a69,MeloI,Minc3s11049g44572,R-CEL-74723,P34462\n" +
			URL_PREFIX+"5b2801b3be737e35a6df151a,MeloI,Minc3s09360g43141|RP-L40e,|RPL40,R-XTR-983157,F6VNK4\n" +
			URL_PREFIX+"5b2801b3be737e35a6df151a,MeloI,Minc3s09360g43141|RP-L40e,|RPL40,R-CFA-983157,F1PDG4\n" +
			URL_PREFIX+"5b354852be737e166372e8dd,EcoAIEC_C7225,AIEC_C7225_05581|APU18_05390| pNDM102337_153|groEL| MS6198_A148| CA268_28940| pNDM10505_155|BET08_16250| NDM1Dok01_N0169,R-HSA-8850539,P48643\n" +
			URL_PREFIX+"5b354852be737e166372e8dd,EcoAIEC_C7225,AIEC_C7225_05581|APU18_05390| pNDM102337_153|groEL| MS6198_A148| CA268_28940| pNDM10505_155|BET08_16250| NDM1Dok01_N0169,R-SSC-8850539,I3LR32\n";

	// There are 5 Target Pathogen identifiers in the sample file, even though there are 7 lines, because some map to multiple Reactome/UniProt identifiers.
	private final static int numberOfExpectedMappings = 5;

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
