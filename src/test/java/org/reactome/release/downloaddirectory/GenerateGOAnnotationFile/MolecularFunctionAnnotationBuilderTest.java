package org.reactome.release.downloaddirectory.GenerateGOAnnotationFile;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.schema.SchemaClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.util.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest({MolecularFunctionAnnotationBuilder.class, GOAGeneratorUtilities.class})
@PowerMockIgnore({"org.apache.logging.log4j.*", "javax.management.*", "javax.script.*", "jdk.internal.reflect.*",
        "javax.xml.*", "com.sun.org.apache.xerces.*", "org.xml.sax.*", "com.sun.xml.*", "org.w3c.dom.*", "org.mockito.*"})

public class MolecularFunctionAnnotationBuilderTest {

    @Mock
    private GKInstance mockReactionInst;
    @Mock
    private GKInstance mockCatalystActivityReference;
    @Mock
    private GKInstance mockCatalystInst;
    @Mock
    private GKInstance mockCatalystPEInst;
    @Mock
    private GKInstance mockActiveUnitInst;
    @Mock
    private GKInstance mockMemberInst;
    @Mock
    private GKInstance mockReferenceEntityInst;
    @Mock
    private GKInstance mockSpeciesInst;
    @Mock
    private GKInstance mockCrossReferenceInst;
    @Mock
    private GKInstance mockGOMolecularFunctionInst;
    @Mock
    private GKInstance mockLitRefInst;
    @Mock
    private GKInstance mockStableIdentifierInst;
    @Mock
    private SchemaClass mockSchemaClass;


    private List<GKInstance> mockCatalystActivityReferenceSet = new ArrayList<>();
    private List<GKInstance> mockCatalystActivitySet = new ArrayList<>();
    private List<GKInstance> mockActiveUnitSet = new ArrayList<>();
    private List<GKInstance> mockLitRefSet = new ArrayList<>();
    private List<GKInstance> mockMemberSet = new ArrayList<>();

