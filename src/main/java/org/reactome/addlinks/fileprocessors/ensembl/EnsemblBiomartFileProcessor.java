package org.reactome.addlinks.fileprocessors.ensembl;

import org.reactome.addlinks.fileprocessors.FileProcessor;

import java.io.*;
import java.util.*;

public class EnsemblBiomartFileProcessor extends FileProcessor<Map<String, List<String>>> {

    public EnsemblBiomartFileProcessor()
    {
        super(null);
    }

    public EnsemblBiomartFileProcessor(String processorName)
    {
        super(processorName);
    }

    //TODO: Add logging
    //TODO: Add commenting
    //TODO: Add unit tests
    //TODO: Rewrite variable names
    //TODO: Global variables for file names
    //TODO: Functional refactor
    //TODO: Function-level commenting

    @Override
    public Map<String, Map<String, List<String>>> getIdMappingsFromFile() throws IOException {

        Map<String, Map<String, List<String>>> mappings = new HashMap<>();
        // Iterate through each biomart file in the addlinks directory
        File microarrayDir = new File(this.pathToFile.toString());
        File[] microarrayFiles = microarrayDir.listFiles();
        for (File microarrayFile : microarrayFiles) {
            // Get species name from filename
            String microarrayFileSpecies = microarrayFile.getName().split("_")[0];
            // Mappings that will be generated from each file for the species.
            Map<String, List<String>> proteinToTranscripts = new HashMap<>();
            Map<String, List<String>> proteinToGenes = new HashMap<>();
            Map<String, List<String>> transcriptToMicroarrays = new HashMap<>();
            Map<String, List<String>> uniprotToProteins = new HashMap<>();
            // Read file and iterate through each line.
            BufferedReader br = new BufferedReader(new FileReader(microarrayFile));
            String microarrayLine;
            while ((microarrayLine = br.readLine()) !=null) {
                // Split each tab-separated line. Each line has four values. The first three are always
                // Ensembl Gene (ENSG), Transcript (ENST) and Protein (ENSP), in that order.
                // The 4th can be a UniProt or microarray identifier, depending on the file being read.
                // All 4 values may not be in each line.
                List<String> tabSplit = Arrays.asList(microarrayLine.split("\t"));
                // Processing of UniProt mapping files.
                // UniProt identifier mapping files are used to fully populate
                // the 'uniprotToProteins' and 'proteinToGenes' mappings.
                if (microarrayFile.getName().contains("uniprot")) {
                    // 'uniprotToProteins' mapping requires the 3rd (protein) and 4th (uniprot)values in the line.
                    if (tabSplit.size() > 3 && !tabSplit.get(2).isEmpty() && !tabSplit.get(3).isEmpty()) {
                        String protein = tabSplit.get(2);
                        String uniprot = tabSplit.get(3);
                        if (uniprotToProteins.get(uniprot) != null) {
                            uniprotToProteins.get(uniprot).add(protein);
                        } else {
                            ArrayList<String> singleProteinArray = new ArrayList<>(Arrays.asList(protein));
                            uniprotToProteins.put(uniprot, singleProteinArray);
                        }
                    }
                    // 'proteinToGenes' mappings require the 1st (gene) and 3rd (protein) values in the line.
                    if (!tabSplit.get(0).isEmpty() && !tabSplit.get(2).isEmpty()) {
                        String gene = tabSplit.get(0);
                        String protein = tabSplit.get(2);
                        if (proteinToGenes.get(protein) != null) {
                            proteinToGenes.get(protein).add(gene);
                        } else {
                            ArrayList<String> singleGeneArray = new ArrayList<>(Arrays.asList(gene));
                            proteinToGenes.put(protein, singleGeneArray);
                        }
                    }
                    // Add each mapping generated to the 'super' mapping data structure that is returned.
                    mappings.put(microarrayFileSpecies + "_uniprotToProtein", uniprotToProteins);
                    mappings.put(microarrayFileSpecies + "_proteinToGenes", proteinToGenes);

                    //TODO: Rename _microarray_probes to _microarray

                    // Processing of Microarray mapping files.
                    // Microarray identifier mapping files are used to fully populate the
                    // 'proteinToTranscripts' and 'transcriptToMicroarray' mappings.
                } else if (microarrayFile.getName().contains("microarray_probes")) {
                    // 'proteinToTranscripts' mapping requires the 2nd (transcript) and 3rd (protein) values in the line.
                    if (tabSplit.size() > 2 && tabSplit.get(1) != null && tabSplit.get(2) != null) {
                        String transcript = tabSplit.get(1);
                        String protein = tabSplit.get(2);
                        if (proteinToTranscripts.get(protein) != null) {
                            proteinToTranscripts.get(protein).add(transcript);
                        } else {
                            ArrayList<String> singleTranscriptArray = new ArrayList<>(Arrays.asList(transcript));
                            proteinToTranscripts.put(protein, singleTranscriptArray);
                        }
                    }
                    // 'transcriptToMicroarrays' mapping requires the 2nd (transcript) and 4th (microarray) values in the line.
                    if (tabSplit.size() > 3 && tabSplit.get(2) != null && tabSplit.get(3) != null) {
                        String transcript = tabSplit.get(1);
                        String probe = tabSplit.get(3);
                        if (transcriptToMicroarrays.get(transcript) != null) {
                            transcriptToMicroarrays.get(transcript).add(probe);
                        } else {
                            ArrayList<String> singleProbeArray = new ArrayList<>(Arrays.asList(probe));
                            transcriptToMicroarrays.put(transcript, singleProbeArray);
                        }
                    }
                    // Add each mapping generated to the 'super' mapping data structure that is returned.
                    mappings.put(microarrayFileSpecies + "_proteinToTranscript", proteinToTranscripts);
                    mappings.put(microarrayFileSpecies + "_transcriptToProbes", transcriptToMicroarrays);
                }
            }
        }
        return mappings;
    }
}
