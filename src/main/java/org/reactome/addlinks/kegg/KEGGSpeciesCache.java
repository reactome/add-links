package org.reactome.addlinks.kegg;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
	private static Map<String,Map<String,String>> speciesMap = new HashMap<String,Map<String,String>>();
	
	/**
	 * This mapping was derived from the Config_Species.pm module in the Release code, relative path: Release/modules/GKB/Config_Species.pm
	 * @author sshorser
	 *
	 */
	/*
	enum speciesMapping
	{
		//If you want more, you can use this regexp:
		// ^[A-Z]\W*([a-z]{3,4})\W*([a-zA-Z0-9 .\[\]\/\:-_]*)\W*([a-zA-Z0-9 .\[\]\/\:-_-]*)
		// to parse this file:
		// http://www.genome.jp/kegg-bin/download_htext?htext=br08601.keg&format=htext&filedir=
		ath("Arabidopsis thaliana"),
		osa("Oryza sativa"),
		cel("Caenorhabditis elegans"),
		ddi("Dictyostelium discoideum"),
		dme("Drosophila melanogaster"),
		eco("Escherichia coli"),
		hsa("Homo sapiens"),
		mja("Methanococcus jannaschii"),
		mmu("Mus musculus"),
		pfa("Plasmodium falciparum"),
		sce("Saccharomyces cerevisiae"),
		spo("Schizosaccharomyces pombe"),
		sso("Sulfolobus solfataricus"),
		tni("Tetraodon nigroviridis"),
		gga("Gallus gallus"),
		rno("Rattus norvegicus"),
		cne("Cryptococcus neoformans"),
		ncr("Neurospora crassa"),
		syn("Synechococcus sp."),
		mtu("Mycobacterium tuberculosis"),
		ehi("Entamoeba histolytica"),
		cme("Cyanidioschyzon merolae"),
		tps("Thalassiosira pseudonana"),
		;
		private String fullName;
		speciesMapping(String s)
		{
			this.fullName = s;
		}
		
		public String getFullName()
		{
			return this.fullName;
		}
	}
	*/
	
	/**
	 * Private constructor to prevent instantiation.
	 */
	private KEGGSpeciesCache()
	{
		
	}
	
	public static void generateSpeciesMapping() throws URISyntaxException, IOException
	{
		// TODO: This probably shouldn't be a public method. This could should only ever run once
		// in the life of the program, so probably better to move it to  a static initializer
		// (or something like that).
		
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
					
					Map<String,String> map = new HashMap<String,String>(2);
					map.put(KEGG_CODE, code);
					map.put(COMMON_NAME, commonName);
					speciesMap.put(name, map);
				}
				else
				{
					logger.debug("Line/pattern mismatch: {}",line);
				}
				
			}
			logger.info("{} keys added to the KEGG species map.",KEGGSpeciesCache.speciesMap.keySet().size());
		}
	}
	
	/**
	 * Get the KEGG code for a species name.
	 * @param name - The name of the species. 
	 * @return The KEGG code for that species.
	 */
	public static String getKEGGCode(String name)
	{
		Map<String,String> m = KEGGSpeciesCache.speciesMap.get(name);
		String code = null;
		if (m != null)
		{
			code = m.get(KEGG_CODE); 
		}
		return code;
	}
	
	/**
	 * Get the KEGG common name for a species. NOTE: Not all species have a common name in the KEGG database, so this may return null
	 * even if it is a valid organism in the KEGG listing.
	 * @param name - the name of the organism to look up.
	 * @return The common name (according to KEGG), if there is one.
	 */
	public static String getKEGGCommonName(String name)
	{
		Map<String,String> m = KEGGSpeciesCache.speciesMap.get(name);
		String commonName = null;
		if (m != null)
		{
			commonName = m.get(COMMON_NAME); 
		}
		return commonName;
	}
}

