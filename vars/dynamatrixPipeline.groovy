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

    if (!dynacfgPipeline.containsKey('failFastSafe')) {
        // Used if failFast==true (has effect if a slowBuild stage fails):
        // true means to use custom dynamatrix implementation which allows
        // running stages to complete, and forbids not yet started ones to
        // proceed when they get onto a node.
        // false means to use the pipeline parallel() step implementation,
        // which tries to abort all running code if one stage fails, ASAP.
        dynacfgPipeline.failFastSafe = true
    }

    if (!dynacfgPipeline.containsKey('delayedIssueAnalysis')) {
        // NOTE: "false" here makes build and branch summary pages very
        // noisy: every analysis is published separately in its left menu
        // and so far no way was found to remove them during e.g. final
        // grouped or aggregated analysis publishing stage.
        // This flag does not however preclude that final publication.
        dynacfgPipeline.delayedIssueAnalysis = true
    }

    if (!dynacfgPipeline.containsKey('traceBuildShell_configureEnvvars')) {
        dynacfgPipeline.traceBuildShell_configureEnvvars = false
    }

    if (!dynacfgPipeline.containsKey('traceBuildShell')) {
        dynacfgPipeline.traceBuildShell = true
    }

    // Use milestones to cancel older PR builds if new iterations land?
    if (!dynacfgPipeline.containsKey('useMilestones')) {
        dynacfgPipeline.useMilestones = true
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

    // Lists unique files changed in the Git checkout, just after stashCleanSrc()
    Set changedFiles = []

    node(infra.labelDefaultWorker()) {
        // On a farm with constrained resources, getting to
        // a build node can take time and current job may
        // be obsolete by then. Note this is re-checked after
        // initial "quick" tests stage. Also note that per
        // https://plugins.jenkins.io/pipeline-milestone-step/
        // a newer build passing the milestone would cancel
        // older jobs that had already passed it!
        // FIXME: We may want trickery to avoid this part?..
        if (dynacfgPipeline?.useMilestones && env?.BRANCH_NAME) {
            if (env.BRANCH_NAME ==~ /^PR-[0-9]+/
            ||  (dynacfgPipeline?.branchStableRegex
                 && !(env.BRANCH_NAME ==~ dynacfgPipeline.branchStableRegex))
            ) {
                // Current build is a PR or not a stable branch
                // Fence off older iteration builds if newer ones exist
                milestone label: "Milestone before quick tests and slowBuild discovery"
            }
        }

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
 *
 * NOTE: stashCleanSrc() now uses a uniquely named lock which unstashing
 * methods should wait on, so it is safe to proceed into the quick build
 * matrix to not delay with agents that prefer to use "scm" or "scm-ws".
 */
                    if (infra.labelCheckoutWorker() != infra.labelDefaultWorker()) {
                        node(infra.labelCheckoutWorker()) {
                            stashCleanSrc(dynacfgPipeline.stashnameSrc, dynacfgPipeline?.bodyStashCmd)
                            changedFiles = infra.listChangedFiles()
                        }
                    } else {
                        // Already on worker suitable for checkouts,
                        // do not need to block requiring a node here
                        stashCleanSrc(dynacfgPipeline.stashnameSrc, dynacfgPipeline?.bodyStashCmd)
                        changedFiles = infra.listChangedFiles()
                    }

                    echo "This build involves the following changedFiles list: ${changedFiles.toString()}"
                }, // stage - stash

                "Quick builds and discovery": {

                    stage("Discover quick build matrix") {
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

                                if (dynacfgPipeline?.failFastSafe) {
                                    dynamatrix.failFast = (dynacfgPipeline?.failFast ? true : false)
                                    dynamatrix.mustAbort = false
                                }

                                dynamatrix.saveDynacfg()
                                dynacfgPipeline.slowBuild.each { def sb ->
                                    if (dynamatrixGlobalState.enableDebugTrace) {
                                        echo "Inspecting a slow build filter configuration: " + Utils.castString(sb)
                                    } else if (sb?.name) {
                                        echo "Inspecting a slow build filter configuration: ${sb.name}"
                                    }

                                    if (Utils.isClosureNotEmpty(sb?.getParStages)) {
                                        countFiltersSeen ++
                                        if (sb?.disabled) {
                                            if (dynamatrixGlobalState.enableDebugTrace || sb?.name)
                                                echo "SKIP: This slow build filter configuration is marked as disabled for this run" + (sb?.name ? ": " + sb.name : "")
                                            countFiltersSkipped++
                                            return // continue
                                        }

                                        if (Utils.isRegex(sb?.branchRegexSource) && Utils.isStringNotEmpty(env?.BRANCH_NAME)) {
                                            // TOTHINK: For PR builds, the BRANCH_NAME
                                            // is `PR-[0-9]+` while there is also a
                                            // CHANGE_BRANCH with the original value.
                                            if (!(env.BRANCH_NAME ==~ sb.branchRegexSource)) {
                                                if (dynamatrixGlobalState.enableDebugTrace || sb?.name)
                                                    echo "SKIP: Source branch name '${env.BRANCH_NAME}' did not match the pattern ~/${sb.branchRegexSource}/ for this slow build filter configuration" + (sb?.name ? ": " + sb.name : "")
                                                countFiltersSkipped++
                                                return // continue
                                            }
                                        }

                                        if (Utils.isRegex(sb?.branchRegexTarget)) {
                                            if (Utils.isStringNotEmpty(env?.CHANGE_TARGET)
                                            && (!(env.CHANGE_TARGET ==~ sb.branchRegexTarget))
                                            ) {
                                                if (dynamatrixGlobalState.enableDebugTrace || sb?.name)
                                                    echo "SKIP: Target branch name '${env.CHANGE_TARGET}' did not match the pattern ~/${sb.branchRegexTarget}/ for this slow build filter configuration" + (sb?.name ? ": " + sb.name : "")
                                                countFiltersSkipped++
                                                return // continue
                                            } // else: CHANGE_TARGET is empty (probably not
                                              // building a PR), or regex matches, so go on

                                            def _CHANGE_TARGET = null
                                            try {
                                                // May be not defined
                                                _CHANGE_TARGET = CHANGE_TARGET
                                            } catch (Throwable t) {
                                                try {
                                                    // May be not defined
                                                    _CHANGE_TARGET = env.CHANGE_TARGET
                                                } catch (Throwable tt) {}
                                            }

                                            if (Utils.isStringNotEmpty(_CHANGE_TARGET)
                                            && (!(_CHANGE_TARGET ==~ sb.branchRegexTarget))
                                            ) {
                                                if (dynamatrixGlobalState.enableDebugTrace || sb?.name)
                                                    echo "SKIP: Target branch name '${_CHANGE_TARGET}' did not match the pattern ~/${sb.branchRegexTarget}/ for this slow build filter configuration" + (sb?.name ? ": " + sb.name : "")
                                                countFiltersSkipped++
                                                return // continue
                                            }

                                            if ( !Utils.isStringNotEmpty(env?.CHANGE_TARGET)
                                            &&   !Utils.isStringNotEmpty(_CHANGE_TARGET)
                                            ) {
                                                // If callers want some setup only for PR
                                                // builds, they can use the source branch
                                                // regex set to /^PR-\d+$/
                                                if (dynamatrixGlobalState.enableDebugTrace || sb?.name)
                                                    echo "NOTE: Target branch name is not set for this build (not a PR?), so ignoring the pattern ~/${sb.branchRegexTarget}/ set for this slow build filter configuration" + (sb?.name ? ": " + sb.name : "")
                                                    // NOT a "skip", just a "FYI"!
                                            }
                                        } // if branchRegexTarget

                                        // By default we run all otherwise not disabled
                                        // scenarios... but really, some test cases do
                                        // not make sense for certain changes and are a
                                        // waste of roud-trip time and compute resources.
                                        if (Utils.isRegex(sb?.appliesToChangedFilesRegex)) {
                                            if (dynamatrixGlobalState.enableDebugTrace)
                                                echo "[DEBUG] Analysing the changedFiles=${changedFiles.toString()} list against the pattern appliesToChangedFilesRegex='${sb.appliesToChangedFilesRegex.toString()}' ..."
                                            if (changedFiles.size() > 0) {
                                                def skip = true

                                                for (cf in changedFiles) {
                                                    if (cf ==~ sb.appliesToChangedFilesRegex) {
                                                        // A changed file name did match
                                                        // the regex for files covered by a
                                                        // scenario, so this scenario should
                                                        // apply to this changeset and not
                                                        // skipped
                                                        skip = false
                                                        break
                                                    }
                                                }

                                                if (skip) {
                                                    if (dynamatrixGlobalState.enableDebugTrace || sb?.name)
                                                        echo "SKIP: Changeset did not include file names which match the pattern appliesToChangedFilesRegex='${sb.appliesToChangedFilesRegex.toString()}' for this slow build filter configuration" + (sb?.name ? ": " + sb.name : "")
                                                    countFiltersSkipped++
                                                    return // continue
                                                } else {
                                                    if (dynamatrixGlobalState.enableDebugTrace)
                                                        echo "[DEBUG] Changeset did include some file name(s) which matched the pattern appliesToChangedFilesRegex='${sb.appliesToChangedFilesRegex.toString()}' for this slow build filter configuration" + (sb?.name ? ": " + sb.name : "")
                                                }
                                            } else {
                                                if (dynamatrixGlobalState.enableDebugTrace || sb?.name)
                                                    echo "WARNING: while handling appliesToChangedFilesRegex='${sb.appliesToChangedFilesRegex.toString()}' " +
                                                        "for this slow build filter configuration, " +
                                                        "the listChangedFiles() call returned an " +
                                                        "empty list, thus either we had no changes " +
                                                        "(would a re-run do that?) or had some error?.. " +
                                                        "So build everything to be safe" + (sb?.name ? ": " + sb.name : ".")
                                            }
                                        } // if appliesToChangedFilesRegex

                                        echo "Did not rule out this slow build filter configuration" + (sb?.name ? ": " + sb.name : "")
                                        // This magic envvar is mapped into stage name
                                        // in the dynamatrix
                                        //### .replaceAll("'", '').replaceAll('"', '').replaceAll(/\s/, '_')
                                        withEnv(["CI_SLOW_BUILD_FILTERNAME=" + ( (sb?.name) ? sb.name.toString().trim() : "N/A" )]) {
                                            sb.mapParStages = [:]
                                            // Use unique clones of "dynamatrix.dynacfg" below,
                                            // to avoid polluting their applied dynacfg based
                                            // just on order of slowBuild scenario parsing:
                                            dynamatrix.restoreDynacfg()
                                            if (Utils.isClosure(sb?.bodyParStages)) {
                                                // body may be empty {}, if user wants so
                                                sb.mapParStages = sb.getParStages(dynamatrix, sb.bodyParStages)
                                            } else {
                                                if (Utils.isClosure(dynacfgPipeline?.slowBuildDefaultBody)) {
                                                    sb.mapParStages = sb.getParStages(dynamatrix, dynacfgPipeline.slowBuildDefaultBody)
                                                } else {
                                                    sb.mapParStages = sb.getParStages(dynamatrix, null)
                                                }
                                            }
                                            stagesBinBuild += sb.mapParStages
                                        }
                                    } else { // if not getParStages
                                        sb.mapParStages = null
                                        if (dynamatrixGlobalState.enableDebugTrace || sb?.name)
                                            echo "SKIP: No (valid) slow build filter definition in this entry" + (sb?.name ? ": " + sb.name : "")
                                        countFiltersSkipped++
                                    }
                                } // dynacfgPipeline.slowBuild.each { sb -> ... }

                                String sbSummarySuffix = "'slow build' configurations over ${countFiltersSeen} filter definition(s) tried " +
                                    "(${countFiltersSkipped} dynacfgPipeline.slowBuild elements were skipped due to build circumstances or as invalid)"
                                String sbSummary = null
                                String sbSummaryCount = "" // non-null string in any case
                                if (stagesBinBuild.size() == 0) {
                                    sbSummary = "Did not discover any ${sbSummarySuffix}"
                                } else {
                                    sbSummary = "Discovered ${stagesBinBuild.size()} ${sbSummarySuffix}"
                                    dynacfgPipeline.slowBuild.each { def sb ->
                                        if (sb?.mapParStages) {
                                            // Note: Char sequence at start of string is parsed for badge markup below
                                            sbSummaryCount += "\n\t* ${sb.mapParStages.size()} hits for: " +
                                                (Utils.isStringNotEmpty(sb?.name) ? sb.name : Utils.castString(sb))
                                        }
                                    }

                                    try {
                                        // TODO: Something similar but with each stage's
                                        // own buildResult verdicts after the build...
                                        def txt = "${sbSummary}\nfor this run ${env?.BUILD_URL}:\n\n"
                                        stagesBinBuild.keySet().sort().each { txt += "${it}\n\n" }
                                        txt += sbSummaryCount
                                        writeFile(file: ".ci.slowBuildStages-list.txt", text: txt)
                                        archiveArtifacts (artifacts: ".ci.slowBuildStages-list.txt", allowEmptyArchive: true)

                                        try {
                                            createSummary(text: "Saved the list of slowBuild stages into a text artifact " +
                                                "<a href='${env.BUILD_URL}/artifact/.ci.slowBuildStages-list.txt'>.ci.slowBuildStages-list.txt</a>",
                                                icon: '/images/48x48/notepad.png')
                                        } catch (Throwable ts) {
                                            echo "WARNING: Tried to createSummary(), but failed to; is the jenkins-badge-plugin installed?"
                                            if (dynamatrixGlobalState.enableDebugTrace) echo t.toString()
                                        }

                                    } catch (Throwable t) {
                                        echo "WARNING: Tried to save the list of slowBuild stages into a text artifact '.ci.slowBuildStages-list.txt', but failed to"
                                        if (dynamatrixGlobalState.enableDebugTrace) echo t.toString()
                                    }

                                    // Note: adds one more point to stagesBinBuild.size() checked below:
                                    if (dynacfgPipeline?.failFastSafe) {
                                        stagesBinBuild.failFast = false
                                    } else {
                                        stagesBinBuild.failFast = dynacfgPipeline.failFast
                                    }
                                }
                                echo sbSummary + sbSummaryCount

                                try {
                                    // Note: we also report "Running..." more or less
                                    // the same message below; but with CI farm contention
                                    // much time can be spent before getting to that line
                                    // Note we are not using "manager" leading to Groovy
                                    // PostBuild Plugin implementation, but the better
                                    // featured jenkins-badge-plugin step
                                    //addInfoBadge(text: sbSummary, id: "Discovery-counter")
                                    // While we add temporarily and remove one badge,
                                    // GPBP is okay (for some reason, Badge plugin leaves
                                    // ugly formatting in job's main page with list of builds):
                                    manager.addInfoBadge(sbSummary)

                                    // Add a line to the build's info page too (note the
                                    // path here is somewhat relative to /static/hexhash/
                                    // that Jenkins adds):
                                    if (sbSummaryCount != "") {
                                        // Note: replace goes by regex so '\*'
                                        sbSummaryCount = sbSummaryCount.replaceAll('\n\t\\* ', '</li><li>').replaceFirst('</li>', '<p>Detailed hit counts:<ul>') + '</li></ul></p>'
                                    }
                                    createSummary(text: sbSummary + sbSummaryCount, icon: '/images/48x48/notepad.png')
                                } catch (Throwable t) {
                                    echo "WARNING: Tried to addInfoBadge() and createSummary(), but failed to; is the jenkins-badge-plugin installed?"
                                    if (dynamatrixGlobalState.enableDebugTrace) echo t.toString()
                                }

                                echo "NOTE: If this is the last line you see in job console log for a long time, then we are waiting for some build agents for shellcheck/spellcheck; slowBuild stage discovery is completed"
                            } // stage item: par1["Discover slow build matrix"]
                        } // if slowBuild...

                        // Walk the plank
                        parallel par1

                        echo "Completed the 'Quick tests and prepare the bigger dynamatrix' stage"
                    } // stage-quick tests
                } // stage quick builds

            ) // parallel-initial
        } // stage-initial

        // Rest of code continues like a scripted pipeline

        // Something in our dynamatrix wrappings precludes seeing
        // what failed on high-level in the parallel block above
        // or even failing the build (partially intended - we did
        // want all shell/spell tests to complete and only fail
        // afterwards if needed... but expected that stage above
        // cause the build abortion if applicable)
        stage("Summarize quick-test results") {
            echo "[Quick tests and prepare the bigger dynamatrix summary] Discovered ${Math.max(stagesBinBuild.size() - 1, 0)} 'slow build' combos to run" + (dynacfgPipeline?.failFast ? "; failFast mode is enabled: " + (dynacfgPipeline?.failFastSafe ? "dynamatrix 'safe'" : "parallel step") + " implementation" : "")
            echo "[Quick tests and prepare the bigger dynamatrix summary] ${currentBuild.result}"
            if (!(currentBuild.result in [null, 'SUCCESS'])) {
                if (Utils.isClosure(dynacfgPipeline?.notifyHandler)) {
                    try {
                        // Can depend on plugins not available at this Jenkins
                        // instance, e.g. instant-messaging and IRC plugins
                        dynacfgPipeline.notifyHandler()
                    } catch (Throwable t) {
                        echo "WARNING: Tried to notify about build result (${currentBuild.result}) by user-provided method, and failed to"
                        if (dynamatrixGlobalState.enableDebugTrace) echo t.toString()
                    }
                }

                error "Quick-test and/or preparation of larger test matrix failed"
            }
        } // stage-quick-summary

        if (stagesBinBuild.size() < 1) {
            try {
                def txt = "No 'slow build' dynamatrix stages discovered"
                //removeBadges(id: "Discovery-counter")
                manager.removeBadges()
                manager.addShortText(txt)
                //currentBuild.rawBuild.getActions().add(org.jvnet.hudson.plugins.groovypostbuild.GroovyPostbuildAction.createShortText(txt))
            } catch (Throwable t) {
                echo "WARNING: Tried to addShortText(), but failed to; are the Groovy Postbuild plugin and jenkins-badge-plugin installed?"
                if (dynamatrixGlobalState.enableDebugTrace) echo t.toString()
            }

            // TODO: `unstable` this?
            echo "No stages were prepared for the 'slow build' dynamatrix, so completing the job"
        } else {
            echo "Scheduling ${stagesBinBuild.size() - 1} stages for the 'slow build' dynamatrix, running this can take a long while..."
            try {
                def txt = "Running ${stagesBinBuild.size() - 1} 'slow build' dynamatrix stages" + (dynacfgPipeline?.failFast ? "; failFast mode is enabled: " + (dynacfgPipeline?.failFastSafe ? "dynamatrix 'safe'" : "parallel step") + " implementation" : "")
                //removeBadges(id: "Discovery-counter")
                manager.removeBadges()
                manager.addShortText(txt)
                //currentBuild.rawBuild.getActions().add(org.jvnet.hudson.plugins.groovypostbuild.GroovyPostbuildAction.createShortText(txt))
            } catch (Throwable t) {
                echo "WARNING: Tried to addShortText(), but failed to; are the Groovy Postbuild plugin and jenkins-badge-plugin installed?"
                if (dynamatrixGlobalState.enableDebugTrace) echo t.toString()
            }

            if (dynacfgPipeline?.useMilestones && env?.BRANCH_NAME) {
                if (env.BRANCH_NAME ==~ /^PR-[0-9]+/
                ||  (dynacfgPipeline?.branchStableRegex
                     && !(env.BRANCH_NAME ==~ dynacfgPipeline.branchStableRegex))
                ) {
                    // Current build is a PR or not a stable branch
                    // Fence off older iteration builds if newer ones exist
                    milestone label: "Milestone before slowBuild matrix"
                }
            }

            def tmpRes = currentBuild.result
            stage("Run the bigger dynamatrix (${stagesBinBuild.size() - 1} stages)") {
                // This parallel, unlike "par1" above, tends to
                // preclude further processing if it fails and
                // so avoids detailing the failure analysis
                warnError(message: "Not all of the 'slow build' succeeded; proceeding to analyze the results") {
                    parallel stagesBinBuild
                    tmpRes = currentBuild.result
                }
            }
            echo "Completed the 'slow build' dynamatrix"

            def analyzeStageName = "Analyze the bigger dynamatrix"
            if (dynamatrix.mustAbort) {
                analyzeStageName += " after fastFailSafe aborted it"
            }
            stage(analyzeStageName) { // TOTHINK: post{always{...}} to the above? Is there one in scripted pipeline?
                doSummarizeIssues()
                def mustAbortMsg = null
                if (dynamatrix.mustAbort) {
                    mustAbortMsg = "'Must Abort' flag was raised by at least one slowBuild stage"
                    echo mustAbortMsg
                }
                // just in case warnError() above had dampened a
                // parallel stage failure, make the verdict known
                // in UI and final result, but let tear-down proceed:
                try { // no idea why, but we can get a nullptr exception here :\
                    if (tmpRes != null)
                        currentBuild.result = currentBuild.result.combine(tmpRes)
                } catch (Throwable t) {}
                try { // we try to track results based on each stage outcome
                    def wr = dynamatrix.getWorstResult()
                    if (wr != null)
                        currentBuild.result = currentBuild.result.combine(wr)
                } catch (Throwable t) {}

                // Beside the standard currentBuild.result we may have tracked
                // a more extensive source of info in the stage count (such as
                // exception states, started/finished less than expected total,
                // etc.) that may indicate e.g. a crashed and restarted Jenkins
                // instance that did not continue the build (because ours are
                // not "persistent" due to NonCPS limitations). In such cases,
                // the latest known Jenkins Result is "SUCCESS" but really we
                // do not know if the code is good across the whole matrix.
                def reportedNonSuccess = false
                switch (currentBuild.result) {
                    // Handle explicitly known faulty verdicts:
                    case 'FAILURE':
                        reportedNonSuccess = true
                        catchError(message: 'Marking a hard FAILURE') {
                            error "slowBuild or something else failed" +
                                (mustAbortMsg ? ("; " + mustAbortMsg) : "")
                        }
                        break
                    case ['UNSTABLE', 'ABORTED', 'NOT_BUILT']:
                        reportedNonSuccess = true
                        warnError(message: 'Marking a soft expected fault') {
                            error "slowBuild or something else did not succeed cleanly" +
                                (mustAbortMsg ? ("; " + mustAbortMsg) : "")
                        }
                        break
                }

                if (!reportedNonSuccess) {
                    // returns Map<ResultDescription, Integer> where the
                    // ResultDescription may be a String representation
                    // of the Result class, or one of our tags, or an
                    // exception text vs. count of hits to that value.
                    def mapCountStages = dynamatrix.getCountStages()

                    // returns Map<Result, Set<String>>
                    def mapres = dynamatrix.reportStageResults()
                    if (mapres == null || mapCountStages == null) {
                        reportedNonSuccess = true
                        catchError(message: 'Marking a hard FAILURE') {
                            currentBuild.result = 'FAILURE'
                            error "Could not investigate dynamatrix stage results"
                        }
                    } else {
                        if (mapCountStages.getAt('STARTED') != mapCountStages.getAt('COMPLETED')
                        ||  mapCountStages.getAt('STARTED') < (stagesBinBuild.size() - 1)
                        ) {
                            reportedNonSuccess = true
                            warnError(message: 'Marking a soft abort') {
                                currentBuild.result = 'ABORTED'
                                def txt = "Only started ${mapCountStages.STARTED} and completed ${mapCountStages.COMPLETED} dynamatrix 'slowBuild' stages, while we should have had ${stagesBinBuild.size() - 1} builds"
                                try {
                                    createSummary(text: "Build seems not finished: " + txt, icon: '/images/48x48/error.png')
                                } catch (Throwable t) {} // no-op
                                error txt
                            }
                        } else {
                            // Totals are as expected, but contents?..
                            // Do we have any faults recorded?
                            if (mapCountStages.getAt('SUCCESS') != mapCountStages.getAt('COMPLETED')) {
                                reportedNonSuccess = true
                                if (mapCountStages.getAt('FAILURE')) {
                                    catchError(message: 'Marking a hard FAILURE') {
                                        currentBuild.result = 'FAILURE'
                                        error "Some slowBuild stage(s) failed"
                                    }
                                } else if (mapCountStages.getAt('ABORTED') || mapCountStages.getAt('ABORTED_SAFE')) {
                                    warnError(message: 'Marking a soft abort') {
                                        currentBuild.result = 'ABORTED'
                                        error "Some slowBuild stage(s) were aborted"
                                    }
                                } else if (mapCountStages.getAt('UNSTABLE')
                                       || (mapCountStages.getAt('NOT_BUILT') && mapCountStages.getAt('NOT_BUILT') != mapCountStages.getAt('COMPLETED'))
                                ) {
                                    warnError(message: 'Marking a soft fault') {
                                        currentBuild.result = 'UNSTABLE'
                                        error "Some slowBuild stage(s) were unstable (expected failure did fail)"
                                    }
                                } else if (mapCountStages.getAt('NOT_BUILT')) {
                                    warnError(message: 'Marking as not built') {
                                        currentBuild.result = 'NOT_BUILT'
                                        error "Some slowBuild stage(s) were not built"
                                    }
                                }
                            }
                        }

                        if (mapres.size() < 1) {
                            reportedNonSuccess = true
                            catchError(message: 'Marking as NOT_BUILT') {
                                currentBuild.result = 'NOT_BUILT'
                                error "Did not find any recorded dynamatrix stage results while we should have had some builds"
                            }
                        } else {
                            def count = 0
                            mapres.each { k, v ->
                                if (Utils.isList(v))
                                    count += v.size()
                            }
                            if (count < (stagesBinBuild.size() - 1)) {
                                reportedNonSuccess = true
                                warnError(message: 'Marking a soft abort') {
                                    currentBuild.result = 'ABORTED'
                                    error "Only found ${count} recorded dynamatrix stage results while we should have had ${stagesBinBuild.size() - 1} builds"
                                }
                            } else {
                                // We seem to know enough verdicts; but are they
                                // definitive?

                                // Remove Jenkins-defined results; and also the
                                // data Dynamatix.groovy classifies; do any remain?
                                def mapresOther = mapCountStages.clone()
                                for (def r in [
                                    'SUCCESS', 'FAILURE', 'UNSTABLE', 'ABORTED', 'NOT_BUILT',
                                    'STARTED', 'COMPLETED', 'ABORTED_SAFE'
                                ]) {
                                    if (mapresOther.containsKey(r)) {
                                        if (Utils.isListNotEmpty(mapresOther[r])) {
                                            switch (r) {
                                                case 'FAILURE':
                                                    catchError(message: 'Marking a hard FAILURE') {
                                                        currentBuild.result = 'FAILURE'
                                                        error "Some slowBuild stage(s) failed"
                                                    }
                                                    break
                                                case ['ABORTED', 'ABORTED_SAFE']:
                                                    warnError(message: 'Marking a soft abort') {
                                                        currentBuild.result = 'ABORTED'
                                                        error "Some slowBuild stage(s) were aborted"
                                                    }
                                                    break
                                                case 'UNSTABLE':
                                                    warnError(message: 'Marking a soft fault') {
                                                        currentBuild.result = 'UNSTABLE'
                                                        error "Some slowBuild stage(s) were unstable (expected failure did fail)"
                                                    }
                                                    break
                                                case 'NOT_BUILT':
                                                    warnError(message: 'Marking as not built') {
                                                        currentBuild.result = 'NOT_BUILT'
                                                        error "Some slowBuild stage(s) were not built"
                                                    }
                                                    break
                                            }
                                        }
                                        mapresOther.remove(r)
                                    }
                                }

                                if (mapresOther.size() > 0) {
                                    // Some categories (key names) remain:
                                    def countOther = 0
                                    mapresOther.each { k, v ->
                                        countOther += v
                                    }

                                    // There may be predefined placeholders
                                    // with zero hit counts - ignore them:
                                    if (countOther > 0) {
                                        warnError(message: 'Marking a soft abort') {
                                            reportedNonSuccess = true
                                            currentBuild.result = 'ABORTED'
                                            error "Got ${countOther} unclassified recorded dynamatrix stage results (exceptions, etc?)"
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                echo "OVERALL: Discovered ${Math.max(stagesBinBuild.size() - 1, 0)} " +
                    "'slow build' combos to run; ended up with following counts: " +
                    dynamatrix.toStringStageCount()

                if (dynamatrixGlobalState.enableDebugTrace)
                    echo dynamatrix.toStringStageCountDump()

                // Build finished, remove the rolling progress via GPBP steps (with id)
                dynamatrix.updateProgressBadge(true)

                if (!reportedNonSuccess && currentBuild.result in [null, 'SUCCESS']) {
                    // Report success as a badge too, so interrupted incomplete
                    // builds (Jenkins/server restart etc.) are more visible
                    try {
                        def txt = dynamatrix.toStringStageCountNonZero()
                        if (!(Utils.isStringNotEmpty(txt))) {
                            txt = dynamatrix.toStringStageCountDumpNonZero()
                        }
                        if (!(Utils.isStringNotEmpty(txt))) {
                            txt = dynamatrix.toStringStageCountDump()
                        }
                        if (!(Utils.isStringNotEmpty(txt))) {
                            txt = dynamatrix.toStringStageCount()
                        }

                        def txtOK = "Build completed successfully"
                        manager.addShortText(txtOK)
                        createSummary(text: txtOK + ": " + txt, icon: '/images/48x48/notepad.png')
                    } catch (Throwable t) {
                        echo "WARNING: Tried to addShortText() and createSummary(), but failed to; are the Groovy Postbuild plugin and jenkins-badge-plugin installed?"
                        if (dynamatrixGlobalState.enableDebugTrace) echo t.toString()
                    }
                } else {
                    try {
                        def txt = dynamatrix.toStringStageCountNonZero()
                        if (!(Utils.isStringNotEmpty(txt))) {
                            txt = dynamatrix.toStringStageCountDumpNonZero()
                        }
                        if (!(Utils.isStringNotEmpty(txt))) {
                            txt = dynamatrix.toStringStageCountDump()
                        }
                        if (!(Utils.isStringNotEmpty(txt))) {
                            txt = dynamatrix.toStringStageCount()
                        }
                        txt = "Not all went well: " + txt
                        manager.addShortText(txt)
                        createSummary(text: txt, icon: '/images/48x48/warning.png')

                        // returns Map<Result, Set<String>>
                        def mapres = dynamatrix.reportStageResults()
                        if (mapres.containsKey(Result.SUCCESS))
                            mapres.remove(Result.SUCCESS)

                        if (mapres.size() > 0) {
                            mapres.each { r, sns ->
                                txt = "<nl>Result: ${r.toString()} (${sns.size()}):\n"
                                sns.each { sn ->
                                    def archPrefix = dynamatrix.getLogKey(sn)
                                    txt += "<li>${sn}"
                                    if (archPrefix) {
                                        // File naming as defined in vars/buildMatrixCellCI.groovy
                                        txt += "\n<p>See build artifacts keyed with: '${archPrefix}' e.g.:<ul>\n"
                                        for (url in [
                                            "${env.BUILD_URL}/artifact/.ci.${archPrefix}.config.log.gz",
                                            "${env.BUILD_URL}/artifact/.ci.${archPrefix}.build.log.gz"
                                        ]) {
                                            txt += "<li><a href='${url}'>${url}</a></li>\n"
                                        }
                                        txt += "</ul></p>"
                                    }
                                    txt += "</li>\n"
                                }
                                txt += "</nl>\n"
                                createSummary(text: txt, icon: '/images/48x48/warning.png')
                            }
                        }
                    } catch (Throwable t) {
                        echo "WARNING: Tried to addShortText() and createSummary(), but failed to; are the Groovy Postbuild plugin and jenkins-badge-plugin installed?"
                        if (dynamatrixGlobalState.enableDebugTrace) echo t.toString()
                    }
                }
            }
        }

        if (Utils.isClosure(dynacfgPipeline?.notifyHandler)) {
            try {
                // Can depend on plugins not available at this Jenkins
                // instance, e.g. instant-messaging and IRC plugins
                dynacfgPipeline.notifyHandler()
            } catch (Throwable t) {
                echo "WARNING: Tried to notify about build result (${currentBuild.result}) by user-provided method, and failed to"
                if (dynamatrixGlobalState.enableDebugTrace) echo t.toString()
            }
        }

    } // node to manage the pipeline

}
