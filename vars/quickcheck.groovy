// Steps should not be in a package, to avoid CleanGroovyClassLoader exceptions...
// package org.nut.dynamatrix;

import org.nut.dynamatrix.*;

import com.cloudbees.groovy.cps.NonCPS;

import org.nut.dynamatrix.DynamatrixSingleBuildConfig;
import org.nut.dynamatrix.NodeData;
import org.nut.dynamatrix.Utils;
import org.nut.dynamatrix.dynamatrixGlobalState;

/*
// Example config for this part of code:

@NonCPS
def stageNameFunc_Quickcheck(DynamatrixSingleBuildConfig dsbc) {
    def labelMap = dsbc.getKVMap(false)
    String sn = ""
    if (labelMap.containsKey("OS_FAMILY"))
        sn += labelMap.OS_FAMILY + "-"
    if (labelMap.containsKey("OS_DISTRO"))
        sn += labelMap.OS_DISTRO + "-"
    if (labelMap.containsKey("ARCH"))
        sn += labelMap.ARCH + "-"
    return "MATRIX_TAG=\"${sn}quickcheckCustom\""
}

    dynacfgPipeline['quickcheck'] = [
        //'stageNameFunc': null,
        'dynamatrixAxesLabels': [~/^OS_.+/, 'ARCH'],
        'shellcheck': [
            'single': '( \${MAKE} shellcheck )',
            'multi': '(cd tests && SHELL_PROGS="$SHELL_PROGS" ./nut-driver-enumerator-test.sh )',
            'multiLabel': 'SHELL',
            'skipShells': [ 'zsh', 'tcsh', 'csh' ]
        ],
        'spellcheck': '( \${MAKE} spellcheck )',
        'stylecheck': '( \${MAKE} stylecheck )'
    ]
 */

/**
 * Check whether quickcheck is enabled in dynacfgPipeline configuration
 */
Boolean isEnabled(Map dynacfgPipeline = [:]) {
    if (dynacfgPipeline == null) return false
    if (!dynacfgPipeline.containsKey('quickcheck')) return false
    def qc = dynacfgPipeline['quickcheck']
    if (qc == null || qc == false || "${qc}".trim().equalsIgnoreCase("false")) return false
    if (qc == true || "${qc}".trim().equalsIgnoreCase("true")) return true
    if (Utils.isMap(qc)) {
        if (qc.containsKey('enabled')) {
            if (qc.enabled == false || "${qc.enabled}".trim().equalsIgnoreCase("false")) {
                return false
            }
            if (qc.enabled == true || "${qc.enabled}".trim().equalsIgnoreCase("true")) {
                return true
            }
        }
        boolean hasCheck = (qc.shellcheck != null && qc.shellcheck != false && !"${qc.shellcheck}".trim().equalsIgnoreCase("false")) ||
                           (qc.spellcheck != null && qc.spellcheck != false && !"${qc.spellcheck}".trim().equalsIgnoreCase("false")) ||
                           (qc.stylecheck != null && qc.stylecheck != false && !"${qc.stylecheck}".trim().equalsIgnoreCase("false"))
        if (hasCheck) return true
        if (qc.containsKey('shellcheck') || qc.containsKey('spellcheck') || qc.containsKey('stylecheck')) {
            return false
        }
        return !qc.isEmpty()
    }
    return (Boolean)qc
}

/**
 * Returns a Set of tuples that a later stage can convert
 * into a Map for `parallel` test running with {@link #makeMap}
 * (avoid having a long-lived Map object which can upset CPS
 * and pipeline serialization).
 */
