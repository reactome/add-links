package org.reactome.addlinks.fileprocessors;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

public class HmdbProteinsFileProcessor extends FileProcessor
{
	private static XPathExpression pathToProteinsList;
	private static XPathExpression pathToAccession;
	private static XPathExpression pathToUniprot;
	private static final Logger logger = LogManager.getLogger();
	static
	{
		try
		{
			pathToProteinsList = XPathFactory.newInstance().newXPath().compile("/proteins/protein");
			pathToAccession = XPathFactory.newInstance().newXPath().compile("accession/text()");
			pathToUniprot = XPathFactory.newInstance().newXPath().compile("uniprot_id");
		}
		catch (XPathExpressionException e)
		{
			e.printStackTrace();
		}
	}

	
	private static final String XML_DEC = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";
	
	@Override
	//HMDB Proteins (and probably metabolites too) could return a 1:n mapping from the file...
	public Map<String, List<String>> getIdMappingsFromFile()
	{
		try
		{
			String dirToHmdbFiles = this.unzipFile(this.pathToFile);
			
			//The HMDB Proteins file is weird: 
			// It contains many XML files concatenated into a single file, so 
			// first we need to deal with this.
			// Options:
			// 1) remove all but the first XML declaration and wrap
			// all elements in a single <ROOT> element.
			// 2) Split into separate files at each XML declaration.
			// Once this is done, the individual XML files can be processed.
			StringBuilder sb = new StringBuilder();
			
			
			Files.list(Paths.get(dirToHmdbFiles))
				//filter for XML files, though we only expect 1 in this case of HMDB Proteins. 
				.filter(p -> p.getFileName().toString().endsWith(".xml"))
				.forEach(p -> {
								try(Stream<String> stream = Files.lines(p))
								{
									//Every line that is not an XML delcaration will be added to the string builder. 
									stream.filter(s -> !s.equals(XML_DEC))
											.forEach( s -> sb.append(s)) ;
								}
								catch (IOException e)
								{
									// TODO Auto-generated catch block
									e.printStackTrace();
								}
							}
						);
			// Wrap everything in a root element.
			sb.insert(0, "<proteins>");
			sb.append("</proteins>");
			// Add an XML Declaration. 
			sb.insert(0, XML_DEC);
			
			//Now that we have a well-formed XML document, we can begin setting up the mapping.
			InputSource source = new InputSource(new ByteArrayInputStream(sb.toString().getBytes("utf-8")));
			
			NodeList root = (NodeList) HmdbProteinsFileProcessor.pathToProteinsList.evaluate(source,XPathConstants.NODESET);
			
			
			for (int i = 0; i < root.getLength(); i++)
			{
				Map<String,ArrayList<String>> accesionToUniprots = new HashMap<String,ArrayList<String>>();
				Node n = root.item(i);
				String accession = HmdbProteinsFileProcessor.pathToAccession.evaluate(n,XPathConstants.STRING).toString();
				NodeList uniprotIds = (NodeList) HmdbProteinsFileProcessor.pathToUniprot.evaluate(n,XPathConstants.NODESET);
				ArrayList<String> uniprots = new ArrayList<>(uniprotIds.getLength());
				for (int j = 0; i < uniprotIds.getLength(); j++)
				{
					String uniprotIdText = XPathFactory.newInstance().newXPath().evaluate("text()", uniprotIds.item(j));
					uniprots.add(uniprotIdText);
				}
				//no, should be mapping uniprot -> accession - and it might NOT be 1:1.
				accesionToUniprots.put(accession, uniprots);
			}
			
			
		}
		catch (Exception e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		// TODO Auto-generated method stub
		return null;
	}

}
