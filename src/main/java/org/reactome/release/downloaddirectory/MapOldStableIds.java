package org.reactome.release.downloaddirectory;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;

import java.io.IOException;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

public class MapOldStableIds {
	private static final Logger logger = LogManager.getLogger();
	/**
	 * This DownloadDirectory module produces a mapping file of current Reactome stable identifiers to old Reactome stable identifiers.
	 * These stable identifiers denote specific instances in Reactome (Pathway, Reaction, Protein) and can be used to access their pages externally.
	 * After release '53', Reactome switched its stable identifier format from 'REACT_XXXXX' to 'R-ABC-XXXXXX'. This new format
	 * contained a bit more information ('ABC' denotes the species) while also mitigating a database corruption issue that sometimes caused
	 * multiple stable identifiers to be mapped to a single instance.
	 * @param dba MySQLAdaptor - Connects to release_current relational database.
	 * @param releaseNumber String - Current release number, used for storing files in release-specific location.
	 * @throws Exception - Thrown if there are issues with the MySQLAdaptor and GKInstance classes.
	 * @throws IOException - Thrown if unable to create or write to file.
	 * @throws SQLException - Thrown if there are issues connecting/querying/interacting with the stable_identifiers database.
	 * @throws ClassNotFoundException - Thrown if unable to find or crete the MySQL driver.
	 */
	public static void execute(MySQLAdaptor dba, String releaseNumber) throws Exception, IOException, SQLException, ClassNotFoundException {

		logger.info("Running MapOldStableIds step");
		ResultSet stableIdResults = retrieveAllStableIdentifiers(dba);

		logger.info("Mapping Old Stable IDs to Current Stable IDs...");
		Map<String, List<String>> dbIdToStableIds = getDbIdToStableIds(stableIdResults);
		List<String> dbIds = new ArrayList<>(dbIdToStableIds.keySet());
		Collections.sort(dbIds);

		// Iterate through array of stable IDs associated with DB ID, splitting into human and non-human groups.
		List<List<Object>> hsaIds = new ArrayList<>();
		List<List<Object>> nonHsaIds = new ArrayList<>();
		for (String dbId : dbIds)
		{
			List<String> stableIds = dbIdToStableIds.get(dbId);
			Collections.sort(stableIds);

			// After sorting the first stable ID in the array is considered the primary ID.
			// An Array of Arrays is used here, with each interior array's first element being
			// the primaryId string and the second element being an array of the remaining stable IDs.
			// Example: [R-HSA-1006169, [REACT_118604]], [R-HSA-1006173, [REACT_119254]]]
			if (!(stableIds.size() < 2) || (stableIds.get(0).matches("^R-.*")))
			{
				String primaryId = stableIds.get(0);
				stableIds.remove(0);
				ArrayList<Object> organizedIds = new ArrayList<>();
				if (primaryId.matches("R-HSA.*"))
				{
					organizedIds.add(primaryId);
					organizedIds.add(stableIds);
					hsaIds.add(organizedIds);
				} else {
					organizedIds.add(primaryId);
					organizedIds.add(stableIds);
					nonHsaIds.add(organizedIds);
				}
			}
		}

		// Reorder the data so that the interior arrays that have only 1 element are going to be output first.
		List<List<Object>> combinedIds = new ArrayList<>();
		combinedIds.addAll(hsaIds);
		combinedIds.addAll(nonHsaIds);
		List<List<Object>> stableIdsToOldIdsMappings = new ArrayList<>();
		List<List<Object>> deferredIds = new ArrayList<>();
		for (List<Object> stableIdsArray : combinedIds)
		{
			@SuppressWarnings("unchecked")
			List<String> secondaryIds = (List<String>) stableIdsArray.get(1);
			if (secondaryIds.size() > 1)
			{
				deferredIds.add(stableIdsArray);
			} else {
				stableIdsToOldIdsMappings.add(stableIdsArray);
			}
		}
		stableIdsToOldIdsMappings.addAll(deferredIds);

		logger.info("Retrieving current stable identifiers from " + dba.getDBName());
		Set<String> currentStableIdentifiers = getCurrentStableIdentifiers(dba);

		writeMappingsToFile(releaseNumber, stableIdsToOldIdsMappings, currentStableIdentifiers);

		logger.info("MapOldStableIds finished");
	}

