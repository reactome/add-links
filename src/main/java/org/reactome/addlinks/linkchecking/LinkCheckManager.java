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
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.InvalidAttributeException;
import org.reactome.addlinks.CustomLoggable;
import org.springframework.beans.support.ArgumentConvertingMethodInvoker;


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
	
	/**
	 * Takes in a list of instances that have Identifier attributes and checks them as links.
	 * @param refDBInst - This accessURL of refDBInst will be used to generate the links that will be checked.
	 * @param instances - The instances.
	 * @param proportionToCheck - The proportion to check. 1.0 means ALL links could be checked.
	 * @param maxToCheck - The maximum actual number to check. If the list has 100000 elements, and proportionToCheck is 0.75, that means 75000 could be checked.
	 * If you set maxToCheck to 100, that overrides the number calculated by proportionToCheck and only 100 will be checked.
	 * @return A map. The key is the identifier, the value is a LinkCheckInfo object, {@link org.reactome.addlinks.linkchecking.LinkCheckInfo}
	 * @throws IllegalArgumentException This exception is thrown if proportionToCheck or maxToCheck are less than zero.
	 */
	public Map<String, LinkCheckInfo> checkLinks(GKInstance refDBInst, List<GKInstance> instances, float proportionToCheck, int maxToCheck) throws IllegalArgumentException
	{
		if (proportionToCheck < 0)
		{
			throw new IllegalArgumentException("\"proportionToCheck\" cannot be negative.");
		}
		
		if (maxToCheck < 0)
		{
			throw new IllegalArgumentException("\"maxToCheck\" cannot be negative.");
		}
		
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
				String identifierString = (String) inst.getAttributeValue(ReactomeJavaConstants.identifier);
				//get the reference DB from the database (if it's not in local cache)
				String accessURL = ((String)refDBInst.getAttributeValue(ReactomeJavaConstants.accessUrl));
				accessURL = LinkCheckManager.tweakZincURL(accessURL);
				String referenceDatabaseName = refDBInst.getDisplayName();
				
				LinkCheckManager.checkTheLink(linkCheckResults, refDBID, inst, identifierString, accessURL, referenceDatabaseName);
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
	 * Tweaks the Zinc URL to perform better.
	 * @param accessURL
	 * @return
	 */
	private static String tweakZincURL(String accessURL)
	{
		String newURL = accessURL;
		if (accessURL.contains("zinc15.docking.org"))
		{
			newURL += "?count=1&sort=no&distinct=no";
		}
		return newURL;
	}
	
	/**
	 * Takes in a (possibly) heterogeneous list of instances with Identifier attributes and then gets links out of them and then checks them.
	 * The instances do NOT need to be associated with the same Reference Database.
	 * @param linksData - A list of instances. ALL of them will be checked.
	 * @return A map. The key is the identifier, the value is a LinkCheckInfo object, {@link org.reactome.addlinks.linkchecking.LinkCheckInfo}
	 */
	public Map<String, LinkCheckInfo> checkLinks(List<GKInstance> instances)
	{
		Map<String, GKInstance> refDBCache = new HashMap<>();
		
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
				accessURL = LinkCheckManager.tweakZincURL(accessURL);
				String referenceDatabaseName = ((String)refDBCache.get(refDBID).getDisplayName());
				
				LinkCheckManager.checkTheLink(linkCheckResults, refDBID, inst, identifierString, accessURL, referenceDatabaseName);
			}
			catch (Exception e)
			{
				e.printStackTrace();
				throw new Error(e);
			}
			
		});
		return linkCheckResults;
	}
	
	private static void checkTheLink(Map<String, LinkCheckInfo> linkCheckResults, String refDBID, GKInstance inst, String identifierString, String accessURL, String referenceDatabaseName)
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
