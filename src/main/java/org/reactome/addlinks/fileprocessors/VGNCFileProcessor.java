package org.reactome.addlinks.fileprocessors;

import org.reactome.addlinks.EnsemblBioMartUtil;

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

    private static final int VGNC_IDENTIFIER_INDEX = 1;
    private static final int UNIPROT_IDENTIFIERS_INDEX = 21;
    private static final boolean FLATTEN_ZIP_OUTPUT = true;
    /**
     * Build map of UniProt identifiers to Vertebrate Gene Nomenclature Committee (VGNC) identifiers that is used to create VGNC cross-references in database.
     * @return - Map<String, List<String>
     */
    @Override
    public Map<String, List<String>> getIdMappingsFromFile()
    {
        Map<String, List<String>> mappings = new HashMap<>();
        try {
            this.unzipFile(this.pathToFile, FLATTEN_ZIP_OUTPUT);
        } catch (Exception e) {
            logger.error("Error unzipping file ({}): {}", this.pathToFile, e.getMessage());
            e.printStackTrace();
        }
        Path inputFilePathUnzipped = Paths.get(this.pathToFile.toAbsolutePath().toString().replace(".gz", ""));

        for (String line : EnsemblBioMartUtil.getLinesFromFile(inputFilePathUnzipped, true)) {
            List<String> tabSplit = Arrays.asList(line.split("\t"));
            if (EnsemblBioMartUtil.necessaryColumnPresent(tabSplit, UNIPROT_IDENTIFIERS_INDEX)) {

                String vgncId = EnsemblBioMartUtil.getIdentifierWithoutPrefix(tabSplit.get(VGNC_IDENTIFIER_INDEX));
                List<String> uniprotIds = Arrays.asList(tabSplit.get(UNIPROT_IDENTIFIERS_INDEX).replaceAll("\"", "").split("\\|"));
                for (String uniprotId : uniprotIds) {
                    mappings.computeIfAbsent(uniprotId, k -> new ArrayList<>()).add(vgncId);
                }
            }
        }

        return mappings;
    }
}