Set<List> call(Map dynacfgPipeline = [:], Boolean returnSet = true) {
    if (dynacfgPipeline == null) {
        dynacfgPipeline = [:]
    }

    // Set of Map'able tuples of <String, Closure>
    Set<List> stagesQuickcheck_arr = []

    Boolean debugTrace = (dynacfgPipeline?.enableDebugTrace || dynacfgPipeline?.quickcheck?.enableDebugTrace)

    // Check if quickcheck is enabled
    if (isEnabled(dynacfgPipeline)) {
        if (debugTrace) this.script.println "[DEBUG] Discovering stagesQuickcheck: calling prepareDynamatrix() step..."

        stagesQuickcheck_arr = prepareDynamatrix([
            dynamatrixAxesLabels: dynacfgPipeline.quickcheck.dynamatrixAxesLabels,
            mergeMode: [ 'dynamatrixAxesLabels': 'replace' ],
            stageNameFunc: dynacfgPipeline.quickcheck.stageNameFunc,
            dynamatrixGithubNotificationContext: "quickbuild-run"
            ] + (dynacfgPipeline.quickcheck?.excludeCombos ? [excludeCombos: dynacfgPipeline.quickcheck?.excludeCombos,] : [:]),
            returnSet) { def delegate -> setDelegate(delegate)
                    DynamatrixSingleBuildConfig MATRIX_DSBC = delegate.dsbc
                    String MATRIX_STAGENAME = delegate.stageName.trim()
                    String MATRIX_TAG = MATRIX_STAGENAME - ~/^MATRIX_TAG="*/ - ~/"*$/

                    String bigStageResult = 'SUCCESS'

                    stage("quickcheck for ${MATRIX_TAG}") {
                        // On current node/workspace, checkout + configure source once
                        stage("prep for ${MATRIX_TAG}") {
                            String msgFail = "Failed stage: prep for ${MATRIX_TAG}" +
                                "\n  for ${Utils.castString(MATRIX_DSBC)}"
                            Boolean didFail = true
                            try {
                                sh """ echo "UNPACKING for '${MATRIX_TAG}'" """
                                withEnvOptional(dynacfgPipeline.defaultTools) {
                                    unstashCleanSrc(dynacfgPipeline.get("stashnameSrc"))

                                    if (dynacfgPipeline?.quickcheck_prepconf != null) {
                                        if (Utils.isStringNotEmpty(dynacfgPipeline.quickcheck_prepconf)) {
                                            sh """ ${dynacfgPipeline.quickcheck_prepconf} """
                                        }
                                    } else if (dynacfgPipeline?.quickcheck?.prepconf != null) {
                                        if (Utils.isStringNotEmpty(dynacfgPipeline.quickcheck.prepconf)) {
                                            sh """ ${dynacfgPipeline.quickcheck.prepconf} """
                                        }
                                    } else {
                                        if (dynacfgPipeline?.buildPhases?.prepconf) {
                                            sh """ ${dynacfgPipeline.buildPhases.prepconf} """
                                        }
                                    }

                                    if (dynacfgPipeline?.quickcheck_configure != null) {
                                        if (Utils.isStringNotEmpty(dynacfgPipeline.quickcheck_configure)) {
                                            sh """ ${dynacfgPipeline.quickcheck_configure} """
                                        }
                                    } else if (dynacfgPipeline?.quickcheck?.configure != null) {
                                        if (Utils.isStringNotEmpty(dynacfgPipeline.quickcheck.configure)) {
                                            sh """ ${dynacfgPipeline.quickcheck.configure} """
                                        }
                                    } else {
                                        if (dynacfgPipeline?.buildPhases?.configure) {
                                            sh """ ${dynacfgPipeline.buildPhases.configure} """
                                        }
                                    }
                                }
                                didFail = false
                            } catch (Throwable t) {
                                if (Utils.isRetryableException(t)) {
                                    echo "[DEBUG]: quickcheck(${dynacfgPipeline.get("stashnameSrc")}) " +
                                        " finished with a verdict classified as " +
                                        "a retryable build agent failure - " +
                                        "will re-schedule: ${msgFail} => ${t.toString()}"
                                    didFail = false
                                    throw t
                                }
                                catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE', message: msgFail) {
                                    throw t
                                }
                            }

                            if (didFail) {
                                bigStageResult = 'FAILURE'
                                MATRIX_DSBC.setWorstResult('FAILURE')
                                MATRIX_DSBC.dsbcResultInterim = 'FAILURE'
                                currentBuild.result = 'FAILURE'
                                manager.buildFailure()
                                unstable(msgFail)
                            }

                            return didFail
                        } // stage(prep)

                        switch (bigStageResult) {
                            case 'FAILURE': if (true) {
                                    currentBuild.result = bigStageResult
                                    String msg = "prep for quickcheck for ${MATRIX_TAG} failed"
                                    infra.reportGithubStageStatus(dynacfgPipeline.get("stashnameSrc"), msg,
                                        'FAILURE', "quickcheck-${MATRIX_TAG}")
                                    msg = "FATAL: ${msg} above"
                                    MATRIX_DSBC.setWorstResult('FAILURE')
                                    MATRIX_DSBC.dsbcResultInterim = 'FAILURE'
                                    echo msg

                                    manager.buildFailure()
                                    unstable(msg)
                                }
                                return false

                            case ['SUCCESS', null]: if (true) {
                                    String msg = "prep for quickcheck for ${MATRIX_TAG} succeeded"
                                    if (!infra.updateGithubStageStatus(dynacfgPipeline.get("stashnameSrc"), msg,
                                        'SUCCESS', "quickcheck-${MATRIX_TAG}"))
                                        echo msg
                                }
                                break
                        }

                        // Collect sub-stages to run on this node
                        List<List> stagesQuickcheckNode = []

                        // Retrieve node labels once
                        Set<String> nodeLabels = []
                        if (env.NODE_LABELS && env.NODE_NAME) {
                            nodeLabels = NodeData.getNodeLabelsByName(env.NODE_NAME)
                        }

                        // 1. Shellcheck sub-stages
                        def scConfig = dynacfgPipeline.quickcheck.shellcheck
                        if (scConfig != null && scConfig != false) {
                            String multiLabel = 'SHELL'
                            String multiCmd = null
                            String singleCmd = null
                            List skipShells = []

                            if (Utils.isMap(scConfig)) {
                                if (scConfig.multiLabel) multiLabel = scConfig.multiLabel
                                multiCmd = scConfig.multi
                                singleCmd = scConfig.single
                                if (scConfig.skipShells) skipShells = scConfig.skipShells
                            } else if (Utils.isString(scConfig)) {
                                multiCmd = scConfig
                                singleCmd = scConfig
                            } else if (scConfig == true) {
                                multiCmd = '( SHELL_PROGS="$SHELL_PROGS" \${MAKE} shellcheck )'
                                singleCmd = '( \${MAKE} shellcheck )'
                            }

                            int scSubstagesCount = 0

                            if (multiCmd != null) {
                                nodeLabels.each { String label ->
                                    if (label.startsWith("${multiLabel}=") || (multiLabel == 'SHELL' && label.startsWith("SHELL_PROGS="))) {
                                        String[] keyValue = label.split("=", 2)
                                        String SHELL_PROGS = keyValue[1]
                                        if (Utils.isListNotEmpty(skipShells)) {
                                            if (SHELL_PROGS in skipShells) {
                                                echo "SKIP SHELLCHECK with ${SHELL_PROGS} for ${MATRIX_TAG}"
                                                return
                                            }
                                        }
                                        scSubstagesCount++
                                        String stagesQuickcheckNode_key = "shellcheck with ${SHELL_PROGS} for ${MATRIX_TAG}"
                                        Closure stagesQuickcheckNode_val = {
                                            String msgFail = "Failed stage: shellcheck with shell '${SHELL_PROGS}' for ${MATRIX_TAG}" +
                                                "\n  for ${Utils.castString(MATRIX_DSBC)}"
                                            Boolean didFail = true
                                            try {
                                                withEnv(["${multiLabel}=${SHELL_PROGS}", "SHELL_PROGS=${SHELL_PROGS}", "SHELL=${SHELL_PROGS}"]) {
                                                    withEnvOptional(dynacfgPipeline.defaultTools) {
                                                        sh """ set +x
                                                        echo "Shell-dependent testing with shell '${SHELL_PROGS}' on `uname -a || hostname || true` system"
                                                        ${multiCmd}
                                                        """
                                                    }
                                                }
                                                didFail = false
                                            } catch (Throwable t) {
                                                if (Utils.isRetryableException(t)) {
                                                    echo "[DEBUG]: quickcheck-shellcheck(${dynacfgPipeline.get("stashnameSrc")}) " +
                                                        " finished with a verdict classified as " +
                                                        "a retryable build agent failure - " +
                                                        "will re-schedule: ${msgFail} => ${t.toString()}"
                                                    didFail = false
                                                    throw t
                                                }
                                                catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE', message: msgFail) {
                                                    throw t
                                                }
                                            }

                                            if (didFail) {
                                                bigStageResult = 'FAILURE'
                                                MATRIX_DSBC.setWorstResult('FAILURE')
                                                MATRIX_DSBC.dsbcResultInterim = 'FAILURE'
                                                currentBuild.result = 'FAILURE'
                                                manager.buildFailure()
                                                unstable(msgFail)
                                            }
                                            return didFail
                                        }
                                        stagesQuickcheckNode << [stagesQuickcheckNode_key, stagesQuickcheckNode_val]
                                    }
                                }
                            }

                            if (scSubstagesCount == 0 && (singleCmd != null || multiCmd != null)) {
                                String cmdToRun = singleCmd ?: multiCmd
                                String stagesQuickcheckNode_key = "shellcheck for ${MATRIX_TAG}"
                                Closure stagesQuickcheckNode_val = {
                                    String msgFail = "Failed stage: shellcheck for ${MATRIX_TAG}" +
                                        "\n  for ${Utils.castString(MATRIX_DSBC)}"
                                    Boolean didFail = true
                                    try {
                                        withEnvOptional(dynacfgPipeline.defaultTools) {
                                            sh """ set +x
                                            echo "Shellcheck test on `uname -a || hostname || true` system"
                                            ${cmdToRun}
                                            """
                                        }
                                        didFail = false
                                    } catch (Throwable t) {
                                        if (Utils.isRetryableException(t)) {
                                            echo "[DEBUG]: quickcheck-shellcheck(${dynacfgPipeline.get("stashnameSrc")}) " +
                                                " finished with a verdict classified as " +
                                                "a retryable build agent failure - " +
                                                "will re-schedule: ${msgFail} => ${t.toString()}"
                                            didFail = false
                                            throw t
                                        }
                                        catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE', message: msgFail) {
                                            throw t
                                        }
                                    }

                                    if (didFail) {
                                        bigStageResult = 'FAILURE'
                                        MATRIX_DSBC.setWorstResult('FAILURE')
                                        MATRIX_DSBC.dsbcResultInterim = 'FAILURE'
                                        currentBuild.result = 'FAILURE'
                                        manager.buildFailure()
                                        unstable(msgFail)
                                    }
                                    return didFail
                                }
                                stagesQuickcheckNode << [stagesQuickcheckNode_key, stagesQuickcheckNode_val]
                            }
                        }

                        // 2. Spellcheck sub-stage (if the agent is capable per populated ASPELL=...)
                        def spConfig = dynacfgPipeline.quickcheck.spellcheck
                        if (spConfig != null && spConfig != false) {
                            String aspellProg = null
                            nodeLabels.each { String label ->
                                if (label.startsWith("ASPELL=")) {
                                    String[] keyValue = label.split("=", 2)
                                    if (keyValue.length > 1 && Utils.isStringNotEmpty(keyValue[1])) {
                                        aspellProg = keyValue[1].trim()
                                    }
                                } else if (label.equals("ASPELL")) {
                                    aspellProg = "aspell"
                                }
                            }

                            if (aspellProg != null) {
                                String spCmd = (spConfig == true) ? '( \${MAKE} spellcheck )' : spConfig.toString()
                                String stagesQuickcheckNode_key = "spellcheck for ${MATRIX_TAG}"
                                Closure stagesQuickcheckNode_val = {
                                    String msgFail = "Failed stage: spellcheck for ${MATRIX_TAG}" +
                                        "\n  for ${Utils.castString(MATRIX_DSBC)}"
                                    Boolean didFail = true
                                    try {
                                        withEnv(["ASPELL=${aspellProg}"]) {
                                            withEnvOptional(dynacfgPipeline.defaultTools) {
                                                sh """ set +x
                                                echo "Spellcheck testing with aspell '${aspellProg}' on `uname -a || hostname || true` system"
                                                ${spCmd}
                                                """
                                            }
                                        }
                                        didFail = false
                                    } catch (Throwable t) {
                                        if (Utils.isRetryableException(t)) {
                                            echo "[DEBUG]: quickcheck-spellcheck(${dynacfgPipeline.get("stashnameSrc")}) " +
                                                " finished with a verdict classified as " +
                                                "a retryable build agent failure - " +
                                                "will re-schedule: ${msgFail} => ${t.toString()}"
                                            didFail = false
                                            throw t
                                        }
                                        catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE', message: msgFail) {
                                            throw t
                                        }
                                    }

                                    if (didFail) {
                                        bigStageResult = 'FAILURE'
                                        MATRIX_DSBC.setWorstResult('FAILURE')
                                        MATRIX_DSBC.dsbcResultInterim = 'FAILURE'
                                        currentBuild.result = 'FAILURE'
                                        manager.buildFailure()
                                        unstable(msgFail)
                                    }
                                    return didFail
                                }
                                stagesQuickcheckNode << [stagesQuickcheckNode_key, stagesQuickcheckNode_val]
                            } else {
                                echo "SKIP SPELLCHECK: agent '${env.NODE_NAME}' does not have populated ASPELL capability"
                            }
                        }

                        // 3. Stylecheck sub-stage (currently placeholder)
                        def stConfig = dynacfgPipeline.quickcheck.stylecheck
                        if (stConfig != null && stConfig != false) {
                            String stCmd = (stConfig == true) ? '( \${MAKE} stylecheck )' : stConfig.toString()
                            String stagesQuickcheckNode_key = "stylecheck for ${MATRIX_TAG}"
                            Closure stagesQuickcheckNode_val = {
                                String msgFail = "Failed stage: stylecheck for ${MATRIX_TAG}" +
                                    "\n  for ${Utils.castString(MATRIX_DSBC)}"
                                Boolean didFail = true
                                try {
                                    withEnvOptional(dynacfgPipeline.defaultTools) {
                                        sh """ set +x
                                        echo "Stylecheck testing (placeholder) on `uname -a || hostname || true` system"
                                        ${stCmd}
                                        """
                                    }
                                    didFail = false
                                } catch (Throwable t) {
                                    if (Utils.isRetryableException(t)) {
                                        echo "[DEBUG]: quickcheck-stylecheck(${dynacfgPipeline.get("stashnameSrc")}) " +
                                            " finished with a verdict classified as " +
                                            "a retryable build agent failure - " +
                                            "will re-schedule: ${msgFail} => ${t.toString()}"
                                        didFail = false
                                        throw t
                                    }
                                    catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE', message: msgFail) {
                                        throw t
                                    }
                                }

                                if (didFail) {
                                    bigStageResult = 'FAILURE'
                                    MATRIX_DSBC.setWorstResult('FAILURE')
                                    MATRIX_DSBC.dsbcResultInterim = 'FAILURE'
                                    currentBuild.result = 'FAILURE'
                                    manager.buildFailure()
                                    unstable(msgFail)
                                }
                                return didFail
                            }
                            stagesQuickcheckNode << [stagesQuickcheckNode_key, stagesQuickcheckNode_val]
                        }

                        println "Discovered ${stagesQuickcheckNode.size()} stagesQuickcheckNode sub-stages for ${MATRIX_TAG}"

                        if (stagesQuickcheckNode.size() > 0) {
                            Map stagesPar = [:]
                            stagesQuickcheckNode.each { List stagesQuickcheckNode_tuple ->
                                String name = (String)stagesQuickcheckNode_tuple[0]
                                Closure closure = (Closure)stagesQuickcheckNode_tuple[1]
                                stagesPar[name] = closure
                            }
                            parallel stagesPar
                        }
                    }

                    currentBuild.result = bigStageResult
                    switch (bigStageResult) {
                        case 'FAILURE': if (true) {
                                String msg = "quickcheck for ${MATRIX_TAG} failed in at least one sub-test"
                                infra.reportGithubStageStatus(dynacfgPipeline.get("stashnameSrc"), msg,
                                    'FAILURE', "quickcheck-${MATRIX_TAG}")
                                msg = "FATAL: ${msg} above"
                                MATRIX_DSBC.setWorstResult('FAILURE')
                                MATRIX_DSBC.dsbcResultInterim = 'FAILURE'
                                echo msg

                                manager.buildFailure()
                                unstable(msg)
                            }
                            break

                        case ['SUCCESS', null]: if (true) {
                                String msg = "quickcheck for ${MATRIX_TAG} succeeded in all tests"
                                if (!infra.updateGithubStageStatus(dynacfgPipeline.get("stashnameSrc"), msg,
                                    'SUCCESS', "quickcheck-${MATRIX_TAG}"))
                                    echo msg
                            }
                            break
                    }
            }
    }

    println "Discovered ${stagesQuickcheck_arr.size()} stagesQuickcheck_arr platform builds"

    return stagesQuickcheck_arr
}

