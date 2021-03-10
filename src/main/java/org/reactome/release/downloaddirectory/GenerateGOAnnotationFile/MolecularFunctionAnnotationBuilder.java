package org.reactome.release.downloaddirectory.GenerateGOAnnotationFile;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.schema.SchemaClass;

import static org.reactome.release.downloaddirectory.GenerateGOAnnotationFile.GOAGeneratorConstants.*;
import static org.reactome.release.downloaddirectory.GenerateGOAnnotationFile.GOAGeneratorUtilities.getCatalystActivityProteins;

import java.util.*;

public class MolecularFunctionAnnotationBuilder {

    private static final Logger logger = LogManager.getLogger();

    /**
     * Initial Molecular Function annotations method that iterates through and validates the reaction's catalyst
     * instances, if any exist.
     * @param reactionInst -- GKInstance from ReactionlikeEvent class.
     * @throws Exception -- MySQLAdaptor exception.
     */
    public static List<String> processMolecularFunctions(GKInstance reactionInst) throws Exception {
        List<String> goaLines = new ArrayList<>();
        // As of v74 (September 2020), CatalystActivity literatureReferences are found in a ReactionlikeEvent's
        // 'catalystActivityReference' attribute, not on the CA instance.
        Collection<GKInstance> catalystReferenceInstances = reactionInst.getAttributeValuesList(
            ReactomeJavaConstants.catalystActivityReference
        );
        for (GKInstance catalystReferenceInst : catalystReferenceInstances) {
            // CatalystActivity instances are drawn from the CatalystActivityReference
            GKInstance catalystInst = (GKInstance) catalystReferenceInst.getAttributeValue(
                ReactomeJavaConstants.catalystActivity
            );
            if (catalystInst != null) {
                Set<GKInstance> proteinInstances = getCatalystActivityProteins(catalystInst);
                goaLines.addAll(
                    processProteins(proteinInstances, reactionInst, catalystInst, catalystReferenceInst)
                );
            }
        }
        return goaLines;
    }

