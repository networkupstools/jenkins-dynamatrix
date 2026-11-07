// Steps should not be in a package, to avoid CleanGroovyClassLoader exceptions...
// package org.nut.dynamatrix;

import org.nut.dynamatrix.*;

import com.cloudbees.groovy.cps.NonCPS;

import org.nut.dynamatrix.DynamatrixSingleBuildConfig;
import org.nut.dynamatrix.Utils;
import org.nut.dynamatrix.dynamatrixGlobalState;

// TODO: make dynacfgPipeline a class?

/*
// Example config for this part of code:

    @NonCPS
    def stageNameFunc_Spellcheck(DynamatrixSingleBuildConfig dsbc) {
        def labelMap = dsbc.getKVMap(false)
        String sn = ""
        if (labelMap.containsKey("OS_FAMILY"))
            sn += labelMap.OS_FAMILY + "-"
        if (labelMap.containsKey("OS_DISTRO"))
            sn += labelMap.OS_DISTRO + "-"
        return "MATRIX_TAG=\"${sn}spellcheckCustom\""
    }

    //dynacfgPipeline['spellcheck'] = true //false
    dynacfgPipeline['spellcheck'] = [
        //'stageNameFunc': null,
        'dynamatrixAxesLabels': [~/^OS_.+/],
        'spellcheck': '( \${MAKE} spellcheck )'
    ]
    //dynacfgPipeline.spellcheck.stageNameFunc = this.&stageNameFunc_Spellcheck

 */

// Don't forget to call the sanity-checker below during pipeline init...
// or maybe do it from the routine here?
// Note that this code relies on more data points than just
// dynacfgPipeline.spellcheck.*

/**
 * Check whether spellcheck is enabled in dynacfgPipeline configuration
 */
Boolean isEnabled(Map dynacfgPipeline = [:]) {
    if (dynacfgPipeline == null) return false
    if (!dynacfgPipeline.containsKey('spellcheck')) return false
    def sc = dynacfgPipeline['spellcheck']
    if (sc == null || sc == false || "${sc}".trim().equalsIgnoreCase("false")) return false
    if (sc == true || "${sc}".trim().equalsIgnoreCase("true")) return true
    if (Utils.isString(sc)) return Utils.isStringNotEmpty(sc)
    if (Utils.isMap(sc)) {
        if (sc.containsKey('enabled')) {
            if (sc.enabled == false || "${sc.enabled}".trim().equalsIgnoreCase("false")) {
                return false
            }
            if (sc.enabled == null || "${sc.enabled}".trim().equalsIgnoreCase("null")) {
                return false
            }
            if (sc.enabled == true || "${sc.enabled}".trim().equalsIgnoreCase("true")) {
                return true
            }
            throw new IllegalArgumentException("dynacfgPipeline.spellcheck.enabled must resolve to 'true'/'false', was: '${sc.enabled}'")
        }
        if (sc.containsKey('spellcheck')) {
            def scsc = sc['spellcheck']
            if (scsc == null || scsc == false || "${scsc}".trim().equalsIgnoreCase("false")) return false
            if (scsc == true || "${scsc}".trim().equalsIgnoreCase("true")) return true
            if (Utils.isString(scsc)) return Utils.isStringNotEmpty(scsc)
        }
        return !sc.isEmpty()
    }
    return (Boolean)sc
}

/**
 * Returns a Set of tuples that a later stage can convert
 * into a Map for `parallel` test running with {@link #makeMap}
 * (avoid having a long-lived Map object which can upset CPS
 * and pipeline serialization).
 */
