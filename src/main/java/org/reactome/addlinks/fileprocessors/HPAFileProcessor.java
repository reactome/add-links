package org.reactome.addlinks.fileprocessors;

import org.reactome.addlinks.EnsemblBioMartUtil;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class HPAFileProcessor extends FileProcessor<List<String>>
{

    public HPAFileProcessor(String processorName)
    {
        super(processorName);
    }

    public HPAFileProcessor()
    {
        super();
    }

    private static final int GENE_NAME_INDEX = 0;
    private static final int ENSEMBL_IDENTIFIER_INDEX = 2;
    private static final int UNIPROT_IDENTIFIER_INDEX = 4;

    /**
     * Build map of UniProt identifiers to Human Protein Atlas (HPA) identifiers that is used to create HPA cross-references in database.
     * @return - Map<String, List<String>
     */
    @Override
    public Map<String, List<String>> getIdMappingsFromFile()
    {

        String dirToHPAFiles = "";
        try {
            dirToHPAFiles = this.unzipFile(this.pathToFile);
        } catch (Exception e) {
            logger.error("Error unzipping file ({}): {}", this.pathToFile, e.getMessage());
            e.printStackTrace();
        }

        if (dirToHPAFiles.isEmpty()) {
            return new HashMap<>();
        }

        Path inputFilePath = Paths.get(dirToHPAFiles, this.pathToFile.getFileName().toString().replace(".zip", ""));

        Map<String, List<String>> mappings = new HashMap<>();
        for (String line : EnsemblBioMartUtil.getLinesFromFile(inputFilePath, true)) {

            List<String> tabSplit = Arrays.asList(line.split("\t"));
            String geneName = tabSplit.get(GENE_NAME_INDEX);
            String ensemblId = tabSplit.get(ENSEMBL_IDENTIFIER_INDEX);
            String uniprotId = tabSplit.get(UNIPROT_IDENTIFIER_INDEX);
            String hpaUrlId = String.join("-", ensemblId, geneName);

            if (necessaryIdentifiersPresent(geneName, ensemblId, uniprotId)) {
                mappings.computeIfAbsent(uniprotId, k -> new ArrayList<>()).add(hpaUrlId);
            }
        }

        return mappings;
    }

    // Checks that all values read from line exist.
    private boolean necessaryIdentifiersPresent(String geneName, String ensemblId, String uniprotId) {
        return !geneName.isEmpty() && !ensemblId.isEmpty() && !uniprotId.isEmpty();
    }
}