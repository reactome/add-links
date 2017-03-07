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

public class OrphanetFileProcessor extends FileProcessor<String>
{
	
	public OrphanetFileProcessor(String processorName)
	{
		super(processorName);
	}

	public OrphanetFileProcessor()
	{
		super(null);
	}
	
	@Override
	public Map<String, String> getIdMappingsFromFile()
	{
		Map<String,String> uniProtToOrphanet = new HashMap<String,String>();
		TransformerFactory factory = TransformerFactory.newInstance();
		try
		{
			//Transform the OrphaNet XML into a more usable CSV file.
			Source source = new StreamSource(this.getClass().getClassLoader().getResourceAsStream("orphanet_transform.xsl"));
			Transformer transformer = factory.newTransformer(source);
			Source xmlSource = new StreamSource(this.pathToFile.toFile());
			String outfileName = this.pathToFile.getParent().toString() + "/" +  this.pathToFile.getFileName().toString() + ".transformed.csv";
			Result outputTarget =  new StreamResult(new File(outfileName));
			transformer.transform(xmlSource, outputTarget);
			
			//Now we need to read the file.
			Files.readAllLines(Paths.get(outfileName)).stream().forEach(line -> 
			{
				String[] parts = line.split(",");
				uniProtToOrphanet.put(parts[2], parts[0]);
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
		
		logger.debug("Number of UniProt IDs in mapping: {}.",uniProtToOrphanet.keySet().size());
		return uniProtToOrphanet;
	}

}
