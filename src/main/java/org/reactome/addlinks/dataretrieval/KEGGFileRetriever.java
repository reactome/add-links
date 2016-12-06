package org.reactome.addlinks.dataretrieval;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Retrieves data from http://rest.kegg.jp/get/hsa:$kegg_gene_id
 * @author sshorser
 *
 */
public class KEGGFileRetriever extends FileRetriever
{
	private static final Logger logger = LogManager.getLogger();

	
	@Override
	protected void downloadData() throws Exception {
		
	}
}
