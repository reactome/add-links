package org.reactome.addlinks.dataretrieval;

import java.io.FileWriter;
import java.io.IOException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class PharosLigandDataRetriever extends PharosDataRetriever
{
	// NOTE: It might be tempting to add "\n" at the end of the "lines" in this string below, but that will result in a "Bad request" response
	// from the GraphQL service. Clearly, queries cannot contain real line breaks.
	// we will create cross-references based on GtP identifiers.
	private static final String LIGANDS_QUERY = "{ \"query\": \"query {"
			+ "  ligands(filter: {facets: [{facet:\\\"Data Source\\\" values:[\\\"Guide to Pharmacology\\\"]}]}){"
			+ "    count"
			+ "    ligands(top:"+PharosDataRetriever.TOP_AMOUNT_TOKEN+" skip:"+PharosDataRetriever.SKIP_AMOUNT_TOKEN+"){"
			+ "      ligid"
			+ "      name"
			+ "      isdrug"
			+ "      synonyms {"
			+ "        name"
			+ "        value"
			+ "      }"
			+ "    }"
			+ "  }"
			+ "}\"}";

	@Override
	protected int processJSONArray(FileWriter fw, JSONObject jsonObj) throws IOException
	{
		try
		{
			JSONArray ligands = (((JSONObject)((JSONObject) jsonObj.get("data")).get("ligands")).getJSONArray("ligands"));
			for (int i = 0; i < ligands.length(); i++)
			{
				JSONObject ligand = ((JSONObject)ligands.get(i));
				Boolean isDrug = ligand.getBoolean("isdrug");
				// Check that the ligand has "isdrug==true" and then get the GtP synonym's value (synonym's "name" will be "Guide to Pharmacology").
				if (isDrug.booleanValue())
				{
					// Get the ligand ID used by Pharos
					String ligid = ligand.getString("ligid");
					String gtpIdentifier = null;
					// Now we process the synonyms...
					JSONArray synonyms = ligand.getJSONArray("synonyms");
					for (int j = 0; j < synonyms.length(); j++)
					{
						JSONObject synonym = (JSONObject)synonyms.get(j);
						if (synonym.getString("name").equals("Guide to Pharmacology"))
						{
							gtpIdentifier = synonym.getString("value");
						}
					}
					if (gtpIdentifier != null && ligid != null)
					{
						fw.write(gtpIdentifier + "\t" + ligid + "\n");
					}

				}
			}
			return ligands.length();
		}
		catch (JSONException e)
		{
			logger.error("Error processing JSON! JSON is: {}", jsonObj.toString());
			logger.error("Exception is: ", e);
			return 0;
		}
	}

	@Override
	protected String getQuery()
	{
		return PharosLigandDataRetriever.LIGANDS_QUERY;
	}
}
