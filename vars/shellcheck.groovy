import org.nut.dynamatrix.*

import org.nut.dynamatrix.DynamatrixSingleBuildConfig
import org.nut.dynamatrix.Utils
import org.nut.dynamatrix.dynamatrixGlobalState;

// TODO: make dynacfgPipeline a class?

/*
// Example config for this part of code:

@NonCPS
def stageNameFunc_Shellcheck(DynamatrixSingleBuildConfig dsbc) {
    // TODO: Offload to a routine and reference by name here?
    // A direct Closure seems to confuse Jenkins/Groovy CPS
    def labelMap = dsbc.getKVMap(false)
    String sn = ""
    if (labelMap.containsKey("OS_FAMILY"))
        sn += labelMap.OS_FAMILY + "-"
    if (labelMap.containsKey("OS_DISTRO"))
        sn += labelMap.OS_DISTRO + "-"
    return "MATRIX_TAG=\"${sn}shellcheckCustom\""
}

    //dynacfgPipeline['shellcheck'] = true //false
    dynacfgPipeline['shellcheck'] = [
        //'stageNameFunc': null,
        'single': '( \${MAKE} shellcheck )',
        'multi': '(cd tests && SHELL_PROGS="$SHELL_PROGS" ./nut-driver-enumerator-test.sh )',
        'multiLabel': 'SHELL_PROGS',
        'skipShells': [ 'zsh', 'tcsh', 'csh' ]
    ]
    //dynacfgPipeline.shellcheck.stageNameFunc = this.&stageNameFunc_Shellcheck

 */

// Don't forget to call the sanity-checker below during pipeline init...
// or maybe do it from the routine here?
// Note that this code relies on mode data points than just dynacfgPipeline.shellcheck.*

