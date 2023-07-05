package org.reactome.release.downloaddirectory.BioPax;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.biopax.validator.api.Validator;
import org.biopax.validator.api.ValidatorUtils;
import org.biopax.validator.api.beans.Validation;
import org.biopax.validator.impl.IdentifierImpl;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.core.io.Resource;

import static org.reactome.release.downloaddirectory.BioPax.Utils.*;

/**
 * @author Joel Weiser (joel.weiser@oicr.on.ca)
 *         Created 9/22/2022
 */
public class FileValidation {
    private static final Logger logger = LogManager.getLogger();

    private static ApplicationContext ctx;
    private static Validator validator;

    // BioPAX validation requires loading of bio ontologies, which takes a few minutes before starting the BioPAX process
    static {
        logger.info("Preparing BioPAX validation rules...");
        ctx = new ClassPathXmlApplicationContext(
            "META-INF/spring/appContext-validator.xml",
            "META-INF/spring/appContext-loadTimeWeaving.xml"
        );
        validator = (Validator) ctx.getBean("validator");
        logger.info("Finished preparing BioPAX validation rules");
    }

    // Once BioPAX validation rules have loaded, the actual BioPAX process can start
    public static void execute(String releaseNumber, int biopaxLevel) throws Exception {
        validateBioPAX(releaseNumber, biopaxLevel);
    }

    // Next, once all species files for the current BioPAX level have been generated, we must validate them to make
    // sure they are appropriately formatted
    private static void validateBioPAX(String releaseNumber, int biopaxLevel) throws Exception {
        // Validate each owl file in the biopax output directory produced by PathwaysConverter
        logger.info("Validating owl files...");
        runBiopaxValidation(releaseNumber);

        // Compress all validation files into individual zip files
        logger.info("Zipping BioPAX" + biopaxLevel + " files...");
        //writeFilesToZipFile(releaseNumber, ".owl", getBiopaxZipStream(biopaxLevel));
        writeFilesToZipFile(releaseNumber, ".xml", getValidatorZipStream(biopaxLevel));
        moveBioPaxValidationFileToDownloadFolder(releaseNumber, biopaxLevel);
    }

    // This function runs each file in the biopax output directory through the biopax validator
    private static void runBiopaxValidation(String releaseNumber) throws Exception {
        Files.newDirectoryStream(
            getBioPaxDirectoryPath(releaseNumber), owlFilename -> owlFilename.toString().endsWith(".owl")
        ).forEach(owlFile -> {
            try {
                String owlFilepath = owlFile.toFile().getPath();
                logger.info("Validating BioPAX file:" + owlFilepath);
                validate(ctx.getResource("file:" + owlFilepath));
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    // Function code parts taken from the BioPax validator project, largely imitating their own 'main' function
    // but leaving out much that we don't need for this. Validates each owl file that is passed through.
    private static void validate(Resource owlResource) throws IOException {
        Validation result = createValidationResult(owlResource);

        writeValidationResult(result, getValidationOutputFilePath(owlResource));

        cleanUpResult(result);
    }

    private static Validation createValidationResult(Resource owlResource) throws IOException {
        final boolean autofix = false;
        final int maxErrors = 0;
        final String profile = "notstrict";

        // Define a new validation result for the input data
        Validation result = new Validation(
            new IdentifierImpl(), owlResource.getDescription(), autofix, null, maxErrors, profile
        );

        result.setDescription(owlResource.getDescription());

        validator.importModel(result, owlResource.getInputStream());
        validator.validate(result);
        result.setModel(null);
        result.setModelData(null);

        return result;
    }

    private static void writeValidationResult(Validation result, String outputFile) throws IOException {
        PrintWriter writer = new PrintWriter(outputFile);
        ValidatorUtils.write(result, writer, null);
        writer.close();
    }

    private static String getValidationOutputFilePath(Resource owlResource) throws IOException {
        return owlResource.getFile().getPath() + "_validation.xml";
    }

    private static void cleanUpResult(Validation result) {
        // Cleanup between files (though validator could instead check several resources and then write one report for
        // all)
        validator.getResults().remove(result);
    }
}
