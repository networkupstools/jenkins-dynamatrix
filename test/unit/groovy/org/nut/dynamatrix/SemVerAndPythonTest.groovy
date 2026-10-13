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
        assertEquals(["4.4.4"], Utils.resolveExtremeVersions("GCCVER", "MIN", gccVersions))
        assertEquals(["10"], Utils.resolveExtremeVersions("GCCVER", "MAX", gccVersions))

        // NOT_MIN / !MIN
        assertEquals(["4.8.0", "4.8.2", "4.9", "7", "10"], Utils.resolveExtremeVersions("GCCVER", "NOT_MIN", gccVersions))
        assertEquals(["4.8.0", "4.8.2", "4.9", "7", "10"], Utils.resolveExtremeVersions("GCCVER", "!MIN", gccVersions))

        // NOT_MAX / !MAX
        assertEquals(["4.4.4", "4.8.0", "4.8.2", "4.9", "7"], Utils.resolveExtremeVersions("GCCVER", "NOT_MAX", gccVersions))
        assertEquals(["4.4.4", "4.8.0", "4.8.2", "4.9", "7"], Utils.resolveExtremeVersions("GCCVER", "!MAX", gccVersions))

        List<String> pyVersions = ["2.7", "3.8", "3.9", "3.10", "3.12"]
        assertEquals("2.7", Utils.resolveExtremeVersion("PYTHONVER", "MIN", pyVersions))
        assertEquals("3.12", Utils.resolveExtremeVersion("PYTHONVER", "MAX", pyVersions))
        assertEquals(["3.8", "3.9", "3.10", "3.12"], Utils.resolveExtremeVersions("PYTHONVER", "NOT_MIN", pyVersions))
        assertEquals(["3.8", "3.9", "3.10", "3.12"], Utils.resolveExtremeVersions("PYTHONVER", "!MIN", pyVersions))
        assertEquals(["2.7", "3.8", "3.9", "3.10"], Utils.resolveExtremeVersions("PYTHONVER", "NOT_MAX", pyVersions))
        assertEquals(["2.7", "3.8", "3.9", "3.10"], Utils.resolveExtremeVersions("PYTHONVER", "!MAX", pyVersions))
    }

    @Test
    void testSingleVersionAgentExtremeResolution() {
        // Single-Version Agents: If an agent only has one version of a compiler installed (e.g., only GCC 14 exists),
        // that single version is simultaneously MIN and MAX. It must not be tagged as NOT_MAX or NOT_MIN.
        List<String> singleGcc = ["14"]
        assertEquals("14", Utils.resolveExtremeVersion("GCCVER", "MIN", singleGcc))
        assertEquals("14", Utils.resolveExtremeVersion("GCCVER", "MAX", singleGcc))
        assertEquals(["14"], Utils.resolveExtremeVersions("GCCVER", "MIN", singleGcc))
        assertEquals(["14"], Utils.resolveExtremeVersions("GCCVER", "MAX", singleGcc))

        assertEquals([], Utils.resolveExtremeVersions("GCCVER", "NOT_MIN", singleGcc))
        assertEquals([], Utils.resolveExtremeVersions("GCCVER", "!MIN", singleGcc))
        assertEquals([], Utils.resolveExtremeVersions("GCCVER", "NOT_MAX", singleGcc))
        assertEquals([], Utils.resolveExtremeVersions("GCCVER", "!MAX", singleGcc))
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

        // NOT_MIN / NOT_MAX with C++11 constraint (viable candidates: 4.8.2, 4.9, 7, 10)
        List<String> notMinC11 = Utils.resolveExtremeVersions("GCCVER", "NOT_MIN", gccVersions, ["CSTDVERSION=11"], cfg.excludeCombos)
        assertEquals(["4.9", "7", "10"], notMinC11)

        List<String> notMaxC11 = Utils.resolveExtremeVersions("GCCVER", "NOT_MAX", gccVersions, ["CSTDVERSION=11"], cfg.excludeCombos)
        assertEquals(["4.8.2", "4.9", "7"], notMaxC11)

        // C++17 constraint has only GCC 10 as viable candidate: single version -> NOT_MIN and NOT_MAX should be empty!
        List<String> notMinC17 = Utils.resolveExtremeVersions("GCCVER", "NOT_MIN", gccVersions, ["CSTDVERSION=17"], cfg.excludeCombos)
        assertEquals([], notMinC17)

        List<String> notMaxC17 = Utils.resolveExtremeVersions("GCCVER", "NOT_MAX", gccVersions, ["CSTDVERSION=17"], cfg.excludeCombos)
        assertEquals([], notMaxC17)
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

        NodeCaps nc = new NodeCaps(null)
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

        // Test NOT_MIN / !MIN and NOT_MAX / !MAX resolution for GCCVER
        Set notMinGcc = nc.resolveAxisValues("GCCVER=NOT_MIN", "test-node", false)
        assertEquals(["4.9", "10"] as Set, notMinGcc)

        Set notMinGccExcl = nc.resolveAxisValues("GCCVER=!MIN", "test-node", false)
        assertEquals(["4.9", "10"] as Set, notMinGccExcl)

        Set notMaxGcc = nc.resolveAxisValues("GCCVER=NOT_MAX", "test-node", false)
        assertEquals(["4.4.4", "4.9"] as Set, notMaxGcc)

        Set notMaxGccExcl = nc.resolveAxisValues("GCCVER=!MAX", "test-node", false)
        assertEquals(["4.4.4", "4.9"] as Set, notMaxGccExcl)

        // Test with returnAssignments = true and fixed prefix
        Set notMaxAssigns = nc.resolveAxisValues("COMPILER=GCC GCCVER=NOT_MAX", "test-node", true)
        assertEquals(["COMPILER=GCC GCCVER=4.4.4", "COMPILER=GCC GCCVER=4.9"] as Set, notMaxAssigns)

        // Test single-version agent in NodeCaps
        NodeData ndSingle = new NodeData()
        ndSingle.nodeName = "single-node"
        ndSingle.labelMap = [
            'COMPILER': ['GCC'] as TreeSet,
            'COMPILER=GCC': null,
            'GCCVER': ['14'] as TreeSet,
            'GCCVER=14': null
        ]
        nc.nodeData["single-node"] = ndSingle

        assertEquals(["14"] as Set, nc.resolveAxisValues("GCCVER=MAX", "single-node", false))
        assertEquals(["14"] as Set, nc.resolveAxisValues("GCCVER=MIN", "single-node", false))
        assertEquals([] as Set, nc.resolveAxisValues("GCCVER=NOT_MAX", "single-node", false))
        assertEquals([] as Set, nc.resolveAxisValues("GCCVER=!MAX", "single-node", false))
        assertEquals([] as Set, nc.resolveAxisValues("GCCVER=NOT_MIN", "single-node", false))
        assertEquals([] as Set, nc.resolveAxisValues("GCCVER=!MIN", "single-node", false))
    }

    @Test
    void testDynamatrixConfigExtremeVersionSubstitution() {
        DynamatrixConfig cfg = new DynamatrixConfig(null)
        cfg.initDefault([
            'defaultDynamatrixConfig': 'C',
            'COMPILERVER': 'NOT_MAX'
        ])
        assertTrue(cfg.dynamatrixAxesLabels.contains('${COMPILER}VER=NOT_MAX'))

        DynamatrixConfig cfg2 = new DynamatrixConfig(null)
        cfg2.initDefault([
            'defaultDynamatrixConfig': 'C',
            'COMPILERVER': '!MAX'
        ])
        assertTrue(cfg2.dynamatrixAxesLabels.contains('${COMPILER}VER=!MAX'))

        DynamatrixConfig cfg3 = new DynamatrixConfig(null)
        cfg3.initDefault([
            'defaultDynamatrixConfig': 'C',
            'GCCVER': 'NOT_MIN'
        ])
        assertTrue(cfg3.dynamatrixAxesLabels.contains('${COMPILER}VER=NOT_MIN'))
    }

    @Test
    void testCompilerFamilyAxisExpansionWithNotMax() {
        NodeData nd = new NodeData()
        nd.nodeName = "multi-compiler-node"
        nd.labelMap = [
            'COMPILER': ['GCC', 'CLANG'] as TreeSet,
            'COMPILER=GCC': null,
            'COMPILER=CLANG': null,
            'GCCVER': ['10', '12', '14'] as TreeSet,
            'GCCVER=10': null,
            'GCCVER=12': null,
            'GCCVER=14': null,
            'CLANGVER': ['16', '17', '18'] as TreeSet,
            'CLANGVER=16': null,
            'CLANGVER=17': null,
            'CLANGVER=18': null
        ]

        NodeCaps nc = new NodeCaps(null)
        nc.nodeData = ["multi-compiler-node": nd]
        nc.isInitialized = true

        // Scope of Axis: Rule applies across compiler families (${COMPILER}VER=NOT_MAX matching both GCCVER=NOT_MAX and CLANGVER=NOT_MAX)
        Set resolvedAxes = nc.resolveAxisName('${COMPILER}VER=NOT_MAX')
        assertTrue(resolvedAxes.contains('COMPILER=GCC GCCVER=NOT_MAX'))
        assertTrue(resolvedAxes.contains('COMPILER=CLANG CLANGVER=NOT_MAX'))

        Set gccNotMax = nc.resolveAxisValues("COMPILER=GCC GCCVER=NOT_MAX", "multi-compiler-node", true)
        assertEquals(["COMPILER=GCC GCCVER=10", "COMPILER=GCC GCCVER=12"] as Set, gccNotMax)

        Set clangNotMax = nc.resolveAxisValues("COMPILER=CLANG CLANGVER=NOT_MAX", "multi-compiler-node", true)
        assertEquals(["COMPILER=CLANG CLANGVER=16", "COMPILER=CLANG CLANGVER=17"] as Set, clangNotMax)
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

    @Test
    void testExactUppercaseRequirement() {
        List<String> gccVersions = ["4.4.4", "4.8.0", "4.8.2", "4.9", "7", "10"]
        // Non-uppercase modes should not match
        assertEquals([], Utils.resolveExtremeVersions("GCCVER", "min", gccVersions))
        assertEquals([], Utils.resolveExtremeVersions("GCCVER", "max", gccVersions))
        assertEquals([], Utils.resolveExtremeVersions("GCCVER", "not_min", gccVersions))
        assertEquals([], Utils.resolveExtremeVersions("GCCVER", "!min", gccVersions))
        assertEquals([], Utils.resolveExtremeVersions("GCCVER", "not_max", gccVersions))
        assertEquals([], Utils.resolveExtremeVersions("GCCVER", "!max", gccVersions))

        assertNull(Utils.resolveExtremeVersion("GCCVER", "min", gccVersions))
        assertNull(Utils.resolveExtremeVersion("GCCVER", "max", gccVersions))

        // NodeCaps should not match lowercase
        NodeData nd = new NodeData()
        nd.nodeName = "test-node"
        nd.labelMap = [
            'COMPILER': ['GCC'] as TreeSet,
            'COMPILER=GCC': null,
            'GCCVER': ['4.4.4', '4.9', '10'] as TreeSet,
            'GCCVER=4.4.4': null,
            'GCCVER=4.9': null,
            'GCCVER=10': null
        ]
        NodeCaps nc = new NodeCaps(null)
        nc.nodeData = ["test-node": nd]
        nc.isInitialized = true

        assertEquals([] as Set, nc.resolveAxisValues("GCCVER=max", "test-node", false))
        assertEquals([] as Set, nc.resolveAxisValues("GCCVER=min", "test-node", false))
        assertEquals([] as Set, nc.resolveAxisValues("GCCVER=not_max", "test-node", false))
        assertEquals([] as Set, nc.resolveAxisValues("GCCVER=!max", "test-node", false))

        // DynamatrixConfig should not substitute lowercase
        DynamatrixConfig cfg = new DynamatrixConfig(null)
        cfg.initDefault([
            'defaultDynamatrixConfig': 'C',
            'COMPILERVER': 'not_max'
        ])
        assertFalse(cfg.dynamatrixAxesLabels.contains('${COMPILER}VER=not_max'))
        assertFalse(cfg.dynamatrixAxesLabels.contains('${COMPILER}VER=NOT_MAX'))
    }
}
