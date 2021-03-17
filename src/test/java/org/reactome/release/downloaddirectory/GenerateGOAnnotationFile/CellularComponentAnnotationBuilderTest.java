package org.reactome.release.downloaddirectory.GenerateGOAnnotationFile;

import org.gk.model.GKInstance;
import org.gk.model.InstanceUtilities;
import org.gk.model.ReactomeJavaConstants;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Matchers.anyCollection;

@RunWith(PowerMockRunner.class)
@PrepareForTest({CellularComponentAnnotationBuilder.class, GOAGeneratorUtilities.class, InstanceUtilities.class})
@PowerMockIgnore({"org.apache.logging.log4j.*", "javax.management.*", "javax.script.*",
    "javax.xml.*", "com.sun.org.apache.xerces.*", "org.xml.sax.*", "com.sun.xml.*", "org.w3c.dom.*", "org.mockito.*"})

public class CellularComponentAnnotationBuilderTest {

    @Mock
    private GKInstance mockReactionInst;
    @Mock
    private GKInstance mockProteinInst;
    @Mock
    private GKInstance mockReferenceEntityInst;
    @Mock
    private GKInstance mockSpeciesInst;
    @Mock
    private GKInstance mockCrossReferenceInst;
    @Mock
    private GKInstance mockCompartmentInst;

    private Set<GKInstance> mockProteinSet = new HashSet<>();

    @Test
    public void cellularComponentAnnotationLineBuilderTest() throws Exception {
        final String dummyUniProtAccession = "P01234";
        final String dummySecondaryIdentifier = "R5678";
        final String dummyTaxonIdentifier = "1234";
        final String dummyReactomeIdentifier = "REACTOME:R-HSA-1234";
        final String dummyGOAccession = "0004321";
        final String dummyGOAccessionWithPrefix = "GO:" + dummyGOAccession;

        final String expectedCellularComponentGOALine = String.join("\t",
        "UniProtKB", dummyUniProtAccession, dummySecondaryIdentifier , "located_in", dummyGOAccessionWithPrefix,
            dummyReactomeIdentifier, "TAS", "", "C", "", "", "protein", "taxon:" + dummyTaxonIdentifier
        );

        initMocks(dummyTaxonIdentifier);

        mockProteinToReturnNoDisqualificationsForAnnotation();
        mockProteinToReturnMockReferenceEntity();
        mockReferenceEntityToReturnUniProtAccession(dummyUniProtAccession);
        mockProteinToReturnSecondaryIdentifier(dummySecondaryIdentifier);
        mockProteinCompartmentToReturnGOAccession(dummyGOAccession);
        mockReactionlikeEventToReturnReactomeIdentifier(dummyReactomeIdentifier);

        Mockito.when(GOAGeneratorUtilities.generateGOALine(
            mockProteinInst,
            GOAGeneratorConstants.CELLULAR_COMPONENT_LETTER,
            GOAGeneratorConstants.CELLULAR_COMPONENT_QUALIFIER,
            dummyGOAccessionWithPrefix,
            dummyReactomeIdentifier,
            GOAGeneratorConstants.TRACEABLE_AUTHOR_STATEMENT_CODE
        )).thenCallRealMethod();

        List<String> goaLines = CellularComponentAnnotationBuilder.processCellularComponents(mockReactionInst);

        assertThat(goaLines.size(), is(equalTo(1)));
        assertThat(goaLines.get(0), is(equalTo(expectedCellularComponentGOALine)));
    }

    @Test
    public void cellularComponentAlternateGOCompartmentReturnsZeroLinesTest() throws Exception {
        final String alternateGOCellularCompartmentTaxonIdentifier = "11676";
        final String dummyGOAccession = "0004321";

        initMocks(alternateGOCellularCompartmentTaxonIdentifier);
        mockProteinToReturnNoDisqualificationsForAnnotation();
        mockProteinCompartmentToReturnGOAccession(dummyGOAccession);

        List<String> goaLines = CellularComponentAnnotationBuilder.processCellularComponents(mockReactionInst);

        assertThat(goaLines.size(), is(equalTo(0)));
    }