    @Test
    public void molecularFunctionAnnotationLineBuilderTest() throws Exception {
        PowerMockito.mockStatic(GOAGeneratorUtilities.class);
        mockCatalystActivityReferenceSet.add(mockCatalystActivityReference);
        mockCatalystActivitySet.add(mockCatalystInst);
        mockActiveUnitSet.add(mockActiveUnitInst);
        mockLitRefSet.add(mockLitRefInst);

        Mockito.when(mockReactionInst.getAttributeValuesList(ReactomeJavaConstants.catalystActivity))
                .thenReturn(mockCatalystActivitySet);
        Mockito.when(mockReactionInst.getAttributeValuesList(ReactomeJavaConstants.catalystActivityReference))
                .thenReturn(mockCatalystActivityReferenceSet);
        Mockito.when(mockReactionInst.getAttributeValue(ReactomeJavaConstants.catalystActivityReference))
                .thenReturn(mockCatalystActivityReference);
        Mockito.when(mockCatalystActivityReference.getAttributeValue(ReactomeJavaConstants.catalystActivity))
                .thenReturn(mockCatalystInst);
        Mockito.when(mockCatalystInst.getDBID()).thenReturn(12345L);
        Mockito.when(mockCatalystInst.getAttributeValue(ReactomeJavaConstants.activity)).thenReturn(mockGOMolecularFunctionInst);
        Mockito.when(mockCatalystInst.getAttributeValue(ReactomeJavaConstants.physicalEntity))
                .thenReturn(mockCatalystPEInst);
        Mockito.when(mockCatalystInst.getAttributeValuesList(ReactomeJavaConstants.activeUnit))
                .thenReturn(mockActiveUnitSet);
        Mockito.when(mockCatalystInst.getAttributeValue(ReactomeJavaConstants.activeUnit)).thenReturn(mockActiveUnitInst);
        Mockito.when(GOAGeneratorUtilities.getGOAnnotatableProteinsFromCatalystActivity(mockCatalystInst)).thenReturn(new HashSet<>(mockActiveUnitSet));

        Mockito.when(mockActiveUnitInst.getSchemClass()).thenReturn(mockSchemaClass);
        Mockito.when(mockSchemaClass.isa(ReactomeJavaConstants.EntityWithAccessionedSequence)).thenReturn(true);
        Mockito.when(mockActiveUnitInst.getAttributeValue(ReactomeJavaConstants.referenceEntity)).thenReturn(mockReferenceEntityInst);
        Mockito.when(mockActiveUnitInst.getAttributeValue(ReactomeJavaConstants.species)).thenReturn(mockSpeciesInst);
        Mockito.when(GOAGeneratorUtilities.getAnyIssueForAnnotationDisqualification(mockActiveUnitInst)).thenReturn("");
        Mockito.when(GOAGeneratorUtilities.isValidProtein(mockActiveUnitInst)).thenReturn(true);
        Mockito.when(GOAGeneratorUtilities.hasExcludedMicrobialSpecies(mockActiveUnitInst)).thenReturn(false);
        Mockito.when(GOAGeneratorUtilities.getReferenceEntityFromProtein(mockActiveUnitInst)).thenCallRealMethod();
        Mockito.when(GOAGeneratorUtilities.getTaxonIdentifier(mockActiveUnitInst)).thenCallRealMethod();
        Mockito.when(((GKInstance) mockSpeciesInst.getAttributeValue(ReactomeJavaConstants.crossReference))).thenReturn(mockCrossReferenceInst);
        Mockito.when(mockCrossReferenceInst.getAttributeValue(ReactomeJavaConstants.identifier)).thenReturn("1234");

        Mockito.when(mockCatalystActivityReference.getAttributeValuesList(ReactomeJavaConstants.literatureReference)).thenReturn(mockLitRefSet);
        Mockito.when(mockLitRefInst.getAttributeValue(ReactomeJavaConstants.pubMedIdentifier)).thenReturn("1234");
        Mockito.when(mockGOMolecularFunctionInst.getAttributeValue(ReactomeJavaConstants.accession)).thenReturn("1234");
        Mockito.when(mockReferenceEntityInst.getAttributeValue(ReactomeJavaConstants.identifier)).thenReturn("R1234");
        Mockito.when(GOAGeneratorUtilities.getSecondaryIdentifier(mockActiveUnitInst)).thenReturn("R5678");
        Mockito.when(GOAGeneratorUtilities.getStableIdentifierIdentifier(mockReactionInst)).thenReturn("1234");



        Mockito.when(GOAGeneratorUtilities.generateGOALine(
                mockActiveUnitInst,
                GOAGeneratorConstants.MOLECULAR_FUNCTION_LETTER,
                GOAGeneratorConstants.MOLECULAR_FUNCTION_QUALIFIER,
                "GO:1234",
                "PMID:1234",
                GOAGeneratorConstants.INFERRED_FROM_EXPERIMENT_CODE
        )).thenCallRealMethod();
        Set<String> goaLines = MolecularFunctionAnnotationBuilder.processMolecularFunctions(mockReactionInst);

        assertThat(goaLines.size(), is(equalTo(1)));
        assertThat(goaLines.iterator().next(), is((equalTo("UniProtKB\tR1234\tR5678\tenables\tGO:1234\tPMID:1234\tEXP\t\tF\t\t\tprotein\ttaxon:1234"))));
    }