	/**
	 * Creates a MySQL driver that queries the stable_identifiers database for identifier and instanceId from the StableIdentifier table.
	 * @param dba MySQLAdaptor, Not used to query the database, but to supply database parameters like host, username and password.
	 * @return ResultSet from MySQL stable_identifiers database that consists of identifiers and their associated instanceId
	 * @throws ClassNotFoundException - Thrown if MySQL driver class isn't found
	 * @throws SQLException - Thrown if there are issues connecting/querying/interacting with stable_identifiers database
	 */
	private static ResultSet retrieveAllStableIdentifiers(MySQLAdaptor dba) throws ClassNotFoundException, SQLException {
		// Need to use mysql driver to access stable_identifiers db
		logger.info("Connecting to stable_identifiers db...");
		Class.forName("com.mysql.jdbc.Driver");
		Connection connect = DriverManager.getConnection("jdbc:mysql://" + dba.getDBHost() + "/stable_identifiers?" + "user=" + dba.getDBUser() + "&password=" + dba.getDBPwd());
		Statement statement = connect.createStatement();
		return statement.executeQuery("SELECT identifier,instanceId FROM StableIdentifier");
	}

	/**
	 * Checks that the primary identifier taken from the stable_identifiers database is currently used, and it has secondary mappings.
	 * @param currentStableIdentifiers Set<String> - Set of all StableIdentifiers currently in database.
	 * @param primaryId String - Primary StableIdentifier that maps to secondaryIds.
	 * @param secondaryIds List<String> - All StableIdentifiers (old and new formats) that map to the primary stable identifier.
	 * @return boolean, indicating it is a currently used StableIdentifier with secondary mappings.
	 */
	private static boolean currentStableIdentifierWithMapping(Set<String> currentStableIdentifiers, String primaryId, List<String> secondaryIds) {
		return currentStableIdentifiers.contains(primaryId) && !secondaryIds.isEmpty();
	}

	/**
	 * Retrieves all StableIdentifiers in the current release database.
	 * @param dba MySQLAdaptor, connecting to release_current database.
	 * @return Set<String>, all StableIdentifiers in current release database.
	 * @throws Exception - Thrown by MySQLAdaptor
	 */
	private static Set<String> getCurrentStableIdentifiers(MySQLAdaptor dba) throws Exception {
		Collection<GKInstance> stableIdentifierInstances = dba.fetchInstancesByClass(ReactomeJavaConstants.StableIdentifier);
		Set<String> currentStableIdentifiersSet = new HashSet<>();
		for (GKInstance stableIdentifierInst : stableIdentifierInstances) {
			currentStableIdentifiersSet.add(stableIdentifierInst.getAttributeValue(ReactomeJavaConstants.identifier).toString());
		}
		return currentStableIdentifiersSet;
	}

	/**
	 * Uses the resultSet from the stable_identifiers database query (which retrieved *all* StableIdentifiers and
	 * their associated instance ids that have ever existed in Reactome) to build a map of instance IDs to StableIdentifiers.
	 * @param stableIdResults ResultSet - Data result of query to stable_identifiers database for stable identifiers and associated instance IDs.
	 * @return Map<String, List<String>> - Mapping of db IDs to Stable Identifiers.
	 * @throws SQLException - Thrown if there are issues accessing the ResultSet object.
	 */
	private static Map<String, List<String>> getDbIdToStableIds(ResultSet stableIdResults) throws SQLException {
		Map<String, List<String>> dbIdToStableIds = new HashMap<>();

		// Iterate through returned results of DB IDs and stable IDs
		while (stableIdResults.next()) {
			String stableId = stableIdResults.getString(1);
			String dbId = stableIdResults.getString(2);

			dbIdToStableIds.computeIfAbsent(dbId, k -> new ArrayList<>()).add(stableId);
		}
		return dbIdToStableIds;
	}

	/**
	 * With the old stable identifier mappings completed, write the results to the 'reactome_stable_ids.txt' file.
	 * @param releaseNumber String - Current release number, used for storing files in release-specific location.
	 * @param stableIdsToOldIdsMappings List<List<Object>> - List of current stable identifier mappings to older mappings.
	 * The interior List<Object> is of the form [String, List<String>].
	 * @param currentStableIdentifiers Set<String>, all StableIdentifiers in current release database.
	 * @throws IOException - Thrown if there are issues with creating mapping file.
	 */
	private static void writeMappingsToFile(String releaseNumber, List<List<Object>> stableIdsToOldIdsMappings, Set<String> currentStableIdentifiers) throws IOException {
		Path oldStableIdsMappingFilePath = Paths.get(releaseNumber, "reactome_stable_ids.txt");
		String header = "# Reactome stable IDs for release " + releaseNumber + "\n" + "Stable_ID\told_identifier(s)\n";
		Files.write(oldStableIdsMappingFilePath, header.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
		for (List<Object> stableIdsArray : stableIdsToOldIdsMappings)
		{
			String primaryId = (String) stableIdsArray.get(0);
			@SuppressWarnings("unchecked")
			List<String> secondaryIds = (ArrayList<String>) stableIdsArray.get(1);
			if (currentStableIdentifierWithMapping(currentStableIdentifiers, primaryId, secondaryIds)) {
				String line = primaryId + "\t" + String.join(",", secondaryIds) + "\n";
				Files.write(oldStableIdsMappingFilePath, line.getBytes(), StandardOpenOption.APPEND);
			}
		}
	}
}
