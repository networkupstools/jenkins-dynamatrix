/* This file ties together bits from the Dynamatrix project,
 * to test something on a diverse set of platforms defined by
 * Jenkins agents that are present and declare capabilities,
 * including the following label names (and their values):
 * OS_FAMILY, OS_DISTRO, COMPILER, ${COMPILER}VER (e.g. GCCVER),
 * SHELL_PROGS, MAKE, ARCH_BITS, ARCH${ARCH_BITS} (e.g. ARCH32)
 */
import org.nut.dynamatrix.dynamatrixGlobalState;
import org.nut.dynamatrix.*;

/*
// For in-place tests as Replay pipeline:
@Library('jenkins-dynamatrix') _
import org.nut.dynamatrix.dynamatrixGlobalState;
import org.nut.dynamatrix.*;

    def dynacfgBase = [:]
    def dynacfgPipeline = [:]

    dynacfgPipeline['stylecheck'] = false //true
    dynacfgPipeline['spellcheck'] = false //true
    dynacfgPipeline['shellcheck'] = true
    dynacfgPipeline['NUT-shellcheck'] = [
        //'stageNameFunc': null,
        'single': '( \${MAKE} shellcheck )',
        'multi': '(cd tests && SHELL_PROGS="$SHELL_PROGS" ./nut-driver-enumerator-test.sh )',
        'multiLabel': 'SHELL_PROGS',
        'skipShells': [ 'zsh', 'tcsh', 'csh' ]
    ]

    dynacfgBase['commonLabelExpr'] = 'nut-builder'
    dynacfgBase['dynamatrixAxesLabels'] = //[~/^OS_.+/]
        ['OS_FAMILY', 'OS_DISTRO', '${COMPILER}VER', 'ARCH${ARCH_BITS}']

    // This closure can be used for slowBuild detailed below, if you just want
    // some same rituals to happen in all executed cases, to avoid repetitive
    // copy-pasting of a closure (or a named reference to one). The body can
    // use delegated dynamatrix variables such as "dsbc" and "stageName" as
    // well as exported envvars for the shell code (label values etc.), e.g.:
    //dynacfgPipeline.slowBuildDefaultBody = { delegate -> setDelegate(delegate)
    //  echo "Running default custom build for '${stageName}' ==> ${dsbc.toString()}"
    //  sh """ hostname; date -u; echo "\${MATRIX_TAG}"; set | sort -n """ }
    // or something like this for a realistic build
    //dynacfgPipeline.slowBuildDefaultBody = { delegate -> setDelegate(delegate)
    //    infra.withEnvOptional(dynacfgPipeline.defaultTools) {
    //        unstashCleanSrc(dynacfgPipeline.stashnameSrc)
    //        buildMatrixCellCI(dynacfgPipeline, dsbc)
    //    }
    //  }

    // Set of dynamatrix configuration selection filters and actual building
    // and/or testing closures to prepare some "slow build" matrix cells,
    // which may adhere or not to the same code pattern (body closure).
    dynacfgPipeline.slowBuild = [
      [name: 'Optional name to help in debug tracing',
       //disabled: true,
       getParStages: { dynamatrix, Closure body ->
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
            }, // getParStages
        //branchRegexSource: ~/^PR-.+$/,
        //branchRegexTarget: ~/^(master|main|stable)$/,
        bodyParStages: null
        //bodyParStages: {}
      ] // one slowBuild filter configuration
    ]

    dynacfgPipeline.bodyStashCmd = { git (url: "/home/jim/nut-DMF", branch: "fightwarn") }

 */

