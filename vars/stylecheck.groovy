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
    if (dynacfgPipeline.stylecheck) {
        node(infra.labelDocumentationWorker()) {
            infra.withEnvOptional(dynacfgPipeline.defaultTools) {
                unstashCleanSrc(dynacfgPipeline.stashnameSrc)
                if (dynacfgPipeline.prepconf)
                    sh """ ${dynacfgPipeline.prepconf} """
                if (dynacfgPipeline.configure)
                    sh """ ${dynacfgPipeline.configure} """
                sh """ ${dynacfgPipeline.stylecheck} """
            }
        }
    }
}

def makeMap(dynacfgPipeline = [:]) {
    def par = [:]
    if (dynacfgPipeline.stylecheck != null) {
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

    if (dynamatrixGlobalState.enableDebugTrace)
        println "SPELLCHECK: " + Utils.castString(dynacfgPipeline['stylecheck'])

    return dynacfgPipeline
}