    @Test
    public void molecularFunctionNoPubMedIdentifierLineBuilderTest() throws Exception {
        PowerMockito.mockStatic(GOAGeneratorUtilities.class);
        mockCatalystActivitySet.add(mockCatalystInst);
        mockActiveUnitSet.add(mockActiveUnitInst);

        Mockito.when(mockReactionInst.getAttributeValuesList(ReactomeJavaConstants.catalystActivity)).thenReturn(mockCatalystActivitySet);
        Mockito.when(GOAGeneratorUtilities.getGOAnnotatableProteinsFromCatalystActivity(mockCatalystInst)).thenReturn(new HashSet<>(mockActiveUnitSet));
        Mockito.when(mockActiveUnitInst.getAttributeValue(ReactomeJavaConstants.referenceEntity)).thenReturn(mockReferenceEntityInst);
        Mockito.when(mockActiveUnitInst.getAttributeValue(ReactomeJavaConstants.species)).thenReturn(mockSpeciesInst);
        Mockito.when(GOAGeneratorUtilities.getAnyIssueForAnnotationDisqualification(mockActiveUnitInst)).thenReturn("");
        Mockito.when(GOAGeneratorUtilities.getReferenceEntityFromProtein(mockActiveUnitInst)).thenCallRealMethod();
        Mockito.when(GOAGeneratorUtilities.getTaxonIdentifier(mockActiveUnitInst)).thenCallRealMethod();
        Mockito.when(GOAGeneratorUtilities.getReactomeIdentifier(mockReactionInst)).thenCallRealMethod();
        Mockito.when(((GKInstance) mockSpeciesInst.getAttributeValue(ReactomeJavaConstants.crossReference))).thenReturn(mockCrossReferenceInst);
        Mockito.when(mockCrossReferenceInst.getAttributeValue(ReactomeJavaConstants.identifier)).thenReturn("1234");
        Mockito.when(mockCatalystInst.getAttributeValue(ReactomeJavaConstants.activity)).thenReturn(mockGOMolecularFunctionInst);
        Mockito.when(mockGOMolecularFunctionInst.getAttributeValue(ReactomeJavaConstants.accession)).thenReturn("1234");
        Mockito.when(mockReferenceEntityInst.getAttributeValue(ReactomeJavaConstants.identifier)).thenReturn("R1234");
        Mockito.when(GOAGeneratorUtilities.getSecondaryIdentifier(mockActiveUnitInst)).thenReturn("R5678");
        Mockito.when(GOAGeneratorUtilities.getStableIdentifierIdentifier(mockReactionInst)).thenReturn("1234");
        Mockito.when(GOAGeneratorUtilities.generateGOALine(
                mockActiveUnitInst,
                GOAGeneratorConstants.MOLECULAR_FUNCTION_LETTER,
                GOAGeneratorConstants.MOLECULAR_FUNCTION_QUALIFIER,
                "GO:1234",
                "REACTOME:1234",
                GOAGeneratorConstants.TRACEABLE_AUTHOR_STATEMENT_CODE
        )).thenCallRealMethod();
        Set<String> goaLines = MolecularFunctionAnnotationBuilder.processMolecularFunctions(mockReactionInst);

        assertThat(goaLines.size(), is(equalTo(1)));
        assertThat(goaLines.iterator().next(), is((equalTo("UniProtKB\tR1234\tR5678\tenables\tGO:1234\tREACTOME:1234\tTAS\t\tF\t\t\tprotein\ttaxon:1234"))));
    }

    @Test
    public void molecularFunctionInvalidCatalystReturnsZeroLinesTest() throws Exception {
        mockCatalystActivitySet.add(mockCatalystInst);

        Mockito.when(mockReactionInst.getAttributeValuesList(ReactomeJavaConstants.catalystActivity)).thenReturn(mockCatalystActivitySet);
        Set<String> goaLines = MolecularFunctionAnnotationBuilder.processMolecularFunctions(mockReactionInst);

        assertThat(goaLines.size(), is(equalTo(0)));
    }

