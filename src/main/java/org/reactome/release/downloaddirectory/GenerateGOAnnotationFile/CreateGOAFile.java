package org.reactome.release.downloaddirectory.GenerateGOAnnotationFile;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

import static org.reactome.release.downloaddirectory.GenerateGOAnnotationFile.GOAGeneratorConstants.GOA_FILENAME;
import static org.reactome.release.downloaddirectory.GenerateGOAnnotationFile.GOAGeneratorConstants.REACTOME_STRING;
import static org.reactome.release.downloaddirectory.GenerateGOAnnotationFile.GOAGeneratorUtilities.getCurrentDateAsYYYYMMDD;
import static org.reactome.release.downloaddirectory.GenerateGOAnnotationFile.GOAGeneratorUtilities.getDateForGOALine;

/**
 * Generates gene_association.reactome file from all curated ReactionlikeEvents in the database.
 * @author jcook
 */
public class CreateGOAFile {

    private static final Logger logger = LogManager.getLogger();

    /**
     * This is called from the Main DownloadDirectory class.
     * @param dbAdaptor -- MySQLAdaptor for database
     * @param releaseNumber -- Reactome release version number
     * @throws Exception -- General exception. Exception types are MySQLAdaptor or IO exceptions.
     */
    public static void execute(MySQLAdaptor dbAdaptor, String releaseNumber) throws Exception {
        logger.info("Generating GO annotation file: gene_association.reactome");

        Set<String> goaLines = new LinkedHashSet<>();
        for (GKInstance reactionInst : getReactionlikeEvents(dbAdaptor)) {
            // Only finding GO accessions from curated ReactionlikeEvents
            if (!isInferred(reactionInst)) {
                logger.info("Creating GO annotations for " + reactionInst);

                goaLines.addAll(CellularComponentAnnotationBuilder.processCellularComponents(reactionInst));
                goaLines.addAll(MolecularFunctionAnnotationBuilder.processMolecularFunctions(reactionInst));
                goaLines.addAll(BiologicalProcessAnnotationBuilder.processBiologicalFunctions(reactionInst));
            }
        }

        writeGOAFile(goaLines);
        moveFile(GOA_FILENAME + ".gz", releaseNumber + "/");
        logger.info("Finished generating gene_association.reactome");
    }

    @SuppressWarnings("unchecked")
    private static Collection<GKInstance> getReactionlikeEvents(MySQLAdaptor dbAdaptor) throws Exception {
        return dbAdaptor.fetchInstancesByClass(ReactomeJavaConstants.ReactionlikeEvent);
    }

    // Parent method that houses electronically and manually inferred instance checks.
    private static boolean isInferred(GKInstance reactionInst) throws Exception {
        return isElectronicallyInferred(reactionInst) || isManuallyInferred(reactionInst);
    }

    private static boolean isManuallyInferred(GKInstance reactionInst) throws Exception {
        return reactionInst.getAttributeValue(ReactomeJavaConstants.inferredFrom) != null;
    }

    private static boolean isElectronicallyInferred(GKInstance reactionInst) throws Exception {
        return reactionInst.getAttributeValue(ReactomeJavaConstants.evidenceType) != null;
    }

    /**
     * Iterates through the lines in the 'goaLines' list, retrieves the date associated with that line and also adds
     * the 'Reactome' column before adding it to the gene_association.reactome file.
     * @param goaLines Gene Association File annotation lines to write to the file
     * @throws IOException -- File writing/reading exceptions.
     */
    private static void writeGOAFile(Set<String> goaLines) throws IOException {
        Path goaFilepath = Paths.get(GOA_FILENAME);

        Files.deleteIfExists(goaFilepath);
        Files.createFile(goaFilepath);

        writeHeader(goaFilepath);

        List<String> lines = goaLines.stream().sorted().map(
            line -> String.join("\t", line, getDateForGOALine(line), REACTOME_STRING, "","")
        ).collect(Collectors.toList());

        Files.write(goaFilepath, lines, StandardOpenOption.APPEND);

        gzipGOAFile(goaFilepath);
    }


    private static void writeHeader(Path goaFilepath) throws IOException {
        final String gafHeader = String.join(System.lineSeparator(),
        "!gaf-version: 2.2",
        "generated-by: Reactome",
        "date-generated: " + getCurrentDateAsYYYYMMDD()
        ).concat(System.lineSeparator());

        Files.write(goaFilepath, gafHeader.getBytes(), StandardOpenOption.APPEND);
    }

    /**
     * Gzips gene_association.reactome file
     * @param goaFilePath Path to the unzipped gene_association.reactome file
     * @throws IOException -- File writing/reading exceptions.
     */
    private static void gzipGOAFile(Path goaFilePath) throws IOException {
        Path goaGzipFilePath = Paths.get(goaFilePath.toAbsolutePath().toString() + ".gz");
        try (
        GZIPOutputStream gzipOutputStream = new GZIPOutputStream(new FileOutputStream(goaGzipFilePath.toFile()));
        FileInputStream fileInputStream = new FileInputStream(goaFilePath.toFile())
        ) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = fileInputStream.read(buffer)) != -1) {
                gzipOutputStream.write(buffer, 0, length);
            }
        }
    }

    private static void moveFile(String sourceFile, String targetDirectory) throws IOException {
        Files.move(Paths.get(sourceFile), Paths.get(targetDirectory, sourceFile), StandardCopyOption.REPLACE_EXISTING);
    }
}