/*
// EXAMPLE: The pipeline can define and pass (below)
// a custom routine to name generated stages.
// Currently the code below defaults to using library-provided method.
@NonCPS
def stageNameFunc_ShellcheckCustom(DynamatrixSingleBuildConfig dsbc) {
    // NOTE: A direct Closure seems to confuse Jenkins/Groovy CPS, so using a func
    def labelMap = dsbc.getKVMap(false)
    String sn = ""
    if (labelMap.containsKey("OS_FAMILY"))
        sn += labelMap.OS_FAMILY + "-"
    if (labelMap.containsKey("OS_DISTRO"))
        sn += labelMap.OS_DISTRO + "-"
    return "MATRIX_TAG=\"${sn}shellcheckCustom\""
}
//dynacfgPipeline.shellcheck.stageNameFunc = this.&stageNameFunc_ShellcheckCustom
*/

def sanityCheckDynacfgPipeline(dynacfgPipeline = [:]) {
    // Base defaults not too specific for any particular toolchain we would use

    if (!dynacfgPipeline.containsKey('buildSystem')) {
        dynacfgPipeline.buildSystem = 'autotools'
    }

    // Initialize default `make` implementation to use (there are many), etc.:
    if (!dynacfgPipeline.containsKey('defaultTools')) {
        dynacfgPipeline['defaultTools'] = [
            'MAKE': 'make'
        ]
    }

    if (!dynacfgPipeline.stashnameSrc) {
        dynacfgPipeline.stashnameSrc = 'src-checkedout'
    }

    if (!dynacfgPipeline.containsKey('failFast')) {
        dynacfgPipeline.failFast = true
    }

    if (!dynacfgPipeline.containsKey('delayedIssueAnalysis')) {
        dynacfgPipeline.delayedIssueAnalysis = true
    }

    if (!dynacfgPipeline.containsKey('traceBuildShell_configureEnvvars')) {
        dynacfgPipeline.traceBuildShell_configureEnvvars = false
    }

    if (!dynacfgPipeline.containsKey('traceBuildShell')) {
        dynacfgPipeline.traceBuildShell = true
    }

    return dynacfgPipeline
}

