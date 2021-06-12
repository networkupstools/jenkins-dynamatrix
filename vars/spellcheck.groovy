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
// Note that this code relies on mode data points than just dynacfgPipeline.shellcheck.*

def call(dynacfgPipeline = [:]) {
    if (dynacfgPipeline.spellcheck && dynacfgPipeline.prepconf && dynacfgPipeline.configure) {
        node(infra.labelDocumentationWorker()) {
            infra.withEnvOptional(dynacfgPipeline.defaultTools) {
                unstashCleanSrc(dynacfgPipeline.stashnameSrc)
                sh """ ${dynacfgPipeline.prepconf} && ${dynacfgPipeline.configure} """
                sh """ ${dynacfgPipeline.spellcheck} """
            }
        }
    }
}

def makeMap(dynacfgPipeline = [:]) {
    def par = [:]
    if (dynacfgPipeline.spellcheck != null) {
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
    //println "SPELLCHECK: " + Utils.castString(dynacfgPipeline['spellcheck'])

    return dynacfgPipeline
}
