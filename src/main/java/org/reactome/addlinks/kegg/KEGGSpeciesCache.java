package org.reactome.addlinks.kegg;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class KEGGSpeciesCache
{
	private static final String COMMON_NAME = "CommonName";
	private static final String KEGG_CODE = "KEGGCode";
	private static final Logger logger = LogManager.getLogger();
	private static String speciesURL = "http://www.genome.jp/kegg-bin/download_htext?htext=br08601.keg&format=htext&filedir=";
	// The map of KEGG codes will be keyed of the proper (Latin) species name, as they are found in KEGG.
	private static Map<String, List<Map<String,String>>> speciesMap = new HashMap<>();
	private static Map<String, String> codesToSpecies = new HashMap<>();
	private static Set<String> allCodes;
	/**
	 * Private constructor to prevent instantiation.
	 */
	private KEGGSpeciesCache()
	{

	}

	/**
	 * Static initializer will query KEGG and populate the caches the first time
	 * this class is referenced.
	 */
	static
	{
		try
		{
			URI uri = new URI(KEGGSpeciesCache.speciesURL);
			HttpGet get = new HttpGet(uri );
			//Need to multiply by 1000 because timeouts are in milliseconds.
			//RequestConfig config = RequestConfig.copy(RequestConfig.DEFAULT).build();
			try( CloseableHttpClient client = HttpClients.createDefault();
				CloseableHttpResponse response = client.execute(get) )
			{
				logger.info("Response: {}",response.getStatusLine());
				String s = EntityUtils.toString(response.getEntity());

				// Process each line with the following regexp:
				//     ^[A-Z]\W*([a-z]{3,4})\W*([a-zA-Z0-9 .=#+,\[\]\/:\-_']*)\W*(\(([a-zA-Z0-9 .=#+,\[\]\/:\-_']*).*\).*)?$
				// Pattern tested here: http://regexr.com/3emij
				// Explained:
				//   first capture group is for the species code.
				//   second capture group is for the formal name.
				//   third and fourth deal with "common name" (in brackets).
				// ...and yes, the formal name and common name *could* contain weird puctuation marks as well as numerals.
				Pattern p = Pattern.compile("^[A-Z]\\W*([a-z]{3,4})\\W*([a-zA-Z 0-9 .=#+,\\[\\]\\/\\:\\-_']*)\\W*(\\(([a-zA-Z 0-9 .=#+,\\[\\]\\/\\:\\-_']*).*\\).*)?$");
				for(String line : s.split("\n"))
				{
					Matcher m = p.matcher(line);
					if (m.matches())
					{
						String code = m.group(1).trim();
						String name = m.group(2).trim();
						String commonName = "";
						// Common names in this file are a little hard to determine/extract.
						// ...and sometimes the KEGG file doesn't have them.
						if (m.group(3) != null)
						{
							commonName = m.group(3).replace("(", "").replace(")","").trim();
						}

						Map<String,String> map = new HashMap<>(2);
						map.put(KEGG_CODE, code);
						map.put(COMMON_NAME, commonName);
						// Check that the name isn't already in the map. Yes, the KEGG species file *does* contain duplicated names with different codes.
						if (speciesMap.containsKey(name))
						{
							speciesMap.get(name).add(map);
						}
						else
						{
							List<Map<String,String>> list = new ArrayList<>();
							list.add(map);
							speciesMap.put(name, list);
						}
						codesToSpecies.put(code, name);
					}
					else
					{
						// The line does not match the pattern needed to extract a species name + KEGG code.
						// This is not actually that serious, as the file contains many lines that are HTML, XML
						// or summary/group headings.
						logger.trace("Line/pattern mismatch: {}",line);
					}
				}
				// Add vg for Virus, and ag for Addendum - they are not in the KEGG species list, but are still valid prefixes.
				codesToSpecies.put("vg", "Virus");
				codesToSpecies.put("ag", "Addendum");
				Map<String, String> virusMap = new HashMap<>();
				virusMap.put(KEGG_CODE, "vg");
				virusMap.put(COMMON_NAME, "");
				speciesMap.put("Virus", Arrays.asList(virusMap));
				Map<String, String> addendumMap = new HashMap<>();
				addendumMap.put(KEGG_CODE, "ag");
				addendumMap.put(COMMON_NAME, "");
				speciesMap.put("Addendum", Arrays.asList(addendumMap));
				logger.info("{} keys added to the KEGG species map.", KEGGSpeciesCache.speciesMap.keySet().size());
			}
		}
		catch (URISyntaxException | IOException e)
		{
			e.printStackTrace();
			throw new Error(e);
		}
	}

	/**
	 * Get the KEGG codes for a species name.
	 * @param name - The name of the species.
	 * @return The KEGG code for that species.
	 */
	public static List<String> getKEGGCodes(String name)
	{
		List<Map<String, String>> list = KEGGSpeciesCache.speciesMap.get(name);
		List<String> codes = null;
		if (list != null)
		{
			codes = list.stream().map( m -> m.get(KEGG_CODE)).collect(Collectors.toList());
		}
		return codes;
	}

	/**
	 * Get the KEGG common names for a species. NOTE: Not all species have a common name in the KEGG database, so this may return null
	 * even if it is a valid organism in the KEGG listing.
	 * @param name - the name of the organism to look up.
	 * @return The common name (according to KEGG), if there is one.
	 */
	public static List<String> getKEGGCommonNames(String name)
	{
		List<Map<String,String>> list = KEGGSpeciesCache.speciesMap.get(name);
		List<String> commonNames = null;
		if (list != null)
		{
			commonNames = list.stream().map( m -> m.get(COMMON_NAME)).collect(Collectors.toList());
		}
		return commonNames;
	}

	/**
	 * Returns the set of KEGG species codes.
	 * @return
	 */
	public static Set<String> getKeggSpeciesCodes()
	{
		// save the codes in a separate variable so that subsquent calls to this function are faster.
		if (KEGGSpeciesCache.allCodes == null)
		{
			KEGGSpeciesCache.allCodes =  KEGGSpeciesCache.speciesMap.values().stream().flatMap( v -> v.stream().map( i -> i.get(KEGG_CODE)).collect(Collectors.toList()).stream() ).collect(Collectors.toSet()) ;
		}
		return KEGGSpeciesCache.allCodes;
	}

	/**
	 * Returns the set of KEGG species names (not "common" names).
	 * @return
	 */
	public static Set<String> getKeggSpeciesNames()
	{
		return speciesMap.keySet();
	}

	/**
	 * If <code>identifier</code> begins with a known KEGG species code, this function will return that prefix.
	 * @param identifier An Identifier.
	 * @return The prefix, if the identifier begins with a prefix.
	 */
	public static String extractKEGGSpeciesCode(String identifier)
	{
		if (identifier != null && identifier.contains(":"))
		{
			String[] parts = identifier.split(":");
			// Species code prefix will be the left-most part, if you split on ":".
			// There could be *other* parts (such as "si" in "dre:si:ch73-368j24.13"), but the species code is what matters here.
			String prefix = parts[0];
			// RETURN the species code, IF it's in the list of known KEGG species codes.
			if (KEGGSpeciesCache.getKeggSpeciesCodes().contains(prefix))
			{
				return prefix;
			}
			else
			{
				logger.warn("Could not extract a KEGG species prefix from identifier: \"{}\". Maybe check the KEGG species list?", identifier);
			}
		}
		return null;
	}

	/**
	 * Strips out the species code prefix from a KEGG identifier string.
	 * @param identifier - the identifier string to prune
	 * @return The identifier, minus any species code prefix that might have been there.
	 */
	public static String pruneKEGGSpeciesCode(String identifier)
	{
		String prunedIdentifier = identifier;
		String prefix = KEGGSpeciesCache.extractKEGGSpeciesCode(identifier);
		if (prefix != null)
		{
			prunedIdentifier = identifier.replaceFirst(prefix + ":", "");
		}
		return prunedIdentifier;
	}

	/**
	 * Returns the KEGG name of a species, give the code.
	 * @param abbreviation
	 * @return
	 */
	public static String getSpeciesName(String abbreviation)
	{
		return codesToSpecies.get(abbreviation);
	}

}