Set<List> call(Map dynacfgPipeline = [:], Boolean returnSet = true) {
    if (dynacfgPipeline == null) {
        dynacfgPipeline = [:]
    }

    // Set of Map'able tuples of <String, Closure>
    Set<List> stagesSpellcheck_arr = []

    Boolean debugTrace = (dynacfgPipeline?.enableDebugTrace || dynacfgPipeline?.spellcheck?.enableDebugTrace)

    // TOTHINK: Should we assume or *ensure* dynacfgPipeline?.spellcheck
    //  was normalized by spellcheck.sanityCheckDynacfgPipeline() if enabled?
    if (isEnabled(dynacfgPipeline)) {
        if (debugTrace) this.script.println "[DEBUG] Discovering stagesSpellcheck: calling prepareDynamatrix() step..."

        stagesSpellcheck_arr = prepareDynamatrix([
            commonLabelExpr: (dynacfgPipeline.spellcheck?.commonLabelExpr ?: infra.labelDocumentationSpellchecker()),
            dynamatrixAxesLabels: dynacfgPipeline.spellcheck.dynamatrixAxesLabels,
            mergeMode: [ 'dynamatrixAxesLabels': 'replace' ],
            stageNameFunc: dynacfgPipeline.spellcheck.stageNameFunc,
            dynamatrixGithubNotificationContext: "quickbuild-run-spellcheck"
            ] + (dynacfgPipeline.spellcheck?.excludeCombos ? [excludeCombos: dynacfgPipeline.spellcheck?.excludeCombos,] : [:]),
            returnSet) { def delegate -> setDelegate(delegate)
                    DynamatrixSingleBuildConfig MATRIX_DSBC = delegate.dsbc
                    String MATRIX_STAGENAME = delegate.stageName.trim()
                    String MATRIX_TAG = MATRIX_STAGENAME - ~/^MATRIX_TAG="*/ - ~/"*$/

                    String bigStageResult = 'SUCCESS'

                    stage("spellcheck for ${MATRIX_TAG}") {
                        stage("prep for ${MATRIX_TAG}") {
                            String msgFail = "Failed stage: prep for ${MATRIX_TAG}" +
                                "\n  for ${Utils.castString(MATRIX_DSBC)}"
                            Boolean didFail = true
                            try {
                                sh """ echo "UNPACKING for '${MATRIX_TAG}'" """
                                withEnvOptional(dynacfgPipeline.defaultTools) {
                                    unstashCleanSrc(dynacfgPipeline.get("stashnameSrc"))

                                    if (dynacfgPipeline?.spellcheck?.prepconf != null) {
                                        if (Utils.isStringNotEmpty(dynacfgPipeline.spellcheck.prepconf)) {
                                            sh """ ${dynacfgPipeline.spellcheck.prepconf} """
                                        } // else: pipeline author wants this skipped
                                    } else {
                                        if (dynacfgPipeline?.buildPhases?.prepconf) {
                                            sh """ ${dynacfgPipeline.buildPhases.prepconf} """
                                        }
                                    }

                                    if (dynacfgPipeline?.spellcheck?.configure != null) {
                                        if (Utils.isStringNotEmpty(dynacfgPipeline.spellcheck.configure)) {
                                            sh """ ${dynacfgPipeline.spellcheck.configure} """
                                        } // else: pipeline author wants this skipped
                                    } else {
                                        if (dynacfgPipeline?.buildPhases?.configure) {
                                            sh """ ${dynacfgPipeline.buildPhases.configure} """
                                        }
                                    }
                                }
                                didFail = false
                            } catch (Throwable t) {
                                if (Utils.isRetryableException(t)) {
                                    echo "[DEBUG]: spellcheck(${dynacfgPipeline.get("stashnameSrc")}) " +
                                        " finished with a verdict classified as " +
                                        "a retryable build agent failure - " +
                                        "will re-schedule: ${msgFail} => ${t.toString()}"
                                    didFail = false
                                    throw t
                                }
                                catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE', message: msgFail) {
                                    throw t
                                }
                            }

                            if (didFail) {
                                bigStageResult = 'FAILURE'
                                MATRIX_DSBC.setWorstResult('FAILURE')
                                MATRIX_DSBC.dsbcResultInterim = 'FAILURE'
                                currentBuild.result = 'FAILURE'
                                manager.buildFailure()
                                unstable(msgFail)
                            }

                            return didFail
                        } // stage(prep)

                        switch (bigStageResult) {
                            case 'FAILURE': if (true) {	// scoping
                                    currentBuild.result = bigStageResult
                                    String msg = "prep for spellcheck for ${MATRIX_TAG} failed"
                                    infra.reportGithubStageStatus(dynacfgPipeline.get("stashnameSrc"), msg,
                                        'FAILURE', "spellcheck-${MATRIX_TAG}")
                                    msg = "FATAL: ${msg} above"
                                    MATRIX_DSBC.setWorstResult('FAILURE')
                                    MATRIX_DSBC.dsbcResultInterim = 'FAILURE'
                                    echo msg

                                    manager.buildFailure()
                                    unstable(msg)
                                }
                                return false

                            case ['SUCCESS', null]: if (true) {	// scoping
                                    String msg = "prep for spellcheck for ${MATRIX_TAG} succeeded"
                                    if (!infra.updateGithubStageStatus(dynacfgPipeline.get("stashnameSrc"), msg,
                                        'SUCCESS', "spellcheck-${MATRIX_TAG}"))
                                        echo msg
                                }
                                break
                        }

                        // Run spellcheck test
                        def spellcheckCmd = dynacfgPipeline.spellcheck.spellcheck
                        if (spellcheckCmd != null && spellcheckCmd != false) {
                            String msgFail = "Failed stage: spellcheck for ${MATRIX_TAG}" +
                                "\n  for ${Utils.castString(MATRIX_DSBC)}"
                            Boolean didFail = true
                            try {
                                withEnvOptional(dynacfgPipeline.defaultTools) {
                                    if (Utils.isClosure(spellcheckCmd)) {
                                        spellcheckCmd()
                                    } else {
                                        sh """ set +x
                                        echo "Spellcheck test on `uname -a || hostname || true` system"
                                        ${spellcheckCmd}
                                        """
                                    }
                                }
                                didFail = false
                            } catch (Throwable t) {
                                if (Utils.isRetryableException(t)) {
                                    echo "[DEBUG]: spellcheck(${dynacfgPipeline.get("stashnameSrc")}) " +
                                        " finished with a verdict classified as " +
                                        "a retryable build agent failure - " +
                                        "will re-schedule: ${msgFail} => ${t.toString()}"
                                    didFail = false
                                    throw t
                                }
                                catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE', message: msgFail) {
                                    throw t
                                }
                            }

                            if (didFail) {
                                bigStageResult = 'FAILURE'
                                MATRIX_DSBC.setWorstResult('FAILURE')
                                MATRIX_DSBC.dsbcResultInterim = 'FAILURE'
                                currentBuild.result = 'FAILURE'
                                manager.buildFailure()
                                unstable(msgFail)
                            }
                        }

                        currentBuild.result = bigStageResult
                        switch (bigStageResult) {
                            case 'FAILURE': if (true) {	// scoping
                                    String msg = "spellcheck for ${MATRIX_TAG} failed"
                                    infra.reportGithubStageStatus(dynacfgPipeline.get("stashnameSrc"), msg,
                                        'FAILURE', "spellcheck-${MATRIX_TAG}")
                                    msg = "FATAL: ${msg} above"
                                    MATRIX_DSBC.setWorstResult('FAILURE')
                                    MATRIX_DSBC.dsbcResultInterim = 'FAILURE'
                                    echo msg

                                    // Let the pipeline fault path take over
                                    manager.buildFailure()
                                    unstable(msg)
                                }
                                break

                            case ['SUCCESS', null]: if (true) {	// scoping
                                    String msg = "spellcheck for ${MATRIX_TAG} succeeded"
                                    if (!infra.updateGithubStageStatus(dynacfgPipeline.get("stashnameSrc"), msg,
                                        'SUCCESS', "spellcheck-${MATRIX_TAG}"))
                                        echo msg
                                }
                                break
                        }
                    } // stage("spellcheck for ${MATRIX_TAG}")
            } // generateBuild
    } // if isEnabled

    println "Discovered ${stagesSpellcheck_arr.size()} stagesSpellcheck_arr platform builds"

    return stagesSpellcheck_arr
}

