package org.reactome.addlinks.fileprocessors.ensembl;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.reactome.addlinks.fileprocessors.GlobbedFileProcessor;

public class EnsemblFileProcessor extends GlobbedFileProcessor<Map<String,List<String>>>
{

	//A list of databses to search for in the XML file. 
	private List<String> dbs;
	
	private static final Logger logger = LogManager.getLogger();
	
	private static final Pattern pattern = Pattern.compile("[^.]+\\.\\d+\\.xml");
	
	@Override
	protected Map<String, Map<String,List<String>>> getIdMappingsFromFilesMatchingGlob()
	{
		//PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + this.fileGlob);
		
		ReactomeMappingFileVisitor visitor = new ReactomeMappingFileVisitor() {
			@Override
			protected void addFileToMapping(Path file, Map<String, Map<String, List<String>>> mapping)
			{
				Matcher patternMatcher = pattern.matcher(file.getFileName().toString());
				if (patternMatcher.matches() )
				{
					processFile(file);
				}
			}
		};
		Map<String,Map<String,List<String>>> mappings = new HashMap<String, Map<String,List<String>>>();
		this.mappings = mappings;
		visitor.setMapping(this.mappings);
		visitor.setMatcher(FileSystems.getDefault().getPathMatcher("glob:" + this.fileGlob));
		this.globFileVisitor = visitor;
		
		try
		{
			Files.walkFileTree(this.pathToFile, this.globFileVisitor);
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		
		return visitor.getMapping();
	}

	private Map<String, Map<String, List<String>>> processFile(Path file) throws TransformerFactoryConfigurationError
	{
		TransformerFactory factory = TransformerFactory.newInstance();
		
		//Map<String,Map<String,List<String>>> mappings = new HashMap<String, Map<String,List<String>>>();
		for (String dbName : dbs)
		{
			AtomicInteger counter = new AtomicInteger(0);
			Map<String,List<String>> ensemblToOther = new HashMap<String, List<String>>();
			logger.info("Extracting data for \"" + dbName + "\" from ENSEMBL-downloaded data; input file is {}", file.toString());
			try
			{
				//Transform the generated Ensembl output XML into a more usable CSV file.
				Source source = new StreamSource(this.getClass().getClassLoader().getResourceAsStream("ensembl_transform.xsl"));
				Transformer transformer = factory.newTransformer(source);
				logger.debug("processing {} for xrefs to {}", file, dbName);
				transformer.setParameter("db", dbName);
				
				if (Files.exists(this.pathToFile))
				{
					Source xmlSource = new StreamSource(file.toFile());
					String outfileName = file.getParent().toString() + "/" + file.getFileName().toString() + "." + dbName + ".transformed.tsv";
					Result outputTarget =  new StreamResult(new File(outfileName));
					transformer.transform(xmlSource, outputTarget);
					logger.info("Building map from generated file: {}",outfileName);
					//Now we read the file we just created.
					Files.readAllLines(Paths.get(outfileName)).forEach( line -> {
						String parts[] = line.split(",");
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
					logger.warn("File {} does not actually exist.", file);
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
