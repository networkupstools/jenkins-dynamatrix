/* This file ties together bits from the Dynamatrix project,
 * to test something on a diverse set of platforms defined by
 * Jenkins agents that are present and declare capabilities,
 * including the following label names (and their values):
 * OS_FAMILY, OS_DISTRO, COMPILER, ${COMPILER}VER (e.g. GCCVER),
 * SHELL_PROGS, MAKE, ARCH_BITS, ARCH${ARCH_BITS} (e.g. ARCH32)
 */
import org.nut.dynamatrix.*;

/*
// For in-place tests as Replay pipeline:
@Library('jenkins-dynamatrix') _
import org.nut.dynamatrix.dynamatrixGlobalState;
import org.nut.dynamatrix.*;

    def dynacfgBase = [:]
    def dynacfgPipeline = [:]

    dynacfgPipeline['spellcheck'] = false //true
    dynacfgPipeline['shellcheck'] = true
    dynacfgPipeline['NUT-shellcheck'] = [
        'single': '( \${MAKE} shellcheck )',
        'multi': '(cd tests && SHELL_PROGS="$SHELL_PROGS" ./nut-driver-enumerator-test.sh )',
        'multiLabel': 'SHELL_PROGS',
        'skipShells': [ 'zsh', 'tcsh', 'csh' ]
    ]

    dynacfgBase['commonLabelExpr'] = 'nut-builder'
    dynacfgBase['dynamatrixAxesLabels'] = //[~/^OS_.+/]
        ['OS_FAMILY', 'OS_DISTRO', '${COMPILER}VER', 'ARCH${ARCH_BITS}']

    dynacfgPipeline.getParStages = { dynamatrix, Closure body ->
        return dynamatrix.generateBuild([
            //commonLabelExpr: dynacfgBase.commonLabelExpr,
            //defaultDynamatrixConfig: dynacfgBase.defaultDynamatrixConfig,

            dynamatrixAxesVirtualLabelsMap: [
                'BITS': [32, 64],
                // 'CSTDVERSION': ['03', '2a'],
                //'CSTDVERSION_${KEY}': [ ['c': '03', 'cxx': '03'], ['c': '99', 'cxx': '98'], ['c': '17', 'cxx': '2a'], 'ansi' ],
                //'CSTDVERSION_${KEY}': [ ['c': '03', 'cxx': '03'], ['c': '99', 'cxx': '98'], ['c': '17', 'cxx': '2a'] ],
                'CSTDVERSION_${KEY}': [ ['c': '99', 'cxx': '11'] ],
                'CSTDVARIANT': ['gnu']
                ],

            mergeMode: [ 'dynamatrixAxesVirtualLabelsMap': 'merge', 'excludeCombos': 'merge' ],
            allowedFailure: [ [~/CSTDVARIANT=c/] ],
            runAllowedFailure: true,
            //dynamatrixAxesLabels: [~/^OS_DISTRO/, '${COMPILER}VER', 'ARCH${ARCH_BITS}'],
            //dynamatrixAxesLabels: ['OS_FAMILY', 'OS_DISTRO', '${COMPILER}VER', 'ARCH${ARCH_BITS}'],
            //dynamatrixAxesLabels: [~/^OS/, '${COMPILER}VER', 'ARCH${ARCH_BITS}'],
            excludeCombos: [ [~/BITS=32/, ~/ARCH_BITS=64/], [~/BITS=64/, ~/ARCH_BITS=32/] ]
            ], body)
    }

    //dynacfgPipeline.bodyParStages = {}

// and lower in code

                            stashCleanSrc(stashnameSrc) {
                                git (url: "/home/jim/nut-DMF", branch: "fightwarn")
                            }

 */

