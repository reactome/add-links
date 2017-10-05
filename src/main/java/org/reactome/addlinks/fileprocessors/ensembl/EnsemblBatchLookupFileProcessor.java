package org.reactome.addlinks.fileprocessors.ensembl;

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

import org.reactome.addlinks.fileprocessors.FileProcessor;

public class EnsemblBatchLookupFileProcessor extends FileProcessor<String>
{
	public EnsemblBatchLookupFileProcessor(String processorName)
	{
		super(processorName);
	}

	public EnsemblBatchLookupFileProcessor()
	{
	}

	private static final String XSL_FILE_NAME = "ensembl-lookup-simplifier.xsl";
	
	@Override
	public Map<String, String> getIdMappingsFromFile()
	{
		Map<String,String> mapping = new HashMap<String, String>();
		TransformerFactory factory = TransformerFactory.newInstance();
		//Transform the generated Ensembl batch-lookup output XML into a more usable CSV file.
		Source source = new StreamSource(this.getClass().getClassLoader().getResourceAsStream(XSL_FILE_NAME));
		Transformer transformer;
		try
		{
			transformer = factory.newTransformer(source);
			if (Files.exists(this.pathToFile))
			{
				Source xmlSource = new StreamSource(this.pathToFile.toFile());
				logger.debug("Transforming {} with {} ",this.pathToFile.getFileName(), XSL_FILE_NAME);
				String outfileName = this.pathToFile.getParent().toString() + "/" +  this.pathToFile.getFileName().toString() + ".transformed.csv";
				Result outputTarget =  new StreamResult(new File(outfileName));
				transformer.transform(xmlSource, outputTarget);
				logger.info("Building map from generated file: {}",outfileName);
				//Now we read the file we just created.
				Files.readAllLines(Paths.get(outfileName)).forEach( line -> {
					String parts[] = line.split(",");
					mapping.put(parts[0], parts[1]);
				});
			}
			else
			{
				logger.warn("File {} does not actually exist.", this.pathToFile);
			}
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
		return mapping;
	}
}
