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
						sh """
							docker run \\
							--rm \\
							-v /var/run/mysqld/mysqld.sock:/var/run/mysqld/mysqld.sock \\
							-v ${pwd()}/config:/config \\
							-v ${pwd()}/${releaseVersion}:${CONT_ROOT}/${releaseVersion} \\
							--net=host \\
							--name ${CONT_NAME} \\
							 ${ECR_URL}:latest \\
							 /bin/bash -c "java -Xmx${env.JAVA_MEM_MAX}m -javaagent:src/main/resources/spring-instrument-4.2.4.RELEASE.jar -jar target/download-directory.jar -g /config/auth.properties"
						"""
						sh "sudo service neo4j start"
						sh "sudo service tomcat9 start"
						sh "sudo mv ${releaseVersion}/* ${env.ABS_DOWNLOAD_PATH}/${releaseVersion}/"
						sh "sudo rm -r ${releaseVersion}*"
					}
				}
			}
		}

		// Execute the verifier jar file checking for the existence and proper file sizes of the download_directory output
		stage('Post: Verify DownloadDirectory ran correctly') {
			steps {
				script {
					def releaseVersion = utils.getReleaseVersion()

					sh """
						docker run \\
						--rm \\
						-v ${pwd()}/${releaseVersion}:${CONT_ROOT}/${releaseVersion}/ \\
						-v \$HOME/.aws:/root/.aws:ro \\
						-e AWS_REGION=us-east-1 \\
						--net=host \\
						--name ${CONT_NAME}_verifier \\
						${ECR_URL}:latest \\
						/bin/bash -c "java -jar target/download-directory-verifier.jar --releaseNumber ${releaseVersion} --output ${CONT_ROOT}/${releaseVersion}"
					"""
				}
			}
		}

		// Creates a list of files and their sizes to use for comparison baseline during next release
		stage('Post: Create files and sizes list to upload for next release\'s verifier') {
			steps {
				script {
					def fileSizeList = "files_and_sizes.txt"
					def releaseVersion = utils.getReleaseVersion()

					sh "find ${releaseNumber} -type f -printf \"%s\t%P\n\" > ${fileSizeList}"
					sh "aws s3 --no-progress cp ${fileSizeList} s3://reactome/private/releases/${releaseVersion}/download_directory/data/"
					sh "rm ${fileSizeList}"
				}
			}
		}

		stage('Post: Validate Release Number in BioPax Files') {
			steps {
				script {
					def biopaxSandboxDir = 'biopax/'
					def releaseVersion = utils.getReleaseVersion()
		
					sh """
						set -e
		
						mkdir -p ${biopaxSandboxDir}
						cp ${env.ABS_DOWNLOAD_PATH}/${releaseVersion}/biopax* ${biopaxSandboxDir}
		
						cd ${biopaxSandboxDir}
		
						unzip -o biopax.zip
		
						numOfOwlFiles=\$(ls -1 *.owl | wc -l)
						numOfCorrectOwlFiles=\$(grep -l "xml:base=\\"http://www.reactome.org/biopax/${releaseVersion}" *.owl | wc -l)
		
						echo "Found \$numOfOwlFiles OWL files, \$numOfCorrectOwlFiles have correct version"
		
						if [ "\$numOfOwlFiles" -eq "\$numOfCorrectOwlFiles" ]; then
							echo "BioPax 3 files have correct Reactome version"
						else
							echo "Not all BioPax 3 files have correct Reactome version."
							exit 1
						fi
					"""
				}
			}
		
			post {
				always {
					sh "rm -rf biopax/"
				}
			}
		}

		// This stage archives all logs and other outputs produced by DownloadDirectory on S3.
		stage('Post: Archive logs and validation files'){
			steps{
				script{
					def releaseVersion = utils.getReleaseVersion()
					def dataFiles = ["${releaseVersion}/biopax_validator.zip", "${releaseVersion}/biopax2_validator.zip"]
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
