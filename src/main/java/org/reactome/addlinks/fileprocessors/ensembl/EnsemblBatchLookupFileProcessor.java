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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.reactome.addlinks.fileprocessors.FileProcessor;

public class EnsemblBatchLookupFileProcessor extends FileProcessor<String>
{
	private static final Logger logger = LogManager.getLogger();
	
	@Override
	public Map<String, String> getIdMappingsFromFile()
	{
		Map<String,String> mapping = new HashMap<String, String>();
		TransformerFactory factory = TransformerFactory.newInstance();
		//Transform the generated Ensembl output XML into a more usable CSV file.
		Source source = new StreamSource(this.getClass().getClassLoader().getResourceAsStream("ensembl-lookup-simplifier.xsl"));
		Transformer transformer;
		try
		{
			transformer = factory.newTransformer(source);
			if (Files.exists(this.pathToFile))
			{
				Source xmlSource = new StreamSource(this.pathToFile.toFile());
				String outfileName = this.pathToFile.getParent().toString() + "/" +  this.pathToFile.getFileName().toString() + ".transformed.csv";
				Result outputTarget =  new StreamResult(new File(outfileName));
				transformer.transform(xmlSource, outputTarget);
				logger.info("Building map from generated file: {}",outfileName);
				//Now we read the file we just created.
				Files.readAllLines(Paths.get(outfileName)).forEach( line -> {
					String parts[] = line.split(",");
					String ensemblId = parts[0];
					String otherDbId = parts[1];
					mapping.put(ensemblId, otherDbId);
//					if (ensemblToOther.containsKey(ensemblId))
//					{
//						List<String> idList = (ensemblToOther.get(ensemblId));
//						idList.add(otherDbId);
//						ensemblToOther.put(ensemblId, idList );
//					}
//					else
//					{
//						List<String> idList = new ArrayList<String>();
//						idList.add(otherDbId);
//						ensemblToOther.put(ensemblId, idList);
//					}
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
