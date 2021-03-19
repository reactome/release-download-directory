package org.reactome.release.downloaddirectory.GenerateGOAnnotationFile;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;

import static org.reactome.release.downloaddirectory.GenerateGOAnnotationFile.GOAGeneratorConstants.GOA_FILENAME;

/**
 * Generates gene_association.reactome file from all curated ReactionlikeEvents in the database.
 * @author jcook
 */
public class CreateGOAFile {

    private static final Logger logger = LogManager.getLogger();

    /**
     * This is called from the Main DownloadDirectory class.
     * @param dbAdaptor -- MySQLAdaptor for database
     * @param releaseNumber
     * @throws Exception -- General exception. Exception types are MySQLAdaptor or IO exceptions.
     */
    public static void execute(MySQLAdaptor dbAdaptor, String releaseNumber) throws Exception {
        logger.info("Generating GO annotation file: gene_association.reactome");

        for (GKInstance reactionInst : (Collection<GKInstance>) dbAdaptor.fetchInstancesByClass(ReactomeJavaConstants.ReactionlikeEvent)) {
            // Only finding GO accessions from curated ReactionlikeEvents
            if (!isInferred(reactionInst)) {
                logger.info("Creating GO annotations for " + reactionInst);
                CellularComponentAnnotationBuilder.processCellularComponents(reactionInst);
                MolecularFunctionAnnotationBuilder.processMolecularFunctions(reactionInst);
                BiologicalProcessAnnotationBuilder.processBiologicalFunctions(reactionInst);
            }
        }

        GOAGeneratorUtilities.outputGOAFile();
        moveFile(GOA_FILENAME + ".gz", releaseNumber + "/");
        logger.info("Finished generating gene_association.reactome");
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

    private static void moveFile(String sourceFile, String targetDirectory) throws IOException {
        Files.move(Paths.get(sourceFile), Paths.get(targetDirectory, sourceFile), StandardCopyOption.REPLACE_EXISTING);
    }
}
