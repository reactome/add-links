package org.reactome.addlinks.fileprocessors.ensembl;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

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

public class EnsemblFileProcessor extends FileProcessor<Map<String,List<String>>>
{

	//A list of databses to search for in the XML file. 
	private List<String> dbs;
	
	private static final Logger logger = LogManager.getLogger();
	
	@Override
	public Map<String, Map<String,List<String>>> getIdMappingsFromFile()
	{
		TransformerFactory factory = TransformerFactory.newInstance();
		
		Map<String,Map<String,List<String>>> mappings = new HashMap<String, Map<String,List<String>>>();
		for (String dbName : dbs)
		{
			AtomicInteger counter = new AtomicInteger(0);
			Map<String,List<String>> ensemblToOther = new HashMap<String, List<String>>();
			logger.info("Extracting data for \"" + dbName + "\" from ENSEMBL-downloaded data; input file is {}", this.pathToFile.toString());
			try
			{
				//Transform the generated Ensembl output XML into a more usable CSV file.
				Source source = new StreamSource(this.getClass().getClassLoader().getResourceAsStream("ensembl_transform.xsl"));
				Transformer transformer = factory.newTransformer(source);
				transformer.setParameter("db", dbName);
				
				if (Files.exists(this.pathToFile))
				{
					Source xmlSource = new StreamSource(this.pathToFile.toFile());
					String outfileName = this.pathToFile.getParent().toString() + "/" +  this.pathToFile.getFileName().toString() + "." + dbName + ".transformed.tsv";
					Result outputTarget =  new StreamResult(new File(outfileName));
					transformer.transform(xmlSource, outputTarget);
					logger.info("Building map from generated file: {}",outfileName);
					//Now we read the file we just created.
					Files.readAllLines(Paths.get(outfileName)).forEach( line -> {
						String parts[] = line.split("\t");
						String ensemblId = parts[0];
						String otherDbId = parts[1];
						counter.getAndIncrement();
						if (ensemblToOther.containsKey(ensemblId))
						{
							List<String> idList = (ensemblToOther.get(ensemblId));
							idList.add(otherDbId);
							ensemblToOther.put(ensemblId, idList );
						}
						else
						{
							List<String> idList = new ArrayList<String>();
							idList.add(otherDbId);
							ensemblToOther.put(ensemblId, idList);
						}
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
				throw new RuntimeException(e);
			}
			catch (TransformerException e)
			{
				e.printStackTrace();
				throw new RuntimeException(e);
			}
			catch (IOException e)
			{
				e.printStackTrace();
				throw new RuntimeException(e);
			}
			logger.info("Processed {} records.",counter.get());
			mappings.put(dbName, ensemblToOther);
		}
		return mappings;
	}

	public void setDbs(List<String> dbs)
	{
		this.dbs = dbs;
	}

}
