package org.reactome.release.downloaddirectory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.persistence.MySQLAdaptor;
import org.reactome.release.downloaddirectory.BioPax.BioPax;
import org.reactome.release.downloaddirectory.GenerateGOAnnotationFile.CreateGOAFile;

public class Main {
	private static final Logger logger = LogManager.getLogger();
	private static final String RESOURCES_DIR = Paths.get("src", "main", "resources").toString();

	public static void main(String[] args) throws Exception {
		String pathToConfig = null;
		String pathToSpeciesConfig = null;
		Set<String> stepsToRun = null;

		Options options = new Options();
		options.addOption(Option.builder("h").longOpt("help").build());
		options.addOption("g", "general", true, "Specify file path to general config");
		options.addOption("s", "species", true, "Specify file path to species config");
		options.addOption("r", "steps", true, "Specify steps to run as a comma delimited string");

		CommandLineParser parser = new DefaultParser();
		try {
			CommandLine cmd = parser.parse(options, args);
			
			if (cmd.hasOption("h")) {
				HelpFormatter formatter = new HelpFormatter();
				formatter.printHelp("java -jar download-directory.jar", "\n", options, "\n", true);
				return;
			}

			// set paths to config files
			pathToConfig = cmd.hasOption("g") ? cmd.getOptionValue("g") : Paths.get(RESOURCES_DIR ,"config.properties").toString();
			pathToSpeciesConfig = cmd.hasOption("s") ? cmd.getOptionValue("s") : Paths.get(RESOURCES_DIR, "Species.json").toString();
			
			// select steps to run
			if (cmd.hasOption("r")) {
				stepsToRun = new HashSet<>(Arrays.asList(cmd.getOptionValue("r").split(",")));
			}
			else {
				try(FileReader fr = new FileReader(Paths.get(RESOURCES_DIR,"stepsToRun.config").toString()); 
					BufferedReader br = new BufferedReader(fr)) {
					stepsToRun = br.lines().filter(line -> !line.startsWith("#")).collect(Collectors.toSet());
				}
			}
		} catch (Exception e) {
			throw new Exception("Error parsing command line arguments: " + e.getMessage());
		}

		logger.info("Beginning Download Directory step");

		Properties props = new Properties();
		props.load(new FileInputStream(pathToConfig));

		//Set up DB adaptor
		String username = props.getProperty("release.database.user");
		String password = props.getProperty("release.database.password");
		String database = props.getProperty("release_current.name");
		String host = props.getProperty("release.database.host");
		int port = Integer.valueOf(props.getProperty("release.database.port"));
		String releaseNumber = props.getProperty("releaseNumber");
		String releaseDirAbsolute = props.getProperty("absoluteReleaseDirectoryPath");
		String releaseDownloadDirWithNumber = Paths.get(releaseDirAbsolute,  "download_directory", releaseNumber).toString();
		MySQLAdaptor dbAdaptor = new MySQLAdaptor(host, database, username, password, port);
		File releaseDir = new File(releaseNumber);
		if (!releaseDir.exists()) {
			releaseDir.mkdir();
		}

		// Temporary system for catching failed steps -- this will need to be cleaned up in future
		List<String> failedSteps = new ArrayList<>();
		//Begin download directory steps
		if (stepsToRun.contains("DatabaseDumps")) {
			// This step takes a DB Dump of the stable_identifiers and test_reactome DBs
			// Outputs: gk_stable_ids.sql, gk_current.sql
			try {
				DatabaseDumps.execute(releaseNumber, username, password, host, port, database);
			} catch (Exception e) {
				failedSteps.add("DatabaseDumps");
				e.printStackTrace();
			}
		}
		if (stepsToRun.contains("BioPAX2") || stepsToRun.contains("BioPAX3")) {
			// This step runs BioPAX level 2 and BioPAX level 3 for Reactome's data using the Pathway-Exchange functions
			// Outputs: biopax2.zip and biopax2_validator.zip, and biopax.zip and biopax_validator.zip (for level 3)
			// These zip files should contain a number of species-specific 'owl' (BioPAX files) and 'xml' validation files
			List<Integer> biopaxLevels = new ArrayList<>();
			if (stepsToRun.contains("BioPAX2")) {
				biopaxLevels.add(2);
			}
			if (stepsToRun.contains("BioPAX3")) {
				biopaxLevels.add(3);
			}
			try {
				BioPax.execute(username, password, host, Integer.toString(port), database, releaseNumber, pathToSpeciesConfig, biopaxLevels);
			} catch (Exception e) {
				failedSteps.add("BioPAX");
				//TODO: Catch the specific exception when its a DataModel problem
				logger.warn("\nAn error was caught during BioPAX -- have you updated the Pathway-Exchange installation?");
				e.printStackTrace();
			}
		}
		if (stepsToRun.contains("GSEAOutput")) {
			// This step converts all Human Pathways to the MSigDB format used by GSEA
			// Output: ReactomePathways.gmt.zip
			try {
				GSEAOutput.execute(dbAdaptor, releaseNumber);
			} catch (Exception e) {
				failedSteps.add("GSEAOutput");
				e.printStackTrace();
			}
		}

		if (stepsToRun.contains("FetchTestReactomeOntologyFiles")) {
			// This step, (formerly fetchEmptyProject), takes the blob output from the Ontology.ontology and parses it into 3 files
			// Outputs: reactome_data_model.pprj, reactome_data_model.pont, reactome_data_model.pins
			try {
				FetchTestReactomeOntologyFiles.execute(dbAdaptor, releaseNumber);
			} catch (Exception e) {
				failedSteps.add("FetchTestReactomeOntologyFiles");
				e.printStackTrace();
			}
		}

		if (stepsToRun.contains("PathwaySummationMappingFile")) {
			// This step takes all Human Pathway and creates a tab-separated file with columns containing the stableIdentifier, name, and summation of the instance
			// Output: pathway2summation.txt
			try {
				PathwaySummationMappingFile.execute(dbAdaptor, releaseNumber);
			} catch (Exception e) {
				failedSteps.add("PathwaySummationMappingFile");
				e.printStackTrace();
			}
		}
		if (stepsToRun.contains("MapOldStableIds")) {
			// This step iterates through all StableIdentifiers and maps them to the old Reactome ID in 'REACT_#####' format. Human instances are displayed first.
			// Output: reactome_stable_ids.txt
			try {
				MapOldStableIds.execute(dbAdaptor, releaseNumber);
			} catch (Exception e) {
				failedSteps.add("MapOldStableIds");
				e.printStackTrace();
			}
		}
		// These file copy commands now use absolute paths instead of relative ones
		if (stepsToRun.contains("GenerateGOAnnotationFile")) {
			// This step generates the gene_association.reactome file
			// Output: gene_association.reactome
			try {
				CreateGOAFile.execute(dbAdaptor, releaseNumber);

			} catch (Exception e) {
				failedSteps.add("GenerateGOAnnotationFile");
				e.printStackTrace();
			}
		}
		if (stepsToRun.contains("protegeexporter")) {
			try {
				ProtegeExporter protegeExporter = new ProtegeExporter(props, releaseDirAbsolute, releaseNumber);
				protegeExporter.execute(dbAdaptor);
			} catch (Exception e) {
				failedSteps.add("protegeexporter");
				e.printStackTrace();
			}
		}
		if (stepsToRun.contains("HumanPathwaysWithDiagrams"))
		{
			logger.info("Generating HumanPathwaysWithDiagrams file");
			try {
				HumanPathwaysWithDiagrams.execute(dbAdaptor, releaseNumber);
			} catch (Exception e) {
				failedSteps.add("HumanPathwaysWithDiagrams");
			}
		}
		if (stepsToRun.contains("CreateReactome2BioSystems"))
		{
			// This step converts Reactome Pathway instances into the NCBI BioSystems format (http://www.ncbi.nlm.nih.gov/biosystems/).
			// Output: ReactomeToBioSystems.zip
			// This zip file contains an 'xml' file for each species in Reactome in BioSystems format
			try {
				CreateReactome2BioSystems.execute(host, database, username, password, port, releaseNumber);
			} catch (Exception e) {
				failedSteps.add("CreateReactome2BioSystems");
				e.printStackTrace();
			}
		}
		if (failedSteps.size() > 0) {
			String failedStepsString = StringUtils.join(failedSteps, ", ");
			logger.warn("Errors were reported in the following step(s): " + failedStepsString + "\n");
		}
		logger.info("Finished DownloadDirectory");
	}
}
