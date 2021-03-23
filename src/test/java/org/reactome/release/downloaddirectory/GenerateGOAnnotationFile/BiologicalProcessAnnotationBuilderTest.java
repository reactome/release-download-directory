package org.reactome.release.downloaddirectory.GenerateGOAnnotationFile;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
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
@PrepareForTest({BiologicalProcessAnnotationBuilder.class, GOAGeneratorUtilities.class})
@PowerMockIgnore({"org.apache.logging.log4j.*", "javax.management.*", "javax.script.*",
    "javax.xml.*", "com.sun.org.apache.xerces.*", "org.xml.sax.*", "com.sun.xml.*", "org.w3c.dom.*", "org.mockito.*"})

public class BiologicalProcessAnnotationBuilderTest {
    @Mock
    private GKInstance mockReactionInst;
    @Mock
    private GKInstance mockCatalystInst;
    @Mock
    private GKInstance mockCatalystPEMemberInst;
    @Mock
    private GKInstance mockReferenceEntityInst;
    @Mock
    private GKInstance mockSpeciesInst;
    @Mock
    private GKInstance mockCrossReferenceInst;
    @Mock
    private GKInstance mockEventReferralInst;
    @Mock
    private GKInstance mockGOBioProcessInst;
    @Mock
    private GKInstance mockProteinInst;

    private List<GKInstance> mockCatalystSet = new ArrayList<>();
    private List<GKInstance> mockMemberSet = new ArrayList<>();
    private List<GKInstance> mockEventReferralSet = new ArrayList<>();
    private List<GKInstance> mockGOBioProcessSet = new ArrayList<>();

    private Set<GKInstance> mockProteinSet = new HashSet<>();

    @Test
    public void biologicalProcessAnnotationWithCatalystLineBuilderTest() throws Exception {
        PowerMockito.mockStatic(GOAGeneratorUtilities.class);
        mockCatalystSet.add(mockCatalystInst);
        mockMemberSet.add(mockCatalystPEMemberInst);
        mockEventReferralSet.add(mockEventReferralInst);
        mockGOBioProcessSet.add(mockGOBioProcessInst);

        Mockito.when(mockReactionInst.getAttributeValuesList(ReactomeJavaConstants.catalystActivity))
            .thenReturn(mockCatalystSet);
        Mockito.when(mockCatalystPEMemberInst.getAttributeValue(ReactomeJavaConstants.referenceEntity))
            .thenReturn(mockReferenceEntityInst);
        Mockito.when(mockCatalystPEMemberInst.getAttributeValue(ReactomeJavaConstants.species))
            .thenReturn(mockSpeciesInst);
        Mockito.when(GOAGeneratorUtilities.getGOAnnotatableProteinsFromCatalystActivity(mockCatalystInst))
            .thenReturn(new HashSet<>(mockMemberSet));
        Mockito.when(GOAGeneratorUtilities.getAnyIssueForAnnotationDisqualification(mockCatalystPEMemberInst))
            .thenReturn("");
        Mockito.when(GOAGeneratorUtilities.getReferenceEntityFromProtein(mockCatalystPEMemberInst))
            .thenCallRealMethod();
        Mockito.when(GOAGeneratorUtilities.getTaxonIdentifier(mockCatalystPEMemberInst))
            .thenCallRealMethod();
        Mockito.when((GKInstance) mockSpeciesInst.getAttributeValue(ReactomeJavaConstants.crossReference))
            .thenReturn(mockCrossReferenceInst);
        Mockito.when(mockCrossReferenceInst.getAttributeValue(ReactomeJavaConstants.identifier))
            .thenReturn("1234");
        Mockito.when(mockReactionInst.getReferers(ReactomeJavaConstants.hasEvent))
            .thenReturn(mockEventReferralSet);
        Mockito.when(mockEventReferralInst.getAttributeValuesList(ReactomeJavaConstants.goBiologicalProcess))
            .thenReturn(mockGOBioProcessSet);
        Mockito.when(mockGOBioProcessInst.getAttributeValue(ReactomeJavaConstants.accession))
            .thenReturn("1234");
        Mockito.when(mockReferenceEntityInst.getAttributeValue(ReactomeJavaConstants.identifier))
            .thenReturn("R1234");
        Mockito.when(GOAGeneratorUtilities.getSecondaryIdentifier(mockCatalystPEMemberInst))
            .thenReturn("R5678");
        Mockito.when(GOAGeneratorUtilities.getReactomeIdentifier(mockEventReferralInst))
            .thenReturn("REACTOME:1234");
        Mockito.when(GOAGeneratorUtilities.generateGOALine(
            mockCatalystPEMemberInst,
            GOAGeneratorConstants.BIOLOGICAL_PROCESS_LETTER,
            GOAGeneratorConstants.BIOLOGICAL_PROCESS_QUALIFIER,
            "GO:1234",
            "REACTOME:1234",
            GOAGeneratorConstants.TRACEABLE_AUTHOR_STATEMENT_CODE
        )).thenCallRealMethod();
        Set<String> goaLines = BiologicalProcessAnnotationBuilder.processBiologicalFunctions(mockReactionInst);

        assertThat(goaLines.size(), is(equalTo(1)));
        assertThat(goaLines.iterator().next(), is((equalTo(
            "UniProtKB\tR1234\tR5678\tinvolved_in\tGO:1234\tREACTOME:1234\tTAS\t\tP\t\t\tprotein\ttaxon:1234")
        )));
    }

