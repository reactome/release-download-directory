package org.reactome.release.downloaddirectory.GenerateGOAnnotationFile;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;

import static org.reactome.release.downloaddirectory.GenerateGOAnnotationFile.GOAGeneratorConstants.*;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

public class GOAGeneratorUtilities {
    private static final Logger logger = LogManager.getLogger();

    // CrossReference IDs of excluded microbial species:
    // C. trachomatis, E. coli, N. meningitidis, S. typhimurium, S. aureus, and T. gondii
    private static final List<String> microbialSpeciesToExclude = Arrays.asList(
        C_TRACHOMATIS_CROSS_REFERENCE, E_COLI_CROSS_REFERENCE, N_MENINGITIDIS_CROSS_REFERENCE,
        S_AUREUS_CROSS_REFERENCE, S_TYPHIMURIUM_CROSS_REFERENCE, T_GONDII_CROSS_REFERENCE
    );
    private static Map<String, Integer> dates = new HashMap<>();
    private static Set<String> goaLines = new HashSet<>();

    /**
     * Verifies existence of the protein's ReferenceEntity and Species, and that the ReferenceDatabase associated with
     * the ReferenceEntity is from UniProt.
     * @param protein -- GKInstance, Protein to check for correct ReferenceEntity (i.e. UniProt) and existence of
     * species
     * @return -- true/false indicating protein validity.
     * @throws Exception -- MySQLAdaptor exception.
     */
    public static boolean isValidProtein(GKInstance protein) throws Exception {
        return proteinHasSpecies(protein) &&
            getReferenceDatabaseNameForReferenceEntity(protein).equals(UNIPROT_STRING);
    }


    /**
     * Checks the protein to determine if it is invalid (no species or not from UniProt) or has a species that is one
     * in a pre-determined list of microbial species (C. trachomatis, E. coli, N. meningitidis, S. typhimurium,
     * S. aureus, and T. gondii).
     *
     * Returns a String describing the first issue found or an empty String if there is no issue.
     *
     * @param protein Protein to check for issue disqualifying it for using in GAF annotation
     * @return String describing first issue found or an empty String if no issue.
     * @throws Exception Thrown if there is a problem in retrieving information from the database for species or
     * reference database
     */
    public static String getAnyIssueForAnnotationDisqualification(GKInstance protein) throws Exception {
        String proteinIssue = "";
        if (!isValidProtein(protein)) {
            proteinIssue = protein.getExtendedDisplayName() + " is an invalid protein, skipping GO annotation";
        } else if (hasExcludedMicrobialSpecies(protein)) {
            proteinIssue = protein.getExtendedDisplayName() + " is from an excluded microbial species, " +
                "skipping GO annotation";
        }

        return proteinIssue;
    }

    /**
     * Retrieves the Reference Entity for the Protein.
     * @param protein Protein for which to retrieve the reference entity
     * @return GKInstance representing the protein's reference entity (or null if the protein has no reference entity)
     * @throws Exception Thrown if there is a problem in retrieving the protein's reference entity from the database
     */
    public static GKInstance getReferenceEntityFromProtein(GKInstance protein) throws Exception {
         return (GKInstance) protein.getAttributeValue(ReactomeJavaConstants.referenceEntity);
    }

    /**
     * Retrieves protein instances from a CatalystActivity instance's "activeUnit" attribute (or "physicalEntity"
     * attribute when the "activeUnit" is empty) when the attribute value is either:
     * 1) An EWAS
     * 2) An EntitySet whose members are exclusively EWASs
     * @param catalystActivity -- GKInstance, CatalystActivity instance from a ReactionlikeEvent
     * @return -- Set of GKInstances, retrieved protein instances from either ActiveUnit or PhysicalEntity.
     * NOTE: Returns an empty set if the CatalystActivity's ActiveUnit (or PhysicalEntity if ActiveUnit is empty) is
     * not an EWAS or EntitySet with only member EWASs.
     * @throws Exception -- MySQLAdaptor exception.
     */
    public static Set<GKInstance> getGOAnnotatableProteinsFromCatalystActivity(GKInstance catalystActivity)
        throws Exception {

        String warningMessage = validateCatalystActivity(catalystActivity);
        if (!warningMessage.isEmpty()) {
            logger.warn(warningMessage);
            return new HashSet<>();
        }

        Set<GKInstance> proteinInstances = new HashSet<>();
        GKInstance activeUnitOrPhysicalEntity = getActiveUnitIfFilledOrElsePhysicalEntity(catalystActivity);
        if (isAnEWAS(activeUnitOrPhysicalEntity)) {
            proteinInstances.add(activeUnitOrPhysicalEntity);
        } else if (isASetWithOnlyEWASMembers(activeUnitOrPhysicalEntity)) {
            proteinInstances.addAll(
                activeUnitOrPhysicalEntity.getAttributeValuesList(ReactomeJavaConstants.hasMember)
            );
        }
        return proteinInstances;
    }

