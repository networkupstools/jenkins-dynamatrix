import org.nut.dynamatrix.*

import org.nut.dynamatrix.DynamatrixSingleBuildConfig
import org.nut.dynamatrix.Utils
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

def call(dynacfgPipeline = [:]) {
    if (dynacfgPipeline?.spellcheck) {
        node(infra.labelDocumentationWorker()) {
            withEnvOptional(dynacfgPipeline.defaultTools) {
                unstashCleanSrc(dynacfgPipeline.stashnameSrc)

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

                sh """ ${dynacfgPipeline.spellcheck} """
            }
        }
    }
}

def makeMap(dynacfgPipeline = [:]) {
    def par = [:]
    if (dynacfgPipeline?.spellcheck != null) {
        par["spellcheck"] = {
            spellcheck(dynacfgPipeline)
        } // spellcheck
    }
    return par
}

def sanityCheckDynacfgPipeline(dynacfgPipeline = [:]) {
    if (dynacfgPipeline.containsKey('spellcheck')) {
        if ("${dynacfgPipeline['spellcheck']}".trim().equals("true")) {
            dynacfgPipeline['spellcheck'] = '( \${MAKE} spellcheck )'
        } else if ("${dynacfgPipeline['spellcheck']}".trim().equals("false")) {
            dynacfgPipeline['spellcheck'] = null
        }
    } else {
        dynacfgPipeline['spellcheck'] = null
    }

    if (dynacfgPipeline.containsKey('spellcheck_prepconf')) {
        if ("${dynacfgPipeline['spellcheck_prepconf']}".trim().equals("true")) {
            // Use whatever buildPhases provide
            dynacfgPipeline['spellcheck_prepconf'] = null
        }
    } else {
        dynacfgPipeline['spellcheck_prepconf'] = null
    }

    if (dynacfgPipeline.containsKey('spellcheck_configure')) {
        if ("${dynacfgPipeline['spellcheck_configure']}".trim().equals("true")) {
            // Use whatever buildPhases provide
            dynacfgPipeline['spellcheck_configure'] = null
        }
    } else {
        dynacfgPipeline['spellcheck_configure'] = null
    }

    if (dynamatrixGlobalState.enableDebugTrace) {
        println "SPELLCHECK_PREPCONF : " + Utils.castString(dynacfgPipeline['spellcheck_prepconf'])
        println "SPELLCHECK_CONFIGURE: " + Utils.castString(dynacfgPipeline['spellcheck_configure'])
        println "SPELLCHECK          : " + Utils.castString(dynacfgPipeline['spellcheck'])
    }

    return dynacfgPipeline
}