    @Test
    public void cellularComponentEmptyCompartmentReturnsZeroLinesTest() throws Exception {
        final String dummyTaxonIdentifier = "1234";

        initMocks(dummyTaxonIdentifier);
        mockProteinToReturnNoDisqualificationsForAnnotation();
        Mockito.when(mockProteinInst.getAttributeValue(ReactomeJavaConstants.compartment)).thenReturn(null);

        List<String> goaLines = CellularComponentAnnotationBuilder.processCellularComponents(mockReactionInst);

        assertThat(goaLines.size(), is(equalTo(0)));
    }

    @Test
    public void cellularComponentExcludedMicrobialSpeciesReturnsZeroLinesTest() throws Exception {
        final String excludedMicrobialSpeciesTaxonIdentifier = "813";
        final String dummyGOAccession = "0004321";

        initMocks(excludedMicrobialSpeciesTaxonIdentifier);
        mockProteinCompartmentToReturnGOAccession(dummyGOAccession);
        mockProteinToReturnMockReferenceEntity();

        Mockito.when(GOAGeneratorUtilities.isValidProtein(mockProteinInst)).thenReturn(true);
        Mockito.when(GOAGeneratorUtilities.checkProteinForDisqualification(mockProteinInst)).thenCallRealMethod();
        Mockito.when(GOAGeneratorUtilities.hasExcludedMicrobialSpecies(mockProteinInst)).thenCallRealMethod();

        List<String> goaLines = CellularComponentAnnotationBuilder.processCellularComponents(mockReactionInst);

        assertThat(goaLines.size(), is(equalTo(0)));
    }

    private void initMocks(String taxonIdentifier) throws Exception {
        PowerMockito.mockStatic(GOAGeneratorUtilities.class);
        PowerMockito.mockStatic(InstanceUtilities.class);

        PowerMockito.when(InstanceUtilities.followInstanceAttributes(Mockito.any(), anyCollection(), Mockito.any()))
            .thenReturn(mockProteinSet);

        mockProteinSet.add(mockProteinInst);

        Mockito.when(mockProteinInst.getAttributeValue(ReactomeJavaConstants.referenceEntity))
            .thenReturn(mockReferenceEntityInst);
        Mockito.when(mockProteinInst.getAttributeValue(ReactomeJavaConstants.species))
            .thenReturn(mockSpeciesInst);

        Mockito.when((GKInstance) mockSpeciesInst.getAttributeValue(ReactomeJavaConstants.crossReference))
            .thenReturn(mockCrossReferenceInst);
        Mockito.when(mockCrossReferenceInst.getAttributeValue(ReactomeJavaConstants.identifier))
            .thenReturn(taxonIdentifier);
        Mockito.when(GOAGeneratorUtilities.getTaxonIdentifier(mockProteinInst)).thenCallRealMethod();
    }

    private void mockProteinToReturnNoDisqualificationsForAnnotation() throws Exception {
        Mockito.when(GOAGeneratorUtilities.checkProteinForDisqualification(mockProteinInst)).thenReturn("");
    }

    private void mockProteinToReturnMockReferenceEntity() throws Exception {
        Mockito.when(GOAGeneratorUtilities.getReferenceEntityFromProtein(mockProteinInst)).thenCallRealMethod();
    }

    private void mockReferenceEntityToReturnUniProtAccession(String uniprotAccession) throws Exception {
        Mockito.when(mockReferenceEntityInst.getAttributeValue(ReactomeJavaConstants.identifier))
            .thenReturn(uniprotAccession);
    }

    private void mockProteinToReturnSecondaryIdentifier(String secondaryIdentifier) throws Exception {
        Mockito.when(GOAGeneratorUtilities.getSecondaryIdentifier(mockProteinInst)).thenReturn(secondaryIdentifier);
    }

    private void mockProteinCompartmentToReturnGOAccession(String goAccession) throws Exception {
        Mockito.when(mockProteinInst.getAttributeValue(ReactomeJavaConstants.compartment))
            .thenReturn(mockCompartmentInst);
        Mockito.when(mockCompartmentInst.getAttributeValue(ReactomeJavaConstants.accession))
            .thenReturn(goAccession);
    }

    private void mockReactionlikeEventToReturnReactomeIdentifier(String reactomeIdentifier) throws Exception {
        Mockito.when(GOAGeneratorUtilities.getReactomeIdentifier(mockReactionInst)).thenReturn(reactomeIdentifier);
    }
}
