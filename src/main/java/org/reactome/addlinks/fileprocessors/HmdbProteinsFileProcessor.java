package org.reactome.addlinks.fileprocessors;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
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
			pathToProteinsList = XPathFactory.newInstance().newXPath().compile("/protein");
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
		Map<String,List<String>> accesionToUniprots = new HashMap<String,List<String>>();
		try
		{
			String dirToHmdbFiles = this.unzipFile(this.pathToFile);
			
			//The HMDB Proteins file is weird: 
			// It contains many XML files concatenated into a single file, so 
			// first we need to deal with this.
			// Split into separate files at each XML declaration.
			// Once this is done, the individual XML files can be processed.
			
			AtomicInteger counter = new AtomicInteger(0);
			Files.list(Paths.get(dirToHmdbFiles))
				//filter for XML files, though we only expect 1 in this case of HMDB Proteins. 
				.filter(p -> p.getFileName().toString().endsWith(".xml"))
				.forEach(p -> {
								logger.debug("Input XML file: {}", p.getFileName().toString());
								try(Stream<String> lineStream = Files.lines(p).sequential())
								{
									StringBuilder sb = new StringBuilder();
									
									lineStream.forEach( s -> {
										if (s.equals(XML_DEC))
										{
											//Dump to a file before emptying the builder.
											if (sb.length() > 0)
											{
												try(FileOutputStream fos = new FileOutputStream( dirToHmdbFiles + "/" + counter.getAndIncrement() + ".xml" ))
												{
													fos.write(sb.toString().getBytes());
												}
												catch(IOException e)
												{
													e.printStackTrace();
												}
											}
											sb.delete(0, sb.length());
											sb.append(s);
										}
										else
										{
											sb.append(s).append("\n");
										}
									}) ;
									
								}
								catch (IOException e)
								{
									// TODO Auto-generated catch block
									e.printStackTrace();
								}
							}
						);
			logger.info("Number of XML files extracted: {}", counter.get());
			
			//Now that we have a well-formed XML document, we can begin setting up the mapping.
			for (int fileNum = 0; fileNum < counter.get(); fileNum++ )
			{
				try
				{
					String fileName = dirToHmdbFiles + "/" + fileNum + ".xml" ;
					InputStream is = new FileInputStream(fileName);
					//InputSource source = new InputSource(new ByteArrayInputStream(sb.toString().getBytes("utf-8")));
					InputSource source = new InputSource(is);
					NodeList root = (NodeList) HmdbProteinsFileProcessor.pathToProteinsList.evaluate(source,XPathConstants.NODESET);
					
					//logger.debug("file: {}", fileName);
					for (int i = 0; i < root.getLength(); i++)
					{
						
						Node n = root.item(i);
						String accession = HmdbProteinsFileProcessor.pathToAccession.evaluate(n,XPathConstants.STRING).toString();
						NodeList uniprotIds = (NodeList) HmdbProteinsFileProcessor.pathToUniprot.evaluate(n,XPathConstants.NODESET);
						ArrayList<String> uniprots = new ArrayList<>(uniprotIds.getLength());
						for (int j = 0; j < uniprotIds.getLength(); j++)
						{
							if (uniprotIds != null && uniprotIds.item(j).hasChildNodes())
							{
								//logger.debug("{}",uniprotIds.item(j).toString());
								String uniprotIdText = (String) XPathFactory.newInstance().newXPath().evaluate("text()", uniprotIds.item(j),XPathConstants.STRING);
								//logger.debug("uniprotId: {}",uniprotIdText);
								uniprots.add(uniprotIdText);
							}
						}
						//no, should be mapping uniprot -> accession - and it might NOT be 1:1.
						accesionToUniprots.put(accession, uniprots);
					}
				}
				catch (XPathExpressionException e)
				{
					logger.error("Error processing file {}, Error is: ",counter.get()+".xml", e.getMessage());
					e.printStackTrace();
				}
			}
			
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		catch (Exception e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		logger.info("Number of accessions: {}", accesionToUniprots.keySet().size());
		return accesionToUniprots;
	}

}
