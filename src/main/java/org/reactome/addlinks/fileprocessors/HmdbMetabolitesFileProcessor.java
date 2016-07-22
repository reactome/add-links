package org.reactome.addlinks.fileprocessors;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

public class HmdbMetabolitesFileProcessor extends FileProcessor
{
	private static XPathExpression pathToAccession;
	private static XPathExpression pathToChebi;
	private static XPathExpression pathToUniprot;
	private static final Logger logger = LogManager.getLogger();
	static
	{
		try
		{
			pathToAccession = XPathFactory.newInstance().newXPath().compile("/metabolite/accession/text()");
			pathToChebi = XPathFactory.newInstance().newXPath().compile("/metabolite/chebi_id/text()");
			pathToUniprot = XPathFactory.newInstance().newXPath().compile("/metabolite/protein_associations/protein/uniprot_id"); 
		}
		catch (XPathExpressionException e)
		{
			e.printStackTrace();
		}
	}
	
	@Override
	public Map<String, String> getIdMappingsFromFile() 
	{
		try
		{
			String dirToHmdbFiles = this.unzipFile(this.pathToFile);
			Map<String,String> chebiToHmdb = new ConcurrentHashMap<String,String>();
			Map<String,String> uniprotToHmdb = new ConcurrentHashMap<String,String>();
			AtomicInteger fileCounter = new AtomicInteger(0);
			Consumer<? super Path> processHmdbFile = path -> {
				try
				{
					fileCounter.incrementAndGet();
					String fileContent = new String(Files.readAllBytes(path));
					InputSource source = new InputSource(new ByteArrayInputStream(fileContent.getBytes("utf-8")));
					
					Node root = (Node) XPathFactory.newInstance().newXPath().evaluate("/", source, XPathConstants.NODE);
					
					String accession = HmdbMetabolitesFileProcessor.pathToAccession.evaluate(root,XPathConstants.STRING).toString();
					//logger.debug("accession: {}",accession);
					String chebiId = HmdbMetabolitesFileProcessor.pathToChebi.evaluate(root,XPathConstants.STRING).toString();
					//logger.debug("ChEBI ID: {}",chebiId);
					NodeList nodeList = (NodeList) HmdbMetabolitesFileProcessor.pathToUniprot.evaluate(root,XPathConstants.NODESET);
					for (int i = 0 ; i < nodeList.getLength() ; i ++)
					{
						String uniprotId = nodeList.item(i).getTextContent();
						uniprotToHmdb.put(uniprotId, accession);
					}
					//logger.debug("# Uniprot mappings: {}",nodeList.getLength());
					chebiToHmdb.put(chebiId, accession);
				}
				catch (Exception e)
				{
					e.printStackTrace();
					throw new RuntimeException(e.getMessage());
					
				}
			};
			
			// Process the HMDB files. //seems to run faster if I run the streams in parallel, but not sure if it's safe...
			Files.list(Paths.get(dirToHmdbFiles))
				.filter(p -> p.getFileName().toString().startsWith("HMDB") 
							&& p.getFileName().toString().endsWith(".xml"))
				.forEach(processHmdbFile);
			
			Map<String,String> mapToHmdb = new HashMap<String,String>(chebiToHmdb.size() + uniprotToHmdb.size());
			mapToHmdb.putAll(chebiToHmdb);
			mapToHmdb.putAll(uniprotToHmdb);
			logger.debug("Number of ChEBI mappings: {} ; Number of Uniprot mappings: {} ; number of files processed: {}", chebiToHmdb.size(), uniprotToHmdb.size(), fileCounter.get());
			return mapToHmdb;
		}
		catch (Exception e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

}
