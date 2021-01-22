package org.reactome.addlinks.dataretrieval;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class MultifileRetriever implements DataRetriever
{
	private List<FileRetriever> retrievers = new ArrayList<>();
	private String retrieverName;
	
	@Override
	public void fetchData() throws Exception
	{
		for (FileRetriever r : this.retrievers)
		{
			r.fetchData();
		}
	}

	public void setRetrievers(List<FileRetriever> retrievers)
	{
		this.retrievers = retrievers;
	}
	
	@Override
	public void setFetchDestination(String ... destinations)
	{
		for (int i = 0; i < destinations.length ; i++)
		{
			if (this.retrievers.get(i) != null)
			{
				this.retrievers.get(i).setFetchDestination(destinations[i]);
			}
			else
			{
				FileRetriever f = new FileRetriever(this.retrieverName + "_" + i);
				f.setFetchDestination(destinations[i]);
				this.retrievers.set(i, f);
			}
		}
	}
	
	@Override
	public void setDataURL(URI ... uris)
	{
		for (int i = 0; i < uris.length ; i++)
		{
			if (this.retrievers.get(i) != null)
			{
				this.retrievers.get(i).setDataURL(uris[i]);
			}
			else
			{
				FileRetriever f = new FileRetriever(this.retrieverName + "_" + i);
				f.setDataURL(uris[i]);
				this.retrievers.set(i, f);
			}
		}
	}

	@Override
	public void setMaxAge(Duration ... ages)
	{
		for (int i = 0; i < ages.length ; i++)
		{
			if (this.retrievers.get(i) != null)
			{
				this.retrievers.get(i).setMaxAge(ages[i]);
			}
			else
			{
				FileRetriever f = new FileRetriever(this.retrieverName + "_" + i);
				f.setMaxAge(ages[i]);
				this.retrievers.set(i, f);
			}
		}
	}
	
	@Override
	public void setRetrieverName(String retrieverName)
	{
		this.retrieverName = retrieverName;
	}

}
