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

    @Override
    public Map<String, Map<String, List<String>>> getIdMappingsFromFile() throws IOException {

        Map<String, Map<String, List<String>>> mappings = new HashMap<>();
        File microarrayDir = new File(this.pathToFile.toString());
        File[] microarrayFiles = microarrayDir.listFiles();
        for (File microarrayFile : microarrayFiles) {
            String microarrayFileSpecies = microarrayFile.getName().split("_")[0];
            BufferedReader br = new BufferedReader(new FileReader(microarrayFile));
            String microarrayLine;
            Map<String, List<String>> proteinToTranscripts = new HashMap<>();
            Map<String, List<String>> transcriptToProbes = new HashMap<>();
            Map<String, List<String>> uniprotToENSP = new HashMap<>();
            while ((microarrayLine = br.readLine()) !=null) {
                List<String> tabSplit = Arrays.asList(microarrayLine.split("\t"));
                if (microarrayFile.getName().contains("uniprot")) {
                    if (tabSplit.size() > 3 && !tabSplit.get(2).isEmpty() && !tabSplit.get(3).isEmpty()) {
                        String protein = tabSplit.get(2);
                        String uniprot = tabSplit.get(3);
                        if (uniprotToENSP.get(uniprot) != null) {
                            uniprotToENSP.get(uniprot).add(protein);
                        } else {
                            ArrayList<String> singleProteinArray = new ArrayList<>(Arrays.asList(protein));
                            uniprotToENSP.put(uniprot, singleProteinArray);
                        }
                    }

                } else if (microarrayFile.getName().contains("microarray_probes")) {
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
                    if (tabSplit.size() > 3 && tabSplit.get(3) != null) {
                        String transcript = tabSplit.get(1);
                        String probe = tabSplit.get(3);
                        if (transcriptToProbes.get(transcript) != null) {
                            transcriptToProbes.get(transcript).add(probe);
                        } else {
                            ArrayList<String> singleProbeArray = new ArrayList<>(Arrays.asList(probe));
                            transcriptToProbes.put(transcript, singleProbeArray);
                        }
                    }
                }
            }
            if (!proteinToTranscripts.isEmpty()) {
                mappings.put(microarrayFileSpecies + "_proteinToTranscript", proteinToTranscripts);
            }
            if (!transcriptToProbes.isEmpty()) {
                mappings.put(microarrayFileSpecies + "_transcriptToProbes", transcriptToProbes);
            }
            if (!uniprotToENSP.isEmpty()) {
                mappings.put(microarrayFileSpecies + "_uniprotToENSP", uniprotToENSP);
            }
        }
        return mappings;
    }
}
