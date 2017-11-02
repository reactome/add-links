package org.reactome.addlinks.dataretrieval.brenda;

import java.net.MalformedURLException;
import java.rmi.RemoteException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

import javax.xml.namespace.QName;
import javax.xml.rpc.ServiceException;

import org.apache.axis.client.Call;
import org.apache.axis.client.Service;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.reactome.addlinks.CustomLoggable;

public class BRENDASoapClient implements CustomLoggable
{
	protected Logger logger;
	private String userName;
	private String password;
	private Service service = new Service(); 
	
	public BRENDASoapClient(String u, String p)
	{
		this.logger = this.createLogger("retrievers/BrendaSoapClient", "RollingRandomAccessFile", this.getClass().getName(), true, Level.DEBUG, this.logger, "Data Retriever");
		this.userName = u;
		this.password = p;
	}
	
	public String callBrendaService(String endpoint, String operation, String wsArgs) throws MalformedURLException, ServiceException, NoSuchAlgorithmException, RemoteException, Exception
	{
		boolean done = false;
		String resultString = null;
		int attempts = 0;
		int maxReAttempts = 3;
		while (!done)
		{
			logger.trace("Calling service operation {} @ {} with arguments: ",operation, endpoint, wsArgs);
			//String endpoint = "http://www.brenda-enzymes.org/soap/brenda_server.php";
			String password = this.password;
			attempts ++;
			try
			{
				Call call = (Call) this.service.createCall();
				MessageDigest md = MessageDigest.getInstance("SHA-256");
				md.update(password.getBytes());
				byte byteData[] = md.digest();
				StringBuffer hexString = new StringBuffer();
				for (int i = 0; i < byteData.length; i++)
				{
					String hex = Integer.toHexString(0xff & byteData[i]);
					if(hex.length()==1) hexString.append('0');
					{
						hexString.append(hex);
					}
				}
				call.setTargetEndpointAddress( new java.net.URL(endpoint) );
				String parameters = this.userName + ","+hexString+","+wsArgs;
				call.setOperationName(new QName("http://soapinterop.org/", operation));
				call.setTimeout( 30 * 1000 );
				resultString = (String) call.invoke( new Object[] {parameters} );
				done = true;
				
			}
			catch (MalformedURLException e)
			{
				logger.error("Bad URL! URL: {} Message: {}", endpoint, e.getMessage());
				throw e;
			}
			catch (ServiceException e)
			{
				logger.error("Could not create Service Call: {}", e.getMessage());
				throw e;
			}
			catch (NoSuchAlgorithmException e)
			{
				logger.error("Could not generate Digest: {}", e.getMessage());
				throw e;
			}
			catch (RemoteException e)
			{
				if (attempts > maxReAttempts)
				{
					logger.error("Error occurred while making webservice call: {}", e.getMessage());
					done = true;
					e.printStackTrace();
					throw e;
				}
				else
				{
					
					//sleep for up to 5 seconds, just to give the server some relief.
					Random r = new Random();
					long sleepAmt = r.nextInt(5000) + 150;
					logger.info("Caught a remote exception, retry after {} ms. {} attempts made so far for this thread...", sleepAmt , attempts);
					Thread.sleep( sleepAmt );
				}
			}
			catch (Exception e)
			{
				throw new Exception(e);
			}
		}
		return resultString;
	}
}