/**
 * Restore Map made by {@link spellcheck#call} from Set of Lists (actually tuples),
 * as needed for `parallel` step. Note it is not constrained as Map<String, Closure>!
 */
Map makeMap(Set<List> stagesSpellcheck_arr = []) {
    println "Restoring discovered stagesSpellcheck platform builds from Set to Map..."
    Map stagesSpellcheck = [:]
    stagesSpellcheck_arr.each { List tup ->
        println "Restoring stagesSpellcheck platform build: ${Utils.castString(tup)}"
        if (Utils.isStringNotEmpty(tup[0]) && Utils.isClosure(tup[1]))
            stagesSpellcheck[(String)(tup[0])] = (Closure)(tup[1])
    }
    println "Discovered ${stagesSpellcheck.size()} stagesSpellcheck platform builds: ${Utils.castString(stagesSpellcheck)}"
    return stagesSpellcheck
}

Map sanityCheckDynacfgPipeline(Map dynacfgPipeline = [:]) {
    // Avoid NPEs (TBD: and changing the original Map's entries unexpectedly
    // commented away currently - this may misbehave vs. generateBuild() =>
    // use of script delegate => caller's original dynacfgPipeline when
    // resolving stage closures):
    if (dynacfgPipeline == null) {
        dynacfgPipeline = [:]
//    } else {
//        dynacfgPipeline = (Map)(dynacfgPipeline.clone())
    }

    if (dynacfgPipeline.containsKey('spellcheck')) {
        if (Utils.isMap(dynacfgPipeline['spellcheck'])) {
            if (!dynacfgPipeline['spellcheck'].containsKey('dynamatrixAxesLabels') || "".equals(dynacfgPipeline['spellcheck']['dynamatrixAxesLabels'])) {
                if (dynamatrixGlobalState.enableDebugTrace)
                    echo "spellcheck.sanityCheckDynacfgPipeline(): populate missing dynacfgPipeline.spellcheck.dynamatrixAxesLabels"
                dynacfgPipeline['spellcheck']['dynamatrixAxesLabels'] = [~/^OS_.+/]
            }
            if (!dynacfgPipeline['spellcheck'].containsKey('spellcheck') || "".equals(dynacfgPipeline['spellcheck']['spellcheck'])) {
                if (dynamatrixGlobalState.enableDebugTrace)
                    echo "spellcheck.sanityCheckDynacfgPipeline(): populate missing dynacfgPipeline.spellcheck.spellcheck = null"
                dynacfgPipeline['spellcheck']['spellcheck'] = null
            } else if ("${dynacfgPipeline['spellcheck']['spellcheck']}".trim().equals("true")) {
                if (dynamatrixGlobalState.enableDebugTrace)
                    echo "spellcheck.sanityCheckDynacfgPipeline(): true: defaulting a reasonable config"
                dynacfgPipeline['spellcheck']['spellcheck'] = '( \${MAKE} spellcheck )'
            } else if ("${dynacfgPipeline['spellcheck']['spellcheck']}".trim().equals("false")) {
                if (dynamatrixGlobalState.enableDebugTrace)
                    echo "spellcheck.sanityCheckDynacfgPipeline(): false: defaulting a null config"
                dynacfgPipeline['spellcheck']['spellcheck'] = null
            }
        } else {
            // Legacy string or boolean... or Closure?
            if ("${dynacfgPipeline['spellcheck']}".trim().equals("true")) {
                // Assign defaults
                if (dynamatrixGlobalState.enableDebugTrace)
                    echo "spellcheck.sanityCheckDynacfgPipeline(): true: defaulting a reasonable config"
                dynacfgPipeline['spellcheck'] = [
                    dynamatrixAxesLabels: [~/^OS_.+/],
                    spellcheck: '( \${MAKE} spellcheck )'
                ]
            } else if ("${dynacfgPipeline['spellcheck']}".trim().equals("false")) {
                if (dynamatrixGlobalState.enableDebugTrace)
                    echo "spellcheck.sanityCheckDynacfgPipeline(): false: defaulting a null-filled config"
                dynacfgPipeline['spellcheck'] = [
                    dynamatrixAxesLabels: [],
                    spellcheck: null
                ]
            } else if (Utils.isClosure(dynacfgPipeline['spellcheck'])
                || Utils.isStringNotEmpty(dynacfgPipeline['spellcheck'])
            ) {
                // Assume it is a direct-command format or closure
                def tmp = dynacfgPipeline['spellcheck']
                dynacfgPipeline['spellcheck'] = [
                    'dynamatrixAxesLabels': [~/^OS_.+/],
                    'spellcheck': tmp
                ]
            } else {
                error "Unsupported dynacfgPipeline['spellcheck'] (expecting Boolean, String, Closure or Map) : ${Utils.castString(dynacfgPipeline['spellcheck'])}"
            }
        }
    } else {
        if (dynamatrixGlobalState.enableDebugTrace)
            echo "spellcheck.sanityCheckDynacfgPipeline(): defaulting a null-filled config"
        dynacfgPipeline['spellcheck'] = [
            dynamatrixAxesLabels: [],
            spellcheck: null
        ]
    }

    if (!dynacfgPipeline.spellcheck.stageNameFunc) {
        if (dynamatrixGlobalState.enableDebugTrace)
            echo "spellcheck.sanityCheckDynacfgPipeline(): populate missing dynacfgPipeline.spellcheck.stageNameFunc"
        dynacfgPipeline.spellcheck.stageNameFunc = DynamatrixSingleBuildConfig.&SpellcheckPlatform_StageNameTagFunc
    }

    if (!dynacfgPipeline.spellcheck.containsKey('commonLabelExpr') || dynacfgPipeline.spellcheck.commonLabelExpr == null) {
        if (dynamatrixGlobalState.enableDebugTrace)
            echo "spellcheck.sanityCheckDynacfgPipeline(): defaulting commonLabelExpr to infra.labelDocumentationSpellchecker()"
        try {
            dynacfgPipeline.spellcheck.commonLabelExpr = infra.labelDocumentationSpellchecker()
        } catch (Throwable ignored) {
            // In unit test or environment where infra / CPS steps aren't bound, do not fail
        }
    }

    if (dynacfgPipeline['spellcheck'].containsKey('prepconf')) {
        if ("${dynacfgPipeline['spellcheck']['prepconf']}".trim().equals("true")) {
            // Use whatever buildPhases provide
            if (dynamatrixGlobalState.enableDebugTrace)
                echo "spellcheck.sanityCheckDynacfgPipeline(): populate missing dynacfgPipeline.spellcheck.prepconf = null"
            dynacfgPipeline['spellcheck']['prepconf'] = null
        }
    } else {
        if (dynacfgPipeline.containsKey('spellcheck_prepconf')) {
            if ("${dynacfgPipeline['spellcheck_prepconf']}".trim().equals("true")) {
                // Use whatever buildPhases provide
                if (dynamatrixGlobalState.enableDebugTrace)
                    echo "spellcheck.sanityCheckDynacfgPipeline(): populate missing dynacfgPipeline.spellcheck.prepconf = null"
                dynacfgPipeline['spellcheck']['prepconf'] = null
            } else {
                dynacfgPipeline['spellcheck']['prepconf'] = dynacfgPipeline['spellcheck_prepconf']
            }
            dynacfgPipeline.remove('spellcheck_prepconf')
        } else {
            if (dynamatrixGlobalState.enableDebugTrace)
                echo "spellcheck.sanityCheckDynacfgPipeline(): populate missing dynacfgPipeline.spellcheck.prepconf = null"
            dynacfgPipeline['spellcheck']['prepconf'] = null
        }
    }

    if (dynacfgPipeline['spellcheck'].containsKey('configure')) {
        if ("${dynacfgPipeline['spellcheck']['configure']}".trim().equals("true")) {
            // Use whatever buildPhases provide
            if (dynamatrixGlobalState.enableDebugTrace)
                echo "spellcheck.sanityCheckDynacfgPipeline(): populate missing dynacfgPipeline.spellcheck.configure = null"
            dynacfgPipeline['spellcheck']['configure'] = null
        }
    } else {
        if (dynacfgPipeline.containsKey('spellcheck_configure')) {
            if ("${dynacfgPipeline['spellcheck_configure']}".trim().equals("true")) {
                // Use whatever buildPhases provide
                if (dynamatrixGlobalState.enableDebugTrace)
                    echo "spellcheck.sanityCheckDynacfgPipeline(): populate missing dynacfgPipeline.spellcheck.configure = null"
                dynacfgPipeline['spellcheck']['configure'] = null
            } else {
                dynacfgPipeline['spellcheck']['configure'] = dynacfgPipeline['spellcheck_configure']
            }
            dynacfgPipeline.remove('spellcheck_configure')
        } else {
            if (dynamatrixGlobalState.enableDebugTrace)
                echo "spellcheck.sanityCheckDynacfgPipeline(): populate missing dynacfgPipeline.spellcheck.configure = null"
            dynacfgPipeline['spellcheck']['configure'] = null
        }
    }

    if (dynamatrixGlobalState.enableDebugTrace) {
        println "SPELLCHECK_PREPCONF : " + Utils.castString(dynacfgPipeline['spellcheck']['prepconf'])
        println "SPELLCHECK_CONFIGURE: " + Utils.castString(dynacfgPipeline['spellcheck']['configure'])
        println "SPELLCHECK          : " + Utils.castString(dynacfgPipeline['spellcheck']['spellcheck'])
    }

    return dynacfgPipeline
}