def call(dynacfgBase = [:], dynacfgPipeline = [:]) {
    // Hacky big switch for a max debug option
    if (true)
    if (false)
    {
    dynamatrixGlobalState.enableDebugTrace = true
    dynamatrixGlobalState.enableDebugErrors = true
    dynamatrixGlobalState.enableDebugMilestones = true
    dynamatrixGlobalState.enableDebugMilestonesDetails = true
    }

/*
// EXAMPLE: The pipeline can define and pass (below)
// a custom routine to name generated stages.
// Currently the code below defaults to using library-provided method.
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
    return "MATRIX_TAG=\"${sn}shellcheck\""
}
*/

    if (!dynacfgBase.containsKey('defaultDynamatrixConfig')) {
        dynacfgBase['defaultDynamatrixConfig'] = "C+CXX"
    }

    DynamatrixConfig dynacfg = new DynamatrixConfig(this)
    dynacfg.initDefault(dynacfgBase)

    if (dynacfg.compilerType in ['C']) {
        dynamatrixGlobalState.stageNameFunc = DynamatrixSingleBuildConfig.&C_StageNameTagFunc
    }

    // Sanity-check the pipeline options
    // Not using closures to make sure envvars are expanded during real
    // shell execution and not at an earlier processing stage by Groovy -
    // so below we define many subshelled blocks in parentheses that would
    // be "pasted" into the `sh` steps.

    // Initialize default `make` implementation to use (there are many), etc.:
    if (!dynacfgPipeline.containsKey('defaultTools')) {
        dynacfgPipeline['defaultTools'] = [
            'MAKE': 'make'
        ]
    }

    // Subshell common operations to prepare codebase:
    if (!dynacfgPipeline.containsKey('prepconf')) {
        dynacfgPipeline['prepconf'] = "( if [ -x ./autogen.sh ]; then ./autogen.sh || exit; else if [ -s configure.ac ] ; then mkdir -p config && autoreconf --install --force --verbose -I config || exit ; fi; fi ; [ -x configure ] || exit )"
    }

    if (!dynacfgPipeline.containsKey('configure')) {
        dynacfgPipeline['configure'] = "( [ -x configure ] || exit ; ./configure \${CONFIG_OPTS} )"
    }

    if (!dynacfgPipeline.containsKey('failFast')) {
        dynacfgPipeline.failFast = true
    }

    // Sanity-check certain build milestones expecting certain cfg structure:
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

    if (dynacfgPipeline.containsKey('shellcheck')) {
        if (Utils.isMap(dynacfgPipeline['shellcheck'])) {
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
                    'single': '( \${MAKE} shellcheck )',
                    'multi': '( SHELL_PROGS="$SHELL_PROGS" \${MAKE} shellcheck )',
                    'multiLabel': 'SHELL_PROGS'
                ]
            } else if ("${dynacfgPipeline['shellcheck']}".trim().equals("false")) {
                dynacfgPipeline['shellcheck'] = [:]
                dynacfgPipeline['shellcheck']['single'] = null
                dynacfgPipeline['shellcheck']['multi'] = null
                dynacfgPipeline['shellcheck']['multiLabel'] = null
            } else {
                error "Unsupported dynacfgPipeline['shellcheck'] (expecting Boolean or Map) : ${Utils.castString(dynacfgPipeline['shellcheck'])}"
            }
        }
    } else {
        dynacfgPipeline['shellcheck'] = [:]
        dynacfgPipeline['shellcheck']['single'] = null
        dynacfgPipeline['shellcheck']['multi'] = null
        dynacfgPipeline['shellcheck']['multiLabel'] = null
    }
    //println "SHELLCHECK: " + Utils.castString(dynacfgPipeline['shellcheck'])

    Dynamatrix dynamatrix = new Dynamatrix(this)
    def stashnameSrc = 'src-checkedout'

    // To hop over CPS limitations, we first store our stages
    // (generated inside CPS code) as a Set of tuples, then
    // convert into a Map just for `parallel`. Go figure...
    def stagesShellcheck_arr = []

    // This is hopefully safer, called not from CPS constraints
    def stagesBinBuild = [:]

    node(infra.labelDefaultWorker()) {
        skipDefaultCheckout()

        // Avoid occasional serialization of pipeline during run:
        // despite best efforts, a Map is sometimes seen here and
        // confuses CPS, about 50% of the runs. Without an active
        // serialization, it works better, and faster (less I/O).
        // We do not much care to "resume" a pipeline run, if the
        // Jenkins instance is restarted while it runs.
        // Also enable other "options" (in Declarative parlance).
        disableResume()
        properties([
            durabilityHint('PERFORMANCE_OPTIMIZED'),
            [$class: 'RebuildSettings', autoRebuild: false, rebuildDisabled: false],
            throttleJobProperty(categories: [], limitOneJobWithMatchingParams: false, maxConcurrentPerNode: 0, maxConcurrentTotal: 0, paramsToUseForLimit: '', throttleEnabled: false, throttleOption: 'project')
        ])

        stage("Initial discovery") {
            parallel (

                "Stash source for workers": {
/*
 * NOTE: For quicker builds, it is recommended to set up the pipeline job
 * using this Jenkinsfile to refer to a local copy of the Git repository
 * maintained on the stashing worker (as a Reference Repo), and do just
 * shallow checkouts (depth=1). Longer history may make sense for release
 * builds with changelog generation, but not for quick test iterations.
 */
                    node(infra.labelCheckoutWorker()) {
                        stashCleanSrc(stashnameSrc)
                        }
                    }
                }, // stage - stash

                "Discover quick build matrix": {
                    // Relatively quick discovery (e.g. filtering axes
                    // by regexes takes long when many build agents are
                    // involved, so that part happens in parallel to
                    // this shellcheck and also a spellcheck, which can
                    // be prepared quickly):

                    // Have some defaults, if only to have all
                    // expected fields defined and node caps cached
                    dynamatrix.prepareDynamatrix(dynacfgBase)

                    // In outer layer, select all suitable builders;
                    // In inner layer, unpack+config the source on
                    // that chosen host once, and use that workspace
                    // for distinct test stages with different shells.
                    // Note that BO UI might not render them separately
                    // (seems to only render the first mentioned stage),
                    // but "Classic UI" Pipeline Steps tree should...
                    //println "SHELLCHECK: ${Utils.castString(dynacfgPipeline.shellcheck)}"
                    if (dynacfgPipeline.shellcheck.single != null || dynacfgPipeline.shellcheck.multi != null) {
                        //println "Discovering stagesShellcheck..."
                        // Note: Different label set, different dynamatrix
                        // instance (inside step), though hopefully same
                        // cached NodeCaps array reused
                        stagesShellcheck_arr = prepareDynamatrix([
                            dynamatrixAxesLabels: [~/^OS_.+/],
                            mergeMode: [ 'dynamatrixAxesLabels': 'replace' ],
                            stageNameFunc: DynamatrixSingleBuildConfig.&ShellcheckPlatform_StageNameTagFunc
                            // EXAMPLE: Can use a pipeline-provided method, see above in this file:
                            //stageNameFunc: this.&stageNameFunc_Shellcheck
                            ],
                            true) { delegate -> setDelegate(delegate)
                                //SCR//script {
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
                                            infra.withEnvOptional(dynacfgPipeline.defaultTools) {
                                                unstashCleanSrc(stashnameSrc)
                                                sh """ ${dynacfgPipeline.prepconf} && ${dynacfgPipeline.configure} """
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
                                                                        infra.withEnvOptional(dynacfgPipeline.defaultTools) {
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
                                                    infra.withEnvOptional(dynacfgPipeline.defaultTools) {
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
                                                infra.withEnvOptional(dynacfgPipeline.defaultTools) {
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
                                //SCR//} // script{} for big shellcheck test on one hit
                            } // generateBuild + closure for one hit of stagesShellcheck
                    } // if dynacfgPipeline.shellcheck

                    println "Discovered ${stagesShellcheck_arr.size()} stagesShellcheck_arr platform builds"

                    /* As noted above, any relatively heavy axis filters,
                     * which need many seconds to process just to decide
                     * what should be built this time, should happen in
                     * the next stage along with quick tests - like the
                     * spellcheck and shellcheck targets.
                     */
                } // stage - discover the matrix

            ) // parallel-initial
        } // stage-initial

        // Rest of code continues like a scripted pipeline

/*
    stage("Run quick tests and prepare the big dynamatrix") {
        parallel ([

            "shellchecks": {
                stagesShellcheck.each { name, closure ->
                    stage(name) {
                        closure()
                    }
                }
            },

            "spellcheck": {
                if (dynacfgPipeline.spellcheck != null) {
                    node(infra.labelDocumentationWorker()) {
                        infra.withEnvOptional(dynacfgPipeline.defaultTools) {
                            unstashCleanSrc(stashnameSrc)
                            sh """ ${dynacfgPipeline.prepconf} && ${dynacfgPipeline.configure} """
                            sh """ ${dynacfgPipeline.spellcheck} """
                        }
                    }
                }
            } // spellcheck

        ]) // parallel-quick
    } // stage-quick
*/

/*
    def par1 = stagesShellcheck

    if (dynacfgPipeline.spellcheck != null) {
        par1["spellcheck"] = {
            node(infra.labelDocumentationWorker()) {
                infra.withEnvOptional(dynacfgPipeline.defaultTools) {
                    unstashCleanSrc(stashnameSrc)
                    sh """ ${dynacfgPipeline.prepconf} && ${dynacfgPipeline.configure} """
                    sh """ ${dynacfgPipeline.spellcheck} """
                }
            }
        } // spellcheck
    }

    par1.failFast = false

    stage("Quick tests and prepare the bigger dynamatrix") {
        parallel par1
    } // stage-quick
*/

        // Do not mix parallel and usual sub-stages inside the
        // stage below - at least, BO does not render that well
        // and it may cause (or not?) CPS faults somehow...
        stage("Quick tests and prepare the bigger dynamatrix") {
            echo "Beginning quick-test stage"

            // Restore Map from Set, as needed for `parallel`
            println "Restoring discovered stagesShellcheck platform builds from Set to Map..."
            def stagesShellcheck = [:]
            stagesShellcheck_arr.each {tup ->
                println "Restoring stagesShellcheck platform build: ${Utils.castString(tup)}"
                stagesShellcheck[tup[0]] = tup[1]
            }
            println "Discovered ${stagesShellcheck.size()} stagesShellcheck platform builds: ${Utils.castString(stagesShellcheck)}"

            def par1 = stagesShellcheck

            if (dynacfgPipeline.spellcheck != null) {
                par1["spellcheck"] = {
                    node(infra.labelDocumentationWorker()) {
                        infra.withEnvOptional(dynacfgPipeline.defaultTools) {
                            unstashCleanSrc(stashnameSrc)
                            sh """ ${dynacfgPipeline.prepconf} && ${dynacfgPipeline.configure} """
                            sh """ ${dynacfgPipeline.spellcheck} """
                        }
                    }
                } // spellcheck
            }

//            par1.failFast = false

            if (dynacfgPipeline.getParStages) {
                par1["Discover slow build matrix"] = {
                    if (dynacfgPipeline.containsKey('bodyParStages')) {
                        stagesBinBuild = dynacfgPipeline.getParStages(dynamatrix, dynacfgPipeline.bodyParStages)
                    } else {
                        stagesBinBuild = dynacfgPipeline.getParStages(dynamatrix, null)
                    }
                    stagesBinBuild.failFast = dynacfgPipeline.failFast
                }
            }

            // Walk the plank
            parallel par1

/*
            // This stage is not really visible in results UI
            // so better use the separate road-bump below
            stage("Summarize quick-test results - 1") {
                echo "[Quick tests and prepare the bigger dynamatrix - 1] ${currentBuild.result}"
                if (currentBuild.result != 'SUCCESS') {
                    error "Quick-test and/or preparation of larger test matrix failed"
                }
            } // stage-quick-summary #1
*/

        } // stage-quick

        // Something in our dynamatrix wrappings precludes seeing
        // what failed on high-level in the parallel block above
        // or even failing the build (partially intended - we did
        // want all shell/spell tests to complete and only fail
        // afterwards if needed... but expected that stage above
        // cause the build abortion if applicable)
        stage("Summarize quick-test results") {
            echo "[Quick tests and prepare the bigger dynamatrix] ${currentBuild.result}"
            echo "Discovered ${stagesBinBuild.size()-1} 'slow build' combos to run"
            if (!currentBuild.result in [null, 'SUCCESS']) {
                error "Quick-test and/or preparation of larger test matrix failed"
            }
        } // stage-quick-summary

    } // node to manage the pipeline

}

