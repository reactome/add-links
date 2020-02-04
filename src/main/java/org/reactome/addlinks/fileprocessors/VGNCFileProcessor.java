package org.reactome.addlinks.fileprocessors;

import org.reactome.addlinks.EnsemblBioMartUtil;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class VGNCFileProcessor extends FileProcessor{

    public VGNCFileProcessor(String processorName)
    {
        super(processorName);
    }

    public VGNCFileProcessor()
    {
        super();
    }

    private static final int vgncIdentifierIndex = 1;
    private static final int uniprotIdentifiersIndex = 21;
    private static final boolean flattenZipOutput = true;
    /**
     * Build map of UniProt identifiers to Vertebrate Gene Nomenclature Committee (VGNC) identifiers that is used to create VGNC cross-references in database.
     * @return - Map<String, List<String>
     */
    @Override
    public Map<String, List<String>> getIdMappingsFromFile()
    {
        Map<String, List<String>> mappings = new HashMap<>();
        try {
            this.unzipFile(this.pathToFile, flattenZipOutput);
        } catch (Exception e) {
            logger.error("Error unzipping file ({}): {}", this.pathToFile, e.getMessage());
            e.printStackTrace();
        }
        Path inputFilePathUnzipped = Paths.get(this.pathToFile.toAbsolutePath().toString().replace(".gz", ""));

        List<String> lines = new ArrayList<>();
        try {
            lines = EnsemblBioMartUtil.getLinesFromFile(inputFilePathUnzipped, true);
        } catch (IOException e) {
            logger.error("Error reading file ({}): {}", inputFilePathUnzipped.toAbsolutePath(), e.getMessage());
            e.printStackTrace();
        }

        for (String line : lines) {
            List<String> tabSplit = Arrays.asList(line.split("\t"));
            if (EnsemblBioMartUtil.necessaryColumnPresent(tabSplit, uniprotIdentifiersIndex)) {

                String vgncId = EnsemblBioMartUtil.getIdentifierWithoutPrefix(tabSplit.get(vgncIdentifierIndex).split(":")[1]);
                List<String> uniprotIds = Arrays.asList(tabSplit.get(uniprotIdentifiersIndex).replaceAll("\"", "").split("\\|"));
                for (String uniprotId : uniprotIds) {
                    mappings.computeIfAbsent(uniprotId, k -> new ArrayList<>()).add(vgncId);
                }
            }
        }

        return mappings;
    }
}