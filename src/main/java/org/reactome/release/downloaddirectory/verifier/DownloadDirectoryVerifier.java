package org.reactome.release.downloaddirectory.verifier;

import org.reactome.release.verifier.DefaultVerifier;
import org.reactome.release.verifier.Verifier;

import java.io.IOException;

/**
 * @author Joel Weiser (joel.weiser@oicr.on.ca)
 * Created 12/20/2025
 */
public class DownloadDirectoryVerifier {

	public static void main(String[] args) throws IOException {
		Verifier verifier = new DefaultVerifier("download_directory");
		verifier.parseCommandLineArgs(args);
		verifier.run();
	}
}
