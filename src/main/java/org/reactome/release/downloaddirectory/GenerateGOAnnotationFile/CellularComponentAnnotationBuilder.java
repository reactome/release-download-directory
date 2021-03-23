package org.reactome.release.downloaddirectory.GenerateGOAnnotationFile;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.model.ClassAttributeFollowingInstruction;
import org.gk.model.GKInstance;
import org.gk.model.InstanceUtilities;
import org.gk.model.ReactomeJavaConstants;

import static org.reactome.release.downloaddirectory.GenerateGOAnnotationFile.GOAGeneratorConstants.*;
import static org.reactome.release.downloaddirectory.GenerateGOAnnotationFile.GOAGeneratorUtilities.getAnyIssueForAnnotationDisqualification;
import static org.reactome.release.downloaddirectory.GenerateGOAnnotationFile.GOAGeneratorUtilities.getReactomeIdentifier;
import static org.reactome.release.downloaddirectory.GenerateGOAnnotationFile.GOAGeneratorUtilities.getTaxonIdentifier;

import java.util.*;

public class CellularComponentAnnotationBuilder {
    private static final Logger logger = LogManager.getLogger();

    // CrossReference IDs of species containing an alternative GO compartment, which do not receive a GO annotation:
    // HIV 1, unknown, C. botulinum, B. anthracis.
    private static final List<String> speciesWithAlternateGOCompartment = Arrays.asList(
        HIV_1_CROSS_REFERENCE, C_BOTULINUM_CROSS_REFERENCE, B_ANTHRACIS_CROSS_REFERENCE
    );

    private static Map<String, List<String>> classToClassAttributesMapForObtainingProteins;

    static {
        classToClassAttributesMapForObtainingProteins = new HashMap<>();

        classToClassAttributesMapForObtainingProteins.put(
            ReactomeJavaConstants.Pathway,
            Arrays.asList(ReactomeJavaConstants.hasEvent)
        );
        classToClassAttributesMapForObtainingProteins.put(
            ReactomeJavaConstants.ReactionlikeEvent,
            Arrays.asList(
                ReactomeJavaConstants.input, ReactomeJavaConstants.output, ReactomeJavaConstants.catalystActivity
            )
        );
        classToClassAttributesMapForObtainingProteins.put(
            ReactomeJavaConstants.Reaction,
            Arrays.asList(
                ReactomeJavaConstants.input, ReactomeJavaConstants.output, ReactomeJavaConstants.catalystActivity
            )
        );
        classToClassAttributesMapForObtainingProteins.put(
            ReactomeJavaConstants.CatalystActivity,
            Arrays.asList(ReactomeJavaConstants.physicalEntity)
        );
        classToClassAttributesMapForObtainingProteins.put(
            ReactomeJavaConstants.Complex,
            Arrays.asList(ReactomeJavaConstants.hasComponent)
        );
        classToClassAttributesMapForObtainingProteins.put(
            ReactomeJavaConstants.EntitySet,
            Arrays.asList(ReactomeJavaConstants.hasMember)
        );
        classToClassAttributesMapForObtainingProteins.put(
            ReactomeJavaConstants.Polymer,
            Arrays.asList(ReactomeJavaConstants.repeatedUnit)
        );
    }

    /**
     * Initial Cellular Compartment annotations method that first retrieves all proteins associated with the
     * ReactionlikeEvent before moving to GO annotation.
     * Then iterates through each retrieved protein, filtering out any that are invalid or are from the excluded
     * species.
     * @param reactionlikeEvent -- GKInstance from ReactionlikeEvent class.
     * @return Set of Cellular Component annotations
     * @throws Exception -- MySQLAdaptor exception.
     */
    public static Set<String> processCellularComponents(GKInstance reactionlikeEvent) throws Exception {
        Set<String> goaLines = new LinkedHashSet<>();
        // First retrieve proteins, then build GO annotation
        for (GKInstance protein : retrieveProteins(reactionlikeEvent)) {
            // Check if the protein has any disqualifying attributes.
            String issueDisqualifyingProtein = getAnyIssueForCellularComponentDisqualification(protein);
            if (issueDisqualifyingProtein.isEmpty()) {
                String goaLine = generateGOCellularCompartmentLine(protein, reactionlikeEvent);
                if (!goaLine.isEmpty()) {
                    goaLines.add(goaLine);
                }
            } else {
                logger.warn(issueDisqualifyingProtein);
            }
        }
        return goaLines;
    }

