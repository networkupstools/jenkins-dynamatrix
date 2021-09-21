import org.nut.dynamatrix.*

import org.nut.dynamatrix.DynamatrixSingleBuildConfig
import org.nut.dynamatrix.Utils
import org.nut.dynamatrix.dynamatrixGlobalState;

// TODO: make dynacfgPipeline a class?

/*
// Example config for this part of code:

    dynacfgPipeline['stylecheck'] = false //true

 */

// Don't forget to call the sanity-checker below during pipeline init...
// or maybe do it from the routine here?
// Note that this code relies on more data points than just
// dynacfgPipeline.stylecheck.*

def call(dynacfgPipeline = [:]) {
    if (dynacfgPipeline?.stylecheck) {
        node(infra.labelDocumentationWorker()) {
            infra.withEnvOptional(dynacfgPipeline?.defaultTools) {
                unstashCleanSrc(dynacfgPipeline.stashnameSrc)

                if (dynacfgPipeline?.stylecheck_prepconf != null) {
                    if (Utils.isStringNotEmpty(dynacfgPipeline.stylecheck_prepconf)) {
                        sh """ ${dynacfgPipeline.stylecheck_prepconf} """
                    } // else: pipeline author wants this skipped
                } else {
                    if (dynacfgPipeline?.buildPhases?.prepconf) {
                        sh """ ${dynacfgPipeline.buildPhases.prepconf} """
                    }
                }

                if (dynacfgPipeline?.stylecheck_configure != null) {
                    if (Utils.isStringNotEmpty(dynacfgPipeline.stylecheck_configure)) {
                        sh """ ${dynacfgPipeline.stylecheck_configure} """
                    } // else: pipeline author wants this skipped
                } else {
                    if (dynacfgPipeline?.buildPhases?.configure) {
                        sh """ ${dynacfgPipeline.buildPhases.configure} """
                    }
                }

                sh """ ${dynacfgPipeline.stylecheck} """
            }
        }
    }
}

def makeMap(dynacfgPipeline = [:]) {
    def par = [:]
    if (dynacfgPipeline?.stylecheck != null) {
        par["stylecheck"] = {
            stylecheck(dynacfgPipeline)
        } // stylecheck
    }
    return par
}

def sanityCheckDynacfgPipeline(dynacfgPipeline = [:]) {
    if (dynacfgPipeline.containsKey('stylecheck')) {
        if ("${dynacfgPipeline['stylecheck']}".trim().equals("true")) {
            dynacfgPipeline['stylecheck'] = '( \${MAKE} stylecheck )'
        } else if ("${dynacfgPipeline['stylecheck']}".trim().equals("false")) {
            dynacfgPipeline['stylecheck'] = null
        }
    } else {
        dynacfgPipeline['stylecheck'] = null
    }

    if (dynacfgPipeline.containsKey('stylecheck_prepconf')) {
        if ("${dynacfgPipeline['stylecheck_prepconf']}".trim().equals("true")) {
            // Use whatever buildPhases provide
            dynacfgPipeline['stylecheck_prepconf'] = null
        }
    } else {
        dynacfgPipeline['stylecheck_prepconf'] = null
    }

    if (dynacfgPipeline.containsKey('stylecheck_configure')) {
        if ("${dynacfgPipeline['stylecheck_configure']}".trim().equals("true")) {
            // Use whatever buildPhases provide
            dynacfgPipeline['stylecheck_configure'] = null
        }
    } else {
        dynacfgPipeline['stylecheck_configure'] = null
    }

    if (dynamatrixGlobalState.enableDebugTrace) {
        println "STYLECHECK_PREPCONF : " + Utils.castString(dynacfgPipeline['stylecheck_prepconf'])
        println "STYLECHECK_CONFIGURE: " + Utils.castString(dynacfgPipeline['stylecheck_configure'])
        println "STYLECHECK          : " + Utils.castString(dynacfgPipeline['stylecheck'])
    }

    return dynacfgPipeline
}
