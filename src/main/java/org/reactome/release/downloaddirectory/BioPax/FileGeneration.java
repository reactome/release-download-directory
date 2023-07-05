package org.reactome.release.downloaddirectory.BioPax;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.reactome.biopax.SpeciesAllPathwaysConverter;
import org.reactome.biopax.SpeciesAllPathwaysLevel3Converter;

import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static org.reactome.release.downloaddirectory.BioPax.Utils.*;

/**
 * @author Joel Weiser (joel.weiser@oicr.on.ca)
 *         Created 9/22/2022
 */
public class FileGeneration {
    private static final Logger logger = LogManager.getLogger();

    // Once BioPAX validation rules have loaded, the actual BioPAX process can start
    public static void execute(String username, String password, String host, String port, String database, String releaseNumber, String pathToSpeciesConfig, int biopaxLevel) throws Exception {
        // We want to run BioPAX level 2 and BioPAX level 3, so the below loop starts with '2'
        for (String speciesName : getSpeciesNames(pathToSpeciesConfig)) {
            logger.info("Generating BioPAX{} {} owl file...", biopaxLevel, speciesName);
            // Generate owl files. This particular step requires a local maven installation of the
            // PathwayExchange jar (see README.md for DownloadDirectory)
            generateBioPAXFile(host, database, username, password, port, releaseNumber, speciesName, biopaxLevel);
        }

        writeFilesToZipFile(releaseNumber, ".owl", getBiopaxZipStream(biopaxLevel));
        moveBioPaxOutputFileToDownloadFolder(releaseNumber, biopaxLevel);
    }

    //Generate BioPAX files using the appropriate SpeciesAllPathwaysConverter function found in Pathway-Exchange
    private static void generateBioPAXFile(String host, String database, String username, String password, String port, String releaseNumber, String speciesName, int biopaxLevel) throws Exception {
        String biopaxDir = getBioPaxDirectory(releaseNumber);

        if (biopaxLevel == 2) {
            SpeciesAllPathwaysConverter converter = new SpeciesAllPathwaysConverter();
            converter.doDump(new String[]{host, database, username, password, port, biopaxDir, speciesName});
        } else if (biopaxLevel == 3) {
            SpeciesAllPathwaysLevel3Converter level3converter = new SpeciesAllPathwaysLevel3Converter();
            level3converter.doDump(new String[]{host, database, username, password, port, biopaxDir, speciesName});
        }
    }

    private static List<String> getSpeciesNames(String pathToSpeciesConfig) throws IOException, ParseException {
        JSONObject speciesFile = (JSONObject) new JSONParser().parse(new FileReader(pathToSpeciesConfig));
        return (List<String>) speciesFile.keySet()
            .stream()
            .map(speciesKey -> getSpeciesName(speciesFile, speciesKey.toString()))
            .collect(Collectors.toList());
    }

    private static String getSpeciesName(JSONObject speciesFile, String speciesKey) {
        return ((JSONArray) ((JSONObject) speciesFile.get(speciesKey)).get("name")).get(0).toString();
    }
}