    /**
     * Performs an AttributeQueryRequest on the incoming reaction instance. This will retrieve all proteins
     * affiliated with the Reaction.
     * @param reactionlikeEvent -- GKInstance from ReactionlikeEvent class.
     * @return -- Set of GKInstances output from the AttributeQueryRequest.
     * @throws Exception -- MySQLAdaptor exception.
     */
    private static Set<GKInstance> retrieveProteins(GKInstance reactionlikeEvent) throws Exception {
        // All EWAS' associated with each of the classes in the instructions will be output for this ReactionlikeEvent.
        String[] outClasses = new String[]{ReactomeJavaConstants.EntityWithAccessionedSequence};
        return InstanceUtilities.followInstanceAttributes(
            reactionlikeEvent, getClassAttributeInstructionsToFollow(), outClasses
        );
    }

    private static List<ClassAttributeFollowingInstruction> getClassAttributeInstructionsToFollow() {
        List<ClassAttributeFollowingInstruction> classesToFollow = new ArrayList<>();
        for (String className : classToClassAttributesMapForObtainingProteins.keySet()) {
            List<String> classAttributes = classToClassAttributesMapForObtainingProteins.get(className);
            classesToFollow.add(getClassAttributeFollowingInstruction(className, classAttributes));
        }
        return classesToFollow;
    }

    private static ClassAttributeFollowingInstruction getClassAttributeFollowingInstruction(
        String className, List<String> classAttributes
    ) {
        final List<String> reverseClassAttributes = Collections.emptyList();

        return new ClassAttributeFollowingInstruction(className, classAttributes, reverseClassAttributes);
    }

    // Possible issues are any generic issues as defined by "getAnyIssueForAnnotationDisqualification" or
    // the protein having a species that has an alternate GO compartment (specific only to Cellular Component
    // annotations and the species list defined above)
    private static String getAnyIssueForCellularComponentDisqualification(GKInstance protein) throws Exception {
        String issueDisqualifyingProtein = getAnyIssueForAnnotationDisqualification(protein);
        if (issueDisqualifyingProtein.isEmpty() && hasSpeciesWithAlternativeGOComponent(protein)) {
            issueDisqualifyingProtein = protein.getExtendedDisplayName() + " is from a species with an alternative " +
                "GO compartment, skipping GO annotation";
        }
        return issueDisqualifyingProtein;
    }

    private static boolean hasSpeciesWithAlternativeGOComponent(GKInstance protein) throws Exception {
        return speciesWithAlternateGOCompartment.contains(getTaxonIdentifier(protein));
    }

    /**
     * Retrieves the Cellular Compartment accession associated with this protein and then calls the GOA line generator.
     * @param protein -- GKInstance, Individual protein instance from the retrieved proteins.
     * @param reactionlikeEvent -- GKInstance, Parent ReactionlikeEvent with which the protein is associated.
     * @throws Exception -- MySQLAdaptor exception.
     */
    private static String generateGOCellularCompartmentLine(GKInstance protein, GKInstance reactionlikeEvent)
        throws Exception {

        String goCellularCompartmentAccession = getCellularCompartmentGOAccession(protein);
        if (goCellularCompartmentAccession.isEmpty()) {
            logger.info(protein.getExtendedDisplayName() + " has no Cellular Compartment accession, " +
                "skipping GO annotation");
            return "";
        }

        String goaLine = GOAGeneratorUtilities.generateGOALine(
            protein,
            CELLULAR_COMPONENT_LETTER,
            CELLULAR_COMPONENT_QUALIFIER,
            goCellularCompartmentAccession,
            getReactomeIdentifier(reactionlikeEvent),
            TRACEABLE_AUTHOR_STATEMENT_CODE
        );

        GOAGeneratorUtilities.assignDateForGOALine(protein, goaLine);
        return goaLine;
    }

    /**
     * Checks for and returns valid GO accession specific to Cellular Compartments in the protein of interest.
     * @param protein -- GKInstance, Individual protein instance from the retrieved proteins.
     * @return -- String, GO Cellular Compartment accession from protein.
     * @throws Exception -- MySQLAdaptor exception.
     */
    private static String getCellularCompartmentGOAccession(GKInstance protein) throws Exception {
        GKInstance compartment = (GKInstance) protein.getAttributeValue(ReactomeJavaConstants.compartment);
        if (compartment == null) {
            return "";
        }

        return GO_IDENTIFIER_PREFIX + compartment.getAttributeValue(ReactomeJavaConstants.accession).toString();
    }

}
