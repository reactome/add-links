package org.reactome.addlinks.fileprocessors;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

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

    @Override
    public Map<String, List<String>> getIdMappingsFromFile()
    {
        Map<String, List<String>> mappings = new HashMap<>();
        String dirToHPAFiles = null;
        try {
            dirToHPAFiles = this.unzipFile(this.pathToFile);
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (dirToHPAFiles != null) {
            Path inputFilePath = Paths.get(dirToHPAFiles, this.pathToFile.getFileName().toString().replace(".zip", ""));

            List<String> lines = new ArrayList<>();
            try {
                lines = getLinesFromFile(inputFilePath, true);
            } catch (IOException e) {
                logger.error("Error reading file ({}): {}", inputFilePath.toString(), e.getMessage());
                e.printStackTrace();
            }

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
        }

        return mappings;
    }

    private boolean necessaryIdentifiersPresent(String geneName, String ensemblId, String uniprotId) {
        return !geneName.isEmpty() && !ensemblId.isEmpty() && !uniprotId.isEmpty();
    }
}