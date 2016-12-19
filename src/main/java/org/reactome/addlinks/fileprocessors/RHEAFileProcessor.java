package org.reactome.addlinks.fileprocessors;


import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Map;

import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

public class RHEAFileProcessor extends FileProcessor<String>
{
	private static final Logger logger = LogManager.getLogger();
	// XPATH expression to get RHEA URIs: /ns2:RheaWsResponse/resultset/rheaReaction/rheaid/rheaUri/uri[../uriresponseformat/text()='cmlreact' ]
	private static XPathExpression rheaURIPath ;
	
	static
	{
		try
		{
			rheaURIPath = XPathFactory.newInstance().newXPath().compile("/ns2:RheaWsResponse/resultset/rheaReaction/rheaid/rheaUri/uri[../uriresponseformat/text()='cmlreact']");
		}
		catch (XPathExpressionException e)
		{
			e.printStackTrace();
			throw new Error("Error creating XPath expression for processing RHEA files: "+e.getMessage(), e);
		}
	}
	
	@Override
	public Map<String, String> getIdMappingsFromFile()
	{
		// For each rheaReaction element in the input XML, we want to get the RHEA ID, then query http://www.ebi.ac.uk/rhea/rest/1.0/ws/reaction/cmlreact/$rhea_id
		// and extract the Reactome ID.
		StringBuilder sb = new StringBuilder();
		try
		{
			Files.readAllLines(this.pathToFile).stream().sequential().forEach(sb::append);
		}
		catch (IOException e)
		{
			e.printStackTrace();
			throw new Error("Error reading file: "+this.pathToFile.toString(), e);
		}
		
		InputStream inStream = new ByteArrayInputStream(sb.toString().getBytes());
		InputSource source = new InputSource(inStream);
		NodeList nodeList = null;
		try
		{
			nodeList = (NodeList) RHEAFileProcessor.rheaURIPath.evaluate(source, XPathConstants.NODESET);
		}
		catch (XPathExpressionException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		if (nodeList != null && nodeList.getLength() > 0)
		{
			logger.debug("# of RHEA URIs in XML response: {}", nodeList.getLength());
			for (int i = 0 ; i < nodeList.getLength() ; i ++)
			{
				String rheaURI = nodeList.item(i).getTextContent();
				//Once we have the RHEA ID, we need to access it and parse the response for the Reactome ID.
				
			}
		}
		
		// TODO Auto-generated method stub
		return null;
	}

}
