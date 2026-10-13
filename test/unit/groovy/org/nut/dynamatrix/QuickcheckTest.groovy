package org.nut.dynamatrix

import org.junit.jupiter.api.Test
import static org.junit.jupiter.api.Assertions.*

class QuickcheckTest {

    @Test
    void testQuickcheckPlatformStageNameTagValue() {
        DynamatrixSingleBuildConfig dsbc = new DynamatrixSingleBuildConfig(null)
        dsbc.buildLabelSet = ["OS_FAMILY=linux", "OS_DISTRO=ubuntu", "ARCH=x86_64"] as Set

        String tagVal = DynamatrixSingleBuildConfig.QuickcheckPlatform_StageNameTagValue(dsbc)
        assertEquals("linux-ubuntu-x86_64-quickcheck", tagVal)

        String tagFunc = DynamatrixSingleBuildConfig.QuickcheckPlatform_StageNameTagFunc(dsbc)
        assertEquals('MATRIX_TAG="linux-ubuntu-x86_64-quickcheck"', tagFunc)
    }

    @Test
    void testQuickcheckPlatformStageNameTagValueWithoutOsFamily() {
        DynamatrixSingleBuildConfig dsbc = new DynamatrixSingleBuildConfig(null)
        dsbc.buildLabelSet = ["OS_DISTRO=debian", "ARCH=arm64"] as Set

        String tagVal = DynamatrixSingleBuildConfig.QuickcheckPlatform_StageNameTagValue(dsbc)
        assertEquals("debian-arm64-quickcheck", tagVal)

        String tagFunc = DynamatrixSingleBuildConfig.QuickcheckPlatform_StageNameTagFunc(dsbc)
        assertEquals('MATRIX_TAG="debian-arm64-quickcheck"', tagFunc)
    }

    @Test
    void testQuickcheckSanityCheckDynacfgPipelineDefaults() {
        GroovyClassLoader gcl = new GroovyClassLoader(this.class.classLoader)
        Class quickcheckClass = gcl.parseClass(new File("vars/quickcheck.groovy"))
        def quickcheckInstance = quickcheckClass.getDeclaredConstructor().newInstance()

        Map dynacfgPipeline = [quickcheck: true]
        Map result = quickcheckInstance.sanityCheckDynacfgPipeline(dynacfgPipeline)

        assertNotNull(result.quickcheck)
        assertTrue(result.quickcheck instanceof Map)
        assertEquals(2, result.quickcheck.dynamatrixAxesLabels.size())
        assertNotNull(result.quickcheck.shellcheck)
        assertEquals('SHELL', result.quickcheck.shellcheck.multiLabel)
        assertNotNull(result.quickcheck.spellcheck)
        assertNotNull(result.quickcheck.stylecheck)
        assertNotNull(result.quickcheck.stageNameFunc)
    }

    @Test
    void testQuickcheckSanityCheckDynacfgPipelineCustomMap() {
        GroovyClassLoader gcl = new GroovyClassLoader(this.class.classLoader)
        Class quickcheckClass = gcl.parseClass(new File("vars/quickcheck.groovy"))
        def quickcheckInstance = quickcheckClass.getDeclaredConstructor().newInstance()

        Map dynacfgPipeline = [
            quickcheck: [
                dynamatrixAxesLabels: ['OS_DISTRO', 'ARCH'],
                shellcheck: [
                    multi: 'make shellcheck',
                    multiLabel: 'SHELL'
                ],
                spellcheck: 'make spellcheck',
                stylecheck: 'make stylecheck'
            ]
        ]
        Map result = quickcheckInstance.sanityCheckDynacfgPipeline(dynacfgPipeline)

        assertEquals(['OS_DISTRO', 'ARCH'], result.quickcheck.dynamatrixAxesLabels)
        assertEquals('make shellcheck', result.quickcheck.shellcheck.multi)
        assertEquals('SHELL', result.quickcheck.shellcheck.multiLabel)
        assertEquals('make spellcheck', result.quickcheck.spellcheck)
        assertEquals('make stylecheck', result.quickcheck.stylecheck)
    }

    @Test
    void testQuickcheckMakeMap() {
        GroovyClassLoader gcl = new GroovyClassLoader(this.class.classLoader)
        Class quickcheckClass = gcl.parseClass(new File("vars/quickcheck.groovy"))
        def quickcheckInstance = quickcheckClass.getDeclaredConstructor().newInstance()

        Closure dummyClosure1 = { -> println "dummy1" }
        Closure dummyClosure2 = { -> println "dummy2" }

        Set<List> stages = [
            ["quickcheck for ubuntu-x86_64", dummyClosure1],
            ["quickcheck for debian-arm64", dummyClosure2]
        ] as Set

        Map map = quickcheckInstance.makeMap(stages)
        assertEquals(2, map.size())
        assertTrue(map.containsKey("quickcheck for ubuntu-x86_64"))
        assertTrue(map.containsKey("quickcheck for debian-arm64"))
    }
}
