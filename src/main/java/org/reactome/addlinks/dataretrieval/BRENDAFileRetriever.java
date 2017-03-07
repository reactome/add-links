package org.reactome.addlinks.dataretrieval;

import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.rmi.RemoteException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;

import javax.xml.namespace.QName;
import javax.xml.rpc.ServiceException;

import org.apache.axis.client.Call;
import org.apache.axis.client.Service;

/**
 * BRENDA File Retriever requires a username and password to connect to the BRENDA SOAP service.
 * @author sshorser
 *
 */
public class BRENDAFileRetriever extends FileRetriever
{
	private String userName;
	private String password;
	private List<String> identifiers;
	private String speciesName;
	private int numThreads = 10;
	
	public BRENDAFileRetriever()
	{
		super();
	}
	
	public BRENDAFileRetriever(String retrieverName)
	{
		super(retrieverName);
	}
	
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
		
		public String callBrendaService(String endpoint, String operation, String wsArgs)
		{
			//String endpoint = "http://www.brenda-enzymes.org/soap/brenda_server.php";
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
				String parameters = this.userName + ","+hexString+","+wsArgs;
				call.setOperationName(new QName("http://soapinterop.org/", operation));
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
	
	public void setIdentifiers(List<String> identifiers)
	{
		this.identifiers = identifiers;
	}
	
	@Override
	protected void downloadData() throws Exception
	{
		AtomicInteger requestCounter = new AtomicInteger(0);
		// The number of identifiers that returned no mapping from BRENDA.
		AtomicInteger noMapping = new AtomicInteger(0);

		BRENDASoapClient client = new BRENDASoapClient(this.userName, this.password);
		StringBuffer sb = new StringBuffer();
		Files.createDirectories(Paths.get(this.destination).getParent());
		logger.info("{} identifiers for species {}", identifiers.size(), speciesName);
		List<Callable<Boolean>> jobs = Collections.synchronizedList(new ArrayList<Callable<Boolean>>());
		identifiers.stream().forEach(uniprotID ->
		{
			Callable<Boolean> job = new Callable<Boolean>()
			{
				@Override
				public Boolean call() throws Exception
				{
					// BRENDA won't work if there's an underscore in the species name.
					String s = speciesName.replace("_", " ");
					String result = client.callBrendaService(getDataURL().toString(), "getSequence", "organism*"+s+"#firstAccessionCode*"+uniprotID);
					
					if (result == null || result.trim().equals(""))
					{
						noMapping.incrementAndGet();
					}

					result = uniprotID + "\t" + result + "\n"; 

					sb.append(result);
					if (requestCounter.incrementAndGet() % 100 == 0)
					{
						logger.debug("{} requests sent to BRENDA, {} returned no mapping.", requestCounter.get(), noMapping.get());
					}
					if (requestCounter.get() >= identifiers.size())
					{
						logger.info("{} requests sent to BRENDA, {} returned no mapping.", requestCounter.get(), noMapping.get());
					}
					return true;
				}
			};
			jobs.add(job);
		});
		ForkJoinPool pool = new ForkJoinPool(numThreads);
		
		if (pool != null && jobs.size() > 0)
		{
			pool.invokeAll(jobs);
		}
		
		Files.write(Paths.get(this.destination), sb.toString().getBytes());
	
	}

	public void setUserName(String userName)
	{
		this.userName = userName;
	}

	public void setPassword(String password)
	{
		this.password = password;
	}

	public String getUserName()
	{
		return this.userName;
	}
	
	public String getPassword()
	{
		return this.password;
	}

	public String getSpeciesName()
	{
		return this.speciesName;
	}

	public void setSpeciesName(String speciesName)
	{
		this.speciesName = speciesName;
	}

	public String getFetchDestination()
	{
		return this.destination;
	}

	public int getNumThreads()
	{
		return numThreads;
	}

	public void setNumThreads(int numThreads)
	{
		this.numThreads = numThreads;
	}
}
;