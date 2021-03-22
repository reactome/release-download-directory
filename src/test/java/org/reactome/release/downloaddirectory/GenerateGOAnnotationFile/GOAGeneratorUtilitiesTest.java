package org.reactome.release.downloaddirectory.GenerateGOAnnotationFile;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.schema.SchemaClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;
import static org.reactome.release.downloaddirectory.GenerateGOAnnotationFile.GOAGeneratorConstants.C_TRACHOMATIS_CROSS_REFERENCE;

@RunWith(MockitoJUnitRunner.class)
@PrepareForTest({GOAGeneratorUtilities.class})
@PowerMockIgnore({"org.apache.logging.log4j.*", "javax.management.*", "javax.script.*",
        "javax.xml.*", "com.sun.org.apache.xerces.*", "org.xml.sax.*", "com.sun.xml.*", "org.w3c.dom.*", "org.mockito.*"})


public class GOAGeneratorUtilitiesTest {

    @Mock
    private GKInstance mockProteinInst;
    @Mock
    private GKInstance mockReferenceEntityInst;
    @Mock
    private GKInstance mockSpeciesInst;
    @Mock
    private GKInstance mockRefDatabaseInst;
    @Mock
    private GKInstance mockCrossReferenceInst;
    @Mock
    private GKInstance mockReactionInst;
    @Mock
    private GKInstance mockModifiedInst;

    private List<GKInstance> mockModifiedSet = new ArrayList<>();

    private final String testGOALine = "UniProtKB\tABCD1234\tABCD1\tlocated_in\tA12345\tREACTOME:123456\tTAS\t\tC\t\t\tprotein\ttaxon:54321A";


    @Test
    public void proteinWithSpeciesAndFromUniProtIsValid() throws Exception {
        mockProteinAsEWAS(mockProteinInst);

        Mockito.when(mockProteinInst.getAttributeValue(ReactomeJavaConstants.species)).thenReturn(mockSpeciesInst);
        Mockito.when(mockProteinInst.getAttributeValue(ReactomeJavaConstants.referenceEntity)).thenReturn(mockReferenceEntityInst);
        Mockito.when(mockReferenceEntityInst.getAttributeValue(ReactomeJavaConstants.referenceDatabase)).thenReturn(mockRefDatabaseInst);
        Mockito.when(mockRefDatabaseInst.getDisplayName()).thenReturn("UniProt");

        assertThat(GOAGeneratorUtilities.isValidProtein(mockProteinInst), is(equalTo(true)));
    }

    @Test
    public void proteinWithoutSpeciesIsNotValid() throws Exception {
        Mockito.when(mockProteinInst.getAttributeValue(ReactomeJavaConstants.species)).thenReturn(null);

        assertThat(GOAGeneratorUtilities.isValidProtein(mockProteinInst), is(equalTo(false)));
    }

    @Test
    public void proteinNotFromUniProtIsNotValid() throws Exception {
        mockProteinAsEWAS(mockProteinInst);

        Mockito.when(mockProteinInst.getAttributeValue(ReactomeJavaConstants.species)).thenReturn(mockSpeciesInst);
        Mockito.when(mockReferenceEntityInst.getAttributeValue(ReactomeJavaConstants.referenceDatabase)).thenReturn(mockRefDatabaseInst);
        Mockito.when(mockRefDatabaseInst.getDisplayName()).thenReturn("Database other than UniProt");

        assertThat(GOAGeneratorUtilities.isValidProtein(mockProteinInst), is(equalTo(false)));
    }

    @Test
    public void generateGOALineTest() throws Exception {
        mockProteinAsEWAS(mockProteinInst);
        mockTaxonIdentifierRetrieval("54321A");

        Mockito.when(mockProteinInst.getAttributeValue(ReactomeJavaConstants.referenceEntity)).thenReturn(mockReferenceEntityInst);
        Mockito.when(mockReferenceEntityInst.getAttributeValue(ReactomeJavaConstants.identifier)).thenReturn("ABCD1234");
        Mockito.when(mockReferenceEntityInst.getAttributeValue(ReactomeJavaConstants.geneName)).thenReturn("ABCD1");

        String goaLine = GOAGeneratorUtilities.generateGOALine(
            mockProteinInst,
            "C",
            "located_in",
            "A12345",
            "REACTOME:123456",
            "TAS"
        );

        assertThat(testGOALine, is(equalTo(goaLine)));
    }

    @Test
    public void hasExcludedMicrobialSpeciesIsTrueForMicrobialSpeciesToExclude() throws Exception {
        final String actualExcludedMicrobialSpeciesTaxonIdentifier = C_TRACHOMATIS_CROSS_REFERENCE;

        mockProteinAsEWAS(mockProteinInst);
        mockTaxonIdentifierRetrieval(actualExcludedMicrobialSpeciesTaxonIdentifier);

        assertThat(GOAGeneratorUtilities.hasExcludedMicrobialSpecies(mockProteinInst), is(equalTo(true)));
    }

    @Test
    public void hasExcludedMicrobialSpeciesIsFalseForUnknownMicrobialSpecies() throws Exception {
        final String fakeMicrobialSpeciesTaxonIdentifier = "812";

        mockProteinAsEWAS(mockProteinInst);
        mockTaxonIdentifierRetrieval(fakeMicrobialSpeciesTaxonIdentifier);

        assertThat(GOAGeneratorUtilities.hasExcludedMicrobialSpecies(mockProteinInst), is(equalTo(false)));
    }

    @Test
    public void assignDateForGOALineTest() throws Exception {
        mockModifiedSet.add(mockModifiedInst);

        Mockito.when(mockReactionInst.getAttributeValuesList(ReactomeJavaConstants.modified)).thenReturn(mockModifiedSet);
        Mockito.when(mockModifiedInst.getAttributeValue(ReactomeJavaConstants.dateTime)).thenReturn("2019-01-01 01:01:01.0");

        GOAGeneratorUtilities.assignDateForGOALine(mockReactionInst, testGOALine);
        String testDate = GOAGeneratorUtilities.getDateForGOALine(testGOALine);

        assertThat(testDate, is(equalTo("20190101")));
    }

    private void mockTaxonIdentifierRetrieval(String taxonIdentifier) throws Exception {
        Mockito.when(mockProteinInst.getAttributeValue(ReactomeJavaConstants.species)).thenReturn(mockSpeciesInst);
        Mockito.when(mockSpeciesInst.getAttributeValue(ReactomeJavaConstants.crossReference)).thenReturn(mockCrossReferenceInst);
        Mockito.when(mockCrossReferenceInst.getAttributeValue(ReactomeJavaConstants.identifier)).thenReturn(taxonIdentifier);
    }

    private void mockProteinAsEWAS(GKInstance mockProteinInst) {
        SchemaClass mockSchemaClass = Mockito.mock(SchemaClass.class);

        Mockito.when(mockProteinInst.getSchemClass()).thenReturn(mockSchemaClass);
        Mockito.when(mockSchemaClass.isa(ReactomeJavaConstants.EntityWithAccessionedSequence)).thenReturn(true);
    }
}
