package org.opensha.nshmp.shading;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class NshmpNameMapperTest {

    @Test
    void mapsRepresentativeTopLevelClasses() {
        assertEquals(
                "org/opensha/nshmp/shaded/gmm/NshmpGmm",
                NshmpNameMapper.mapInternalName("gov/usgs/earthquake/nshmp/gmm/Gmm"));
        assertEquals(
                "org/opensha/nshmp/shaded/gmm/NshmpGmmInput",
                NshmpNameMapper.mapInternalName("gov/usgs/earthquake/nshmp/gmm/GmmInput"));
        assertEquals(
                "org/opensha/nshmp/shaded/calc/NshmpSite",
                NshmpNameMapper.mapInternalName("gov/usgs/earthquake/nshmp/calc/Site"));
        assertEquals(
                "org/opensha/nshmp/shaded/geo/NshmpLocation",
                NshmpNameMapper.mapInternalName("gov/usgs/earthquake/nshmp/geo/Location"));
    }

    @Test
    void preservesNestedSuffixes() {
        assertEquals(
                "org/opensha/nshmp/shaded/gmm/NshmpGmmInput$Builder",
                NshmpNameMapper.mapInternalName("gov/usgs/earthquake/nshmp/gmm/GmmInput$Builder"));
        assertEquals(
                "org/opensha/nshmp/shaded/calc/NshmpExceedanceModel$1",
                NshmpNameMapper.mapInternalName("gov/usgs/earthquake/nshmp/calc/ExceedanceModel$1"));
    }

    @Test
    void leavesUnrelatedClassNamesUnchanged() {
        assertEquals(
                "com/google/common/base/Preconditions",
                NshmpNameMapper.mapInternalName("com/google/common/base/Preconditions"));
    }
}
