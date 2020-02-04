package org.reactome.addlinks.fileprocessors;

import org.reactome.addlinks.EnsemblBioMartUtil;

import java.io.IOException;
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

    private static final int geneNameIndex = 0;
    private static final int ensemblIdentifierIndex = 2;
    private static final int uniprotIdentifierIndex = 4;

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

        List<String> lines = new ArrayList<>();
        try {
            lines = EnsemblBioMartUtil.getLinesFromFile(inputFilePath, true);
        } catch (IOException e) {
            logger.error("Error reading file ({}): {}", inputFilePath.toAbsolutePath(), e.getMessage());
            e.printStackTrace();
        }

        Map<String, List<String>> mappings = new HashMap<>();
        for (String line : lines) {

            List<String> tabSplit = Arrays.asList(line.split("\t"));
            String geneName = tabSplit.get(geneNameIndex);
            String ensemblId = tabSplit.get(ensemblIdentifierIndex);
            String uniprotId = tabSplit.get(uniprotIdentifierIndex);
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