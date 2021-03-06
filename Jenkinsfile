// This Jenkinsfile is used by Jenkins to run the 'DownloadDirectory' step of Reactome's release.
// It requires that the 'BioModels' step has been run successfully before it can be run.

import org.reactome.release.jenkins.utilities.Utilities

// Shared library maintained at 'release-jenkins-utils' repository.
def utils = new Utilities()

pipeline {
	agent any

	stages {
		// This stage checks that an upstream project 'BioModels' was run successfully for its last build.
		stage('Check AddLinks-Insertion build succeeded'){
			steps{
				script{
                    			utils.checkUpstreamBuildsSucceeded("Relational-Database-Updates/job/BioModels")
				}
			}
		}
		// This stage clones, builds, and install the Pathway-Exchange dependency needed for DownloadDirectory.
		stage('Setup: Install Pathway Exchange artifact'){
			steps{
				script{
					sh "./build_pathway_exchange.sh"
				}
			}
		}
		// This stage builds an archive containing the download directory jar and its dependencies.
		// It also unpacks that archive to be used in the following stage.
		stage('Setup: Unpack DownloadDirectory archive'){
			steps{
				script{
					sh "mvn clean package -DskipTests"
					sh "rm -rf download-directory"
					sh "unzip -o target/download-directory-distr.zip"
				}
			}
		}
		// This stage executes the DownloadDirectory code. It generates various files that are downloadable from the reactome website.
		// The files that are produced are configurable. See the 'Running specific modules of Download Directory' section in the README.
		stage('Main: Run DownloadDirectory'){
			steps{
				script{
					withCredentials([file(credentialsId: 'Config', variable: 'ConfigFile')]){
						sh "java -Xmx${env.JAVA_MEM_MAX}m -javaagent:download-directory/lib/spring-instrument-4.2.4.RELEASE.jar -jar download-directory/download-directory.jar $ConfigFile"
					}
				}
			}
		}
		// Archive all download directory files before moving them to download/XX folder.
		stage('Post: Create archive and move files to download folder'){
		    	steps{
		        	script{
					def releaseVersion = utils.getReleaseVersion()
					def downloadDirectoryArchive = "download-directory-v${releaseVersion}.tgz"
					dir("${releaseVersion}"){
						sh "tar -zcvf ${downloadDirectoryArchive} *"
					}
					sh "mv ${releaseVersion}/${downloadDirectoryArchive} ."
					sh "mv ${releaseVersion}/* ${env.ABS_DOWNLOAD_PATH}/${releaseVersion}/"
					sh "rm -r ${releaseVersion}*"
		        	}
		    	}
		}
		// This stage archives all logs and other outputs produced by DownloadDirectory on S3.
		stage('Post: Archive logs and validation files'){
			steps{
				script{
				        def releaseVersion = utils.getReleaseVersion()
					def dataFiles = ["download-directory-v${releaseVersion}.tgz", "biopax_validator.zip", "biopax2_validator.zip"]
					def logFiles = []
					def foldersToDelete = ["/tmp/protege_files/"]
					// This file is left over in the repository after zipping it up
					sh "rm -f gene_association.reactome"
				    	utils.cleanUpAndArchiveBuildFiles("download_directory", dataFiles, logFiles, foldersToDelete)
				}
			}
		}
	}
}
