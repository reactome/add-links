package org.reactome.addlinks.fileprocessors;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stax.StAXSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

public class HmdbMetabolitesFileProcessor extends FileProcessor<Map<HmdbMetabolitesFileProcessor.HMDBFileMappingKeys, ? extends Collection<String>>>
{
	private static final Object METABOLITE_ELEMENT_NAME = "metabolite";


	public HmdbMetabolitesFileProcessor(String processorName)
	{
		super(processorName);
	}

	public HmdbMetabolitesFileProcessor()
	{
		super(null);
	}
	
	public enum HMDBFileMappingKeys
	{
		CHEBI,
		UNIPROT
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
			
			// These probably don't need to be atomic anymore since we're no longer processing the "files" in parallel.
			AtomicInteger fileCounter = new AtomicInteger(0);
			AtomicInteger totalChEBIMappingCounter = new AtomicInteger(0);
			AtomicInteger totalUniProtMappingCounter = new AtomicInteger(0);
			
			TransformerFactory factory = TransformerFactory.newInstance();
			try
			{
				//String dirToFile = this.unzipFile(this.pathToFile);
				String inputFilename = dirToHmdbFiles + "/" + this.pathToFile.getFileName().toString().replaceAll(".zip", ".xml");
				
				//Transform the HMDB XML into a more usable CSV file.
				String outfileName = inputFilename + ".transformed.tsv";
				this.transformXmlToTsv(inputFilename, outfileName);
				
				//Now we need to read the file.
				Files.readAllLines(Paths.get(outfileName)).stream().forEach(line -> 
				{
					String[] parts = line.split("\t");
					// Map uniprot to accession.
					Map<HMDBFileMappingKeys, Collection<String>> hmdbVals = new ConcurrentHashMap<HMDBFileMappingKeys, Collection<String>>();
					if (parts.length >= 2 && parts[1] != null && !parts[1].trim().equals(""))
					{
						hmdbVals.put( HMDBFileMappingKeys.CHEBI, Arrays.asList(parts[1]));
						totalChEBIMappingCounter.incrementAndGet();
					}
					
					if (parts.length >= 3 && parts[2] != null && !parts[2].trim().equals(""))
					{
						hmdbVals.put( HMDBFileMappingKeys.UNIPROT, Arrays.asList(Arrays.copyOfRange(parts, 2, parts.length - 1)));
						totalUniProtMappingCounter.addAndGet(parts.length);
					}
					
					hmdb2ChebiAndUniprot.put(parts[0], hmdbVals);
				});
			}
			catch (IOException e)
			{
				e.printStackTrace();
				throw new Error(e);
			}
			catch (Exception e)
			{
				e.printStackTrace();
				throw new Error(e);
			}
			
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

	
	private void transformXmlToTsv(String inputFilename, String outfileName)
	{
		System.setProperty("javax.xml.transform.TransformerFactory", "com.sun.org.apache.xalan.internal.xsltc.trax.TransformerFactoryImpl");
		try(OutputStream outStream = new FileOutputStream(outfileName);)
		{
			XMLInputFactory xif = XMLInputFactory.newInstance();
			XMLStreamReader xsr = xif.createXMLStreamReader(new FileReader(inputFilename));
			xsr.nextTag();
			Source source = new StreamSource(this.getClass().getClassLoader().getResourceAsStream("hmdb_metabolites_transform.xsl"));
			TransformerFactory factory = TransformerFactory.newInstance();
			Transformer transformer = factory.newTransformer(source);
			// While there are start elements...
			while (xsr.nextTag() == XMLStreamConstants.START_ELEMENT)
			{
				// Check if the current start element is "metabolite" - that's the root element that we will transform.
				if (xsr.getName().getLocalPart().equals(METABOLITE_ELEMENT_NAME))
				{
					// Get everything rooted under the "metabolite" element.
					StAXSource src = new StAXSource(xsr);
					// get a Result that points to the output file 
					Result result = new StreamResult(outStream);
					// execute the XSL transform - the transformed output will go to the output file via `result`.
					transformer.transform(src, result);
				}
			}
			xsr.close();
		}
		catch (FileNotFoundException e ) 
		{
			logger.error("Input XML file was not found!", e);
		}
		catch (XMLStreamException e)
		{
			logger.error("Error streaming XML file!", e);
		}
		catch (TransformerConfigurationException e)
		{
			logger.error("Error with XSL file!", e);
		}
		catch (TransformerException e)
		{
			logger.error("Data transformation error!", e);
		}
		catch (IOException e)
		{
			logger.error("I/O Error", e);
		}
	}
}