    /**
     * Builds most of the GO annotation line that will be added to gene_association.reactome.
     * @param protein -- GKInstance, protein for which to get an annotation line.
     * @param goLetter -- String, can be "C", "F" or "P" for Cellular Component, Molecular Function, or Biological
     * Process annotations, respectively.
     * @param goQualifier -- String, GO Qualifier that describes the association meaning between a protein and a
     * GO term
     * @param goAccession -- String, GO accession taken from the protein/catalyst/reaction instance.
     * @param eventIdentifier -- String, StableIdentifier of the protein/catalyst/reaction. Will have either a
     * 'REACTOME' or 'PMID' prefix.
     * @param evidenceCode -- String, Will be either "TAS" (Traceable Author Statement) or "EXP" (Experimentally
     * Inferred). Most will be TAS, unless there is a PMID accession.
     * @return -- GO annotation line, excluding the DateTime and 'Reactome' columns.
     * @throws Exception -- MySQLAdaptor exception.
     */
    public static String generateGOALine(GKInstance protein, String goLetter, String goQualifier,
                                         String goAccession, String eventIdentifier, String evidenceCode)
        throws Exception {

        List<String> goaLine = new ArrayList<>();
        goaLine.add(UNIPROT_KB_STRING);
        goaLine.add(
            getReferenceEntityFromProtein(protein).getAttributeValue(ReactomeJavaConstants.identifier).toString()
        );
        goaLine.add(getSecondaryIdentifier(protein));
        goaLine.add(goQualifier);
        goaLine.add(goAccession);
        goaLine.add(eventIdentifier);
        goaLine.add(evidenceCode);
        goaLine.add("");
        goaLine.add(goLetter);
        goaLine.add("");
        goaLine.add("");
        goaLine.add(PROTEIN_STRING);
        goaLine.add(TAXON_PREFIX + getTaxonIdentifier(protein));
        goaLines.add(String.join("\t", goaLine));
        return String.join("\t", goaLine);
    }

    /**
     * Returns the value for the 'secondaryIdentifier' column in the GOA line.
     * @param protein -- GKInstance, protein from which to get a secondary identifier.
     * @return -- String, value taken from the secondaryIdentifier, geneName or identifier attributes, whichever is
     * not null.
     * @throws Exception -- MySQLAdaptor exception.
     */
    public static String getSecondaryIdentifier(GKInstance protein) throws Exception {
        GKInstance referenceEntity = getReferenceEntityFromProtein(protein);
        if (referenceEntity.getAttributeValue(ReactomeJavaConstants.secondaryIdentifier) != null) {
            return referenceEntity.getAttributeValue(ReactomeJavaConstants.secondaryIdentifier).toString();
        } else if (referenceEntity.getAttributeValue(ReactomeJavaConstants.geneName) != null) {
            return referenceEntity.getAttributeValue(ReactomeJavaConstants.geneName).toString();
        } else {
            return referenceEntity.getAttributeValue(ReactomeJavaConstants.identifier).toString();
        }
    }

    /**
     * Checks if the protein's species is from an microbial taxon that is excluded from being used in GO annotation.
     * @param protein -- GKInstance, Protein of which to check its species' CrossReference identifier.
     * @return -- true if the taxonIdentifier is found in the microbialSpeciesToExclude array, false if not.
     * @throws Exception Thrown if unable to get protein's species or taxon identifier from species' cross reference
     */
    public static boolean hasExcludedMicrobialSpecies(GKInstance protein) throws Exception {
        return microbialSpeciesToExclude.contains(getTaxonIdentifier(protein));
    }

    public static String getReactomeIdentifier(GKInstance event) throws Exception {
        return REACTOME_IDENTIFIER_PREFIX + getStableIdentifierIdentifier(event);
    }

    /**
     * Retrieves the stable identifier string associated with the incoming instance
     * @param eventInst -- GKInstance, Reaction or other Event instance
     * @return -- String, stable identifier string
     * @throws Exception -- MySQL exception
     */
    public static String getStableIdentifierIdentifier(GKInstance eventInst) throws Exception {
        GKInstance stableIdentifierInst =
            (GKInstance) eventInst.getAttributeValue(ReactomeJavaConstants.stableIdentifier);

        return stableIdentifierInst.getAttributeValue(ReactomeJavaConstants.identifier).toString();
    }

