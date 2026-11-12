package org.nut.dynamatrix

import org.nut.dynamatrix.NodeCaps;
import org.nut.dynamatrix.NodeData;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestFarmNodeDataTest {

    /** Size of both {@link TestFarmNodeData#FARM_NODES_CLASSES}
     *  and {@link TestFarmNodeData#FARM_NODES_LABELS} */
    final static int expectedTestFarmNodeDataCount = 73

    @Test
    void testFarmNodeDataCountAndKeys() {
        assertEquals(expectedTestFarmNodeDataCount, TestFarmNodeData.getNodeCount())
        assertEquals(expectedTestFarmNodeDataCount, TestFarmNodeData.getNodeNames().size())
        assertEquals(expectedTestFarmNodeDataCount, TestFarmNodeData.FARM_NODES_CLASSES.size())
        assertEquals(expectedTestFarmNodeDataCount, TestFarmNodeData.FARM_NODES_LABELS.size())

        // Verify key nodes exist
        assertTrue(TestFarmNodeData.FARM_NODES_LABELS.containsKey('0-master-worker'))
        assertTrue(TestFarmNodeData.FARM_NODES_LABELS.containsKey('danutci-freebsd-15-amd64'))
        assertTrue(TestFarmNodeData.FARM_NODES_LABELS.containsKey('donutci-jenkins-debian12-x86_64-worker'))
        assertTrue(TestFarmNodeData.FARM_NODES_LABELS.containsKey('nutci-ubuntu-plucky2504-amd64'))
    }

    @Test
    void testNodeDataCreationFromMock() {
        NodeData nd = TestFarmNodeData.getNodeData('danutci-freebsd-15-amd64')
        assertNotNull(nd)
        assertEquals('danutci-freebsd-15-amd64', nd.getNodeName())
        assertTrue(nd.labelSet.contains('nut-builder:alldrv'))
        assertTrue(nd.labelSet.contains('CLANGVER=19'))
        assertTrue(nd.labelSet.contains('GCCVER=14'))
        assertTrue(nd.labelSet.contains('GCCVER=15'))

        // Verify labelMap populated properly with TreeSet for multi-value keys
        assertTrue(nd.labelMap.containsKey('CLANGVER'))
        assertTrue(nd.labelMap.containsKey('GCCVER'))
        assertTrue(nd.labelMap.containsKey('OS_FAMILY'))
        assertTrue(nd.labelMap['CLANGVER'] instanceof TreeSet)
        assertTrue(((TreeSet) nd.labelMap['CLANGVER']).contains('19'))
        assertTrue(((TreeSet) nd.labelMap['CLANGVER']).contains('22'))
        assertTrue(((TreeSet) nd.labelMap['GCCVER']).contains('14'))
        assertTrue(((TreeSet) nd.labelMap['GCCVER']).contains('15'))
        assertTrue(nd.labelMap.containsKey('CLANGVER=19'))
        assertNull(nd.labelMap['CLANGVER=19'])
    }

    @Test
    void testNodeDataMapAndNodeCaps() {
        Map<String, NodeData> nodeDataMap = TestFarmNodeData.getNodeDataMap()
        assertEquals(expectedTestFarmNodeDataCount, nodeDataMap.size())

        NodeCaps nc = TestFarmNodeData.getNodeCaps()
        assertTrue(nc.isInitialized)
        assertEquals(expectedTestFarmNodeDataCount, nc.nodeData.size())

        // Query GCCVER values on a node with multiple GCC versions
        Set gccVersions = nc.resolveAxisValues('GCCVER', 'danutci-freebsd-15-amd64', false)
        assertTrue(gccVersions.contains('14'))
        assertTrue(gccVersions.contains('15'))

        // Query MAX / MIN GCCVER on danutci-freebsd-15-amd64
        Set maxGcc = nc.resolveAxisValues('GCCVER=MAX', 'danutci-freebsd-15-amd64', false)
        assertEquals(['15'] as Set, maxGcc)

        Set minGcc = nc.resolveAxisValues('GCCVER=MIN', 'danutci-freebsd-15-amd64', false)
        assertEquals(['14'] as Set, minGcc)

        // Query subset of nodes
        List<String> subset = ['0-master-worker', 'danutci-oi']
        NodeCaps ncSubset = TestFarmNodeData.getNodeCaps(subset)
        assertEquals(2, ncSubset.nodeData.size())
        assertTrue(ncSubset.nodeData.containsKey('0-master-worker'))
        assertTrue(ncSubset.nodeData.containsKey('danutci-oi'))
    }

    @Test
    void testVirtualCStdPairMaxEvaluation() {
        NodeCaps nc = TestFarmNodeData.getNodeCaps(['danutci-freebsd-15-amd64', 'donutci-debian-altroot--jenkins-centos7-amd64+ssh'])
        Dynamatrix dynamatrix = new Dynamatrix(null, nc)

        Map cfgMap = [
            'defaultDynamatrixConfig': 'C+CXX',
            'dynamatrixAxesLabels': ['OS_DISTRO', 'COMPILER', '${COMPILER}VER', 'ARCH${ARCH_BITS}'],
            'dynamatrixAxesVirtualLabelsMap': [
                'CSTDVERSION_${KEY}': [
                    ['c': '99', 'cxx': '98'],
                    ['c': 'MAX', 'cxx': 'MAX']
                ],
                'CSTDVARIANT': ['gnu']
            ]
        ]

        dynamatrix.prepareDynamatrix(cfgMap)
        Set<DynamatrixSingleBuildConfig> dsbcs = dynamatrix.generateBuildConfigSet()
        assertNotNull(dsbcs)
        assertFalse(dsbcs.isEmpty())

        // Verify that NO single build config has literal 'MAX' in its standard version or stage name
        dsbcs.each { DynamatrixSingleBuildConfig dsbc ->
            Map<String, String> kv = dsbc.getKVMap(false)
            String cVer = kv.get('CSTDVERSION_c')
            String cxxVer = kv.get('CSTDVERSION_cxx')
            assertNotNull(cVer, "CSTDVERSION_c should be present: ${dsbc}")
            assertNotNull(cxxVer, "CSTDVERSION_cxx should be present: ${dsbc}")
            assertNotEquals('MAX', cVer, "CSTDVERSION_c must not be literal MAX: ${dsbc}")
            assertNotEquals('MAX', cxxVer, "CSTDVERSION_cxx must not be literal MAX: ${dsbc}")
            assertFalse(cVer.contains('MAX'), "CSTDVERSION_c must not contain MAX: ${dsbc}")
            assertFalse(cxxVer.contains('MAX'), "CSTDVERSION_cxx must not contain MAX: ${dsbc}")

            // Verify stage name or tag does not produce gnuMAX
            String cStdArg = DynamatrixSingleBuildConfig.C_stdarg(kv.get('CSTDVARIANT'), kv.get('CSTDVERSION_c'))
            if (cStdArg != null) {
                assertFalse(cStdArg.contains('gnuMAX'), "C_stdarg must not produce gnuMAX: ${cStdArg}")
            }
        }

        // Verify we have both 99/98 builds and MAX-resolved builds (e.g. 17/17)
        boolean has99_98 = dsbcs.any { dsbc ->
            Map<String, String> kv = dsbc.getKVMap(false)
            kv.get('CSTDVERSION_c') == '99' && kv.get('CSTDVERSION_cxx') == '98'
        }
        assertTrue(has99_98, "Should have gnu99/98 builds")

        boolean hasMaxResolved = dsbcs.any { dsbc ->
            Map<String, String> kv = dsbc.getKVMap(false)
            kv.get('CSTDVERSION_c') in ['11', '17', '2x'] && kv.get('CSTDVERSION_cxx') in ['11', '14', '17', '2a', '2b']
        }
        assertTrue(hasMaxResolved, "Should have MAX-resolved builds (e.g. 17/17)")
    }

    @Test
    void testVirtualCStdPairMaxEvaluationWithCompilerVerMax() {
        NodeCaps nc = TestFarmNodeData.getNodeCaps(['danutci-freebsd-15-amd64', 'donutci-debian-altroot--jenkins-centos7-amd64+ssh'])
        Dynamatrix dynamatrix = new Dynamatrix(null, nc)

        Map cfgMap = [
            'defaultDynamatrixConfig': 'C+CXX',
            'COMPILERVER': 'MAX',
            'dynamatrixAxesLabels': ['OS_DISTRO', 'COMPILER', '${COMPILER}VER', 'ARCH${ARCH_BITS}'],
            'dynamatrixAxesVirtualLabelsMap': [
                'CSTDVERSION_${KEY}': [
                    ['c': '99', 'cxx': '98'],
                    ['c': 'MAX', 'cxx': 'MAX']
                ],
                'CSTDVARIANT': ['gnu']
            ]
        ]

        dynamatrix.prepareDynamatrix(cfgMap)
        Set<DynamatrixSingleBuildConfig> dsbcs = dynamatrix.generateBuildConfigSet()
        assertNotNull(dsbcs)
        assertFalse(dsbcs.isEmpty())

        // Verify no literal 'MAX' in C standard versions
        dsbcs.each { DynamatrixSingleBuildConfig dsbc ->
            Map<String, String> kv = dsbc.getKVMap(false)
            String cVer = kv.get('CSTDVERSION_c')
            String cxxVer = kv.get('CSTDVERSION_cxx')
            assertNotNull(cVer, "CSTDVERSION_c should be present: ${dsbc}")
            assertNotNull(cxxVer, "CSTDVERSION_cxx should be present: ${dsbc}")
            assertNotEquals('MAX', cVer, "CSTDVERSION_c must not be literal MAX: ${dsbc}")
            assertNotEquals('MAX', cxxVer, "CSTDVERSION_cxx must not be literal MAX: ${dsbc}")
        }

        // Verify both 99/98 and MAX builds exist
        boolean has99_98 = dsbcs.any { dsbc ->
            Map<String, String> kv = dsbc.getKVMap(false)
            kv.get('CSTDVERSION_c') == '99' && kv.get('CSTDVERSION_cxx') == '98'
        }
        assertTrue(has99_98, "Should have gnu99/98 builds")

        boolean hasMaxResolved = dsbcs.any { dsbc ->
            Map<String, String> kv = dsbc.getKVMap(false)
            kv.get('CSTDVERSION_c') in ['11', '17', '2x'] && kv.get('CSTDVERSION_cxx') in ['11', '14', '17', '2a', '2b']
        }
        assertTrue(hasMaxResolved, "Should have MAX-resolved builds")
    }
}
