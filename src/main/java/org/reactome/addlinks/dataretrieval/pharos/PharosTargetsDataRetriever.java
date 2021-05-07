package org.reactome.addlinks.dataretrieval.pharos;

import java.io.FileWriter;
import java.io.IOException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class PharosTargetsDataRetriever extends PharosDataRetriever
{
	// NOTE: It might be tempting to add "\n" at the end of the "lines" in this string below, but that will result in a "Bad request" response
	// from the GraphQL service. Clearly, queries cannot contain real line breaks.
	private static final String TARGETS_QUERY = "{ \"query\": \"query {"
			+ "    targets {"
			+ "        count"
			+ "        targets(top:"+PharosDataRetriever.TOP_AMOUNT_TOKEN+" skip:"+PharosDataRetriever.SKIP_AMOUNT_TOKEN+") {"
			+ "            uniprot"
			+ "        }"
			+ "    }"
			+ "}\" }";

	// Data from Pharos should look like this:
	//	{
	//		  "data": {
	//		    "targets": {
	//		      "count": 20412,
	//		      "targets": [
	//		        {
	//		          "uniprot": "A8MVA2"
	//		        },
	//		        {
	//		          "uniprot": "A8MX34"
	//		        },
	//		        ...


	/**
	 * Implements the logic to process JSON from Pharos.
	 * @throws PharosDataException
	 */
	protected int processJSONArray(FileWriter writer, JSONObject jsonObj) throws IOException, PharosDataException
	{
		try
		{
			// result could be simply flattened with jq using the jq expression: ".data.targets.targets[].uniprot"
			// but some people find jq weird, so we'll just stick with javax.json and explicitly access elements by keys.
			JSONArray targets = (((JSONObject)((JSONObject) jsonObj.get("data")).get("targets")).getJSONArray("targets"));
			for (int i = 0; i < targets.length(); i++)
			{
				if (((JSONObject)targets.get(i)).has("uniprot"))
				{
					writer.write(((JSONObject)targets.get(i)).get("uniprot") + "\n");
				}
				else
				{
					// A missing "uniprot" key suggests a possibly serious problem with the data that was returned from Pharos.
					throw new PharosDataException("The \"uniprot\" key was not present, but it should be (in fact, it should be the ONLY key at this depth in the document). Check your query!");
				}
			}
			return targets.length();
		}
		catch (JSONException e)
		{
			// If there's a problem with the JSON, log the JSON and throw a PharosDataException
			logger.error("Error processing JSON! JSON source is: {}", jsonObj.toString());
			throw new PharosDataException(e.getMessage());
		}

	}


	@Override
	protected String getQuery()
	{
		return PharosTargetsDataRetriever.TARGETS_QUERY;
	}

}
