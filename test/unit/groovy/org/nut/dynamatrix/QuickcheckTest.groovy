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

    @Test
    void testQuickcheckIsEnabled() {
        GroovyClassLoader gcl = new GroovyClassLoader(this.class.classLoader)
        Class quickcheckClass = gcl.parseClass(new File("vars/quickcheck.groovy"))
        def quickcheckInstance = quickcheckClass.getDeclaredConstructor().newInstance()

        assertFalse(quickcheckInstance.isEnabled(null))
        assertFalse(quickcheckInstance.isEnabled([:]))
        assertFalse(quickcheckInstance.isEnabled([quickcheck: false]))
        assertFalse(quickcheckInstance.isEnabled([quickcheck: 'false']))
        assertFalse(quickcheckInstance.isEnabled([quickcheck: [:]]))
        assertFalse(quickcheckInstance.isEnabled([quickcheck: [enabled: false]]))
        assertFalse(quickcheckInstance.isEnabled([quickcheck: [shellcheck: null, spellcheck: false, stylecheck: null]]))

        assertTrue(quickcheckInstance.isEnabled([quickcheck: true]))
        assertTrue(quickcheckInstance.isEnabled([quickcheck: 'true']))
        assertTrue(quickcheckInstance.isEnabled([quickcheck: [enabled: true]]))
        assertTrue(quickcheckInstance.isEnabled([quickcheck: [shellcheck: true]]))
        assertTrue(quickcheckInstance.isEnabled([quickcheck: [spellcheck: 'make spellcheck']]))
        assertTrue(quickcheckInstance.isEnabled([quickcheck: [stylecheck: 'make stylecheck']]))
    }

    @Test
    void testSpellcheckIsEnabled() {
        GroovyClassLoader gcl = new GroovyClassLoader(this.class.classLoader)
        Class spellcheckClass = gcl.parseClass(new File("vars/spellcheck.groovy"))
        def spellcheckInstance = spellcheckClass.getDeclaredConstructor().newInstance()

        assertFalse(spellcheckInstance.isEnabled(null))
        assertFalse(spellcheckInstance.isEnabled([:]))
        assertFalse(spellcheckInstance.isEnabled([spellcheck: false]))
        assertFalse(spellcheckInstance.isEnabled([spellcheck: 'false']))
        assertFalse(spellcheckInstance.isEnabled([spellcheck: '']))
        assertFalse(spellcheckInstance.isEnabled([spellcheck: [enabled: false]]))

        assertTrue(spellcheckInstance.isEnabled([spellcheck: true]))
        assertTrue(spellcheckInstance.isEnabled([spellcheck: 'true']))
        assertTrue(spellcheckInstance.isEnabled([spellcheck: 'make spellcheck']))
        assertTrue(spellcheckInstance.isEnabled([spellcheck: [enabled: true]]))
    }

    @Test
    void testStylecheckIsEnabled() {
        GroovyClassLoader gcl = new GroovyClassLoader(this.class.classLoader)
        Class stylecheckClass = gcl.parseClass(new File("vars/stylecheck.groovy"))
        def stylecheckInstance = stylecheckClass.getDeclaredConstructor().newInstance()

        assertFalse(stylecheckInstance.isEnabled(null))
        assertFalse(stylecheckInstance.isEnabled([:]))
        assertFalse(stylecheckInstance.isEnabled([stylecheck: false]))
        assertFalse(stylecheckInstance.isEnabled([stylecheck: 'false']))
        assertFalse(stylecheckInstance.isEnabled([stylecheck: '']))
        assertFalse(stylecheckInstance.isEnabled([stylecheck: [enabled: false]]))

        assertTrue(stylecheckInstance.isEnabled([stylecheck: true]))
        assertTrue(stylecheckInstance.isEnabled([stylecheck: 'true']))
        assertTrue(stylecheckInstance.isEnabled([stylecheck: 'make stylecheck']))
        assertTrue(stylecheckInstance.isEnabled([stylecheck: [enabled: true]]))
    }

    @Test
    void testShellcheckIsEnabled() {
        GroovyClassLoader gcl = new GroovyClassLoader(this.class.classLoader)
        Class shellcheckClass = gcl.parseClass(new File("vars/shellcheck.groovy"))
        def shellcheckInstance = shellcheckClass.getDeclaredConstructor().newInstance()

        assertFalse(shellcheckInstance.isEnabled(null))
        assertFalse(shellcheckInstance.isEnabled([:]))
        assertFalse(shellcheckInstance.isEnabled([shellcheck: false]))
        assertFalse(shellcheckInstance.isEnabled([shellcheck: 'false']))
        assertFalse(shellcheckInstance.isEnabled([shellcheck: '']))
        assertFalse(shellcheckInstance.isEnabled([shellcheck: [enabled: false]]))
        assertFalse(shellcheckInstance.isEnabled([shellcheck: [single: null, multi: null]]))

        assertTrue(shellcheckInstance.isEnabled([shellcheck: true]))
        assertTrue(shellcheckInstance.isEnabled([shellcheck: 'true']))
        assertTrue(shellcheckInstance.isEnabled([shellcheck: 'make shellcheck']))
        assertTrue(shellcheckInstance.isEnabled([shellcheck: [single: 'make shellcheck']]))
        assertTrue(shellcheckInstance.isEnabled([shellcheck: [multi: 'make shellcheck']]))
    }
}