    /**
     * Iterates through the lines in the 'goaLines' list, retrieves the date associated with that line and also adds
     * the 'Reactome' column before adding it to the gene_association.reactome file.
     * @throws IOException -- File writing/reading exceptions.
     */
    public static void outputGOAFile() throws IOException {

        Path goaFilepath = Paths.get(GOA_FILENAME);

        Files.deleteIfExists(goaFilepath);
        Files.createFile(goaFilepath);

        writeHeader(goaFilepath);

        List<String> lines = goaLines.stream().sorted().map(
            line -> String.join("\t", line, dates.get(line).toString(), REACTOME_STRING, "","")
        ).collect(Collectors.toList());

        Files.write(goaFilepath, lines, StandardOpenOption.APPEND);

        gzipGOAFile(goaFilepath);
    }

    /**
     * Finds most recent modification date for a GOA line. This is a bit of a moving target since GOA lines can be
     * generated convergently for each type of GO annotation. Depending on if it is looking at the individual protein
     * or whole reaction level, the date attribute may not be the most recent. If it is found that the goaLine was
     * generated earlier but that a more recent modification date exists based on the entity that is currently being
     * checked, then it will just update that date value in the hash associated with the line. (Yes, this is weird).
     * @param entityInst -- GKInstance, Protein/catalyst/reaction that is receiving a GO annotation.
     * @param goaLine -- String, GO annotation line, used for checking the 'dates' structure.
     * @return -- int, parsed from the dateTime of the entityInst's modified or created attributes.
     * @throws Exception -- MySQLAdaptor exception.
     */
    public static Integer assignDateForGOALine(GKInstance entityInst, String goaLine) throws Exception {
        int instanceDate;
        Collection<GKInstance> modifiedInstances = entityInst.getAttributeValuesList(ReactomeJavaConstants.modified);
        if (!modifiedInstances.isEmpty()) {
            List<GKInstance> modifiedInstancesList = new ArrayList<>(modifiedInstances);
            GKInstance mostRecentModifiedInst = modifiedInstancesList.get(modifiedInstancesList.size() - 1);
            instanceDate = getDate(mostRecentModifiedInst);
        } else {
            GKInstance createdInst = (GKInstance) entityInst.getAttributeValue(ReactomeJavaConstants.created);
            instanceDate = getDate(createdInst);
        }

        // Stores date in global hash that allows date value to be updated if a more recent date was found.
        if (dates.get(goaLine) == null || instanceDate > dates.get(goaLine)) {
            dates.put(goaLine, instanceDate);
        }
        return instanceDate;
    }

    static String getTaxonIdentifier(GKInstance protein) throws Exception {
        GKInstance speciesInst = (GKInstance) protein.getAttributeValue(ReactomeJavaConstants.species);

        return ((GKInstance) speciesInst.getAttributeValue(ReactomeJavaConstants.crossReference))
            .getAttributeValue(ReactomeJavaConstants.identifier).toString();
    }

    /**
     * Retrieves date from instance and formats it for GO annotation file.
     * @param instanceEditInst -- GKInstance, instanceEdit from either a Modified or Created instance.
     * @return -- Integer, from instanceEdit's dateTime. Parsed to remove the Timestamp.
     * @throws Exception -- MySQLAdaptor exception.
     */
    private static int getDate(GKInstance instanceEditInst) throws Exception {
        return Integer.valueOf(
                instanceEditInst.getAttributeValue(ReactomeJavaConstants.dateTime)
                        .toString()
                        .split(" ")[0]
                        .replaceAll("-", "")
        );
    }

    private static void writeHeader(Path goaFilepath) throws IOException {
        final String gafHeader = String.join(System.lineSeparator(),
            "!gaf-version: 2.2",
            "generated-by: Reactome",
            "date-generated: " + getCurrentDateAsYYYYMMDD()
        ).concat(System.lineSeparator());

        Files.write(goaFilepath, gafHeader.getBytes(), StandardOpenOption.APPEND);
    }

    private static String getCurrentDateAsYYYYMMDD() {
        return Instant.now().atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }

    /**
     * Gzips gene_association.reactome file
     * @param goaFilePath Path to the unzipped gene_association.reactome file
     * @throws IOException -- File writing/reading exceptions.
     */
    private static void gzipGOAFile(Path goaFilePath) throws IOException {
        Path goaFilePathGZipped = Paths.get(goaFilePath.toAbsolutePath().toString() + ".gz");
        GZIPOutputStream gzipOutputStream = new GZIPOutputStream(new FileOutputStream(goaFilePathGZipped.toFile()));
        try (FileInputStream fileInputStream = new FileInputStream(goaFilePath.toFile())) {
            byte[] buffer = new byte[1024];
            int length;
            while((length=fileInputStream.read(buffer)) != -1) {
                gzipOutputStream.write(buffer, 0, length);
            }
            gzipOutputStream.close();
        }
    }

