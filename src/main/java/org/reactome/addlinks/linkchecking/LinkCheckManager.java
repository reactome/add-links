package org.reactome.addlinks.linkchecking;

import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.model.GKInstance;
import org.gk.persistence.MySQLAdaptor;


public class LinkCheckManager
{

	private static final Logger logger = LogManager.getLogger();
	private MySQLAdaptor dbAdaptor;
	
	public void setDbAdaptor(MySQLAdaptor adaptor)
	{
		this.dbAdaptor = adaptor;	
	}
	
	/**
	 * Takes in a list of Identifier instances and then gets links out of them and then checks them.
	 * @param linksData
	 */
	public Map<String, LinkCheckInfo> checkLinks(List<GKInstance> instances)
	{
		Map<String,GKInstance> refDBCache = new HashMap<String,GKInstance>();
		
		// This map stores results, keyed by DB_ID of the objects.
		Map<String, LinkCheckInfo> linkCheckResults = Collections.synchronizedMap( new HashMap<String, LinkCheckInfo>(instances.size()) );
		
		//for (GKInstance inst : instances)
		instances.parallelStream().forEach( inst -> 
		{
			// checking a link is more than just a true/false - the status code needs
			// to be taken into account too. And we could track other things such as number of 
			// retries, and time to get the response.
			// Maybe we should pass a call-back function to the link-checker 
			// to be called when the link-checker has gotten a response.
			
			try
			{
				String identifierString = (String) inst.getAttributeValue("identifier");
				
				String refDBID =  ((GKInstance) inst.getAttributeValue("referenceDatabase")).getDBID().toString();
				if (!refDBCache.containsKey(refDBID))
				{
					GKInstance refDBInstance = this.dbAdaptor.fetchInstance(Long.valueOf(refDBID));
					refDBCache.put(refDBID, refDBInstance);
				}
				logger.debug(refDBCache.get(refDBID));
				//get the reference DB from the database (if it's not in local cache)
				String accessURL = ((String)refDBCache.get(refDBID).getAttributeValue("accessUrl"));
				String referenceDatabaseName = ((String)refDBCache.get(refDBID).getDisplayName());
				URI uri = new URI(  accessURL.replace("###ID###", identifierString) );
				LinkChecker checker = new LinkChecker(uri, identifierString);
			
				LinkCheckInfo info = checker.checkLink();
				if (!(info.isKeywordFound() && info.getStatusCode() == 200))
				{
					LinkCheckManager.logger.warn("Link {} produced status code: {} ; keyword {} was not found.",uri.toString(), info.getStatusCode(), identifierString );
				}
				else
				{
					LinkCheckManager.logger.debug("Link {} produced status code: {} ; keyword {} was found.",uri.toString(), info.getStatusCode(), identifierString );
				}
				info.setReferenceDatabaseDBID(refDBID);
				info.setIdentifierDBID(inst.getDBID().toString());
				info.setReferenceDatabaseName(referenceDatabaseName);
				linkCheckResults.put(inst.getDBID().toString(), info);
				// Sleep for 2 seconds so that the server we're talking to doesn't think we're trying to DOS them.
				Thread.sleep(Duration.ofSeconds(2).toMillis());
			}
			catch (Exception e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
				throw new Error(e);
			}
			
		});
		return linkCheckResults;
	}
}
