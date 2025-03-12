// This Jenkinsfile is used by Jenkins to run the 'DownloadDirectory' step of Reactome's release.
// It requires that the 'BioModels' step has been run successfully before it can be run.

import org.reactome.release.jenkins.utilities.Utilities

// Shared library maintained at 'release-jenkins-utils' repository.
def utils = new Utilities()

pipeline {
    agent any

    environment {
        ECR_URL = 'public.ecr.aws/reactome/release-download-directory'
        CONT_NAME = 'release_download_directory_container'
        CONT_ROOT = '/opt/release-download-directory'
    }
	
	stages {
		// This stage checks that an upstream project 'BioModels' was run successfully for its last build.
		stage('Check BioModels build succeeded'){
			steps{
				script{
					utils.checkUpstreamBuildsSucceeded("database_updates/job/BioModels")
				}
			}
		}

		stage('Setup: Pull and clean docker environment'){
			steps{
				sh "docker pull ${ECR_URL}:latest"
				sh """
					if docker ps -a --format '{{.Names}}' | grep -Eq '${CONT_NAME}'; then
						docker rm -f ${CONT_NAME}
					fi
				"""
			}
		}

		// This stage executes the DownloadDirectory code. It generates various files that are downloadable from the reactome website.
		// The files that are produced are configurable. See the 'Running specific modules of Download Directory' section in the README.
		stage('Main: Run DownloadDirectory'){
			steps{
				script{
					def releaseVersion = utils.getReleaseVersion()
					withCredentials([file(credentialsId: 'Config', variable: 'ConfigFile')]){
						sh "sudo service tomcat9 stop"
						sh "sudo service neo4j stop"
						sh "mkdir -p config"
						sh "sudo cp $ConfigFile config/auth.properties"
						sh "docker run -v /var/run/mysqld/mysqld.sock:/var/run/mysqld/mysqld.sock  -v \$(pwd)/config:/config -v \$(pwd)/${releaseVersion}:/gitroot/reactome-release-directory/${releaseVersion} --net=host  ${ECR_URL}:latest /bin/bash -c \'java -Xmx${env.JAVA_MEM_MAX}m -javaagent:src/main/resources/spring-instrument-4.2.4.RELEASE.jar -jar target/download-directory.jar -g /config/auth.properties\'"
						sh "sudo service neo4j start"
						sh "sudo service tomcat9 start"
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
					sh "sudo chown jenkins:jenkins ${releaseVersion} -R"
					sh "tar -zcvf ${downloadDirectoryArchive} ${releaseVersion}"
					sh "mv ${releaseVersion}/biopax_validator.zip ."
					sh "mv ${releaseVersion}/biopax2_validator.zip ."
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
