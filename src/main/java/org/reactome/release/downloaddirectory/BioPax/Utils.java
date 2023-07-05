package org.reactome.release.downloaddirectory.BioPax;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * @author Joel Weiser (joel.weiser@oicr.on.ca)
 *         Created 9/23/2022
 */
public class Utils {

    public static Path getBioPaxDirectoryPath(String releaseNumber) {
        return Paths.get(getBioPaxDirectory(releaseNumber));
    }

    public static String getBioPaxDirectory(String releaseNumber) {
        return releaseNumber + "_biopax";
    }

    public static void createBioPaxTemporaryOutputDirectory(String releaseNumber) throws IOException {
        Files.createDirectories(getBioPaxDirectoryPath(releaseNumber));
    }

    public static void moveBioPaxOutputFileToDownloadFolder(String releaseNumber, int bioPaxLevel) throws IOException {
        Files.move(
            getOutputFileNameAsPath(bioPaxLevel),
            getOutputDownloadFolderPath(releaseNumber, bioPaxLevel),
            StandardCopyOption.REPLACE_EXISTING
        );
    }

    public static void moveBioPaxValidationFileToDownloadFolder(String releaseNumber, int bioPaxLevel)
        throws IOException {

        Files.move(
            Paths.get(getValidationOutputFileName(bioPaxLevel)),
            getValidationFileDownloadFolderPath(releaseNumber, bioPaxLevel),
            StandardCopyOption.REPLACE_EXISTING
        );
    }

    public static void deleteBioPaxTemporaryOutputDirectory(String releaseNumber) throws IOException {
        Files.list(getBioPaxDirectoryPath(releaseNumber)).forEach(bioPaxFile -> {
            try {
                Files.delete(bioPaxFile);
            } catch (IOException e) {
                throw new RuntimeException("Can not delete " + bioPaxFile);
            }
        });
        Files.deleteIfExists(getBioPaxDirectoryPath(releaseNumber));
    }

    public static Path getBioPaxDownloadFolderPath(String releaseNumber) {
        return Paths.get(releaseNumber);
    }

    public static Path getOutputFileNameAsPath(int bioPaxLevel) {
        return Paths.get(getOutputFileName(bioPaxLevel));
    }

    public static ZipOutputStream getBiopaxZipStream(int biopaxLevel) throws FileNotFoundException {
        return getZipOutputStream(getOutputFileName(biopaxLevel));
    }

    public static ZipOutputStream getValidatorZipStream(int biopaxLevel) throws FileNotFoundException {
        return getZipOutputStream(getValidationOutputFileName(biopaxLevel));
    }

    // Zip utilities
    public static void writeFilesToZipFile(String releaseNumber, String fileExtension, ZipOutputStream zipOutputStream) throws IOException {
        Files.newDirectoryStream(
            getBioPaxDirectoryPath(releaseNumber), path -> path.toString().endsWith(fileExtension)
        ).forEach(path -> {
            try {
                writeToZipFile(path.toFile(), zipOutputStream);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        zipOutputStream.close();
    }

    private static String getOutputFileName(int bioPaxLevel) {
        return bioPaxLevel == 2 ? "biopax2.zip" : "biopax.zip";
    }

    private static String getValidationOutputFileName(int bioPaxLevel) {
        return bioPaxLevel == 2 ? "biopax2_validator.zip" : "biopax_validator.zip";
    }

    private static Path getOutputDownloadFolderPath(String releaseNumber, int bioPaxLevel) {
        return getBioPaxDownloadFolderPath(releaseNumber).resolve(getOutputFileNameAsPath(bioPaxLevel));
    }

    private static Path getValidationFileDownloadFolderPath(String releaseNumber, int bioPaxLevel) {
        return getBioPaxDownloadFolderPath(releaseNumber).resolve(getValidationOutputFileName(bioPaxLevel));
    }

    private static ZipOutputStream getZipOutputStream(String fileName) throws FileNotFoundException {
        return new ZipOutputStream(new FileOutputStream(fileName));
    }

    //Function for compressing Biopax and validation files
    private static void writeToZipFile(File file, ZipOutputStream zipOutputStream) throws IOException {
        FileInputStream zipInputStream = new FileInputStream(file);
        zipOutputStream.putNextEntry(new ZipEntry(file.getName().replaceAll(" +", "_")));
        byte[] bytes = new byte[1024];
        int length;
        while ((length = zipInputStream.read(bytes)) >= 0) {
            zipOutputStream.write(bytes, 0, length);
        }
        zipOutputStream.closeEntry();
        zipInputStream.close();
    }
}
