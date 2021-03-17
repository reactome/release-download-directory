package org.reactome.release.downloaddirectory.GenerateGOAnnotationFile;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;

import static org.reactome.release.downloaddirectory.GenerateGOAnnotationFile.GOAGeneratorConstants.*;
import static org.reactome.release.downloaddirectory.GenerateGOAnnotationFile.GOAGeneratorUtilities.checkProteinForDisqualification;
import static org.reactome.release.downloaddirectory.GenerateGOAnnotationFile.GOAGeneratorUtilities.getGOAnnotatableProteinsFromCatalystActivity;
import static org.reactome.release.downloaddirectory.GenerateGOAnnotationFile.GOAGeneratorUtilities.getReactomeIdentifier;

import java.util.*;

public class BiologicalProcessAnnotationBuilder {

    private static final Logger logger = LogManager.getLogger();

    // When attempting to find BiologicalProcess accessions, sometimes referral Event instances need to be checked.
    // We cap this at 2 recursions (the parent and grandparent referrals).
    private static final int MAX_RECURSION_LEVEL = 2;

    /**
     * Initial Biological Function annotations method that determines how to retrieve proteins for annotation.
     * Protein retrieval methods differ depending on the presence of a catalyst.
     * @param reaction -- GKInstance from ReactionlikeEvent class
     * @throws Exception -- MySQLAdaptor exception
     */
    public static List<String> processBiologicalFunctions(GKInstance reaction) throws Exception {
        Collection<GKInstance> catalystInstances = reaction.getAttributeValuesList(
            ReactomeJavaConstants.catalystActivity
        );
        List<String> goaLines = new ArrayList<>();
        for (GKInstance catalystInst : catalystInstances) {
            Set<GKInstance> proteinInstances = getGOAnnotatableProteinsFromCatalystActivity(catalystInst);
            goaLines.addAll(processProteins(proteinInstances, reaction));
        }
        return goaLines;
    }

    /**
     * Iterates through all retrieved proteins, filtering out any that are invalid or are from the excluded species.
     * @param proteins -- Set of GKInstances, these are all catalyst proteins
     * @param reaction -- GKInstance, parent reaction instance.
     * @throws Exception -- MySQLAdaptor exception.
     */
    private static List<String> processProteins(Set<GKInstance> proteins, GKInstance reaction)
        throws Exception {
        List<String> goaLines = new ArrayList<>();
        for (GKInstance protein : proteins) {
            String issueDisqualifyingProtein = checkProteinForDisqualification(protein);
            if (issueDisqualifyingProtein.isEmpty()) {
                goaLines.addAll(getGOBiologicalProcessLine(protein, reaction));
            } else {
                logger.warn(issueDisqualifyingProtein);
            }
        }
        return goaLines;
    }

    /**
     * Before creating GOA line for BP annotations, the reaction in question needs to be checked for the existence of
     * a 'goBiologicalProcess' attribute. If there is none than the instance's 'hasEvent' referrals are checked for
     * any.
     * @param protein -- GKInstance, Protein instance.
     * @param reaction -- GKInstance, parent reaction instance.
     * @throws Exception -- MySQLAdaptor exception.
     */
    private static List<String> getGOBiologicalProcessLine(GKInstance protein, GKInstance reaction) throws Exception {
        List<String> goaLines = new ArrayList<>();
        for (Map<String, String> biologicalProcessAccession : getGOBiologicalProcessAccessions(reaction, 0)) {
            String goaLine = GOAGeneratorUtilities.generateGOALine(
                protein,
                BIOLOGICAL_PROCESS_LETTER,
                BIOLOGICAL_PROCESS_QUALIFIER,
                biologicalProcessAccession.get(ACCESSION_STRING),
                biologicalProcessAccession.get(EVENT_STRING),
                TRACEABLE_AUTHOR_STATEMENT_CODE
            );
            GOAGeneratorUtilities.assignDateForGOALine(reaction, goaLine);
            goaLines.add(goaLine);
        }
        return goaLines;
    }

    /**
     * This method checks for a populated 'goBiologicalProcess' attribute in the incoming instance. If there are none
     * and the max recursion has been reached, its 'hasEvent' referral is checked for it. Once finding it, it returns
     * the 'accession' and 'identifier' for each one, which will be used to generate a GOA line.
     * @param event -- GKInstance, Can be the original reaction instance, or, if it had no Biological Process
     * accessions, its Event referrals.
     * @param recursion -- int, Indicates number of times the method has been recursively called.
     * @return -- 1 or more Maps containing the GO accession string and event instance it is associated with. These
     * maps contain two fields: {"event":"Reactome:identifier"}, and {"accession":"GO:Accession}"
     * @throws Exception -- MySQLAdaptor exception.
     */
    private static List<Map<String, String>> getGOBiologicalProcessAccessions(GKInstance event, int recursion)
        throws Exception {

        List<Map<String, String>> goBiologicalProcessAccessions = new ArrayList<>();
        if (recursion <= MAX_RECURSION_LEVEL) {
            Collection<GKInstance> goBiologicalProcessInstances = event.getAttributeValuesList(
                ReactomeJavaConstants.goBiologicalProcess
            );
            if (!goBiologicalProcessInstances.isEmpty()) {
                for (GKInstance goBiologicalProcessInst : goBiologicalProcessInstances) {
                    Map<String, String> goBiologicalProcessAccession = new HashMap<>();
                    goBiologicalProcessAccession.put(
                        ACCESSION_STRING,
                        getGOAccessionWithPrefix(goBiologicalProcessInst)
                    );
                    goBiologicalProcessAccession.put(EVENT_STRING, getReactomeIdentifier(event));
                    goBiologicalProcessAccessions.add(goBiologicalProcessAccession);

                }
            } else {
                recursion++;
                Collection<GKInstance> hasEventReferralInstances =
                    (Collection<GKInstance>) event.getReferers(ReactomeJavaConstants.hasEvent);
                if (hasEventReferralInstances != null) {
                    for (GKInstance hasEventReferralInst : hasEventReferralInstances) {
                        goBiologicalProcessAccessions.addAll(
                            getGOBiologicalProcessAccessions(hasEventReferralInst, recursion)
                        );
                    }
                }
            }
        }
        return goBiologicalProcessAccessions;
    }

    /**
     * Retrieves the accession from a GO Biological Process Instance and prepends the "GO:" prefix
     * @param goBiologicalProcess -- GKInstance, a GO Biological Process Instance
     * @return -- The accession of the GO Biological Process Instance with the "GO:" prefix
     * @throws Exception -- MySQLAdaptor exception
     */
    private static String getGOAccessionWithPrefix(GKInstance goBiologicalProcess) throws Exception {
        return GO_IDENTIFIER_PREFIX +
            goBiologicalProcess.getAttributeValue(ReactomeJavaConstants.accession).toString();
    }

}
