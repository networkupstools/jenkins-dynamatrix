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

def call(Map dynacfgPipeline = [:]) {
    if (dynacfgPipeline?.spellcheck) {
        infra.reportGithubStageStatus(dynacfgPipeline.get("stashnameSrc"),
                'awaiting spellcheck results',
                'PENDING', "spellcheck")
        node(infra.labelDocumentationWorker()) {
            withEnvOptional(dynacfgPipeline.defaultTools) {
                unstashCleanSrc(dynacfgPipeline.get("stashnameSrc"))

                if (dynacfgPipeline?.spellcheck_prepconf != null) {
                    if (Utils.isStringNotEmpty(dynacfgPipeline.spellcheck_prepconf)) {
                        sh """ ${dynacfgPipeline.spellcheck_prepconf} """
                    } // else: pipeline author wants this skipped
                } else {
                    if (dynacfgPipeline?.buildPhases?.prepconf) {
                        sh """ ${dynacfgPipeline.buildPhases.prepconf} """
                    }
                }

                if (dynacfgPipeline?.spellcheck_configure != null) {
                    if (Utils.isStringNotEmpty(dynacfgPipeline.spellcheck_configure)) {
                        sh """ ${dynacfgPipeline.spellcheck_configure} """
                    } // else: pipeline author wants this skipped
                } else {
                    if (dynacfgPipeline?.buildPhases?.configure) {
                        sh """ ${dynacfgPipeline.buildPhases.configure} """
                    }
                }

                try {
                    sh """ ${dynacfgPipeline.spellcheck} """
                    infra.reportGithubStageStatus(dynacfgPipeline.get("stashnameSrc"),
                            'spellcheck passed for this commit',
                            'SUCCESS', "spellcheck")
                } catch (Throwable t) {
                    infra.reportGithubStageStatus(dynacfgPipeline.get("stashnameSrc"),
                            'spellcheck failed for this commit',
                            'FAILURE', "spellcheck")
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
    if (dynacfgPipeline?.spellcheck != null) {
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
        if ("${dynacfgPipeline['spellcheck']}".trim().equals("true")) {
            if (dynamatrixGlobalState.enableDebugTrace)
                echo "spellcheck.sanityCheckDynacfgPipeline(): true: defaulting a reasonable config"
            dynacfgPipeline['spellcheck'] = '( \${MAKE} spellcheck )'
        } else if ("${dynacfgPipeline['spellcheck']}".trim().equals("false")) {
            if (dynamatrixGlobalState.enableDebugTrace)
                echo "spellcheck.sanityCheckDynacfgPipeline(): false: defaulting a null config"
            dynacfgPipeline['spellcheck'] = null
        }
    } else {
        if (dynamatrixGlobalState.enableDebugTrace)
            echo "spellcheck.sanityCheckDynacfgPipeline(): defaulting a null config"
        dynacfgPipeline['spellcheck'] = null
    }

    if (dynacfgPipeline.containsKey('spellcheck_prepconf')) {
        if ("${dynacfgPipeline['spellcheck_prepconf']}".trim().equals("true")) {
            // Use whatever buildPhases provide
            if (dynamatrixGlobalState.enableDebugTrace)
                echo "spellcheck.sanityCheckDynacfgPipeline(): populate missing dynacfgPipeline.spellcheck_prepconf = null"
            dynacfgPipeline['spellcheck_prepconf'] = null
        }
    } else {
        if (dynamatrixGlobalState.enableDebugTrace)
            echo "spellcheck.sanityCheckDynacfgPipeline(): populate missing dynacfgPipeline.spellcheck_prepconf = null"
        dynacfgPipeline['spellcheck_prepconf'] = null
    }

    if (dynacfgPipeline.containsKey('spellcheck_configure')) {
        if ("${dynacfgPipeline['spellcheck_configure']}".trim().equals("true")) {
            // Use whatever buildPhases provide
            if (dynamatrixGlobalState.enableDebugTrace)
                echo "spellcheck.sanityCheckDynacfgPipeline(): populate missing dynacfgPipeline.spellcheck_configure = null"
            dynacfgPipeline['spellcheck_configure'] = null
        }
    } else {
        if (dynamatrixGlobalState.enableDebugTrace)
            echo "spellcheck.sanityCheckDynacfgPipeline(): populate missing dynacfgPipeline.spellcheck_configure = null"
        dynacfgPipeline['spellcheck_configure'] = null
    }

    if (dynamatrixGlobalState.enableDebugTrace) {
        println "SPELLCHECK_PREPCONF : " + Utils.castString(dynacfgPipeline['spellcheck_prepconf'])
        println "SPELLCHECK_CONFIGURE: " + Utils.castString(dynacfgPipeline['spellcheck_configure'])
        println "SPELLCHECK          : " + Utils.castString(dynacfgPipeline['spellcheck'])
    }

    return dynacfgPipeline
}
