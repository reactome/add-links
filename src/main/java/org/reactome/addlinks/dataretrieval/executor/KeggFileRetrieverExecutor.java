package org.reactome.addlinks.dataretrieval.executor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.reactome.addlinks.dataretrieval.KEGGFileRetriever;
import org.reactome.addlinks.dataretrieval.UniprotFileRetriever;
import org.reactome.addlinks.db.ReferenceObjectCache;
import org.reactome.addlinks.kegg.KEGGSpeciesCache;
import org.reactome.release.common.dataretrieval.FileRetriever;

public class KeggFileRetrieverExecutor extends AbstractFileRetrieverExecutor
{
	private ReferenceObjectCache objectCache;
	private Map<String, UniprotFileRetriever> uniprotFileRetrievers;

	public KeggFileRetrieverExecutor(Map<String, ? extends FileRetriever> retrievers, Map<String, UniprotFileRetriever> uniprotRetrievers, List<String> retrieverFilter, ReferenceObjectCache cache)
	{
		super(retrievers, retrieverFilter);
		this.objectCache = cache;
		this.uniprotFileRetrievers = uniprotRetrievers;
	}

	@Override
	public Boolean call() throws Exception
	{
		if (this.fileRetrieverFilter.contains("KEGGRetriever"))
		{
			logger.info("Executing KEGG retriever");
			UniprotFileRetriever uniprotToKeggRetriever = this.uniprotFileRetrievers.get("UniProtToKEGG");
			// Get the KEGG retriever that is defined in the Spring config.
			KEGGFileRetriever keggFileRetriever = (KEGGFileRetriever) this.fileRetrievers.get("KEGGRetriever");

			// Now we need to loop through the species.
			String downloadDestination = keggFileRetriever.getFetchDestination();

			List<Callable<Boolean>> keggJobs = new ArrayList<Callable<Boolean>>();

			// Loop over the species that are valid for KEGG...
			for (String speciesName : objectCache.getSetOfSpeciesNames().stream().sequential()
												.filter(speciesName -> KEGGSpeciesCache.getKEGGCodes(speciesName)!=null)
												.collect(Collectors.toList()))
			{
				String speciesCode = objectCache.getSpeciesNamesToIds().get(speciesName).get(0);
				logger.debug("Species Name: {} Species Code: {}", speciesName, speciesCode);

				// A predicate to determine if a UniProt mapping file is valid for KEGG to use.
				// It's valid if: it's not a "notMapped" file; it contains "KEGG"; it's valid on the filesystem; the current (of this loop) species is in the filename; it has content
				Predicate<String> isValidKEGGmappingFile = new Predicate<String>()
				{
					@Override
					public boolean test(String fileName)
					{
						try
						{
							return !fileName.contains(".notMapped")
									&& fileName.contains("KEGG")
									&& Files.exists(Paths.get(fileName))
									&& objectCache.getSpeciesNamesToIds().get(speciesName).stream().anyMatch(s -> fileName.contains(s))
									&& Files.lines(Paths.get(fileName)).count() > 1;
						}
						catch (IOException e1)
						{
							e1.printStackTrace();
							return false;
						}
					}
				};

				List<Path> uniProtToKeggFiles = uniprotToKeggRetriever.getActualFetchDestinations().stream()
																			.filter(fileName -> isValidKEGGmappingFile.test(fileName))
																			.map(fileName -> Paths.get(fileName))
																			.collect(Collectors.toList());
				// This could happen if the UniProt files were already downloaded. In that case, uniprotToKeggRetriever.getActualFetchDestinations() will return
				// NULL because nothing was downloaded this time.
				if (uniProtToKeggFiles == null || uniProtToKeggFiles.isEmpty())
				{
					// Since the uniprotToKeggRetriever didn't download anything, maybe we can check in the directory and see if there are any other files there.
					String uniProtToKeggDestination = uniprotToKeggRetriever.getFetchDestination();

					try
					{
						// We'll try to search for everything in the uniprotToKeggRetriever's destination's directory.
						uniProtToKeggFiles = Files.list(Paths.get(uniProtToKeggDestination).getParent())
													.filter(path -> isValidKEGGmappingFile.test(path.toString()) )
													.collect(Collectors.toList());
					}
					catch (IOException e)
					{
						e.printStackTrace();
					}

				}

				// If we have an OK file...
				if (uniProtToKeggFiles != null && uniProtToKeggFiles.size() > 0)
				{
					List<Path> files = uniProtToKeggFiles;
					// create a new job that will actually execute the KEGG data retrieval.
					Callable<Boolean> job = new Callable<Boolean>()
					{
						@Override
						public Boolean call() throws Exception
						{
							KEGGFileRetriever retriever = new KEGGFileRetriever(keggFileRetriever.getRetrieverName());
							// Use the values set in the KEGG retriever that was defined by Spring to populate the fields of NEW KEGG retrievers!
							retriever.setNumRetries(keggFileRetriever.getNumRetries());
							retriever.setAdapter(keggFileRetriever.getAdapter());
							retriever.setDataURL(keggFileRetriever.getDataURL());
							retriever.setUniprotToKEGGFiles(files);
							retriever.setMaxAge(keggFileRetriever.getMaxAge());
							String uniprotID = objectCache.getRefDbNamesToIds().get("UniProt").get(0);
							// the ".2" is for the ReferenceDatabase - in this case it is UniProt whose DB_ID is 2.
							retriever.setFetchDestination(downloadDestination.replaceAll(".txt", "." + speciesCode + "."+uniprotID+".txt"));
							try
							{
								retriever.fetchData();
							}
							catch (Exception e)
							{
								e.printStackTrace();
								throw new Error(e);
							}
							return true;
						}
					};
					keggJobs.add(job);
				}
				else
				{
					logger.info("Sorry, No uniprot-to-kegg mappings found for species {} / {}", speciesName, speciesCode);
				}
			}
			// These jobs are not very CPU intense so it is probably not too serious to them ALL in parallel.
			ForkJoinPool pool = new ForkJoinPool(keggJobs.size());
			pool.invokeAll(keggJobs);
		}
		else
		{
			logger.info("Skipping KEGGRetriever");
		}
		return true;
	}

}
