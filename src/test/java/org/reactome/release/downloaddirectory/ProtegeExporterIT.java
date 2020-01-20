package org.reactome.release.downloaddirectory;

import static org.junit.Assert.assertTrue;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import java.util.stream.Collectors;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.io.FileUtils;
import org.gk.persistence.MySQLAdaptor;
import org.junit.Before;
import org.junit.Test;

@SuppressWarnings("static-method")
public class ProtegeExporterIT
{
	private static final String RELEASE_NUM = "70";
	private static final String pathToJavaRoot = ".";

	private static String host;
	private static String name;
	private static String user;
	private static String password;
	private static String releaseDir;
	private static List<String> perlLibPaths;

	@Before
	public void setup() throws IOException
	{
		System.out.println("pathToJavaRoot is: " + pathToJavaRoot);

		Properties props = new Properties();
		try(FileInputStream integrationTestProperties = new FileInputStream("src/test/resources/it.properties")) {
			props.load(integrationTestProperties);
		}

		ProtegeExporterIT.host = props.getProperty("db.host");
		ProtegeExporterIT.name = props.getProperty("db.name");
		ProtegeExporterIT.user = props.getProperty("db.user");
		ProtegeExporterIT.password = props.getProperty("db.password");
		ProtegeExporterIT.releaseDir = props.getProperty("release.dir");
		ProtegeExporterIT.perlLibPaths = getPerlLibPathArguments(props.getProperty("perllib.paths"));

		Files.createDirectories(Paths.get(RELEASE_NUM));
	}

	@Test
	public void testProtegeExporterIT() throws SQLException, IOException
	{
		MySQLAdaptor adaptor = new MySQLAdaptor(
			ProtegeExporterIT.host, ProtegeExporterIT.name, ProtegeExporterIT.user, ProtegeExporterIT.password
		);

		ProtegeExporter testExporter = new ProtegeExporter();

		testExporter.setReleaseDirectory(releaseDir);
		testExporter.setPathToWrapperScript(Paths.get(pathToJavaRoot, "src", "main", "resources").toString());
		testExporter.setExtraIncludes(perlLibPaths);
		testExporter.setParallelism(4);
		Set<Long> pathwayIds = new HashSet<>(Arrays.asList(1670466L, 8963743L, 870392L, 1500931L, 5205647L));
		testExporter.setPathwayIdsToProcess(pathwayIds);
		testExporter.setDownloadDirectory(RELEASE_NUM);
		testExporter.setSpeciesToProcess(new HashSet<>(Collections.singletonList("Mycobacterium tuberculosis")));
		testExporter.execute(adaptor);

		final String pathToFinalTar = Paths.get(pathToJavaRoot, RELEASE_NUM, "protege_files.tar").toString();
		assertTrue(Files.exists(Paths.get(pathToFinalTar)));
		assertTrue(Files.size(Paths.get(pathToFinalTar)) > 0);

		// Now, let's see if the tar contents are valid.
		try(InputStream inStream = new FileInputStream(pathToFinalTar);
			TarArchiveInputStream tains = new TarArchiveInputStream(inStream))
		{
			boolean done = false;
			boolean tarContainsPathwayID = false;
			while (!done)
			{
				TarArchiveEntry entry = tains.getNextTarEntry();
				if (entry == null)
				{
					done = true;
				}
				else
				{
					for (Long pathwayId : pathwayIds)
					{
						if (entry.getName().contains(pathwayId.toString()) && entry.getSize() > 0)
						{
							tarContainsPathwayID = true;
							// can exit the loop early if at least one pathway ID was found.
							done = true;
						}
					}
				}
			}
			assertTrue(tarContainsPathwayID);
		}

		if (cleanupAfterTest())
		{
			System.out.println(
				"You specified \"cleanup\", so the directories \"" + RELEASE_NUM + "\" and \"" +
				ProtegeExporter.PROTEGE_FILES_DIR + "\" will now be removed."
			);
			FileUtils.deleteDirectory(Paths.get(RELEASE_NUM).toFile());
			FileUtils.deleteDirectory(Paths.get(ProtegeExporter.PROTEGE_FILES_DIR).toFile());
		}
	}

	// Set clean up from the command line as: -Dcleanup=true (cleanup is false by default)
	private boolean cleanupAfterTest()
	{
		String cleanupAfterTest = System.getProperty("cleanup");

		return cleanupAfterTest != null && Boolean.parseBoolean(cleanupAfterTest);
	}

	// Takes a comma delimited String of paths to library directories for Perl, appends the "-I" flag, and returns
	// the paths as a list of Strings
	private List<String> getPerlLibPathArguments(String perlLibPaths)
	{
		return Arrays.stream(perlLibPaths.split(","))
			.map(path -> "-I" + path)
			.collect(Collectors.toList());
	}
}
