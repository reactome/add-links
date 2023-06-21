package org.reactome.addlinks.dataretrieval;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.reactome.release.common.dataretrieval.FileRetriever;

public class UniprotFileRetriever extends FileRetriever {
	// To be used to wait 500 ms to retry if a URL from UniProt returns nothing.
	private String mapFromDb="";
	private String mapToDb="";
	private BufferedInputStream inStream;
	// A list of paths that were actually downloaded to.
	private static List<String> actualFetchDestinations = Collections.synchronizedList(new ArrayList<String>());

	private final int maxAttemptCount = 5;
	/**
	 * This enum provides a  mapping between Reactome names for reference
	 * databases and the Uniprot ID that is used by their mapping service.
	 * @author sshorser
	 *
	 */
	public enum UniprotDB {
		// For a list of database IDs that Uniprot can map from, see: https://www.uniprot.org/help/api_idmapping
		OMIM("MIM"),
		PDB("PDB"),
		RefSeqPeptide("RefSeq_Protein"),
		RefSeqRNA("RefSeq_Nucleotide"),
		ENSEMBL("Ensembl"),
		ENSEMBLProtein("Ensembl_Protein"),
		ENSEMBLGenomes("Ensembl_Genomes"),
		ENSEMBLTranscript("Ensembl_Transcript"),
		Wormbase("WormBase"),
		Entrez_Gene("GeneID"),
		GeneName("HGNC"),
		KEGG("KEGG"),
		UniProt("UniProtKB"),
		UCSC("UCSC");

		private String uniprotName;
		private static Map<String,UniprotDB> mapToEnum;

		UniprotDB(String uniprotName) {
			this.uniprotName = uniprotName;
			updateMap(uniprotName);
		}

		private void updateMap(String uniprotName) {
			if (mapToEnum == null ) {
				mapToEnum = new HashMap<>(10);
			}
			mapToEnum.put(uniprotName, this);
		}

		public String getUniprotName()
		{
			return this.uniprotName;
		}

		public static UniprotDB uniprotDBFromUniprotName(String uniprotName)
		{
			return mapToEnum.get(uniprotName);
		}
	}

	public UniprotFileRetriever() { super(); }

	public UniprotFileRetriever(String retrieverName) {
		super(retrieverName);
	}

	/**
	 * Getting data from UniProt is a 3-stage process:
	 * 1) POST a list of identifiers to UniProt. The response received contains a URL to the mapped data.
	 * 2) GET the data from the URL in the response from 1).
	 * 3) GET the "not" mapped data from the URL in the response from 1).
	 */
	@Override
	public void downloadData() {
		// Check inputs:
		if (this.inStream == null) {
			throw new RuntimeException("inStream is null! You must provide an data input stream!");
		} else if (this.mapFromDb.trim().length() == 0) {
			throw new RuntimeException("You must provide a database name to map from!");
		} else if(this.mapToDb.trim().length() == 0) {
			throw new RuntimeException("You must provide a database name to map to!");
		}

		List<String> uniProtIdentifiers =
			new BufferedReader(new InputStreamReader(this.inStream)).lines().collect(Collectors.toList());

		List<List<String>> batchesOfUniProtIdentifiers = getBatchesOfUniProtIdentifiers(uniProtIdentifiers);

		try {
			Files.createDirectories(getOutputFilePath().getParent());
			writeHeader();
			for (List<String> uniProtIdentifierBatch : batchesOfUniProtIdentifiers) {
				UniProtQuery uniprotQuery = UniProtQuery.getUniProtQuery();
				Map<String, List<String>> uniProtIdentifierToTargetDatabaseIdentifiers =
                    uniprotQuery.getMapping(uniProtIdentifierBatch, this.mapToDb);
				writeUniProtMappings(uniProtIdentifierToTargetDatabaseIdentifiers);
			}
		} catch (Exception e) {
			throw new RuntimeException("Unable to create mapping file", e);
		}
	}

	private List<List<String>> getBatchesOfUniProtIdentifiers(List<String> identifiers) {
		// Limited by process builder allowed command length - command increases in size for every identifier queried
		// in a batch
		final int batchSize = 4000;
		List<List<String>> batchesOfUniProtIdentifiers = new ArrayList<>();
		List<String> uniProtIdentifierBatch = new ArrayList<>();
		for (String identifier: identifiers) {
			uniProtIdentifierBatch.add(identifier);
			if (uniProtIdentifierBatch.size() >= batchSize) {
				batchesOfUniProtIdentifiers.add(new ArrayList<>(uniProtIdentifierBatch));
				uniProtIdentifierBatch.clear();
			}
		}
		if (uniProtIdentifierBatch.size() > 0) {
			batchesOfUniProtIdentifiers.add(new ArrayList<>(uniProtIdentifierBatch));
		}
		return batchesOfUniProtIdentifiers;
	}

	private void writeHeader() throws URISyntaxException, IOException {
		Files.write(
			getOutputFilePath(),
			String.format("From\tTo%n").getBytes(),
			StandardOpenOption.CREATE, StandardOpenOption.APPEND
		);
	}

	private void writeUniProtMappings(Map<String, List<String>> uniProtMappings) throws URISyntaxException, IOException {
		for (String uniProtIdentifier : uniProtMappings.keySet()) {
			for (String targetIdentifier : uniProtMappings.get(uniProtIdentifier)) {
				Files.write(
					getOutputFilePath(),
					String.format("%s\t%s%n", uniProtIdentifier, targetIdentifier).getBytes(),
					StandardOpenOption.APPEND
				);
			}
		}
	}

	private Path getOutputFilePath() throws URISyntaxException {
		return Paths.get(new URI("file://" + this.destination));
	}

	public String getMapFromDb()
	{
		return this.mapFromDb;
	}

	public String getMapToDb()
	{
		return this.mapToDb;
	}

	public void setMapFromDbEnum(UniprotDB mapFromDb)
	{
		this.mapFromDb = mapFromDb.getUniprotName();
	}

	public void setMapToDbEnum(UniprotDB mapToDb)
	{
		this.mapToDb = mapToDb.getUniprotName();
	}

	public void setMapFromDb(String mapFromDb)
	{
		this.mapFromDb = mapFromDb;
	}

	public void setMapToDb(String mapToDb)
	{
		this.mapToDb = mapToDb;
	}

	public void setDataInputStream(BufferedInputStream inStream)
	{
		this.inStream = inStream;
	}

	public String getFetchDestination()
	{
		return this.destination;
	}

	public List<String> getActualFetchDestinations()
	{
		return UniprotFileRetriever.actualFetchDestinations;
	}
}
