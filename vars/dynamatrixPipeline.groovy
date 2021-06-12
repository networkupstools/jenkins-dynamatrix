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
        //'stageNameFunc': null,
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

    // Sanity-check the pipeline options
    dynacfgPipeline = sanityCheckDynacfgPipeline(dynacfgPipeline)
    dynacfgPipeline = configureEnvvars.sanityCheckDynacfgPipeline(dynacfgPipeline)
    dynacfgPipeline = autotools.sanityCheckDynacfgPipeline(dynacfgPipeline)

    // Sanity-check certain build milestones expecting certain cfg structure:
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
                        stashCleanSrc(dynacfgPipeline.stashnameSrc, dynacfgPipeline?.bodyStashCmd)
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
                    stagesShellcheck_arr = shellcheck(dynacfgPipeline, true)

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

            // For this stage, we do not want parallel build scenarios
            // aborted if stuff breaks in one codepath, it is relatively
            // small and fast anyway:
            par1.failFast = false

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
        } // stage-quick

        // Something in our dynamatrix wrappings precludes seeing
        // what failed on high-level in the parallel block above
        // or even failing the build (partially intended - we did
        // want all shell/spell tests to complete and only fail
        // afterwards if needed... but expected that stage above
        // cause the build abortion if applicable)
        stage("Summarize quick-test results") {
            echo "[Quick tests and prepare the bigger dynamatrix] Discovered ${stagesBinBuild.size()-1} 'slow build' combos to run"
            echo "[Quick tests and prepare the bigger dynamatrix] ${currentBuild.result}"
            if (!(currentBuild.result in [null, 'SUCCESS'])) {
                error "Quick-test and/or preparation of larger test matrix failed"
            }
        } // stage-quick-summary

    } // node to manage the pipeline

}
