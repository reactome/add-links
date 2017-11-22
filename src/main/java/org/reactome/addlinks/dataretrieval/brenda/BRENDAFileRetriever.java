package org.reactome.addlinks.dataretrieval.brenda;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.reactome.addlinks.dataretrieval.FileRetriever;

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
	private int numThreads = 4;
	
	public BRENDAFileRetriever()
	{
		super();
	}
	
	public BRENDAFileRetriever(String retrieverName)
	{
		super(retrieverName);
	}
	
	public void setIdentifiers(List<String> identifiers)
	{
		this.identifiers = identifiers;
	}
	
	@Override
	protected void downloadData() throws Exception
	{
		AtomicInteger requestCounter = new AtomicInteger(0);
		//AtomicInteger sleepAmount = new AtomicInteger(0);
		// The number of identifiers that returned no mapping from BRENDA.
		AtomicInteger noMapping = new AtomicInteger(0);

		BRENDASoapClient client = new BRENDASoapClient(this.userName, this.password);
		StringBuffer sb = new StringBuffer();
		Files.createDirectories(Paths.get(this.destination).getParent());
		logger.info("{} identifiers for species {}", identifiers.size(), speciesName);
		List<Callable<Boolean>> jobs = Collections.synchronizedList(new ArrayList<Callable<Boolean>>());
		long startTime = System.currentTimeMillis();
		identifiers.stream().forEach(uniprotID ->
		{
			Callable<Boolean> job = new Callable<Boolean>()
			{
				@Override
				public Boolean call() throws Exception
				{
					// BRENDA won't work if there's an underscore in the species name.
					String s = speciesName.replace("_", " ");
					String result = null;
					try
					{
						int sleepMillis = (requestCounter.incrementAndGet() % numThreads) * 112; // 125 is best factor so far... 120 might be *slightly* better... 112 seems best so far, faster than 120 but does not trigger re-requests.
						//logger.trace("Sleeping {} millis", sleepMillis);
						Thread.sleep(sleepMillis);
						result = client.callBrendaService(getDataURL().toString(), "getSequence", "organism*"+s+"#firstAccessionCode*"+uniprotID);
					}
					catch (Exception e)
					{
						//e.printStackTrace();
						logger.error("Error was caught when trying to call the BRENDA service! Uniprot ID: {}; Species: {}; Error message: {}", uniprotID, speciesName, e.getMessage());
						throw new Error(e);
					}
					if (result == null || result.trim().equals(""))
					{
						noMapping.incrementAndGet();
					}

					result = uniprotID + "\t" + result + "\n"; 

					sb.append(result);
					if (requestCounter.get() % 1000 == 0 || requestCounter.get() >= identifiers.size())
					{
						long currentTime = System.currentTimeMillis();
						long elapsed = currentTime - startTime;
						logger.debug("{} requests sent to BRENDA, {} returned no mapping. {} seconds for {} requests, {} per second.", requestCounter.get(), noMapping.get(), TimeUnit.MILLISECONDS.toSeconds(elapsed), requestCounter.get(), (double)requestCounter.get() / (double)TimeUnit.MILLISECONDS.toSeconds(elapsed));
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