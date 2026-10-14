// Steps should not be in a package, to avoid CleanGroovyClassLoader exceptions...
// package org.nut.dynamatrix;

import org.nut.dynamatrix.*;

import org.nut.dynamatrix.DynamatrixSingleBuildConfig;
import org.nut.dynamatrix.Utils;
import org.nut.dynamatrix.dynamatrixGlobalState;

// TODO: make dynacfgPipeline a class?

/*
// Example config for this part of code:

    dynacfgPipeline['spellcheck'] = false //true

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
            return (sc.enabled != false && !"${sc.enabled}".trim().equalsIgnoreCase("false"))
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

def call(Map dynacfgPipeline = [:]) {
    // TOTHINK: Assume or *ensure* dynacfgPipeline?.spellcheck was
    //  normalized by spellcheck.sanityCheckDynacfgPipeline() ?
    if (isEnabled(dynacfgPipeline)) {
        infra.reportGithubStageStatus(dynacfgPipeline.get("stashnameSrc"),
                'awaiting spellcheck results',
                'PENDING', "spellcheck")

        Boolean succeeded = null
        while (!succeeded) {
            try {
                node(infra.labelDocumentationSpellchecker()) {
                    withEnvOptional(dynacfgPipeline.defaultTools) {
                        unstashCleanSrc(dynacfgPipeline.get("stashnameSrc"))

                        if (dynacfgPipeline.spellcheck?.prepconf != null) {
                            if (Utils.isStringNotEmpty(dynacfgPipeline.spellcheck.prepconf)) {
                                sh """ ${dynacfgPipeline.spellcheck.prepconf} """
                            } // else: pipeline author wants this skipped
                        } else {
                            if (dynacfgPipeline?.buildPhases?.prepconf) {
                                sh """ ${dynacfgPipeline.buildPhases.prepconf} """
                            }
                        }

                        if (dynacfgPipeline.spellcheck?.configure != null) {
                            if (Utils.isStringNotEmpty(dynacfgPipeline.spellcheck.configure)) {
                                sh """ ${dynacfgPipeline.spellcheck.configure} """
                            } // else: pipeline author wants this skipped
                        } else {
                            if (dynacfgPipeline?.buildPhases?.configure) {
                                sh """ ${dynacfgPipeline.buildPhases.configure} """
                            }
                        }

                        sh """ ${dynacfgPipeline.spellcheck.spellcheck} """
                        succeeded = true
                        infra.reportGithubStageStatus(dynacfgPipeline.get("stashnameSrc"),
                            'spellcheck passed for this commit',
                            'SUCCESS', "spellcheck")
                    }
                }
            } catch (Throwable t) {
                if (Utils.isRetryableException(t)) {
                    echo "[DEBUG]: spellcheck(${dynacfgPipeline.get("stashnameSrc")}) " +
                        " finished with a verdict classified as " +
                        "a retryable build agent failure - " +
                        "will re-schedule: ${t.toString()}"
                    succeeded = false
                } else {
                    infra.reportGithubStageStatus(dynacfgPipeline.get("stashnameSrc"),
                        'spellcheck failed for this commit',
                        'FAILURE', "spellcheck")
                    // Let the pipeline fault path take over
                    throw t
                }
            }
        }
    }
}

/**
 * Provide a Map using {@link spellcheck#call} as needed for `parallel` step.
 * Note it is not constrained as Map<String, Closure>!
 */
Map makeMap(Map dynacfgPipeline = [:]) {
    Map par = [:]
    if (isEnabled(dynacfgPipeline)) {
        par["spellcheck"] = {
            spellcheck(dynacfgPipeline)
        } // spellcheck
    }
    return par
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
        if (Utils.isMapNotEmpty(dynacfgPipeline['spellcheck'])) {
            if ("${dynacfgPipeline['spellcheck']['spellcheck']}".trim().equals("true")) {
                if (dynamatrixGlobalState.enableDebugTrace)
                    echo "spellcheck.sanityCheckDynacfgPipeline(): true: defaulting a reasonable config"
                dynacfgPipeline['spellcheck']['spellcheck'] = '( \${MAKE} spellcheck )'
            } else if ("${dynacfgPipeline['spellcheck']['spellcheck']}".trim().equals("false")) {
                if (dynamatrixGlobalState.enableDebugTrace)
                    echo "spellcheck.sanityCheckDynacfgPipeline(): false: defaulting a null config"
                dynacfgPipeline['spellcheck']['spellcheck'] = null
            }
        } else {
            // Legacy string or boolean
            if ("${dynacfgPipeline['spellcheck']}".trim().equals("true")) {
                if (dynamatrixGlobalState.enableDebugTrace)
                    echo "spellcheck.sanityCheckDynacfgPipeline(): true: defaulting a reasonable config"
                dynacfgPipeline['spellcheck'] = [
                    spellcheck: '( \${MAKE} spellcheck )'
                ]
            } else if ("${dynacfgPipeline['spellcheck']}".trim().equals("false")) {
                if (dynamatrixGlobalState.enableDebugTrace)
                    echo "spellcheck.sanityCheckDynacfgPipeline(): false: defaulting a null config"
                dynacfgPipeline['spellcheck'] = [
                    spellcheck: null
                ]
            } else (Utils.isStringNotEmpty(dynacfgPipeline['spellcheck'])) {
                // Assume it is a legacy direct-command format
                def tmp = dynacfgPipeline['spellcheck']
                dynacfgPipeline['spellcheck'] = [
                    spellcheck: tmp
                ]
            }
        }
    } else {
        if (dynamatrixGlobalState.enableDebugTrace)
            echo "spellcheck.sanityCheckDynacfgPipeline(): defaulting a null config"
        dynacfgPipeline['spellcheck'] = [
            spellcheck: null
        ]
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
        if ("${dynacfgPipeline['spellcheck_configure']}".trim().equals("true")) {
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
