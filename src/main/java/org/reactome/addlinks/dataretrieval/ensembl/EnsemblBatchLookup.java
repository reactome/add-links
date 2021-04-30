package org.reactome.addlinks.dataretrieval.ensembl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.entity.EntityBuilder;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.reactome.util.ensembl.EnsemblServiceResponseProcessor.EnsemblServiceResult;
import org.reactome.release.common.dataretrieval.FileRetriever;
import org.reactome.util.ensembl.EnsemblServiceResponseProcessor;

public class EnsemblBatchLookup  extends FileRetriever
{
	private String species;
	private List<String> identifiers;

	public String getFetchDestination()
	{
		return this.destination;
	}

	public void setSpecies(String s)
	{
		this.species = s;
	}

	public void setIdentifiers(List<String> identifiers)
	{
		this.identifiers = identifiers;
	}

	public EnsemblBatchLookup() { }

	public EnsemblBatchLookup(String retrieverName)
	{
		super(retrieverName);
	}

	/**
	 * Does a batch lookup by POSTing to http://rest.ensembl.org/lookup/id?${SPECIES}
	 * @param identifiers - a list of ENSEMBL identifiers to look up.
	 * @param species - The species name, will be appended to the URL.
	 * @return The result of the lookup, as an XML string.
	 */
	private String doBatchLookup(List<String> identifiers, String species)
	{
		logger.debug("{} identifiers need to be looked up for species {}; will be done in batch sizes of 1000", identifiers.size(), species);
		if (identifiers.size() > 0)
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
				index += i;
				// Remove trailing "," and add the "]" to complete the JSON array.
				String identifiersList = (sb.toString().substring(0, sb.toString().length() - 1)) + "]";


				HttpPost post = new HttpPost(this.getDataURL().toString()+"?species="+species);

				HttpEntity attachment = EntityBuilder.create()
										.setBinary(("{ \"ids\":"+identifiersList + " }").getBytes())
										.setContentType(ContentType.APPLICATION_JSON)
										.build();
				post.setEntity(attachment);
				post.addHeader("Accept","text/xml");

				try
				{
					boolean requestDone = false;
					EnsemblServiceResponseProcessor responseProcessor = new EnsemblServiceResponseProcessor(this.logger);
					while (!requestDone)
					{

						logger.debug("Submitting batch request - {}", index);
						try (CloseableHttpClient postClient = HttpClients.createDefault();
								CloseableHttpResponse postResponse = postClient.execute(post);)
						{
							EnsemblServiceResult result = responseProcessor.processResponse(postResponse, post.getURI());
							// This means we need to wait, and then retry
							if (!result.getWaitTime().equals(Duration.ZERO))
							{
								logger.info("Need to wait: {} seconds.", result.getWaitTime().getSeconds());
								Thread.sleep(result.getWaitTime().toMillis());
							}
							else
							{
								if (result.getStatus() == HttpStatus.SC_OK)
								{

									String responseString = result.getResult();
									resultBuilder.append(responseString);
									requestDone = true;
								}
								else if (result.isOkToRetry())
								{
									// The only case where isOkToRetry is true is when the rate limit was exceeded or when the endpoint timed out.
									// So, setting requestDone to !isOkToRetry should terminate the request-loop.
									requestDone = false;
								}
							}
						}
					}
				}
				catch (IOException e)
				{
					e.printStackTrace();
					logger.error("Error occurred while sending webservice request: {}", e.getMessage());
					// This is probably not recoverable.
					throw new Error(e);
				}
				catch (InterruptedException e)
				{
					e.printStackTrace();
					logger.error("Something bad happened while waiting to re-try the webservice request: {}", e.getMessage());
					// Throw a new error, this is probably not recoverable.
					throw new Error(e);
				}

				if (index >= this.identifiers.size())
				{
					done = true;
				}
			}
			logger.info("{} requests remaining for ENSEMBL service.", EnsemblServiceResponseProcessor.getNumRequestsRemaining());
			resultBuilder.append("</results>");
			return resultBuilder.toString();
		}
		else
		{
			logger.info("Empty/null identifiers list was given for species {}; no lookup request was sent.", species);
			return "";
		}

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
