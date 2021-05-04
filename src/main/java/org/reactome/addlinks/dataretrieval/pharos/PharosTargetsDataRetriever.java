package org.reactome.addlinks.dataretrieval;

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


	/**
	 * Implements the logic to process JSON from Pharos.
	 */
	protected int processJSONArray(FileWriter fw, JSONObject jsonObj) throws IOException
	{
		JSONArray targets = (((JSONObject)((JSONObject) jsonObj.get("data")).get("targets")).getJSONArray("targets"));
		for (int i = 0; i < targets.length(); i++)
		{
			fw.write(((JSONObject)targets.get(i)).get("uniprot") + "\n");
		}
		return targets.length();
	}


	@Override
	protected String getQuery()
	{
		return PharosTargetsDataRetriever.TARGETS_QUERY;
	}

}