    /**
     * Iterates through each protein from a Catalyst's ActiveUnit/PhysicalEntity, filtering out any that are invalid,
     * are from the excluded species or that have no activity value.
     * @param proteinInstances -- Set of GKInstances, EWAS' from the ActiveUnit/PhysicalEntity of the catalyst.
     * @param reactionInst -- GKInstance, parent reaction the catalyst/proteins come from.
     * @param catalystInst -- GKInstance, catalyst instance from reaction.
     * @param catalystReferenceInst -- GKInstance, CatalystActivityReference instance from the incoming reactionInst's
     * 'catalystActivityReference' slot.
     * @throws Exception -- MySQLAdaptor exception.
     */
    private static List<String> processProteins(Set<GKInstance> proteinInstances,
                                                GKInstance reactionInst,
                                                GKInstance catalystInst,
                                                GKInstance catalystReferenceInst) throws Exception {

        List<String> goaLines = new ArrayList<>();
        for (GKInstance proteinInst : proteinInstances) {
            GKInstance referenceEntityInst = (GKInstance) proteinInst.getAttributeValue(
                ReactomeJavaConstants.referenceEntity
            );
            GKInstance speciesInst = (GKInstance) proteinInst.getAttributeValue(ReactomeJavaConstants.species);
            // Check if the protein has any disqualifying attributes.
            boolean validProtein = GOAGeneratorUtilities.isValidProtein(referenceEntityInst, speciesInst);
            if (validProtein) {
                String taxonIdentifier = (
                    (GKInstance) speciesInst.getAttributeValue(ReactomeJavaConstants.crossReference)
                ).getAttributeValue(ReactomeJavaConstants.identifier).toString();
                if (!GOAGeneratorUtilities.isExcludedMicrobialSpecies(taxonIdentifier)) {
                    if (catalystInst.getAttributeValue(ReactomeJavaConstants.activity) != null) {
                        goaLines.addAll(
                            generateGOMolecularFunctionLine(
                                catalystInst, referenceEntityInst, taxonIdentifier, reactionInst, catalystReferenceInst
                            )
                        );
                    } else {
                        logger.info("Catalyst has no GO_MolecularFunction attribute, skipping GO annotation");
                    }
                } else {
                    logger.info("Protein is from an excluded microbial species, skipping GO annotation");
                }
            } else {
                logger.info("Invalid protein, skipping GO annotation");
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
     * @param referenceEntityInst -- GKInstance, ReferenceEntity instance from protein instance.
     * @param taxonIdentifier -- String, CrossReference ID of protein's species.
     * @param reactionInst -- GKInstance, parent reaction instance that protein/catalyst comes from.
     * @param catalystReferenceInst -- GKInstance, CatalystActivityReference instance from the incoming reactionInst's
     * 'catalystActivityReference' slot.
     * @throws Exception -- MySQLAdaptor exception.
     */
    private static List<String> generateGOMolecularFunctionLine(GKInstance catalystInst, GKInstance referenceEntityInst,
                                                                String taxonIdentifier, GKInstance reactionInst,
                                                                GKInstance catalystReferenceInst) throws Exception {
        List<String> goaLines = new ArrayList<>();
        List<String> pubMedIdentifiers = new ArrayList<>();
        // Literature references are drawn from a CatalystActivityReference instance associated with the incoming
        // reactionInst
        for (GKInstance literatureReferenceInst : getLiteratureReferences(catalystReferenceInst)) {
            pubMedIdentifiers.add(
                PUBMED_IDENTIFIER_PREFIX +
                literatureReferenceInst.getAttributeValue(ReactomeJavaConstants.pubMedIdentifier).toString()
            );
        }
        GKInstance activityInst = (GKInstance) catalystInst.getAttributeValue(ReactomeJavaConstants.activity);
        String goaLine = "";
        if (!GOAGeneratorUtilities.isProteinBindingAnnotation(activityInst)) {
            String goAccession =
                GO_IDENTIFIER_PREFIX + activityInst.getAttributeValue(ReactomeJavaConstants.accession).toString();
            if (!pubMedIdentifiers.isEmpty()) {
                for (String pubmedIdentifier : pubMedIdentifiers) {
                    goaLine = GOAGeneratorUtilities.generateGOALine(
                        referenceEntityInst,
                        MOLECULAR_FUNCTION_LETTER,
                        MOLECULAR_FUNCTION_QUALIFIER,
                        goAccession,
                        pubmedIdentifier,
                        INFERRED_FROM_EXPERIMENT_CODE,
                        taxonIdentifier
                    );
                    GOAGeneratorUtilities.assignDateForGOALine(catalystInst, goaLine);
                    goaLines.add(goaLine);
                }
            } else {
                String reactomeIdentifier =
                    REACTOME_IDENTIFIER_PREFIX + GOAGeneratorUtilities.getStableIdentifierIdentifier(reactionInst);
                goaLine = GOAGeneratorUtilities.generateGOALine(
                    referenceEntityInst,
                    MOLECULAR_FUNCTION_LETTER,
                    MOLECULAR_FUNCTION_QUALIFIER,
                    goAccession,
                    reactomeIdentifier,
                    TRACEABLE_AUTHOR_STATEMENT_CODE,
                    taxonIdentifier
                );
                GOAGeneratorUtilities.assignDateForGOALine(catalystInst, goaLine);
                goaLines.add(goaLine);
            }
        } else {
            logger.info("Accession is for protein binding, skipping GO annotation");
        }
        return goaLines;
    }

    private static List<GKInstance> getLiteratureReferences(GKInstance catalystReferenceInst) {
        return (Collection<GKInstance>)
            catalystReferenceInst.getAttributeValuesList(ReactomeJavaConstants.literatureReference);
    }
}