def call(dynacfgPipeline = [:], Boolean returnSet = true) {
    // Returns a Set of tuples that a later stage can convert
    // into a Map for `parallel` test running (avoid having a
    // long-lived Map object which can upset CPS and pipeline
    // serialization).
    def stagesShellcheck_arr = []

    // In outer layer, select all suitable builders;
    // In inner layer, unpack+config the source on
    // that chosen host once, and use that workspace
    // for distinct test stages with different shells.
    // Note that BO UI might not render them separately
    // (seems to only render the first mentioned stage),
    // but "Classic UI" Pipeline Steps tree should...
    //println "SHELLCHECK: ${Utils.castString(dynacfgPipeline.shellcheck)}"
    if (dynacfgPipeline?.shellcheck?.single != null || dynacfgPipeline?.shellcheck?.multi != null) {
        //println "Discovering stagesShellcheck..."
        // Note: Different label set, different dynamatrix
        // instance (inside step), though hopefully same
        // cached NodeCaps array reused
        stagesShellcheck_arr = prepareDynamatrix([
            dynamatrixAxesLabels: dynacfgPipeline.shellcheck.dynamatrixAxesLabels,
            mergeMode: [ 'dynamatrixAxesLabels': 'replace' ],
            stageNameFunc: dynacfgPipeline.shellcheck.stageNameFunc
            ],
            returnSet) { delegate -> setDelegate(delegate)
                    def MATRIX_TAG = delegate.stageName.trim() - ~/^MATRIX_TAG="*/ - ~/"*$/

                    // Cache faults of sub-tests as a fault of this big stage,
                    // but let them all pass first so we know all shells which
                    // complain and not just the first one per host
                    // See also https://stackoverflow.com/a/58737417/4715872
                    def bigStageResult = 'SUCCESS'

                    // Let BO render all this work somehow at least
                    // It also tends to say "QueuedWaiting for run to start"
                    // until everything is done... Classic UI flowGraphTable
                    // fares a lot better but less user-friently to glance.
                    stage("shellcheck for ${MATRIX_TAG}") {
                        // On current node/workspace, prepare source once for
                        // tests that are not expected to impact each other
                        stage("prep for ${MATRIX_TAG}") {
                            sh """ echo "UNPACKING for '${MATRIX_TAG}'" """
                            withEnvOptional(dynacfgPipeline.defaultTools) {
                                unstashCleanSrc(dynacfgPipeline.stashnameSrc)

                                if (dynacfgPipeline?.shellcheck_prepconf != null) {
                                    if (Utils.isStringNotEmpty(dynacfgPipeline.shellcheck_prepconf)) {
                                        sh """ ${dynacfgPipeline.shellcheck_prepconf} """
                                    } // else: pipeline author wants this skipped
                                } else {
                                    if (dynacfgPipeline?.buildPhases?.prepconf) {
                                        sh """ ${dynacfgPipeline.buildPhases.prepconf} """
                                    }
                                }

                                if (dynacfgPipeline?.shellcheck_configure != null) {
                                    if (Utils.isStringNotEmpty(dynacfgPipeline.shellcheck_configure)) {
                                        sh """ ${dynacfgPipeline.shellcheck_configure} """
                                    } // else: pipeline author wants this skipped
                                } else {
                                    if (dynacfgPipeline?.buildPhases?.configure) {
                                        sh """ ${dynacfgPipeline.buildPhases.configure} """
                                    }
                                }

                            }
                            return true
                        }

                        // Jenkins Groovy CPS does not like to track a Map
                        // since it (or its iterator) can not be serialized.
                        // Instead we will keep an array of tuples and use
                        // that below to launch the generated substages.
                        // Loosely inspired by ideas from https://stackoverflow.com/a/40166064/4715872
                        def stagesShellcheckNode = []
                        // Iterate with separate verdicts when/if `make shellcheck`
                        // (or equivalent) actually supports various $SHELL tests
                        if (dynacfgPipeline.shellcheck.multi != null) {
                            if (dynacfgPipeline.shellcheck.multiLabel != null) {
                                if (env.NODE_LABELS && env.NODE_NAME) {
                                    NodeData.getNodeLabelsByName(env.NODE_NAME).each { label ->
                                        if (label.startsWith("${dynacfgPipeline.shellcheck.multiLabel}=")) {
                                            String[] keyValue = label.split("=", 2)
                                            String SHELL_PROGS=keyValue[1]
                                            if (Utils.isListNotEmpty(dynacfgPipeline.shellcheck.skipShells)) {
                                                // TODO: Variant with Map for "shell on OS"? e.g. ['zsh': /.*(inux|indows).*/]
                                                if (SHELL_PROGS in dynacfgPipeline.shellcheck.skipShells) {
                                                    echo "SKIP SHELLCHECK with ${SHELL_PROGS} for ${MATRIX_TAG}"
                                                    return
                                                }
                                            }
                                            def stagesShellcheckNode_key = "Test with ${SHELL_PROGS} for ${MATRIX_TAG}"
                                            def stagesShellcheckNode_val = {
                                                def msgFail = "Failed stage: ${stageName} with shell '${SHELL_PROGS}'" + "\n  for ${Utils.castString(dsbc)}"
                                                def didFail = true
                                                catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE', message: msgFail) {
                                                    withEnv(["${dynacfgPipeline.shellcheck.multiLabel}=${SHELL_PROGS}"]) {
                                                        withEnvOptional(dynacfgPipeline.defaultTools) {
                                                            sh """ set +x
                                                            echo "Shell-dependent testing with shell '${SHELL_PROGS}' on `uname -a || hostname || true` system"
                                                            ${dynacfgPipeline.shellcheck.multi}
                                                            """
                                                        }
                                                    }
                                                    didFail = false
                                                }

                                                if (didFail) {
                                                    // Track the big-stage fault to explode in the end:
                                                    bigStageResult = 'FAILURE'
                                                    dsbc.dsbcResult = 'FAILURE'
                                                    // Track the small-stage fault in a way that we can continue with other sub-stages:
                                                    //echo msgFail
                                                    currentBuild.result = 'FAILURE'
                                                    manager.buildFailure()
                                                    // Using unstable here to signal that something is
                                                    // wrong with the stage verdict; given the earlier
                                                    // harsher FAILURE that would be the verdict used.
                                                    unstable(msgFail)
                                                }
                                                return didFail
                                            } // added stage
                                            def stagesShellcheckNode_tuple = [stagesShellcheckNode_key, stagesShellcheckNode_val]
                                            stagesShellcheckNode << stagesShellcheckNode_tuple
                                        }
                                    }
                                }
                            }

                            // TOTHINK: Skip agent that does not declare any shell labels?..
                            if (stagesShellcheckNode.size() == 0) {
                                def stagesShellcheckNode_tuple = ["Test with default shell(s) for ${MATRIX_TAG}", {
                                    withEnvOptional(dynacfgPipeline.defaultTools) {
                                        sh """ set +x
                                        echo "Shell-dependent testing with default shell on `uname -a || hostname || true` system"
                                        ${dynacfgPipeline.shellcheck.multi}
                                        """
                                    }
                                    return true
                                }]
                                stagesShellcheckNode << stagesShellcheckNode_tuple
                            }
                        }

                        if (dynacfgPipeline.shellcheck.single != null) {
                            def stagesShellcheckNode_tuple = ["Generic-shell test for ${MATRIX_TAG}", {
                                withEnvOptional(dynacfgPipeline.defaultTools) {
                                    sh """ set +x
                                    echo "Generic-shell test (with recipe defaults) on `uname -a || hostname || true` system"
                                    ${dynacfgPipeline.shellcheck.single}
                                    """
                                }
                                return true
                            }]
                            stagesShellcheckNode << stagesShellcheckNode_tuple
                        }

                        println "Discovered ${stagesShellcheckNode.size()} stagesShellcheckNode sub-stages for ${MATRIX_TAG}"

/*
                        stagesShellcheckNode.each { stagesShellcheckNode_tuple ->
                            def name = stagesShellcheckNode_tuple[0] // stagesShellcheckNode_key
                            def closure = stagesShellcheckNode_tuple[1] // stagesShellcheckNode_val
                            stage(name) {
                                return closure.call()
                            }
                        }
*/
                        if (true) {
                            def stagesPar = [:]
                            stagesShellcheckNode.each { stagesShellcheckNode_tuple ->
                                def name = stagesShellcheckNode_tuple[0] // stagesShellcheckNode_key
                                def closure = stagesShellcheckNode_tuple[1] // stagesShellcheckNode_val
                                stagesPar[name] = closure
                            }
                            parallel stagesPar
                            //parallel stagesShellcheckNode
                        }
                    }

                    // Even if our bigStageResult is not set (is success)
                    // but some other way of providing the verdict failed,
                    // it can not get any better. So we can just assign.
                    currentBuild.result = bigStageResult
                    if (bigStageResult == 'FAILURE') {
                        def msg = "FATAL: shellcheck for ${MATRIX_TAG} failed in at least one sub-test above"
                        //echo msg
                        manager.buildFailure()
                        //error msg
                        unstable(msg)
                }
            } // generateBuild + closure for one hit of stagesShellcheck
    } // if dynacfgPipeline.shellcheck

    println "Discovered ${stagesShellcheck_arr.size()} stagesShellcheck_arr platform builds"

    return stagesShellcheck_arr
}