    private static GKInstance getActiveUnitIfFilledOrElsePhysicalEntity(GKInstance catalystActivity) throws Exception {
        GKInstance activeUnitInst = (GKInstance) catalystActivity.getAttributeValue(ReactomeJavaConstants.activeUnit);

        if (activeUnitInst != null) {
            return activeUnitInst;
        } else {
            return (GKInstance) catalystActivity.getAttributeValue(ReactomeJavaConstants.physicalEntity);
        }
    }

    private static String validateCatalystActivity(GKInstance catalystActivity) throws Exception {
        GKInstance activeUnitOrPhysicalEntity = getActiveUnitIfFilledOrElsePhysicalEntity(catalystActivity);
        String warningMessage = "";
        if (activeUnitOrPhysicalEntity == null) {
            warningMessage = "Active Unit/Physical Entity in " + catalystActivity.getExtendedDisplayName() +
                " is null - skipping annotation";
        } else if (!hasCompartment(activeUnitOrPhysicalEntity)) {
            warningMessage = "Active Unit/Physical Entity " + activeUnitOrPhysicalEntity.getExtendedDisplayName() +
                " has no compartment in Catalyst Activity " + catalystActivity.getExtendedDisplayName() + " -" +
                " skipping annotation";
        } else if (!isAnEWAS(activeUnitOrPhysicalEntity) && !isASetWithOnlyEWASMembers(activeUnitOrPhysicalEntity)) {
            warningMessage = "Active Unit/Physical Entity " + activeUnitOrPhysicalEntity.getExtendedDisplayName() +
                " is not an EWAS or an EntitySet with only EWAS members - skipping annotation";
        }
        return warningMessage;
    }

    /**
     * Shared catalyst validation method between MolecularFunction and BiologicalProcess classes that checks for
     * existence of compartment attribute.
     * @param catalystPEInst -- PhysicalEntity attribute from a Catalyst instance
     * @return -- true/false indicating PhysicalEntity validity.
     * @throws Exception -- MySQLAdaptor exception.
     */
    private static boolean hasCompartment(GKInstance catalystPEInst) throws Exception {
        return catalystPEInst != null && catalystPEInst.getAttributeValue(ReactomeJavaConstants.compartment) != null;
    }

    private static boolean isAnEWAS(GKInstance physicalEntity) {
        return physicalEntity.getSchemClass().isa(ReactomeJavaConstants.EntityWithAccessionedSequence);
    }

    private static boolean isASetWithOnlyEWASMembers(GKInstance physicalEntity) throws Exception {
        return physicalEntity.getSchemClass().isa(ReactomeJavaConstants.EntitySet) &&
            hasOnlyEWASMembers(physicalEntity);
    }


    /**
     * Checks that the incoming EntitySet instance only has EWAS members
     * @param entitySetInst -- GKInstance,  ActiveUnit/PhysicalEntity of a catalyst that is an EntitySet
     * @return -- boolean, true if the instance only contains EWAS' in its hasMember attribute, false if not.
     * @throws Exception -- MySQLAdaptor exception.
     */
    private static boolean hasOnlyEWASMembers(GKInstance entitySetInst) throws Exception {
        Collection<GKInstance> memberInstances = (Collection<GKInstance>) entitySetInst.getAttributeValuesList(
            ReactomeJavaConstants.hasMember
        );
        return memberInstances
            .stream()
            .allMatch(member -> member.getSchemClass().isa(ReactomeJavaConstants.EntityWithAccessionedSequence));
    }

    private static boolean proteinHasSpecies(GKInstance protein) throws Exception {
        GKInstance proteinSpecies = getSpecies(protein);
        return proteinSpecies != null;
    }

    private static GKInstance getSpecies(GKInstance instance) throws Exception {
        return (GKInstance) instance.getAttributeValue(ReactomeJavaConstants.species);
    }

    private static String getReferenceDatabaseNameForReferenceEntity(GKInstance protein) throws Exception {
        GKInstance referenceDatabase = getReferenceDatabaseForReferenceEntity(protein);

        return referenceDatabase != null ? referenceDatabase.getDisplayName() : "";
    }

    private static GKInstance getReferenceDatabaseForReferenceEntity(GKInstance protein) throws Exception {
        GKInstance referenceEntity = getReferenceEntityFromProtein(protein);

        return referenceEntity != null ?
                (GKInstance) referenceEntity.getAttributeValue(ReactomeJavaConstants.referenceDatabase) :
                null;
    }
}