    @Test
    public void molecularFunctionNotOnlyEWASMembersReturnsZeroLinesTest() throws Exception {
        mockMemberSet.add(mockMemberInst);

        Mockito.when(mockReactionInst.getAttributeValuesList(ReactomeJavaConstants.catalystActivity)).thenReturn(mockCatalystActivitySet);
        Mockito.when(mockCatalystInst.getAttributeValue(ReactomeJavaConstants.physicalEntity)).thenReturn(mockCatalystPEInst);
        Mockito.when(mockCatalystInst.getAttributeValuesList(ReactomeJavaConstants.activeUnit)).thenReturn(mockActiveUnitSet);
        Mockito.when(mockCatalystInst.getAttributeValue(ReactomeJavaConstants.activeUnit)).thenReturn(mockActiveUnitInst);
        Mockito.when(mockActiveUnitInst.getSchemClass()).thenReturn(mockSchemaClass);
        Mockito.when(mockSchemaClass.isa(ReactomeJavaConstants.EntitySet)).thenReturn(true);
        Mockito.when(mockActiveUnitInst.getAttributeValuesList(ReactomeJavaConstants.hasMember)).thenReturn(mockMemberSet);
        Mockito.when(mockMemberInst.getSchemClass()).thenReturn(mockSchemaClass);
        Set<String> goaLines = MolecularFunctionAnnotationBuilder.processMolecularFunctions(mockReactionInst);

        assertThat(goaLines.size(), is(equalTo(0)));
    }

    @Test
    public void molecularFunctionNullCatalystActivityReturnsZeroLinesTest() throws Exception {
        mockLitRefSet.add(mockLitRefInst);

        PowerMockito.mockStatic(GOAGeneratorUtilities.class);

        Mockito.when(mockReactionInst.getAttributeValuesList(ReactomeJavaConstants.catalystActivity)).thenReturn(mockCatalystActivitySet);
        Mockito.when(mockCatalystInst.getAttributeValue(ReactomeJavaConstants.physicalEntity)).thenReturn(mockCatalystPEInst);
        Mockito.when(mockCatalystInst.getAttributeValuesList(ReactomeJavaConstants.activeUnit)).thenReturn(mockActiveUnitSet);
        Mockito.when(mockCatalystInst.getAttributeValue(ReactomeJavaConstants.activeUnit)).thenReturn(mockActiveUnitInst);
        Mockito.when(mockActiveUnitInst.getSchemClass()).thenReturn(mockSchemaClass);
        Mockito.when(mockSchemaClass.isa(ReactomeJavaConstants.EntityWithAccessionedSequence)).thenReturn(true);
        Mockito.when(mockActiveUnitInst.getAttributeValue(ReactomeJavaConstants.referenceEntity)).thenReturn(mockReferenceEntityInst);
        Mockito.when(mockActiveUnitInst.getAttributeValue(ReactomeJavaConstants.species)).thenReturn(mockSpeciesInst);
        Mockito.when(GOAGeneratorUtilities.isValidProtein(mockActiveUnitInst)).thenReturn(true);
        Mockito.when(((GKInstance) mockSpeciesInst.getAttributeValue(ReactomeJavaConstants.crossReference))).thenReturn(mockCrossReferenceInst);
        Mockito.when(mockCrossReferenceInst.getAttributeValue(ReactomeJavaConstants.identifier)).thenReturn("1234");
        Mockito.when(mockCatalystInst.getAttributeValue(ReactomeJavaConstants.activity)).thenReturn(null);
        Set<String> goaLines = MolecularFunctionAnnotationBuilder.processMolecularFunctions(mockReactionInst);

        assertThat(goaLines.size(), is(equalTo(0)));
    }

    @Test
    public void proteinBindingAnnotationTest() throws Exception {
        Mockito.when(mockCatalystInst.getAttributeValue(ReactomeJavaConstants.activity))
                .thenReturn(mockGOMolecularFunctionInst);
        Mockito.when(mockGOMolecularFunctionInst.getAttributeValue(ReactomeJavaConstants.accession)).thenReturn("0005515");
        assertThat(MolecularFunctionAnnotationBuilder.isProteinBindingAnnotation(mockCatalystInst), is(equalTo(true)));

        Mockito.when(mockGOMolecularFunctionInst.getAttributeValue(ReactomeJavaConstants.accession)).thenReturn("1234567");
        assertThat(MolecularFunctionAnnotationBuilder.isProteinBindingAnnotation(mockCatalystInst), is(equalTo(false)));
    }
}
