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
}
