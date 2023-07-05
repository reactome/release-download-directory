package org.reactome.release.downloaddirectory.BioPax;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static org.reactome.release.downloaddirectory.BioPax.Utils.createBioPaxTemporaryOutputDirectory;
import static org.reactome.release.downloaddirectory.BioPax.Utils.deleteBioPaxTemporaryOutputDirectory;

public class BioPax {
	private static final Logger logger = LogManager.getLogger();

	public static void execute(
		String username, String password, String host, String port, String database,
		String releaseNumber, String pathToSpeciesConfig, List<Integer> biopaxLevels
	) throws Exception {
		for (int biopaxLevel : biopaxLevels) {
			createBioPaxTemporaryOutputDirectory(releaseNumber);

			logger.info("Running BioPax level {} generation and validation", biopaxLevel);
			FileGeneration.execute(
				username, password, host, port, database, releaseNumber, pathToSpeciesConfig, biopaxLevel
			);
			FileValidation.execute(releaseNumber, biopaxLevel);

			deleteBioPaxTemporaryOutputDirectory(releaseNumber);
		}
		//deleteBioPaxTemporaryOutputDirectory(releaseNumber);
		logger.info("Finished BioPAX");
	}
}
