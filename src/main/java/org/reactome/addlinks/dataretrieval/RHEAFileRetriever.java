package org.reactome.addlinks.dataretrieval;

import java.net.URI;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
/**
 * @deprecated - There is a Rhea2Reactome file that Rhea provides: ftp://ftp.ebi.ac.uk/pub/databases/rhea/tsv/ 
 * and since it seems OK to use the current process of linking to Rhea what they linked to us, a simple FileRetriever pointed at this URL
 * is probably OK.
 * @author sshorser
 *
 */
public class RHEAFileRetriever extends FileRetriever
{
	private static final Logger logger = LogManager.getLogger();
	private List<String> chebiList;
	
	public void setChEBIList(List<String> chebiList)
	{
		this.chebiList = chebiList;
	}

	/**
	 * For RHEA file downloads, we need an input list of ChEBI IDs. 
	 * We will then query RHEA (such as: http://www.ebi.ac.uk/rhea/rest/1.0/ws/reaction?q=chebi:57444) for each ChEBI and save the results.
	 */
	@Override
	protected void downloadData() throws Exception
	{
		String fetchDestBase = this.destination;
		URI baseURI = this.uri;
		logger.debug("{} ChEBI IDs will be looked up in RHEA.", this.chebiList.size());
		AtomicInteger fileDownloadCounter = new AtomicInteger(0);
		chebiList.parallelStream().forEach(chebiID -> 
		{
			URI uri;
			try
			{
				uri = new URI(baseURI.toString() + chebiID );
				this.setDataURL(uri);
				this.setFetchDestination(fetchDestBase + "/ChEBI_" + chebiID + "_From_Rhea.xml");
				super.downloadData();
				int currentCount = fileDownloadCounter.incrementAndGet();
				
				if (currentCount % 100 == 0)
				{
					logger.debug("{} RHEA files downloaded.", currentCount);
				}
			}
			catch (Exception e)
			{
				e.printStackTrace();
				throw new Error(e);
			}
		});
	}
}
