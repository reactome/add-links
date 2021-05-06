package org.reactome.addlinks.dataretrieval.pharos;

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

	// Data from Pharos should look like this:
	//{
	//  "data": {
	//    "ligands": {
	//      "count": 5136,
	//      "ligands": [
	//        {
	//          "ligid": "9XY1117CMPQQ",
	//          "name": "acetazolamide",
	//          "isdrug": true,
	//          "synonyms": [
	//            {
	//              "name": "unii",
	//              "value": "O3FX965V0I"
	//            },
	//            {
	//              "name": "PubChem",
	//              "value": "1986"
	//            },
	//            {
	//              "name": "Guide to Pharmacology",
	//              "value": "6792"
	//            },
	//            {
	//              "name": "ChEMBL",
	//              "value": "CHEMBL20"
	//            },
	//            {
	//              "name": "DrugCentral",
	//              "value": "56"
	//            },
	//            {
	//              "name": "pt",
	//              "value": "ACETAZOLAMIDE"
	//            },
	//            {
	//              "name": "LyCHI",
	//              "value": "9XY1117CMPQQ"
	//            }
	//          ]
	//        },
	//        {
	//          "ligid": "4UXS9UGRZQTR",
	//          "name": "staurosporin",
	//          "isdrug": false,
	//          "synonyms": [
	//            {
	//              "name": "PubChem",
	//              "value": "44259"
	//            },
	//            {
	//              "name": "Guide to Pharmacology",
	//              "value": "346"
	//            },
	//            {
	//              "name": "ChEMBL",
	//              "value": "CHEMBL388978"
	//            },
	//            {
	//              "name": "LyCHI",
	//              "value": "4UXS9UGRZQTR"
	//            }
	//          ]
	//        },
	//        ...

	@Override
	protected int processJSONArray(FileWriter writer, JSONObject jsonObj) throws IOException
	{
		int i = 0;
		try
		{
			JSONArray ligands = (((JSONObject)((JSONObject) jsonObj.get("data")).get("ligands")).getJSONArray("ligands"));
			for (i = 0; i < ligands.length(); i++)
			{
				JSONObject ligand = ((JSONObject)ligands.get(i));
				boolean isDrug = ligand.getBoolean("isdrug");
				// Check that the ligand has "isdrug==true" and then get the GtP synonym's value (synonym's "name" will be "Guide to Pharmacology").
				if (isDrug)
				{
					// Get the ligand ID used by Pharos
					String ligid = ligand.getString("ligid");
					String gtpIdentifier = null;
					// Now we process the synonyms...
					JSONArray synonyms = ligand.getJSONArray("synonyms");
					boolean done = false;
					int j = 0;
					while (!done)
					{
						JSONObject synonym = (JSONObject)synonyms.get(j);
						if (synonym.getString("name").equals("Guide to Pharmacology"))
						{
							gtpIdentifier = synonym.getString("value");
							done = true;
						}
						j++;
						done = j >= synonyms.length();
					}
					if (gtpIdentifier != null && ligid != null)
					{
						writer.write(gtpIdentifier + "\t" + ligid + "\n");
					}

				}
			}
			return ligands.length();
		}
		catch (JSONException e)
		{
			logger.error("Error processing JSON! JSON source is: {}", jsonObj.toString());
			logger.error("Exception is: ", e);
			// Still need to return something so return the number of items examined.
			// But if a JSONException occurs, this will very likely be 0.
			return i;
		}
	}

	@Override
	protected String getQuery()
	{
		return PharosLigandDataRetriever.LIGANDS_QUERY;
	}
}
