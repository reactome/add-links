package org.reactome.addlinks.linkchecking;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.conn.HttpHostConnectException;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.gk.model.GKInstance;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.InvalidAttributeException;
import org.reactome.addlinks.CustomLoggable;


public class LinkCheckManager implements CustomLoggable
{

	private static Logger logger ;
	private MySQLAdaptor dbAdaptor;
	
	public LinkCheckManager()
	{
		if (LinkCheckManager.logger  == null)
		{
			LinkCheckManager.logger = this.createLogger("LinkCheckManager", "RollingRandomAccessFile", this.getClass().getName(), true, Level.DEBUG);
		}
	}
	
	public void setDbAdaptor(MySQLAdaptor adaptor)
	{
		this.dbAdaptor = adaptor;	
	}
	
	public Map<String, LinkCheckInfo> checkLinks(GKInstance refDBInst, List<GKInstance> instances, float proportionToCheck, int maxToCheck)
	{
		Map<String, LinkCheckInfo> linkCheckResults = Collections.synchronizedMap( new HashMap<String, LinkCheckInfo>(instances.size()) );
		
		int numberOfInstancesToCheck ;
		if (instances.size() < maxToCheck)
		{
			numberOfInstancesToCheck = instances.size();
		}
		else
		{
			numberOfInstancesToCheck = Math.min( Math.min( (int)(instances.size() * proportionToCheck) , instances.size()), maxToCheck);
		}
		
		Collections.shuffle(instances);
		List<GKInstance> instancesToCheck = instances.subList(0, numberOfInstancesToCheck);
		
		logger.info("Checking links for {}; requested proportion of links to check: {}; max allows links to check: {}; Total # of possible links to check: {}; {}*{}: {}; *actual* number of links to check: {}",
				refDBInst, proportionToCheck, maxToCheck, instances.size(), proportionToCheck , instances.size(), (int)(instances.size() * proportionToCheck), instancesToCheck.size());
		
		String refDBID = refDBInst.getDBID().toString();
		instancesToCheck.parallelStream().forEach( inst -> {
			try
			{
				String identifierString = (String) inst.getAttributeValue("identifier");
				//get the reference DB from the database (if it's not in local cache)
				String accessURL = ((String)refDBInst.getAttributeValue("accessUrl"));
				String referenceDatabaseName = refDBInst.getDisplayName();
				
				checkTheLink(linkCheckResults, refDBID, inst, identifierString, accessURL, referenceDatabaseName);
			}
			catch (URISyntaxException e)
			{
				e.printStackTrace();
			}
			catch (InterruptedException r)
			{
				r.printStackTrace();
			}
			catch (HttpHostConnectException e)
			{
				e.printStackTrace();
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
			catch (InvalidAttributeException e)
			{
				e.printStackTrace();
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		});
		return linkCheckResults;
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
				// special case for Zinc: adding these args will reduce the chance of a timeout.
				// Example: http://zinc15.docking.org/orthologs/CYH3_HUMAN/predictions/subsets/purchasable/?count=10&sort=no&distinct=no
				if (accessURL.contains("zinc15.docking.org"))
				{
					accessURL += "?count=1&sort=no&distinct=no";
				}
				String referenceDatabaseName = ((String)refDBCache.get(refDBID).getDisplayName());
				
				checkTheLink(linkCheckResults, refDBID, inst, identifierString, accessURL, referenceDatabaseName);
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
	
	private void checkTheLink(Map<String, LinkCheckInfo> linkCheckResults, String refDBID, GKInstance inst, String identifierString, String accessURL, String referenceDatabaseName)
			throws URISyntaxException, HttpHostConnectException, IOException, Exception, InterruptedException
	{
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
		info.setIdentifier(identifierString);
		info.setReferenceDatabaseDBID(refDBID);
		info.setIdentifierDBID(inst.getDBID().toString());
		info.setReferenceDatabaseName(referenceDatabaseName);
		linkCheckResults.put(inst.getDBID().toString(), info);
		// Sleep for 2 seconds between requests so that the server we're talking to doesn't think we're trying to DOS them.
		Thread.sleep(Duration.ofSeconds(2).toMillis());
	}
}