def makeMap(stagesShellcheck_arr = []) {
    // Restore Map from Set, as needed for `parallel`
    println "Restoring discovered stagesShellcheck platform builds from Set to Map..."
    def stagesShellcheck = [:]
    stagesShellcheck_arr.each {tup ->
        println "Restoring stagesShellcheck platform build: ${Utils.castString(tup)}"
        stagesShellcheck[tup[0]] = tup[1]
    }
    println "Discovered ${stagesShellcheck.size()} stagesShellcheck platform builds: ${Utils.castString(stagesShellcheck)}"
    return stagesShellcheck
}

def sanityCheckDynacfgPipeline(dynacfgPipeline = [:]) {
    if (dynacfgPipeline.containsKey('shellcheck')) {
        if (Utils.isMap(dynacfgPipeline['shellcheck'])) {
            if (!dynacfgPipeline['shellcheck'].containsKey('dynamatrixAxesLabels') || "".equals(dynacfgPipeline['shellcheck']['dynamatrixAxesLabels'])) {
                dynacfgPipeline['shellcheck']['dynamatrixAxesLabels'] = [~/^OS_.+/]
            }
            if (!dynacfgPipeline['shellcheck'].containsKey('single') || "".equals(dynacfgPipeline['shellcheck']['single'])) {
                dynacfgPipeline['shellcheck']['single'] = null
            }
            if (!dynacfgPipeline['shellcheck'].containsKey('multi') || "".equals(dynacfgPipeline['shellcheck']['multi'])) {
                dynacfgPipeline['shellcheck']['multi'] = null
            }
            if (!dynacfgPipeline['shellcheck'].containsKey('multiLabel') || "".equals(dynacfgPipeline['shellcheck']['multiLabel'])) {
                dynacfgPipeline['shellcheck']['multiLabel'] = 'SHELL_PROGS'
            }
        } else {
            if ("${dynacfgPipeline['shellcheck']}".trim().equals("true")) {
                // Assign defaults
                dynacfgPipeline['shellcheck'] = [
                    'dynamatrixAxesLabels': [~/^OS_.+/],
                    'single': '( \${MAKE} shellcheck )',
                    'multi': '( SHELL_PROGS="$SHELL_PROGS" \${MAKE} shellcheck )',
                    'multiLabel': 'SHELL_PROGS'
                ]
            } else if ("${dynacfgPipeline['shellcheck']}".trim().equals("false")) {
                dynacfgPipeline['shellcheck'] = [:]
                dynacfgPipeline['shellcheck']['dynamatrixAxesLabels'] = []
                dynacfgPipeline['shellcheck']['single'] = null
                dynacfgPipeline['shellcheck']['multi'] = null
                dynacfgPipeline['shellcheck']['multiLabel'] = null
            } else {
                error "Unsupported dynacfgPipeline['shellcheck'] (expecting Boolean or Map) : ${Utils.castString(dynacfgPipeline['shellcheck'])}"
            }
        }
    } else {
        dynacfgPipeline['shellcheck'] = [:]
        dynacfgPipeline['shellcheck']['dynamatrixAxesLabels'] = []
        dynacfgPipeline['shellcheck']['single'] = null
        dynacfgPipeline['shellcheck']['multi'] = null
        dynacfgPipeline['shellcheck']['multiLabel'] = null
    }

    if (!dynacfgPipeline.shellcheck.stageNameFunc) {
        dynacfgPipeline.shellcheck.stageNameFunc = DynamatrixSingleBuildConfig.&ShellcheckPlatform_StageNameTagFunc
    }

    if (dynacfgPipeline.containsKey('shellcheck_prepconf')) {
        if ("${dynacfgPipeline['shellcheck_prepconf']}".trim().equals("true")) {
            // Use whatever buildPhases provide
            dynacfgPipeline['shellcheck_prepconf'] = null
        }
    } else {
        dynacfgPipeline['shellcheck_prepconf'] = null
    }

    if (dynacfgPipeline.containsKey('shellcheck_configure')) {
        if ("${dynacfgPipeline['shellcheck_configure']}".trim().equals("true")) {
            // Use whatever buildPhases provide
            dynacfgPipeline['shellcheck_configure'] = null
        }
    } else {
        dynacfgPipeline['shellcheck_configure'] = null
    }

    if (dynamatrixGlobalState.enableDebugTrace) {
        println "SHELLCHECK_PREPCONF : " + Utils.castString(dynacfgPipeline['shellcheck_prepconf'])
        println "SHELLCHECK_CONFIGURE: " + Utils.castString(dynacfgPipeline['shellcheck_configure'])
        println "SHELLCHECK          : " + Utils.castString(dynacfgPipeline['shellcheck'])
    }

    return dynacfgPipeline
}
