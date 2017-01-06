package org.reactome.addlinks.dataretrieval.ensembl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.client.entity.EntityBuilder;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.reactome.addlinks.dataretrieval.FileRetriever;

public class EnsemblBatchLookup  extends FileRetriever
{
	
	private static final Logger logger = LogManager.getLogger();
	private String species;
	private List<String> identifiers;

	
	public void setSpecies(String s)
	{
		this.species = s;
	}
	
	public void setIdentifiers(List<String> identifiers)
	{
		this.identifiers = identifiers;
	}
	
	/**
	 * Does a batch lookup by POSTing to http://rest.ensembl.org/lookup/id?${SPECIES}
	 * @param identifiers - a list of ENSEMBL identifiers to look up.
	 * @param species - The species name, will be appended to the URL.
	 * @return The result of the lookup, as an XML string.
	 */
	private String doBatchLookup(List<String> identifiers, String species)
	{
		// $ curl -H "Content-type: application/json" -H "Accept:text/xml" -X POST -d '{ "ids":["ENSGALP00000056694","ENSGALP00000056695","ENSGALP00000000000"]}' http://rest.ensembl.org/lookup/id/?species=gallus_gallus
		// <opt>
		//   <data ENSGALP00000000000="">
		//     <ENSGALP00000056694 id="ENSGALP00000056694" Parent="ENSGALT00000080481" db_type="core" end="5024" length="324" object_type="Translation" species="gallus_gallus" start="4050" />
		//     <ENSGALP00000056695 id="ENSGALP00000056695" Parent="ENSGALT00000061540" db_type="core" end="48345104" length="981" object_type="Translation" species="gallus_gallus" start="48335217" />
		//   </data>
		// </opt>
		// # In the example above, you can see that unsuccessful IDs become attributes with no value in the "data" element.
		// # In this example below, you can see that when everything maps successfully, the output looks a little different:
		// <opt>
		//   <!-- What ENSEMBL results look like when everything can be successfully looked up -->
		//   <data name="ENSGALP00000056694" Parent="ENSGALT00000080481" db_type="core" end="5024" id="ENSGALP00000056694" length="324" object_type="Translation" species="gallus_gallus" start="4050" />
		//   <data name="ENSGALP00000056695" Parent="ENSGALT00000061540" db_type="core" end="48345104" id="ENSGALP00000056695" length="981" object_type="Translation" species="gallus_gallus" start="48335217" />
		//   <data name="ENSGALP00000056696" Parent="ENSGALT00000080370" db_type="core" end="3954" id="ENSGALP00000056696" length="139" object_type="Translation" species="gallus_gallus" start="409" />
		// </opt>
		////////////////////////////////////////////////////////////////////
		////////////////////////////////////////////////////////////////////
		// Use this XPath (2.0 - because of using concat on attributes)
		// expression to combine the input IDs with the matching Trascript IDs:
		//	//opt//.[@Parent != null]/concat(@id,',',@Parent)
		// Actually, this might be better (faster):
		//	//opt/(data|.)/*[@Parent ne null]/concat(@id,',',@Parent)
		// See: ensembl-lookup-simplifier.xsl for actual implementation.
		StringBuilder resultBuilder = new StringBuilder();
		resultBuilder.append("<results>");
		
		boolean done = false;
		int index = 0;
		// Loop on groups of 1000 identifiers because their service limits to 1000 per request.
		while (!done)
		{
			StringBuilder sb = new StringBuilder("[");
			int i = 0;
			while ( i < 1000 && index + i < identifiers.size() )
			{
				sb.append("\"").append(identifiers.get(index + i)).append("\",");
				i ++;
			}
			index = i;
			// Remove trailing "," and add the "]" to complete the JSON array.
			String identifiersList = (sb.toString().substring(0, sb.toString().length() - 1)) + "]";


			HttpPost post = new HttpPost(this.getDataURL().toString()+"?species="+species);
			
			HttpEntity attachment = EntityBuilder.create()
									.setBinary(("{ \"ids\":"+identifiersList + " }").getBytes())
									.setContentType(ContentType.APPLICATION_JSON)
									.build();
			post.setEntity(attachment);
			post.addHeader("Accept","text/xml");
			logger.debug("Submitting batch request - {}", index);
			try (CloseableHttpClient postClient = HttpClients.createDefault();
					CloseableHttpResponse postResponse = postClient.execute(post);)
			{
				String responseString = EntityUtils.toString(postResponse.getEntity());
				resultBuilder.append(responseString);
			}
			catch (IOException e)
			{
				e.printStackTrace();
				throw new Error(e);
			}
			
			if (index >= this.identifiers.size())
			{
				done = true;
			}
		}
		
		resultBuilder.append("</results>");
		return resultBuilder.toString();
	}
	
	@Override
	public void downloadData()
	{
		String results = this.doBatchLookup(identifiers, species);
		try
		{
			Files.createDirectories(Paths.get(this.destination).getParent());
			Files.write(Paths.get(this.destination), results.getBytes());
		}
		catch (IOException e)
		{
			logger.error("File could not be written because: {}",e.getMessage());
			e.printStackTrace();
			throw new Error(e);
		}
	}
}
