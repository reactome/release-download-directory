package org.reactome.release.downloaddirectory.GenerateGOAnnotationFile;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;

import static org.reactome.release.downloaddirectory.GenerateGOAnnotationFile.GOAGeneratorConstants.*;
import static org.reactome.release.downloaddirectory.GenerateGOAnnotationFile.GOAGeneratorUtilities.*;

import java.util.*;

public class MolecularFunctionAnnotationBuilder {

    private static final Logger logger = LogManager.getLogger();

    /**
     * Initial Molecular Function annotations method that iterates through and validates the reaction's catalyst
     * instances, if any exist.
     * @param reactionlikeEvent -- GKInstance from ReactionlikeEvent class.
     * @throws Exception -- MySQLAdaptor exception.
     */
    public static List<String> processMolecularFunctions(GKInstance reactionlikeEvent) throws Exception {
        List<String> goaLines = new ArrayList<>();

        for (GKInstance catalystActivity : getCatalystActivitiesWithAnActivityValue(reactionlikeEvent)) {
            for (GKInstance protein : getGOAnnotatableProteinsFromCatalystActivity(catalystActivity)) {
                String issueDisqualifyingProtein = checkProteinForDisqualification(protein);
                if (issueDisqualifyingProtein.isEmpty()) {
                    goaLines.addAll(generateGOMolecularFunctionLines(catalystActivity, protein, reactionlikeEvent));
                } else {
                    logger.warn(issueDisqualifyingProtein);
                }
            }
        }
        return goaLines;
    }

    /**
     * Generating GOA lines for MF annotations depends on if the catalyst has any literatureReferences (meaning it has
     * a PubMed annotation). If it does, multiple GOA lines that are specific to each PubMed annotation will be output,
     * or, if there are no literatureReferences just a single line with a Reactome identifier will be output.
     * The GOA line generation will be called differently depending on this.
     * @param catalystInst -- GKInstance, catalyst instance from reaction.
     * @param protein -- GKInstance, protein for which to generate a annotation line
     * @param reactionInst -- GKInstance, parent reaction instance that protein/catalyst comes from.
     * @throws Exception -- MySQLAdaptor exception.
     */
    private static List<String> generateGOMolecularFunctionLines(GKInstance catalystInst, GKInstance protein,
                                                                GKInstance reactionInst) throws Exception {
        List<String> goaLines = new ArrayList<>();

        if (isProteinBindingAnnotation(catalystInst)) {
            logger.info("Accession is for protein binding, skipping GO annotation");
            return goaLines;
        }

        List<String> pubMedIdentifiers = getPubMedIdentifiers(catalystInst, reactionInst);
        if (!pubMedIdentifiers.isEmpty()) {
            return generateGOMolecularFunctionLinesForLiteratureReferences(protein, catalystInst, pubMedIdentifiers);
        } else {
            String reactomeIdentifier =
                REACTOME_IDENTIFIER_PREFIX + GOAGeneratorUtilities.getStableIdentifierIdentifier(reactionInst);

            return generateGOMolecularFunctionLinesForReactomeReferences(protein, catalystInst, reactomeIdentifier);
        }
    }

    private static List<String> generateGOMolecularFunctionLinesForLiteratureReferences(
        GKInstance protein, GKInstance catalystActivity, List<String> pubMedIdentifiers
    ) throws Exception {
        List<String> goMFLinesForLiteratureReferences = new ArrayList<>();
        for (String pubmedIdentifier : pubMedIdentifiers) {
            goMFLinesForLiteratureReferences.add(
                generateGOMolecularFunctionLineForAnnotationWithLiteratureReference(
                    protein, catalystActivity, pubmedIdentifier
                )
            );
        }
        return goMFLinesForLiteratureReferences;
    }

    private static List<String> generateGOMolecularFunctionLinesForReactomeReferences(
        GKInstance protein, GKInstance catalystActivity, String reactomeIdentifier
    ) throws Exception {
        List<String> goMFLinesForReactomeReference = new ArrayList<>();
        goMFLinesForReactomeReference.add(
            generateGOMolecularFunctionLine(
                protein, catalystActivity, reactomeIdentifier, TRACEABLE_AUTHOR_STATEMENT_CODE
            )
        );
        return goMFLinesForReactomeReference;
    }

    private static String generateGOMolecularFunctionLineForAnnotationWithLiteratureReference(
        GKInstance protein, GKInstance catalystActivity, String identifier
    ) throws Exception {
        return generateGOMolecularFunctionLine(protein, catalystActivity, identifier, INFERRED_FROM_EXPERIMENT_CODE);
    }

