package org.reactome.addlinks.dataretrieval;

import java.net.MalformedURLException;
import java.rmi.RemoteException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.xml.namespace.QName;
import javax.xml.rpc.ServiceException;

import org.apache.axis.client.Call;
import org.apache.axis.client.Service;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
;

/**
 * BRENDA File Retriever requires a username and password to connect to the BRENDA SOAP service.
 * @author sshorser
 *
 */
public class BRENDAFileRetriever extends FileRetriever
{
	private static final Logger logger = LogManager.getLogger();
	private String userName;
	private String password;
	
	
	public class BRENDASoapClient
	{
		private String userName;
		private String password;
		private Service service = new Service(); 
		
		public BRENDASoapClient(String u, String p)
		{
			this.userName = u;
			this.password = p;
		}
		
		public String callBrendaService()
		{
			String endpoint = "http://www.brenda-enzymes.org/soap/brenda_server.php";
			String password = this.password;

			String resultString = null;
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
				String parameters = this.userName + ","+hexString+",ecNumber*1.1.1.1#organism*Mus musculus";
				call.setOperationName(new QName("http://soapinterop.org/", "getEcNumbersFromSynonyms"));
				resultString = (String) call.invoke( new Object[] {parameters} );
				
				return resultString;
			}
			catch (MalformedURLException e)
			{
				logger.error("Bad URL! URL: {} Message: {}", endpoint, e.getMessage());
				throw new Error(e);
			}
			catch (ServiceException e)
			{
				logger.error("Could not create Service Call: {}", e.getMessage());
				throw new Error(e);
			}
			catch (NoSuchAlgorithmException e)
			{
				logger.error("Could not generate Digest: {}", e.getMessage());
				throw new Error(e);
			}
			catch (RemoteException e)
			{
				logger.error("Error occurred while making webservice call: {}", e.getMessage());
				throw new Error(e);
			}
		}
	}
	
	@Override
	protected void downloadData() throws Exception
	{
		BRENDASoapClient client = new BRENDASoapClient(this.userName, this.password);
		String result = client.callBrendaService();
		System.out.println(result);
	}

	public void setUserName(String userName)
	{
		this.userName = userName;
	}

	public void setPassword(String password)
	{
		this.password = password;
	}
}