    @Test
    public void biologicalProcessAnnotationBuilderWithoutCatalystLineBuilderTest() throws Exception {
        PowerMockito.mockStatic(GOAGeneratorUtilities.class);
        mockCatalystSet.add(mockCatalystInst);
        mockProteinSet.add(mockProteinInst);
        mockGOBioProcessSet.add(mockGOBioProcessInst);

        Mockito.when(mockReactionInst.getAttributeValuesList(ReactomeJavaConstants.catalystActivity))
            .thenReturn(mockCatalystSet);
        Mockito.when(mockProteinInst.getAttributeValue(ReactomeJavaConstants.referenceEntity))
            .thenReturn(mockReferenceEntityInst);
        Mockito.when(mockProteinInst.getAttributeValue(ReactomeJavaConstants.species))
            .thenReturn(mockSpeciesInst);
        Mockito.when(GOAGeneratorUtilities.getGOAnnotatableProteinsFromCatalystActivity(mockCatalystInst))
            .thenReturn(mockProteinSet);
        Mockito.when(GOAGeneratorUtilities.getAnyIssueForAnnotationDisqualification(mockProteinInst))
            .thenReturn("");
        Mockito.when(GOAGeneratorUtilities.getReferenceEntityFromProtein(mockProteinInst))
            .thenCallRealMethod();
        Mockito.when(GOAGeneratorUtilities.getTaxonIdentifier(mockProteinInst))
            .thenCallRealMethod();
        Mockito.when(((GKInstance) mockSpeciesInst.getAttributeValue(ReactomeJavaConstants.crossReference)))
            .thenReturn(mockCrossReferenceInst);
        Mockito.when(mockCrossReferenceInst.getAttributeValue(ReactomeJavaConstants.identifier))
            .thenReturn("1234");
        Mockito.when(mockReactionInst.getAttributeValuesList(ReactomeJavaConstants.goBiologicalProcess))
            .thenReturn(mockGOBioProcessSet);
        Mockito.when(mockGOBioProcessInst.getAttributeValue(ReactomeJavaConstants.accession))
            .thenReturn("1234");
        Mockito.when(mockReferenceEntityInst.getAttributeValue(ReactomeJavaConstants.identifier))
            .thenReturn("R1234");
        Mockito.when(GOAGeneratorUtilities.getSecondaryIdentifier(mockProteinInst))
            .thenReturn("R5678");
        Mockito.when(GOAGeneratorUtilities.getReactomeIdentifier(mockReactionInst))
            .thenReturn("REACTOME:1234");

        Mockito.when(GOAGeneratorUtilities.generateGOALine(
            mockProteinInst,
            GOAGeneratorConstants.BIOLOGICAL_PROCESS_LETTER,
            GOAGeneratorConstants.BIOLOGICAL_PROCESS_QUALIFIER,
            "GO:1234",
            "REACTOME:1234",
            GOAGeneratorConstants.TRACEABLE_AUTHOR_STATEMENT_CODE
        )).thenCallRealMethod();
        Set<String> goaLines = BiologicalProcessAnnotationBuilder.processBiologicalFunctions(mockReactionInst);

        assertThat(goaLines.size(), is(equalTo(1)));
        assertThat(goaLines.iterator().next(), is((equalTo(
            "UniProtKB\tR1234\tR5678\tinvolved_in\tGO:1234\tREACTOME:1234\tTAS\t\tP\t\t\tprotein\ttaxon:1234")
        )));
    }
}