    private static String generateGOMolecularFunctionLine(
        GKInstance protein, GKInstance catalystActivity, String identifier, String evidenceCode
    ) throws Exception {
        String goaLine = GOAGeneratorUtilities.generateGOALine(
            protein,
            MOLECULAR_FUNCTION_LETTER,
            MOLECULAR_FUNCTION_QUALIFIER,
            getGOAccession(catalystActivity),
            identifier,
            evidenceCode
        );

        GOAGeneratorUtilities.assignDateForGOALine(catalystActivity, goaLine);
        return goaLine;
    }

    @SuppressWarnings("unchecked")
    private static List<GKInstance> getCatalystActivitiesWithAnActivityValue(GKInstance reactionlikeEvent)
            throws Exception {

        Collection<GKInstance> allCatalystActivities = reactionlikeEvent.getAttributeValuesList(
                ReactomeJavaConstants.catalystActivity
        );

        List<GKInstance> validCatalystActivities = new ArrayList<>();
        for (GKInstance catalystActivity : allCatalystActivities) {
            if (catalystActivity.getAttributeValue(ReactomeJavaConstants.activity) != null) {
                validCatalystActivities.add(catalystActivity);
            } else {
                logger.warn("Catalyst has no GO_MolecularFunction attribute, skipping GO annotation");
            }
        }
        return validCatalystActivities;
    }

    private static String getGOAccession(GKInstance catalystActivity) throws Exception {
        GKInstance activityInst = (GKInstance) catalystActivity.getAttributeValue(ReactomeJavaConstants.activity);
        return GO_IDENTIFIER_PREFIX + activityInst.getAttributeValue(ReactomeJavaConstants.accession).toString();
    }

    /**
     * Checks that the GO accession is not for Protein Binding (Molecular Function - GO:0005515).
     * These don't receive a GO annotation since they require an "IPI" evidence code.
     * @param catalystActivity -- GKInstance, CatalystActivity instance for which to check the activity to see if it is
     * the protein binding GO term
     * @return -- true if goAccession matches the protein binding annotation value, false if not.
     */
    static boolean isProteinBindingAnnotation(GKInstance catalystActivity) throws Exception {
        return getGOAccession(catalystActivity).equals(PROTEIN_BINDING_ANNOTATION);
    }

    private static List<String> getPubMedIdentifiers(GKInstance catalystInst, GKInstance reactionInst)
            throws Exception {

        List<String> pubMedIdentifiers = new ArrayList<>();
        // Literature references are drawn from a CatalystActivityReference instance associated with the incoming
        // reactionInst
        for (GKInstance literatureReference : getLiteratureReferences(catalystInst, reactionInst)) {
            pubMedIdentifiers.add(
                PUBMED_IDENTIFIER_PREFIX +
                    literatureReference.getAttributeValue(ReactomeJavaConstants.pubMedIdentifier).toString()
            );
        }

        return pubMedIdentifiers;
    }

    @SuppressWarnings("unchecked")
    // As of v74 (September 2020), CatalystActivity literatureReferences are found in a ReactionlikeEvent's
    // 'catalystActivityReference' attribute, not on the CA instance.
    private static List<GKInstance> getLiteratureReferences(GKInstance catalystActivity, GKInstance reaction)
            throws Exception {

        List<GKInstance> literatureReferences = new ArrayList<>();

        GKInstance catalystActivityReference = getCatalystActivityReference(reaction);
        if (catalystActivityReference != null &&
                catalystActivityIsInCatalystActivityReference(catalystActivity, catalystActivityReference)) {
            literatureReferences.addAll(
                catalystActivityReference.getAttributeValuesList(ReactomeJavaConstants.literatureReference)
            );
        }

        return literatureReferences;
    }

    private static GKInstance getCatalystActivityReference(GKInstance reaction) throws Exception {
        return (GKInstance) reaction.getAttributeValue(ReactomeJavaConstants.catalystActivityReference);
    }

    private static boolean catalystActivityIsInCatalystActivityReference(
        GKInstance catalystActivity, GKInstance catalystActivityReference
    ) throws Exception {
        GKInstance catalystActivityFromCatalystActivityReference =
            (GKInstance) catalystActivityReference.getAttributeValue(ReactomeJavaConstants.catalystActivity);

        return catalystActivityFromCatalystActivityReference.getDBID()
                .equals(catalystActivity.getDBID());
    }
}
