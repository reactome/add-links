package org.reactome.addlinks.fileprocessors;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.http.conn.params.ConnConnectionParamBean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

public class HmdbMetabolitesFileProcessor extends FileProcessor<Map<HmdbMetabolitesFileProcessor.HMDBFileMappingKeys, ? extends Collection<String>>>
{
	public enum HMDBFileMappingKeys
	{
		CHEBI,
		UNIPROT
	}
	
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
	
	/**
	 * This override will process all the HMDB Molecules files. 
	 * It will return a map that is keyed by HMDB accession. <br />
	 *  - The values are maps: one key is "UniProt" and will return a list of UniProt IDs that the HMDB Accession maps to. <br/>
	 *  - The other key is "ChEBI" which is a list of ChEBI IDs (there will never be more than 1 value in this list because there can only be 1 ChEBI in the XML)
	 *  that the HMDB Accession maps to.
	 * @see org.reactome.addlinks.fileprocessors.FileProcessor#getIdMappingsFromFile()
	 */
	@Override
	public Map<String, Map<HmdbMetabolitesFileProcessor.HMDBFileMappingKeys, ? extends Collection<String>>> getIdMappingsFromFile() 
	{
		try
		{
			String dirToHmdbFiles = this.unzipFile(this.pathToFile);
			
			Map<String, Map<HMDBFileMappingKeys, ? extends Collection<String>>> hmdb2ChebiAndUniprot = new ConcurrentHashMap<String, Map<HMDBFileMappingKeys, ? extends Collection<String>>>();
			
			AtomicInteger fileCounter = new AtomicInteger(0);
			AtomicInteger totalChEBIMappingCounter = new AtomicInteger(0);
			AtomicInteger totalUniProtMappingCounter = new AtomicInteger(0);
			// This consumer will process files.
			Consumer<? super Path> processHmdbFile = path -> {
				try
				{
					fileCounter.incrementAndGet();
					String fileContent = new String(Files.readAllBytes(path));
					InputSource source = new InputSource(new ByteArrayInputStream(fileContent.getBytes("utf-8")));
					
					Node root = (Node) XPathFactory.newInstance().newXPath().evaluate("/", source, XPathConstants.NODE);
					
					String accession = HmdbMetabolitesFileProcessor.pathToAccession.evaluate(root,XPathConstants.STRING).toString();
					logger.trace("accession: {}",accession);
					String chebiId = HmdbMetabolitesFileProcessor.pathToChebi.evaluate(root,XPathConstants.STRING).toString();
					logger.trace("ChEBI ID: {}",chebiId);
					NodeList nodeList = (NodeList) HmdbMetabolitesFileProcessor.pathToUniprot.evaluate(root,XPathConstants.NODESET);
					
					Collection<String> uniProtList = Collections.synchronizedCollection(new ArrayList<String>());
					for (int i = 0 ; i < nodeList.getLength() ; i ++)
					{
						totalUniProtMappingCounter.getAndIncrement();
						String uniprotId = nodeList.item(i).getTextContent();
						uniProtList.add(uniprotId);
					}
					logger.trace("# Uniprot mappings: {}",nodeList.getLength());

					// Build the result for THIS file.
					Map<HMDBFileMappingKeys, Collection<String>> hmdbVals = new ConcurrentHashMap<HMDBFileMappingKeys, Collection<String>>();
					hmdbVals.put(HMDBFileMappingKeys.UNIPROT, uniProtList);
					if (chebiId != null && !chebiId.trim().equals(""))
					{
						totalChEBIMappingCounter.getAndIncrement();
					}
					hmdbVals.put(HMDBFileMappingKeys.CHEBI, Arrays.asList(chebiId));
					// Add the result from THIS file to the main map.
					hmdb2ChebiAndUniprot.put(accession, hmdbVals);
				}
				catch (Exception e)
				{
					e.printStackTrace();
					throw new RuntimeException(e.getMessage());
					
				}
			};
			
			// Process the HMDB files.
			Files.list(Paths.get(dirToHmdbFiles))
				.filter(p -> p.getFileName().toString().startsWith("HMDB") 
							&& p.getFileName().toString().endsWith(".xml"))
				.parallel()
				.forEach(processHmdbFile);
			
			logger.debug("\nHMDB Metabolites file processing Summary:"
						+ "\n\tNumber of ChEBI mappings: {} ;"
						+ "\n\tNumber of Uniprot mappings: {} ;"
						+ "\n\tNumber of files processed: {} ;"
						+ "\n\tNumber of HMDB accessions that have some mapping: {} ;",
						totalChEBIMappingCounter.get(), totalUniProtMappingCounter.get(), fileCounter.get(), hmdb2ChebiAndUniprot.keySet().size());
			return hmdb2ChebiAndUniprot;
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		return null;
	}

}