def call(dynacfgBase = [:], dynacfgPipeline = [:]) {
    // dynacfgBase = Base configuration for Dynamatrix for this pipeline
    // dynacfgPipeline = Step-dependent setup in sub-maps

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
        // Set global default
        dynamatrixGlobalState.stageNameFunc = DynamatrixSingleBuildConfig.&C_StageNameTagFunc
    }

    // Sanity-check the pipeline options based on settings provided by caller
    dynacfgPipeline = sanityCheckDynacfgPipeline(dynacfgPipeline)
    dynacfgPipeline = configureEnvvars.sanityCheckDynacfgPipeline(dynacfgPipeline)
    dynacfgPipeline = autotools.sanityCheckDynacfgPipeline(dynacfgPipeline)
    dynacfgPipeline = ci_build.sanityCheckDynacfgPipeline(dynacfgPipeline)

    // Sanity-check certain build milestones expecting certain cfg structure:
    dynacfgPipeline = stylecheck.sanityCheckDynacfgPipeline(dynacfgPipeline)
    dynacfgPipeline = spellcheck.sanityCheckDynacfgPipeline(dynacfgPipeline)
    dynacfgPipeline = shellcheck.sanityCheckDynacfgPipeline(dynacfgPipeline)

    Dynamatrix dynamatrix = new Dynamatrix(this)

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

        def pipelineProps = [
            durabilityHint('PERFORMANCE_OPTIMIZED'),
            [$class: 'RebuildSettings', autoRebuild: false, rebuildDisabled: false],
            throttleJobProperty(categories: [], limitOneJobWithMatchingParams: false, maxConcurrentPerNode: 0, maxConcurrentTotal: 0, paramsToUseForLimit: '', throttleEnabled: false, throttleOption: 'project')
        ]
        if (Utils.isListNotEmpty(dynacfgPipeline?.paramsList)) {
            pipelineProps.add(parameters(dynacfgPipeline.paramsList))
        }
        properties(pipelineProps)

        if (Utils.isClosure(dynacfgPipeline?.paramsHandler)) {
            // Optional sanity-checks, assignment of dynacfgPipeline.* fields, etc.
            stage("Handle build parameters") {
                dynacfgPipeline.paramsHandler()
            }
        }

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
                    if (infra.labelCheckoutWorker() != infra.labelDefaultWorker()) {
                        node(infra.labelCheckoutWorker()) {
                            stashCleanSrc(dynacfgPipeline.stashnameSrc, dynacfgPipeline?.bodyStashCmd)
                        }
                    } else {
                        // Already on worker suitable for checkouts,
                        // do not need to block requiring a node here
                        stashCleanSrc(dynacfgPipeline.stashnameSrc, dynacfgPipeline?.bodyStashCmd)
                    }
                }, // stage - stash

                "Discover quick build matrix": {
                    // Relatively quick discovery (e.g. filtering axes
                    // by regexes takes long when many build agents are
                    // involved, so that part happens in parallel to
                    // this shellcheck and also optional spellcheck and/or
                    // stylecheck, which presumably can be prepared quickly):

                    // Have some defaults, if only to have all
                    // expected fields defined and node caps cached
                    dynamatrix.prepareDynamatrix(dynacfgBase)

                    // In outer layer, select all suitable builders;
                    // In inner layer, unpack+config the source on
                    // that chosen host once, and use that workspace
                    // for distinct test stages with different shells.
                    stagesShellcheck_arr = shellcheck(dynacfgPipeline, true)

                    /* As noted above, any relatively heavy axis filters,
                     * which need many seconds to process just to decide
                     * what should be built this time, should happen in
                     * the next stage along with quick tests - like the
                     * stylecheck, spellcheck and shellcheck targets.
                     */
                } // stage - discover the matrix

            ) // parallel-initial
        } // stage-initial

        // Rest of code continues like a scripted pipeline

        // Do not mix parallel and usual sub-stages inside the
        // stage below - at least, BO does not render that well
        // and it may cause (or not?) CPS faults somehow...
        stage("Quick tests and prepare the bigger dynamatrix") {
            echo "Beginning quick-test stage"

            // Convert back from the Set of tuples we used to
            // avoid storing a Map for too long - and making CPS sad
            def par1 = shellcheck.makeMap(stagesShellcheck_arr)

            // Nothing gets added (empty [:] ignored) if not enabled:
            par1 += spellcheck.makeMap(dynacfgPipeline)
            par1 += stylecheck.makeMap(dynacfgPipeline)

            // For this stage, we do not want parallel build scenarios
            // aborted if stuff breaks in one codepath, it is relatively
            // small and fast anyway:
            par1.failFast = false

            if (dynacfgPipeline?.slowBuild && dynacfgPipeline.slowBuild.size() > 0) {
                par1["Discover slow build matrix"] = {
                    def countFiltersSeen = 0
                    def countFiltersSkipped = 0
                    // The "slowBuild" is a set of Maps, each of them describes
                    // a dynamatrix selection filter. Having a series of those
                    // with conditions known to developer of the pipeline (and
                    // project it represents) can be more efficient than making
                    // a huge matrix of virtual or agent-driven labels and then
                    // filtering away lots of "excludeCombos" from that.
                    dynacfgPipeline.slowBuild.each { def sb ->
                        if (dynamatrixGlobalState.enableDebugTrace) {
                            echo "Inspecting a slow build filter configuration: " + Utils.castString(sb)
                        } else if (sb?.name) {
                            echo "Inspecting a slow build filter configuration: ${sb.name}"
                        }
                        if (Utils.isClosureNotEmpty(sb?.getParStages)) {
                            countFiltersSeen ++
                            if (sb?.disabled) {
                                if (dynamatrixGlobalState.enableDebugTrace)
                                    echo "SKIP: This filter configuration is marked as disabled for this run"
                                countFiltersSkipped++
                                return // continue
                            }
                            if (Utils.isRegex(sb?.branchRegexSource) && Utils.isStringNotEmpty(env?.BRANCH_NAME)) {
                                if (!(env.BRANCH_NAME ==~ sb.branchRegexSource)) {
                                    if (dynamatrixGlobalState.enableDebugTrace)
                                        echo "SKIP: Source branch name '${env.BRANCH_NAME}' did not match the pattern ${sb.branchRegexSource} for this filter configuration"
                                    countFiltersSkipped++
                                    return // continue
                                }
                            }
                            if (Utils.isRegex(sb?.branchRegexTarget)) {
                                if (Utils.isStringNotEmpty(env?.CHANGE_TARGET)
                                && (!(env.CHANGE_TARGET ==~ sb.branchRegexTarget))
                                ) {
                                    if (dynamatrixGlobalState.enableDebugTrace)
                                        echo "SKIP: Target branch name '${env.CHANGE_TARGET}' did not match the pattern ${sb.branchRegexTarget} for this filter configuration"
                                    countFiltersSkipped++
                                    return // continue
                                } // else: CHANGE_TARGET is empty (probably not
                                  // building a PR), or regex matches, so go on

                                def _CHANGE_TARGET = null
                                try {
                                    // May be not defined
                                    _CHANGE_TARGET = CHANGE_TARGET
                                } catch (Throwable t) {}

                                if (Utils.isStringNotEmpty(_CHANGE_TARGET)
                                && (!(_CHANGE_TARGET ==~ sb.branchRegexTarget))
                                ) {
                                    if (dynamatrixGlobalState.enableDebugTrace)
                                        echo "SKIP: Target branch name '${_CHANGE_TARGET}' did not match the pattern ${sb.branchRegexTarget} for this filter configuration"
                                    countFiltersSkipped++
                                    return // continue
                                }

                                if ( !Utils.isStringNotEmpty(env?.CHANGE_TARGET)
                                &&   !Utils.isStringNotEmpty(_CHANGE_TARGET)
                                ) {
                                    // If callers want some setup only for PR
                                    // builds, they can use the source branch
                                    // regex set to /^PR-\d+$/
                                    if (dynamatrixGlobalState.enableDebugTrace)
                                        echo "Target branch name is not set for this build (not a PR?), so ignoring the pattern ${sb.branchRegexTarget} set for this filter configuration"
                                }
                            }

                            // NOTE/TODO: Seems this trick does not work well,
                            // perhaps with because a node() gets optionally
                            // defined in the executed stage body. Maybe we
                            // should inject this debugging aid into the
                            // dynamatrixAxesCommonEnv or similar...
                            withEnv(["CI_SLOW_BUILD_FILTERNAME=" + ( (sb?.name) ? sb.name.toString().replaceAll("'", '').replaceAll('"', '').replaceAll(/\\s/, '_') : "N/A" )]) {
                                if (Utils.isClosure(sb?.bodyParStages)) {
                                    // body may be empty {}, if user wants so
                                    stagesBinBuild += sb.getParStages(dynamatrix, sb.bodyParStages)
                                } else {
                                    if (Utils.isClosure(dynacfgPipeline?.slowBuildDefaultBody)) {
                                        stagesBinBuild += sb.getParStages(dynamatrix, dynacfgPipeline.slowBuildDefaultBody)
                                    } else {
                                        stagesBinBuild += sb.getParStages(dynamatrix, null)
                                    }
                                }
                            }
                        } else {
                            if (dynamatrixGlobalState.enableDebugTrace)
                                echo "SKIP: No (valid) filter definition in this entry"
                            countFiltersSkipped++
                        }
                    }

                    String sbSummarySuffix = "'slow build' configurations over ${countFiltersSeen} filter definition(s) tried " +
                        "(${countFiltersSkipped} dynacfgPipeline.slowBuild elements were skipped due to build circumstances or as invalid)"
                    String sbSummary = null
                    if (stagesBinBuild.size() == 0) {
                        sbSummary = "Did not discover any ${sbSummarySuffix}"
                    } else {
                        sbSummary = "Discovered ${stagesBinBuild.size()} ${sbSummarySuffix}"
                        // Note: adds one more point to stagesBinBuild.size() checked below:
                        stagesBinBuild.failFast = dynacfgPipeline.failFast
                    }
                    echo sbSummary
                    try {
                        // Note: we also report "Running..." more or less
                        // the same message below; but with CI farm contention
                        // much time can be spent before getting to that line
                        manager.addInfoBadge(text: sbSummary, id: "Discovery-counter")
                        // Add one to the build's info page
                        manager.createSummary(text: sbSummary, icon: 'info.gif')
                    } catch (Throwable t) {
                        echo "WARNING: Tried to addInfoBadge() and createSummary(), but failed to; is the Groovy Postbuild plugin and/or jenkins-badge-plugin installed?"
                    }
                }
            }

            // Walk the plank
            parallel par1
        } // stage-quick

        // Something in our dynamatrix wrappings precludes seeing
        // what failed on high-level in the parallel block above
        // or even failing the build (partially intended - we did
        // want all shell/spell tests to complete and only fail
        // afterwards if needed... but expected that stage above
        // cause the build abortion if applicable)
        stage("Summarize quick-test results") {
            echo "[Quick tests and prepare the bigger dynamatrix summary] Discovered ${Math.max(stagesBinBuild.size()-1, 0)} 'slow build' combos to run"
            echo "[Quick tests and prepare the bigger dynamatrix summary] ${currentBuild.result}"
            if (!(currentBuild.result in [null, 'SUCCESS'])) {
                if (Utils.isClosure(dynacfgPipeline?.notifyHandler)) {
                    try {
                        // Can depend on plugins not available at this Jenkins
                        // instance, e.g. instant-messaging and IRC plugins
                        dynacfgPipeline.notifyHandler()
                    } catch (Throwable t) {
                        echo "WARNING: Tried to notify about build result (${currentBuild.result}) by user-provided method, and failed to"
                    }
                }

                error "Quick-test and/or preparation of larger test matrix failed"
            }
        } // stage-quick-summary

        if (stagesBinBuild.size() > 1) {
            echo "Scheduling ${stagesBinBuild.size()-1} stages for the 'slow build' dynamatrix, running this can take a long while..."
            try {
                def txt = "Running ${stagesBinBuild.size()-1} 'slow build' dynamatrix stages"
                manager.addShortText(txt)
                manager.removeBadges(id: "Discovery-counter")
                //currentBuild.rawBuild.getActions().add(org.jvnet.hudson.plugins.groovypostbuild.GroovyPostbuildAction.createShortText(txt))
            } catch (Throwable t) {
                echo "WARNING: Tried to addShortText(), but failed to; is the Groovy Postbuild plugin installed?"
            }
            stage("Run the bigger dynamatrix (${stagesBinBuild.size()-1} stages)") {
                // This parallel, unlike "par1" above, tends to
                // preclude further processing if it fails and
                // so avoids detailing the failure analysis
                warnError(message: "Not all of the 'slow build' succeeded; proceeding to analyze the results") {
                    parallel stagesBinBuild
                }
            }
            echo "Completed the 'slow build' dynamatrix"
            stage("Analyze the bigger dynamatrix") { // TOTHINK: post{always{...}} to the above? Is there one in scripted pipeline?
                doSummarizeIssues()
            }
        } else {
            // TODO: `unstable` this?
            echo "No stages were prepared for the 'slow build' dynamatrix, so completing the job"
        }

        if (Utils.isClosure(dynacfgPipeline?.notifyHandler)) {
            try {
                // Can depend on plugins not available at this Jenkins
                // instance, e.g. instant-messaging and IRC plugins
                dynacfgPipeline.notifyHandler()
            } catch (Throwable t) {
                echo "WARNING: Tried to notify about build result (${currentBuild.result}) by user-provided method, and failed to"
            }
        }

    } // node to manage the pipeline

}
