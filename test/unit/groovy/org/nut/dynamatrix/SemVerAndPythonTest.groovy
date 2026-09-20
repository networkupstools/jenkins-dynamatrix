package org.nut.dynamatrix

import org.junit.jupiter.api.Test
import static org.junit.jupiter.api.Assertions.*

class SemVerAndPythonTest {

    @Test
    void testParseSemVer() {
        assertEquals([19, 0, 0], Utils.parseSemVer("19"))
        assertEquals([19, 1, 0], Utils.parseSemVer("19.1"))
        assertEquals([4, 4, 4], Utils.parseSemVer("4.4.4"))
        assertEquals([4, 4, 4], Utils.parseSemVer("gcc-4.4.4-illumos"))
        assertEquals([10, 2, 1], Utils.parseSemVer("gcc version 10.2.1-custom"))
    }

    @Test
    void testCompareSemVer() {
        assertTrue(Utils.compareSemVer("19", "19.1") < 0)
        assertTrue(Utils.compareSemVer("19.1", "19") > 0)
        assertEquals(0, Utils.compareSemVer("19", "19.0.0"))
        assertTrue(Utils.compareSemVer("4.4.4", "4.9") < 0)
        assertTrue(Utils.compareSemVer("4.9", "10") < 0)
        assertTrue(Utils.compareSemVer("10.1", "10.0.1") > 0)
        assertEquals(0, Utils.compareSemVer("gcc-4.4.4-illumos", "4.4.4"))
    }

    @Test
    void testSortSemVer() {
        List<String> list = ["19.1", "4.4.4", "19", "10", "4.9", "gcc-4.4.4-illumos"]
        List<String> sortedAsc = Utils.sortSemVer(list, true)
        assertEquals("4.4.4", sortedAsc[0])
        assertEquals("19.1", sortedAsc[sortedAsc.size() - 1])

        List<String> sortedDesc = Utils.sortSemVer(list, false)
        assertEquals("19.1", sortedDesc[0])
    }

    @Test
    void testResolveExtremeVersionWithoutConstraints() {
        List<String> gccVersions = ["4.4.4", "4.8.0", "4.8.2", "4.9", "7", "10"]
        assertEquals("4.4.4", Utils.resolveExtremeVersion("GCCVER", "MIN", gccVersions))
        assertEquals("10", Utils.resolveExtremeVersion("GCCVER", "MAX", gccVersions))

        List<String> pyVersions = ["2.7", "3.8", "3.9", "3.10", "3.12"]
        assertEquals("2.7", Utils.resolveExtremeVersion("PYTHONVER", "MIN", pyVersions))
        assertEquals("3.12", Utils.resolveExtremeVersion("PYTHONVER", "MAX", pyVersions))
    }

    @Test
    void testResolveExtremeVersionWithStandardConstraints() {
        List<String> gccVersions = ["4.4.4", "4.8.0", "4.8.2", "4.9", "7", "10"]
        DynamatrixConfig cfg = new DynamatrixConfig(null)
        cfg.initDefault("C")

        // C++11 constraint: GCC 4.4.4 and 4.8.0 should be excluded, 4.8.2 should be the MIN supported version
        String minC11 = Utils.resolveExtremeVersion("GCCVER", "MIN", gccVersions, ["CSTDVERSION=11"], cfg.excludeCombos)
        assertEquals("4.8.2", minC11)

        // C++17 constraint: GCC 10 should be the MIN supported version (from available list where 7 supports up to 14)
        String minC17 = Utils.resolveExtremeVersion("GCCVER", "MIN", gccVersions, ["CSTDVERSION=17"], cfg.excludeCombos)
        assertEquals("10", minC17)

        // MAX version with C++11 constraint should still be 10
        String maxC11 = Utils.resolveExtremeVersion("GCCVER", "MAX", gccVersions, ["CSTDVERSION=11"], cfg.excludeCombos)
        assertEquals("10", maxC11)
    }

    @Test
    void testNodeCapsPythonAndExtremeResolution() {
        NodeData nd = new NodeData()
        nd.nodeName = "test-node"
        nd.labelMap = [
            'COMPILER': ['GCC'] as TreeSet,
            'COMPILER=GCC': null,
            'GCCVER': ['4.4.4', '4.9', '10'] as TreeSet,
            'GCCVER=4.4.4': null,
            'GCCVER=4.9': null,
            'GCCVER=10': null,
            'PYTHON': ['3.9', '3.10', '2.7'] as TreeSet,
            'PYTHON=3.9': null,
            'PYTHON=3.10': null,
            'PYTHON=2.7': null,
            'OS_DISTRO': ['debian'] as TreeSet,
            'OS_DISTRO=debian': null
        ]

        NodeCaps nc = new NodeCaps()
        nc.nodeData = ["test-node": nd]
        nc.isInitialized = true

        // Test PYTHONVER axis values resolution
        Set pyVals = nc.resolveAxisValues("PYTHONVER", "test-node", false)
        assertTrue(pyVals.contains("3.9"))
        assertTrue(pyVals.contains("3.10"))
        assertTrue(pyVals.contains("2.7"))

        Set pyAssigns = nc.resolveAxisValues("PYTHONVER", "test-node", true)
        assertTrue(pyAssigns.contains("PYTHON=3.9") || pyAssigns.contains("PYTHONVER=3.9"))

        // Test MAX and MIN resolution for GCCVER
        Set maxGcc = nc.resolveAxisValues("GCCVER=MAX", "test-node", false)
        assertEquals(["10"] as Set, maxGcc)

        Set minGcc = nc.resolveAxisValues("GCCVER=MIN", "test-node", false)
        assertEquals(["4.4.4"] as Set, minGcc)

        // Test MAX and MIN resolution for PYTHONVER
        Set maxPy = nc.resolveAxisValues("PYTHONVER=MAX", "test-node", false)
        assertEquals(["3.10"] as Set, maxPy)

        Set minPy = nc.resolveAxisValues("PYTHONVER=MIN", "test-node", false)
        assertEquals(["2.7"] as Set, minPy)
    }

    @Test
    void testDynamatrixSingleBuildConfigPythonEnvvarExport() {
        DynamatrixSingleBuildConfig dsbc = new DynamatrixSingleBuildConfig(null)
        dsbc.buildLabelSet = ["OS_DISTRO=debian", "PYTHON=3.9"] as Set

        Map<String, String> kvMap = dsbc.getKVMap(false)
        assertEquals("3.9", kvMap.get("PYTHON"))
        assertEquals("3.9", kvMap.get("PYTHONVER"))

        // Export PYTHON="$it" into envvarSet
        if (dsbc.envvarSet == null) dsbc.envvarSet = [] as Set
        dsbc.envvarSet.add("PYTHON=${kvMap['PYTHON']}")

        Set kvSet = dsbc.getKVSet()
        assertTrue(kvSet.contains("PYTHON=3.9"))
    }
}
