package org.reactome.addlinks.dataretrieval.ensembl;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.reactome.addlinks.dataretrieval.FileRetriever;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;

public class EnsemblBiomartMicroarrayRetriever extends FileRetriever {

    public EnsemblBiomartMicroarrayRetriever() { }

    public EnsemblBiomartMicroarrayRetriever(String retrieverName)
    {
        super(retrieverName);
    }

    private static final int UNIPROT_QUERY_TOTAL = 2;

    protected void downloadData() throws Exception {

        for (String biomartSpeciesName : getBiomartSpeciesNames()) {
            System.out.println("Retrieving microarray files for " + biomartSpeciesName);
            File addlinksDirectories = new File(this.destination);
            addlinksDirectories.mkdirs();
            URL microarrayUrlWithSpecies = new URL(this.getDataURL().toString().replace("BIOMART_SPECIES_NAME", biomartSpeciesName));
            HttpURLConnection biomartMicroarrayConnection = (HttpURLConnection) microarrayUrlWithSpecies.openConnection();
            BufferedReader brm = new BufferedReader(new InputStreamReader(biomartMicroarrayConnection.getInputStream()));
            String microarrayLine;
            Set<String> microarrayProbeIds = new HashSet<>();
            while ((microarrayLine = brm.readLine()) != null) {
                microarrayProbeIds.add(microarrayLine);
            }
            brm.close();
            for (String probeId : microarrayProbeIds) {
                try {
                    System.out.println("\tRetrieving probes associated with " + probeId);
                    String speciesMicroarrayFilepath = this.destination + biomartSpeciesName + "_microarray_probes";
                    File speciesMicroarrayFile = new File(speciesMicroarrayFilepath);
                    String biomartMicroarrayQuery = getBiomartQuery(biomartSpeciesName, probeId);
                    URL probeUrlWithSpecies = new URL(biomartMicroarrayQuery);
                    HttpURLConnection biomartProbeConnection = (HttpURLConnection) probeUrlWithSpecies.openConnection();
                    if (biomartProbeConnection.getResponseCode() == 200) {
                        BufferedReader brp = new BufferedReader(new InputStreamReader(biomartProbeConnection.getInputStream()));
                        String probeLine;
                        Set<String> probeIdLines = new HashSet<>();
                        boolean isSuccess = false;
                        while ((probeLine = brp.readLine()) != null) {
                            List<String> tabSplit = Arrays.asList(probeLine.split("\t"));
                            if (tabSplit.size() == 4) {
                                probeIdLines.add(probeLine + "\n");
                            }
                            if (probeLine.contains("success")) {
                                isSuccess = true;
                            }
                        }
                        if (isSuccess && !probeIdLines.isEmpty()) {

                            if (!speciesMicroarrayFile.exists()) {
                                speciesMicroarrayFile.createNewFile();
                            }

                            for (String probeIdLine : probeIdLines) {
                                Files.write(Paths.get(speciesMicroarrayFilepath), probeIdLine.getBytes(), StandardOpenOption.APPEND);
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            String speciesUniprotFilepath = this.destination + biomartSpeciesName + "_uniprot";
            File speciesUniprotFile = new File(speciesUniprotFilepath);
            speciesUniprotFile.createNewFile();
            Set<String> uniprotIdLines = new HashSet<>();
            int successCount = 0;
            for (String uniprotQueryId : Arrays.asList("uniprotsptrembl", "uniprotswissprot")) {
                String biomartUniprotQuery = getBiomartQuery(biomartSpeciesName, uniprotQueryId);
                URL biomartUniprotURL = new URL(biomartUniprotQuery);
                HttpURLConnection biomartUniprotConnection = (HttpURLConnection) biomartUniprotURL.openConnection();
                if (biomartUniprotConnection.getResponseCode() == 200) {
                    BufferedReader bru = new BufferedReader(new InputStreamReader(biomartUniprotConnection.getInputStream()));
                    String uniprotLine;
                    while ((uniprotLine = bru.readLine()) != null) {
                        List<String> tabSplit = Arrays.asList(uniprotLine.split("\t"));
                        if (tabSplit.size() == 4) {
                            uniprotIdLines.add(uniprotLine + "\n");
                        }
                        if (uniprotLine.contains("success")) {
                           successCount++;
                        }
                    }
                }
            }

            if (successCount == UNIPROT_QUERY_TOTAL)
            for (String uniprotIdLine : uniprotIdLines) {
                Files.write(Paths.get(speciesUniprotFilepath), uniprotIdLine.getBytes(), StandardOpenOption.APPEND);
            }
        }
    }

    private String getBiomartQuery(String biomartSpeciesName, String queryId) throws IOException {
        String defaultBiomartQuery = "http://www.ensembl.org/biomart/martservice?query=%3C?xml%20version=%221.0%22%20encoding=%22UTF-8%22?%3E%3C!DOCTYPE%20Query%3E%3CQuery%20%20virtualSchemaName%20=%20%22default%22%20formatter%20=%20%22TSV%22%20header%20=%20%220%22%20uniqueRows%20=%20%220%22%20count%20=%20%22%22%20completionStamp%20=%20%221%22%3E%3CDataset%20name%20=%20%22BIOMART_SPECIES_NAME_gene_ensembl%22%20interface%20=%20%22default%22%20%3E%3CAttribute%20name%20=%20%22ensembl_gene_id%22%20/%3E%3CAttribute%20name%20=%20%22ensembl_transcript_id%22%20/%3E%3CAttribute%20name%20=%20%22ensembl_peptide_id%22%20/%3E%3CAttribute%20name%20=%20%22BIOMART_QUERY_ID%22%20/%3E%3C/Dataset%3E%3C/Query%3E";
        String updatedBiomartQuery = defaultBiomartQuery.replace("BIOMART_SPECIES_NAME", biomartSpeciesName).replace("BIOMART_QUERY_ID", queryId);

        return updatedBiomartQuery;
    }

    private List<String> getBiomartSpeciesNames() throws IOException, ParseException {
        Properties applicationProps = new Properties();
        String propertiesLocation = System.getProperty("config.location");
        try(FileInputStream fis = new FileInputStream(propertiesLocation))
        {
            applicationProps.load(fis);
        }
        String pathToSpeciesConfig = applicationProps.getProperty("pathToSpeciesConfig");
        JSONParser parser = new JSONParser();
        Object obj = parser.parse(new FileReader(pathToSpeciesConfig));
        JSONObject jsonObject = (JSONObject) obj;

        List<String> biomartNames = new ArrayList<>();
        for (Object speciesKey : jsonObject.keySet()) {
            JSONObject speciesJson = (JSONObject) jsonObject.get(speciesKey);
            JSONArray speciesNames = (JSONArray) speciesJson.get("name");
            String speciesName = (String) speciesNames.get(0);
            String biomartName = speciesName.substring(0,1).toLowerCase() + speciesName.split(" ")[1];
            biomartNames.add(biomartName);
        }
        return biomartNames;
    }


}