/**
 * Restore Map made by {@link quickcheck#call} from Set of Lists (tuples),
 * as needed for `parallel` step.
 */
Map makeMap(Set<List> stagesQuickcheck_arr = []) {
    println "Restoring discovered stagesQuickcheck platform builds from Set to Map..."
    Map stagesQuickcheck = [:]
    stagesQuickcheck_arr.each { List tup ->
        println "Restoring stagesQuickcheck platform build: ${Utils.castString(tup)}"
        if (Utils.isStringNotEmpty(tup[0]) && Utils.isClosure(tup[1]))
            stagesQuickcheck[(String)(tup[0])] = (Closure)(tup[1])
    }
    println "Discovered ${stagesQuickcheck.size()} stagesQuickcheck platform builds: ${Utils.castString(stagesQuickcheck)}"
    return stagesQuickcheck
}

Map sanityCheckDynacfgPipeline(Map dynacfgPipeline = [:]) {
    if (dynacfgPipeline == null) {
        dynacfgPipeline = [:]
    }

    if (dynacfgPipeline.containsKey('quickcheck')) {
        if (Utils.isMap(dynacfgPipeline['quickcheck'])) {
            if (!dynacfgPipeline['quickcheck'].containsKey('dynamatrixAxesLabels') || "".equals(dynacfgPipeline['quickcheck']['dynamatrixAxesLabels'])) {
                if (dynamatrixGlobalState.enableDebugTrace)
                    echo "quickcheck.sanityCheckDynacfgPipeline(): populate missing dynacfgPipeline.quickcheck.dynamatrixAxesLabels"
                dynacfgPipeline['quickcheck']['dynamatrixAxesLabels'] = [~/^OS_.+/, 'ARCH']
            }
            if (!dynacfgPipeline['quickcheck'].containsKey('shellcheck')) {
                if (dynamatrixGlobalState.enableDebugTrace)
                    echo "quickcheck.sanityCheckDynacfgPipeline(): populate default dynacfgPipeline.quickcheck.shellcheck"
                dynacfgPipeline['quickcheck']['shellcheck'] = [
                    'single': '( \${MAKE} shellcheck )',
                    'multi': '( SHELL_PROGS="$SHELL_PROGS" \${MAKE} shellcheck )',
                    'multiLabel': 'SHELL',
                    'skipShells': []
                ]
            } else if (Utils.isMap(dynacfgPipeline['quickcheck']['shellcheck'])) {
                if (!dynacfgPipeline['quickcheck']['shellcheck'].containsKey('multiLabel')) {
                    dynacfgPipeline['quickcheck']['shellcheck']['multiLabel'] = 'SHELL'
                }
            }
            if (!dynacfgPipeline['quickcheck'].containsKey('spellcheck')) {
                if (dynamatrixGlobalState.enableDebugTrace)
                    echo "quickcheck.sanityCheckDynacfgPipeline(): populate default dynacfgPipeline.quickcheck.spellcheck"
                dynacfgPipeline['quickcheck']['spellcheck'] = '( \${MAKE} spellcheck )'
            }
            if (!dynacfgPipeline['quickcheck'].containsKey('stylecheck')) {
                if (dynamatrixGlobalState.enableDebugTrace)
                    echo "quickcheck.sanityCheckDynacfgPipeline(): populate default dynacfgPipeline.quickcheck.stylecheck"
                dynacfgPipeline['quickcheck']['stylecheck'] = '( \${MAKE} stylecheck )'
            }
        } else {
            if ("${dynacfgPipeline['quickcheck']}".trim().equals("true")) {
                if (dynamatrixGlobalState.enableDebugTrace)
                    echo "quickcheck.sanityCheckDynacfgPipeline(): true: defaulting a reasonable config"
                dynacfgPipeline['quickcheck'] = [
                    'dynamatrixAxesLabels': [~/^OS_.+/, 'ARCH'],
                    'shellcheck': [
                        'single': '( \${MAKE} shellcheck )',
                        'multi': '( SHELL_PROGS="$SHELL_PROGS" \${MAKE} shellcheck )',
                        'multiLabel': 'SHELL',
                        'skipShells': []
                    ],
                    'spellcheck': '( \${MAKE} spellcheck )',
                    'stylecheck': '( \${MAKE} stylecheck )'
                ]
            } else if ("${dynacfgPipeline['quickcheck']}".trim().equals("false")) {
                if (dynamatrixGlobalState.enableDebugTrace)
                    echo "quickcheck.sanityCheckDynacfgPipeline(): false: defaulting a null-filled config"
                dynacfgPipeline['quickcheck'] = [:]
                dynacfgPipeline['quickcheck']['dynamatrixAxesLabels'] = []
                dynacfgPipeline['quickcheck']['shellcheck'] = null
                dynacfgPipeline['quickcheck']['spellcheck'] = null
                dynacfgPipeline['quickcheck']['stylecheck'] = null
            } else {
                error "Unsupported dynacfgPipeline['quickcheck'] (expecting Boolean or Map) : ${Utils.castString(dynacfgPipeline['quickcheck'])}"
            }
        }
    } else {
        if (dynamatrixGlobalState.enableDebugTrace)
            echo "quickcheck.sanityCheckDynacfgPipeline(): defaulting a null-filled config"
        dynacfgPipeline['quickcheck'] = [:]
        dynacfgPipeline['quickcheck']['dynamatrixAxesLabels'] = []
        dynacfgPipeline['quickcheck']['shellcheck'] = null
        dynacfgPipeline['quickcheck']['spellcheck'] = null
        dynacfgPipeline['quickcheck']['stylecheck'] = null
    }

    if (!dynacfgPipeline.quickcheck.stageNameFunc) {
        if (dynamatrixGlobalState.enableDebugTrace)
            echo "quickcheck.sanityCheckDynacfgPipeline(): populate missing dynacfgPipeline.quickcheck.stageNameFunc"
        dynacfgPipeline.quickcheck.stageNameFunc = DynamatrixSingleBuildConfig.&QuickcheckPlatform_StageNameTagFunc
    }

    if (dynacfgPipeline.containsKey('quickcheck_prepconf')) {
        if ("${dynacfgPipeline['quickcheck_prepconf']}".trim().equals("true")) {
            if (dynamatrixGlobalState.enableDebugTrace)
                echo "quickcheck.sanityCheckDynacfgPipeline(): populate missing dynacfgPipeline.quickcheck_prepconf = null"
            dynacfgPipeline['quickcheck_prepconf'] = null
        }
    } else {
        if (dynamatrixGlobalState.enableDebugTrace)
            echo "quickcheck.sanityCheckDynacfgPipeline(): populate missing dynacfgPipeline.quickcheck_prepconf = null"
        dynacfgPipeline['quickcheck_prepconf'] = null
    }

    if (dynacfgPipeline.containsKey('quickcheck_configure')) {
        if ("${dynacfgPipeline['quickcheck_configure']}".trim().equals("true")) {
            if (dynamatrixGlobalState.enableDebugTrace)
                echo "quickcheck.sanityCheckDynacfgPipeline(): populate missing dynacfgPipeline.quickcheck_configure = null"
            dynacfgPipeline['quickcheck_configure'] = null
        }
    } else {
        if (dynamatrixGlobalState.enableDebugTrace)
            echo "quickcheck.sanityCheckDynacfgPipeline(): populate missing dynacfgPipeline.quickcheck_configure = null"
        dynacfgPipeline['quickcheck_configure'] = null
    }

    if (dynamatrixGlobalState.enableDebugTrace) {
        println "QUICKCHECK_PREPCONF : " + Utils.castString(dynacfgPipeline['quickcheck_prepconf'])
        println "QUICKCHECK_CONFIGURE: " + Utils.castString(dynacfgPipeline['quickcheck_configure'])
        println "QUICKCHECK          : " + Utils.castString(dynacfgPipeline['quickcheck'])
    }

    return dynacfgPipeline
}
