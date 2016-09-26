package org.reactome.addlinks.fileprocessors;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
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

public class EnsemblFileProcessor extends FileProcessor
{

	//A list of databses to search for in the XML file. 
	private List<String> dbs;
	
	private static final Logger logger = LogManager.getLogger();
	
	@Override
	public Map<String, ?> getIdMappingsFromFile()
	{
		TransformerFactory factory = TransformerFactory.newInstance();
		Source source = new StreamSource(this.getClass().getClassLoader().getResourceAsStream("ensembl_transform.xsl"));
		for (String dbName : dbs)
		{
			logger.info("Extracting data for \"dbname\" from ENSEMBL-downloaded data.");
			try
			{
				//Transform the generated Ensembl output XML into a more usable CSV file.
				Transformer transformer = factory.newTransformer(source);
				transformer.setParameter("db", dbName);
				Source xmlSource = new StreamSource(this.pathToFile.toFile());
				String outfileName = this.pathToFile.getParent().toString() + "/" +  this.pathToFile.getFileName().toString() + ".transformed.csv";
				Result outputTarget =  new StreamResult(new File(outfileName));
				transformer.transform(xmlSource, outputTarget);
				
				//Now we read the file we just created.
				Files.readAllLines(Paths.get(outfileName)).forEach( line -> {
					String parts[] = line.split(",");
					String ensemblId = parts[0];
					String otherDbId = parts[1];
				});
			}
			catch (TransformerConfigurationException e)
			{
				e.printStackTrace();
			}
			catch (TransformerException e)
			{
				e.printStackTrace();
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}
		return null;
	}

	public void setDbs(List<String> dbs)
	{
		this.dbs = dbs;
	}

}
