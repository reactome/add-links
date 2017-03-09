package org.reactome.addlinks.fileprocessors;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

public class HmdbProteinsFileProcessor extends FileProcessor<String>
{
	public HmdbProteinsFileProcessor()
	{
		super(null);
	}
	
	public HmdbProteinsFileProcessor(String processorName)
	{
		super(processorName);
	}
	
	@Override
	//HMDB Proteins look like they're a 1:1 mapping. this will be a mapping from 
	public Map<String, String> getIdMappingsFromFile()
	{
		Map<String, String> accesionToUniprots = new HashMap<String, String>();
		TransformerFactory factory = TransformerFactory.newInstance();
		try
		{
			String dirToFile = this.unzipFile(this.pathToFile);
			String inputFilename = dirToFile + "/" + this.pathToFile.getFileName().toString().replaceAll(".zip", ".xml");
			
			//Transform the OrphaNet XML into a more usable CSV file.
			Source source = new StreamSource(this.getClass().getClassLoader().getResourceAsStream("hmdb_protein_transform.xsl"));
			Transformer transformer = factory.newTransformer(source);
			Source xmlSource = new StreamSource(inputFilename);
			String outfileName = inputFilename + ".transformed.tsv";
			Result outputTarget =  new StreamResult(new File(outfileName));
			transformer.transform(xmlSource, outputTarget);
			
			//Now we need to read the file.
			Files.readAllLines(Paths.get(outfileName)).stream().forEach(line -> 
			{
				String[] parts = line.split("\t");
				// Map uniprot to accession.
				accesionToUniprots.put(parts[1], parts[0]);
			});
		}
		catch (TransformerConfigurationException e)
		{
			e.printStackTrace();
			throw new Error(e);
		}
		catch (TransformerException e)
		{
			e.printStackTrace();
			throw new Error(e);
		}
		catch (IOException e)
		{
			e.printStackTrace();
			throw new Error(e);
		}
		catch (Exception e)
		{
			e.printStackTrace();
			throw new Error(e);
		}

		logger.info("Number of HMDB accessions: {}", accesionToUniprots.keySet().size());
		return accesionToUniprots;
	}

}
