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

    def dynacfgBase = [:]
    def dynacfgPipeline = [:]

    dynacfgPipeline['spellcheck'] = false //true
    dynacfgPipeline['shellcheck'] = true
    dynacfgPipeline['NUT-shellcheck'] = [
        'single': '( \${MAKE} shellcheck )',
        'multi': '(cd tests && SHELL_PROGS="$SHELL_PROGS" ./nut-driver-enumerator-test.sh )',
        'multiLabel': 'SHELL_PROGS'
    ]

    dynacfgBase['commonLabelExpr'] = 'nut-builder'
    dynacfgBase['dynamatrixAxesLabels'] = [~/^OS_.+/]

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

    def stagesShellcheck = [:]

    pipeline {
        agent none
        options {
            skipDefaultCheckout()
        }
        stages {

            stage("Initial discovery") {
                parallel {

                    stage("Stash source for workers") {
/*
 * NOTE: For quicker builds, it is recommended to set up the pipeline job
 * using this Jenkinsfile to refer to a local copy of the Git repository
 * maintained on the stashing worker (as a Reference Repo), and do just
 * shallow checkouts (depth=1). Longer history may make sense for release
 * builds with changelog generation, but not for quick test iterations.
 */
                        agent { label infra.labelCheckoutWorker() }
                        steps {
                            stashCleanSrc(stashnameSrc)
                        }
                    } // stage - stash

                    stage("Discover build matrix") {
                        // Relatively quick discovery (e.g. filtering axes
                        // by regexes takes long when many build agents are
                        // involved, so that part happens in parallel to
                        // this shellcheck and also a spellcheck, which can
                        // be prepared quickly):
                        steps {
                            script {
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
                                    stagesShellcheck = dynamatrix.generateBuild([
                                        dynamatrixAxesLabels: [~/^OS_.+/],
                                        mergeMode: [ 'dynamatrixAxesLabels': 'replace' ],
                                        stageNameFunc: { DynamatrixSingleBuildConfig dsbc ->
                                                // TODO: Offload to a routine and reference by name here?
                                                // A direct Closure seems to confuse Jenkins/Groovy CPS
                                                def labelMap = dsbc.getKVMap(false)
                                                String sn = ""
                                                if (labelMap.containsKey("OS_FAMILY"))
                                                    sn += labelMap.OS_FAMILY + "-"
                                                if (labelMap.containsKey("OS_DISTRO"))
                                                    sn += labelMap.OS_DISTRO + "-"
                                                return "MATRIX_TAG=${sn}shellcheck"
                                            }
                                        ]) { delegate -> setDelegate(delegate)
                                            script {
                                                // On current node/workspace, prepare source once for
                                                // tests that are not expected to impact each other
                                                def MATRIX_TAG = delegate.stageName - ~/^MATRIX_TAG=/

                                                stage("prep for ${MATRIX_TAG}") {
                                                    sh """ echo "UNPACKING for '${MATRIX_TAG}'" """
                                                    infra.withEnvOptional(dynacfgPipeline.defaultTools) {
                                                        unstashCleanSrc(stashnameSrc)
                                                        sh """ ${dynacfgPipeline.prepconf} && ${dynacfgPipeline.configure} """
                                                    }
                                                }

                                                def stagesShellcheckNode = [:]
                                                // Iterate with separate verdicts when/if `make shellcheck`
                                                // (or equivalen) actually supports various $SHELL tests
                                                if (dynacfgPipeline.shellcheck.multi != null) {
                                                    if (env.NODE_LABELS && env.NODE_NAME &&  dynacfgPipeline.shellcheck.multiLabel != null) {
                                                        for (label in NodeData.getNodeLabelsByName(env.NODE_NAME)) {
                                                            if (label.startsWith("${dynacfgPipeline.shellcheck.multiLabel}=")) {
                                                                String[] keyValue = label.split("=", 2)
                                                                String SHELL_PROGS=keyValue[1]
                                                                stagesShellcheckNode["Test with ${SHELL_PROGS} for ${MATRIX_TAG}"] = {
                                                                    def msgFail = "Failed stage: ${stageName}" + "\n  for ${Utils.castString(dsbc)}"
                                                                    catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE', message: msgFail) {
                                                                        withEnv(["${dynacfgPipeline.shellcheck.multiLabel}=${SHELL_PROGS}"]) {
                                                                            infra.withEnvOptional(dynacfgPipeline.defaultTools) {
                                                                                sh """ echo "Shell-dependent testing with shell '${SHELL_PROGS}' on `uname -a || hostname || true` system" """
                                                                                sh """ ${dynacfgPipeline.shellcheck.multi} """
                                                                            }
                                                                        }
                                                                    }
                                                                } // added stage
                                                            }
                                                        }
                                                    }

                                                    // TOTHINK: Skip agent that does not declare shell labels?..
                                                    if (stagesShellcheckNode.size() == 0) {
                                                        stagesShellcheckNode["Test with default shell(s) for ${MATRIX_TAG}"] = {
                                                            infra.withEnvOptional(dynacfgPipeline.defaultTools) {
                                                                sh """ echo "Shell-dependent testing with default shell on `uname -a || hostname || true` system" """
                                                                sh """ ${dynacfgPipeline.shellcheck.multi} """
                                                            }
                                                        }
                                                    }
                                                }

                                                if (dynacfgPipeline.shellcheck.single != null) {
                                                    stagesShellcheckNode["Generic-shell test for ${MATRIX_TAG}"] = {
                                                        infra.withEnvOptional(dynacfgPipeline.defaultTools) {
                                                            sh """ echo "Generic-shell test (with recipe defaults) on `uname -a || hostname || true` system" """
                                                            sh """ ${dynacfgPipeline.shellcheck.single} """
                                                        }
                                                    }
                                                }

                                                stagesShellcheckNode.each { name, closure ->
                                                    stage(name) {
                                                        closure()
                                                    }
                                                }

                                            }
                                        } // generateBuild + closure for one hit of stagesShellcheck
                                } // if dynacfgPipeline.shellcheck

                                println "Discovered ${stagesShellcheck.size()} stagesShellcheck builds"
                            } // script
                        } // steps
                        /* As noted above, any relatively heavy axis filters,
                         * which need many seconds to process just to decide
                         * what should be built this time, should happen in
                         * the next stage along with quick tests - like the
                         * spellcheck and shellcheck targets.
                         */
                    } // stage - discover the matrix

                } // parallel-initial
            } // stage-initial

        } // stages
    } // pipeline

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

    stage("Quick tests and prepare the bigger dynamatrix") {
        parallel par1
    } // stage-quick

}

