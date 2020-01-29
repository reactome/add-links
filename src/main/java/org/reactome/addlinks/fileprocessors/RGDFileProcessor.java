package org.reactome.addlinks.fileprocessors;

import org.reactome.addlinks.EnsemblBiomartUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class RGDFileProcessor extends FileProcessor{

    public RGDFileProcessor(String processorName)
    {
        super(processorName);
    }

    public RGDFileProcessor()
    {
        super();
    }

    private static final int rgdIdentifierIndex = 0;
    private static final int uniprotIdentifiersIndex = 21;

    @Override
    public Map<String, List<String>> getIdMappingsFromFile()
    {
        Map<String, List<String>> mappings = new HashMap<>();
        Path inputFilePath = Paths.get(this.pathToFile.toAbsolutePath().toString().replace(".gz", ""));

        List<String> lines = new ArrayList<>();
        try {
            lines = EnsemblBiomartUtil.getLinesFromFile(inputFilePath, true);
        } catch (IOException e) {
            logger.error("Error reading file ({}): {}", inputFilePath.toString(), e.getMessage());
            e.printStackTrace();
        }

        for (String line : lines) {
            if (fileBodyLine(line)) {
                List<String> tabSplit = Arrays.asList(line.split("\t"));
                String rgdId = tabSplit.get(rgdIdentifierIndex);
                List<String> uniprotIds = Arrays.asList(tabSplit.get(uniprotIdentifiersIndex).split(";"));
                if (!uniprotIds.isEmpty()) {
                    for (String uniprotId : uniprotIds) {
                        mappings.computeIfAbsent(uniprotId, k -> new ArrayList<>()).add(rgdId);
                    }
                }
            }
        }
        return mappings;
    }

    private boolean fileBodyLine(String line) {
        return Character.isDigit(line.charAt(0));
    }
}