package org.reactome.addlinks.dataretrieval.pharos;

import java.io.FileWriter;
import java.io.IOException;

import org.json.JSONArray;
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
	 */
	protected int processJSONArray(FileWriter writer, JSONObject jsonObj) throws IOException
	{
		JSONArray targets = (((JSONObject)((JSONObject) jsonObj.get("data")).get("targets")).getJSONArray("targets"));
		for (int i = 0; i < targets.length(); i++)
		{
			if (((JSONObject)targets.get(i)).has("uniprot"))
			{
				writer.write(((JSONObject)targets.get(i)).get("uniprot") + "\n");
			}
			else
			{
				logger.error("The \"uniprot\" key was not present, but it should be (in fact, it should be the ONLY key at this depth in the document). Check your query!");
				// Something's wrong with the data, so gracefully exit the loop.
				i = targets.length() + 1;
			}
		}
		return targets.length();
	}


	@Override
	protected String getQuery()
	{
		return PharosTargetsDataRetriever.TARGETS_QUERY;
	}

}
