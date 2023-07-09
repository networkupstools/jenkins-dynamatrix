package org.nut.dynamatrix;

import org.nut.dynamatrix.dynamatrixGlobalState;
import org.nut.dynamatrix.*;

import java.security.MessageDigest; // md5

/*
 * Run one combination of settings in the matrix for chosen compiler, etc.
 */
void call(Map dynacfgPipeline = [:], DynamatrixSingleBuildConfig dsbc = null, String stageName = null) {
    // Values in env.* inspected below come from the anticipated build
    // matrix settings (agent labels, etc.), configureEnvvars.groovy and
    // tool configuration like autotools.groovy or ci_build.groovy

    // NOTE: Currently the values set ONLY by configureEnvvars.groovy
    // are not yet set and so not exposed in env[] by the time we build
    // the ID string below. In fact, it may be too complex to reliably
    // extract CONFIG_ENVVARS set in shell back to groovy and shell again.

    // TODO: Pass into build additional hints that may be in dsbc, e.g.
    // clioptSet - but gotta decide to which tool they should go and when
    // (e.g. configure script takes some opts, ci_build normally does not)

    // for analysis part after the build
    String compilerTool = null
    // Allowed elements are characters, digits, dashes and underscores
    // (more precisely, the ID must match the regular expression `\p{Alnum}[\p{Alnum}-_]*`
    String id = ""

        String msg = "Building with "
        if (env?.COMPILER) {
            id = env.COMPILER.toUpperCase().trim()
            switch (env.COMPILER) {
                // TODO: Handle alternately-named cross-compilers like packaged
                // like "arm-linux-gnueabi-gcc(-10)" or third-party snapshots
                // named like "arm-linux-gnueabi-20140823-20131011-gcc-4.8.1"
                // Note that would also need some cooperation from agent labels
                // and generic "${COMPILER}VER" support, possibly some use of
                // dynamatrixAxesCommonOpts and/or dynamatrixAxesCommonEnv
                case ['gcc', 'GCC']:
                    compilerTool = 'gcc'
                    if (env?.GCCVER) {
                        id += "-${env.GCCVER}"
                        try {
                            if (env?.GCCVER?.replaceAll(/\..*$/, '')?.toInteger() < 4) {
                                compilerTool = 'gcc3'
                            }
                        } catch (Throwable ignored) {}
                    }
                    break
                case ['clang', 'CLANG']:
                    compilerTool = 'clang'
                    if (env?.CLANGVER) id += "-${env.CLANGVER}"
                    break
                default:
                    try {
                        if (env["${env.COMPILER}VER"]) {
                            id += "-" + env["${env.COMPILER}VER"]
                        }
                    } catch (Throwable ignored) {} // ignore
                    break
            }
        } else {
            if (env?.GCCVER) {
                id = "GCC-${env.GCCVER}"
                compilerTool = 'gcc'
                try {
                    if (env?.GCCVER?.replaceAll(/\..*$/, '')?.toInteger() < 4) {
                        compilerTool = 'gcc3'
                    }
                } catch (Throwable ignored) {}
            } else if (env?.CLANGVER) {
                id = "CLANG-${env.CLANGVER}"
                compilerTool = 'clang'
            } else {
                echo "WARNING: env got no COMPILER, and no supported {COMPILER}VER... Proceeding by default..."
            }
        }
        if (id != "") msg += "${id} "

        if (env?.CSTDVERSION_c) {
            def tmp = DynamatrixSingleBuildConfig.C_stdarg(env?.CSTDVARIANT, env?.CSTDVERSION_c, false)
            id += "_${tmp}"
            msg += "STD=${tmp} "
        } else {
            if (env?.STDARG) {
                id += "_${env.STDARG}"
                msg += ("STD=${env.STDARG} " - ~/-std=/)
            }
        }

        if (env?.CSTDVERSION_cxx) {
            def tmp = DynamatrixSingleBuildConfig.C_stdarg(env?.CSTDVARIANT, env?.CSTDVERSION_cxx, true, true)
            id += "_${tmp}".replaceAll(/\+/, 'x')
            msg += "STD=${tmp} "
        } else {
            if (env?.STDXXARG) {
                id += "_${env.STDXXARG}"
                msg += ("STD=${env.STDXXARG} " - ~/-std=/)
            }
        }

        if (env?.BUILD_WARNOPT) {
            id += "_WARN=${env.BUILD_WARNOPT}"
            msg += "WARN=${env.BUILD_WARNOPT} "
        }

        if (env?.ARCHARG || env?.BITSARG || env?.ARCH_BITS || env?.BITS
            || (env?.ARCH_BITS && env["ARCH${env.ARCH_BITS}"])
            || (env?.BITS && env["ARCH${env.BITS}"])
            || env?.OS_FAMILY || env?.OS_DISTRO
        ) {
            msg += "on "
            if (env?.ARCH_BITS && env["ARCH${env.ARCH_BITS}"]) {
                id += "_${env["ARCH${env.ARCH_BITS}"]}"
                msg += "${env["ARCH${env.ARCH_BITS}"]} "
            } else if (env?.BITS && env["ARCH${env.BITS}"]) {
                id += "_${env["ARCH${env.BITS}"]}"
                msg += "${env["ARCH${env.BITS}"]} "
            } else {
                if (env?.ARCHARG) {
                    id += "_${env.ARCHARG}"
                    msg += ("${env.ARCHARG} " - ~/-arch[ =]/)
                }
            }

            if (env?.ARCH_BITS) {
                id += "_${env.ARCH_BITS}"
                msg += "${env.ARCH_BITS}-bit "
            } else if (env?.BITS) {
                id += "_${env.BITS}"
                msg += "${env.BITS}-bit "
            } else {
                if (env?.BITSARG) {
                    id += "_${env.BITSARG}"
                    msg += ("${env.BITSARG} " - ~/-m/) + "-bit "
                }
            }

            if (env?.OS_FAMILY || env?.OS_DISTRO) {
                if (env?.OS_FAMILY && env?.OS_DISTRO) {
                    id += "_${env.OS_FAMILY}-${env.OS_DISTRO}"
                    msg += "${env.OS_FAMILY}-${env.OS_DISTRO} "
                } else {
                    // Only one of those strings is present
                    def x = env?.OS_FAMILY
                    if (x == null) x = env?.OS_DISTRO
                    id += "_${x}"
                    msg += "${x} "
                }
            }

            msg += "platform"
        }

        id = id.trim().replaceAll(/\\s+/, '_').replaceAll(/[^\p{Alnum}-_]/, '_')
        if (stageName)
            msg = msg.trim() + " for ${stageName}"

        if (env?.CI_SLOW_BUILD_FILTERNAME)
            msg = msg.trim() + " :: as part of slowBuild filter: ${env.CI_SLOW_BUILD_FILTERNAME}"

        // Strive for unique name prefix across many similar builds executed
        String archPrefix = id
        if (stageName)
            archPrefix += "--" + stageName
        archPrefix = archPrefix.trim().replaceAll(/\s+/, '').replaceAll(/[^\p{Alnum}-_=+.]+/, '-')
        if (archPrefix.length() > 100) { // Help filesystems that limit filename or path size
            String hash = "MD5_" + MessageDigest.getInstance("MD5").digest(archPrefix.bytes).encodeHex().toString().trim()
            //groovy-2.5//archPrefix = "MD5_" + archPrefix.md5().trim()
            echo "Archived log prefix for this build '${archPrefix}' was too long, so truncating it to ${hash}"
            sh """ echo '${hash} => ${archPrefix}' > '.ci.${hash}.hashed.log' """
            archPrefix = hash
        }

        // Build a multiline shell script
        // Split that into many shell steps (each with configureEnvvars
        // re-run or some re-import of the first generated value, if needed),
        // and/or sequential stages to visualize in BO UI build progress
        String cmdCommonLabel = ""
        String cmdPrepLabel = ""
        String cmdBuildLabel = ""
        String cmdTest1Label = ""
        String cmdTest2Label = ""

        String cmdPrepLog = ""
        String cmdBuildLog = ""
        String cmdTest1Log = ""
        String cmdTest2Log = ""

        String cmdCommon = """ """
        String cmdPrep = ""
        String cmdBuild = ""
        String cmdTest1 = ""
        String cmdTest2 = ""
        if (dynacfgPipeline?.traceBuildShell_configureEnvvars) {
            cmdCommon = """ set -x
"""
        } else {
            cmdCommon = """ set +x
"""
        }

        if (dynacfgPipeline?.buildSystem) {
            cmdCommonLabel = "With ${dynacfgPipeline.buildSystem}: "
        }

        if (dynacfgPipeline?.configureEnvvars) {
            // Might be better to evict into cmdPrep alone, but for e.g.
            // the ci_build.sh tooling defaults, we only call "check"
            // and that handles everything for the BUILD_TYPE requested
            cmdCommon += """ ${dynacfgPipeline?.configureEnvvars}
"""
            cmdCommonLabel += "configureEnvvars "
        }

        // Note += here: do not want to lose CONFIG_ENVVARS :)
        if (dynacfgPipeline?.traceBuildShell) {
            cmdCommon += """
set -x
"""
        } else {
            cmdCommon += """
set +x
"""
        }

        // Remember where to get the logs
        if (dsbc?.thisDynamatrix) {
            dsbc.thisDynamatrix.setLogKey(stageName, archPrefix)
        }

        // Note: log files below are used for warnings-ng processing
        // and their namesakes will be removed before the build.
        // TODO: invent a way around `git status` violations for projects that care?
        if (dynacfgPipeline?.buildPhases?.prepconf) {
            cmdPrepLog = ".ci.${archPrefix}.prepconf.log"
            cmdPrep += cmdlineBuildLogged("${dynacfgPipeline.buildPhases.prepconf}", cmdPrepLog, stageName, env?.CI_SLOW_BUILD_FILTERNAME, archPrefix)
            cmdPrepLabel += "prepconf "
        }

        if (dynacfgPipeline?.buildPhases?.configure) {
            cmdPrepLog = ".ci.${archPrefix}.prepconf.log"
            cmdPrep += cmdlineBuildLogged("${dynacfgPipeline.buildPhases.configure}", cmdPrepLog, stageName, env?.CI_SLOW_BUILD_FILTERNAME, archPrefix)
            cmdPrepLabel += "configure "
        }

        if (dynacfgPipeline?.buildPhases?.buildQuiet) {
            cmdBuildLog = ".ci.${archPrefix}.build.log"
            cmdBuild += cmdlineBuildLogged("${dynacfgPipeline.buildPhases.buildQuiet}", cmdBuildLog, stageName, env?.CI_SLOW_BUILD_FILTERNAME, archPrefix)
            cmdBuildLabel += "buildQuiet "
        } else if (dynacfgPipeline?.buildPhases?.build) {
            cmdBuildLog = ".ci.${archPrefix}.build.log"
            cmdBuild += cmdlineBuildLogged("${dynacfgPipeline.buildPhases.build}", cmdBuildLog, stageName, env?.CI_SLOW_BUILD_FILTERNAME, archPrefix)
            cmdBuildLabel += "build "
        }

        if (dynacfgPipeline?.buildPhases?.check) {
            cmdTest1Log = ".ci.${archPrefix}.check.log"
            cmdTest1 += cmdlineBuildLogged("${dynacfgPipeline.buildPhases.check}", cmdTest1Log, stageName, env?.CI_SLOW_BUILD_FILTERNAME, archPrefix)
            cmdTest1Label += "check "
        }

        if (dynacfgPipeline?.buildPhases?.distcheck) {
            cmdTest2Log = ".ci.${archPrefix}.distcheck.log"
            cmdTest2 += cmdlineBuildLogged("${dynacfgPipeline.buildPhases.distcheck}", cmdTest2Log, stageName, env?.CI_SLOW_BUILD_FILTERNAME, archPrefix)
            cmdTest2Label += "distcheck "
        }

        if (stageName) {
            if (cmdPrepLabel != "")
                cmdPrepLabel = cmdPrepLabel.trim() + " for ${stageName}"
            if (cmdBuildLabel != "")
                cmdBuildLabel = cmdBuildLabel.trim() + " for ${stageName}"
            if (cmdTest1Label != "")
                cmdTest1Label = cmdTest1Label.trim() + " for ${stageName}"
            if (cmdTest2Label != "")
                cmdTest2Label = cmdTest2Label.trim() + " for ${stageName}"
        }

        def shRes = 0
        String strMayFail = ""
        if (dsbc?.isAllowedFailure) strMayFail += " (may fail)"
        String lastLog = ""
        String lastErr = ""

        stage('Prep' + strMayFail) {
            echo msg
            sh " rm -f .ci*.log* "
            //if (dynamatrixGlobalState.enableDebugTrace)
            //if (dynacfgPipeline?.configureEnvvars)
                sh label: 'Report compilers', script: cmdCommon + """ ( eval \$CONFIG_ENVVARS; echo "CC: \$CC => `command -v "\$CC"`"; echo "CXX: \$CXX => `command -v "\$CXX"`" ; hostname; ) | tee ".ci.${archPrefix}.configureEnvvars.log" ; """
            sh label: 'Save a report of envvars', script: """ ( cat << 'EOF_MSG'
Actual original envvars for build scenario described as:
    ${msg}

EOF_MSG
set | grep -E '^[^ ]*=' | sort -n ) > ".ci.${archPrefix}.origEnvvars.log" ; """

            sh label: 'Save a report of envvars', script: cmdCommon + """ ( cat << 'EOF_MSG'
Applied parsed envvars (compiler/tools-related adjustments, e.g. CONFIG_ENVVARS, STD(XX)ARG and (LD)BITSARG) for build scenario described as:
    ${msg}

EOF_MSG
set | grep -E '^[^ ]*=' | sort -n ) > ".ci.${archPrefix}.parsedEnvvars.log" ; """

            if (cmdPrep != "") {
                lastLog = cmdPrepLog
                def res = sh (script: cmdCommon + cmdPrep, returnStatus: true, label: (cmdCommonLabel + cmdPrepLabel.trim()))
                if (res != 0) {
                    shRes = res
                    dsbc?.setWorstResult('UNSTABLE')
                    if (dsbc?.thisDynamatrix) { dsbc.thisDynamatrix.setWorstResult(stageName, 'UNSTABLE') }
                    lastErr = "FAILED 'Prep'" + (stageName ? " for ${stageName}" : "")
                    unstable lastErr
                }
            }
        }

        if (cmdBuild != "" && shRes == 0) {
            stage('Build' + strMayFail) {
                lastLog = cmdBuildLog
                def res = sh (script: cmdCommon + cmdBuild, returnStatus: true, label: (cmdCommonLabel + cmdBuildLabel.trim()))
                if (res != 0) {
                    shRes = res
                    dsbc?.setWorstResult('UNSTABLE')
                    if (dsbc?.thisDynamatrix) { dsbc.thisDynamatrix.setWorstResult(stageName, 'UNSTABLE') }
                    lastErr = "FAILED 'Build'" + (stageName ? " for ${stageName}" : "")
                    unstable lastErr
                }
            }
        }

        String nameTest1 = "Test1"
        String nameTest2 = "Test2"

        if ( (cmdTest1 != "" && cmdTest2 == "")
        ||   (cmdTest1 == "" && cmdTest2 != "")
        ) {
            nameTest1 = "Test"

            // ci_build all-in-one?
            if (cmdBuild == "")
                nameTest1 = "Build+Test"

            // any ci_build scenario hints?
            if (env.BUILD_TYPE)
                nameTest1 += " ${env.BUILD_TYPE}"

            nameTest2 = nameTest1
        }

        if (cmdTest1 != "" && shRes == 0) {
            stage(nameTest1 + strMayFail) {
                lastLog = cmdTest1Log
                def res = sh (script: cmdCommon + cmdTest1, returnStatus: true, label: (cmdCommonLabel + cmdTest1Label.trim()))
                if (res != 0) {
                    shRes = res
                    dsbc?.setWorstResult('UNSTABLE')
                    if (dsbc?.thisDynamatrix) { dsbc.thisDynamatrix.setWorstResult(stageName, 'UNSTABLE') }
                    lastErr = "FAILED 'Test1'" + (stageName ? " for ${stageName}" : "")
                    unstable lastErr
                }
            }
        }

        if (cmdTest2 != "" && shRes == 0) {
            stage(nameTest2 + strMayFail) {
                lastLog = cmdTest2Log
                def res = sh (script: cmdCommon + cmdTest2, returnStatus: true, label: (cmdCommonLabel + cmdTest2Label.trim()))
                if (res != 0) {
                    shRes = res
                    dsbc?.setWorstResult('UNSTABLE')
                    if (dsbc?.thisDynamatrix) { dsbc.thisDynamatrix.setWorstResult(stageName, 'UNSTABLE') }
                    lastErr = "FAILED 'Test2'" + (stageName ? " for ${stageName}" : "")
                    unstable lastErr
                }
            }
        }

    // This name might go to e.g. notifications (of success/fail)
    // so the string better be unique and meaningful
    String resName = "Collect results"
    if (stageName != null && stageName != "")
        resName = "Results for ${stageName}"
    if (env?.CI_SLOW_BUILD_FILTERNAME)
        resName = resName.trim() + " :: as part of slowBuild filter: ${env.CI_SLOW_BUILD_FILTERNAME}"
    stage("${resName}" + strMayFail) {
        // Capture this after all the stages: different tools
        // might generate the files at different times
        // Needs Warnings-NG plugin, Forensics API plugin, Git Forensics plugin...
        echo "Completed with result ${shRes} (" +
            (shRes == 0 ? "SUCCESS" : ( (dsbc?.isAllowedFailure) ? "ALLOWED " : "" ) + "FAILURE") +
            ")" + (dsbc == null ? "" : " this build configuration: " + dsbc.toString()) +
            (env?.CI_SLOW_BUILD_FILTERNAME ? " :: as part of slowBuild filter: ${env.CI_SLOW_BUILD_FILTERNAME}" : "")

        // Strip workspace paths and relative-to-rootfs (../../../usr/include)
        // from inspected logs, by sed or by some tool args?..
        sh label: 'Sanitize paths in build log files', script: """
for F in .ci.*.log ; do
    [ -s "\$F" ] || continue
    sed -e "s|`pwd`||" \\
        -e "s|\${WORKSPACE}||" \\
        -e "s|\${WORKSPACE_TMP}||" \\
        -e "s/\\.\\.\\(\\/\\.\\.\\)*\\/\\(usr|opt|s*bin|lib[0-9]*\\)\\//\\/\\2\\//" \\
        < "\$F" > ".ci-sanitizedPaths.\$F"
done
"""

        // NOTE: Subdirs support for cppcheck*.xml and config.log here,
        // similar to analysis above allows for out-of-tree builds etc.
        sh label: 'Compress collected logs', script: """
if [ -n "`ls -1 .ci.*.log`" ]; then gzip .ci.*.log; fi
find . -type f -name config.log -o config.nut_report_feature.log -o -name 'cppcheck*.xml' | sed 's,^\\./,,' \
| while read F ; do
    N="`echo "\$F" | tr '/' '_'`"
    if [ -s "\$F" ]; then gzip < "\$F" > '.ci.${archPrefix}.'"\$N"'.gz' || true ; fi
done
"""
        archiveArtifacts (artifacts: ".ci.${archPrefix}*", allowEmptyArchive: true)

        // Log scan analyses, once finely grained per tool/config,
        // and another clumping all tools together to deduplicate
        def i = null    // retval of scanForIssues()
        def ia = null   // retval of scanForIssues() for aggregated results
        def cppcheck = null
        def cppcheckAggregated = null
        // "filters" below help ignore problems in system headers
        // (with e.g. C90 build mode).
        // TOTHINK: should we try to get distcheck dirs (like nut-2.4.7.1/*)
        // to avoid duplicates? Or on the contrary, would we want to see
        // some bugs in generated dist'ed code even if original does not
        // have them?
        String filterSysHeaders = ".*[/\\\\]usr[/\\\\].*\\.(h|hpp)\$"
        // Scanner has a limitation regex for "URL" of the analysis:
        String warningsNgId = (id + "-" + archPrefix) //.toLowerCase()
        switch (compilerTool) {
            case 'gcc':
                i = scanForIssues tool: gcc(id: warningsNgId, pattern: '.ci-sanitizedPaths.*.log'), filters: [ excludeFile(filterSysHeaders) ]
                ia = scanForIssues tool: gcc(id: 'CC_CXX_compiler', pattern: '.ci-sanitizedPaths.*.log'), filters: [ excludeFile(filterSysHeaders) ]
                break
            case 'gcc3':
                i = scanForIssues tool: gcc3(id: warningsNgId, pattern: '.ci-sanitizedPaths.*.log'), filters: [ excludeFile(filterSysHeaders) ]
                ia = scanForIssues tool: gcc3(id: 'CC_CXX_compiler', pattern: '.ci-sanitizedPaths.*.log'), filters: [ excludeFile(filterSysHeaders) ]
                break
            case 'clang':
                i = scanForIssues tool: clang(id: warningsNgId, pattern: '.ci-sanitizedPaths.*.log'), filters: [ excludeFile(filterSysHeaders) ]
                ia = scanForIssues tool: clang(id: 'CC_CXX_compiler', pattern: '.ci-sanitizedPaths.*.log'), filters: [ excludeFile(filterSysHeaders) ]
                break
        }
        if (0 == sh (returnStatus:true, script: """ test -n "`find . -name 'cppcheck*.xml' 2>/dev/null`" && echo "Found cppcheck XML reports" """)) {
            // Note: warningsNgId starts with e.g. "_gnu17..."
            // so no trailing punctuation after "CppCheck" string:
            cppcheck = scanForIssues tool: cppCheck(id: "CppCheck" + warningsNgId, pattern: '**/cppcheck*.xml'), filters: [ excludeFile(filterSysHeaders) ], sourceCodeEncoding: 'UTF-8'
            cppcheckAggregated = scanForIssues tool: cppCheck(id: 'CppCheck_analyser', pattern: '**/cppcheck*.xml'), filters: [ excludeFile(filterSysHeaders) ], sourceCodeEncoding: 'UTF-8'
        }

        if (i != null) {
            dynamatrixGlobalState.issueAnalysis << i
            if (dynacfgPipeline?.delayedIssueAnalysis) {
                // job should call doSummarizeIssues() in the end
                // for aggregated results over all build codepaths
                echo "Collected issues analysis was logged to make a big summary in the end"
            } else {
                // Publish individual build scenario results now
                // NOTE: This makes build and branch summary pages very noisy:
                // every analysis is published separately in its left menu
                // and so far no way was found to remove them during e.g.
                // final grouped or aggregated analysis publishing stage.
                doSummarizeIssues([i], warningsNgId + "--analysis", warningsNgId + "--analysis")
            }
        }
        if (ia != null) {
            dynamatrixGlobalState.issueAnalysisAggregated << ia
            if (dynacfgPipeline?.delayedIssueAnalysis) {
                // job should call doSummarizeIssues() in the end
                // for aggregated results over all build codepaths
                echo "Collected aggregated issues analysis was logged to make a big summary in the end"
            } else {
                // Publish individual build scenario results now
                //doSummarizeIssues([ia], "CC_CXX_compiler_aggregated_analysis", "C/C++ compiler aggregated analysis")
                echo "Aggregated analysis is always delayed (C/C++ compiler)"
            }
        }

        if (cppcheck != null) {
            dynamatrixGlobalState.issueAnalysisCppcheck << cppcheck
            if (dynacfgPipeline?.delayedIssueAnalysis) {
                // job should call doSummarizeIssues() in the end
                // for aggregated results over all build codepaths
                echo "Collected issues analysis was logged to make a big summary in the end"
            } else {
                // Publish individual build scenario results now
                doSummarizeIssues([cppcheck], "CppCheck" + warningsNgId + "--analysis", "CppCheck--" + warningsNgId + "--analysis")
            }
        }
        if (cppcheckAggregated != null) {
            dynamatrixGlobalState.issueAnalysisAggregatedCppcheck << cppcheckAggregated
            if (dynacfgPipeline?.delayedIssueAnalysis) {
                // job should call doSummarizeIssues() in the end
                // for aggregated results over all build codepaths
                echo "Collected aggregated issues analysis was logged to make a big summary in the end"
            } else {
                // Publish individual build scenario results now
                //doSummarizeIssues([cppcheckAggregated], "CppCheck_aggregated_analysis", "CppCheck aggregated analysis")
                echo "Aggregated analysis is always delayed (CppCheck)"
            }
        }

        if (shRes != 0) {
            // Add a summary page entry as we go through the build,
            // so developers can quickly find the faults
            try {
                String sumtxt = null

                // Used to be '/images/48x48/(error|warning).png'
                // after the logic below, an arg for createSummary()
                // Maybe sourced from https://github.com/jenkinsci/jenkins/blob/master/war/src/main/webapp/images/48x48 :
                String sumimg
                String suming_prefix = '/images/svgs/'
                String suming_suffix = '.svg'
                if (dsbc?.isAllowedFailure) {
                    sumtxt = "[UNSTABLE (non-success is expected)] "
                    sumimg = 'warning'
                } else {
                    sumtxt = "[FAILURE (not anticipated)] "
                    sumimg = 'error'
                }

                if (Utils.isStringNotEmpty(msg))
                    sumtxt += msg + ": "
                sumtxt += lastErr.replaceFirst(/ for .*$/, '')
                sumtxt += "<ul>"
                try {
                    for (String F in ["origEnvvars", "parsedEnvvars", "configureEnvvars", "config"]) {
                        if (fileExists(".ci.${archPrefix}.${F}.log.gz")) {
                            sumtxt += "<li><a href='${env.BUILD_URL}/artifact/.ci.${archPrefix}.${F}.log.gz'>.ci.${archPrefix}.${F}.log.gz</a></li>"
                        }
                    }
                    def files = findFiles(glob: ".ci.${archPrefix}.*_config*.log.gz")
                    if (Utils.isListNotEmpty(files)) {
                        files.each { def FF -> // FileWrapper FF ->
                            sumtxt += "<li><a href='${env.BUILD_URL}/artifact/${FF.name}'>${FF.name}</a></li>"
                        }
                    }
                } catch (Throwable ignored) {} // no-op, possibly some iteration/fileExists problem
                sumtxt += "<li><a href='${env.BUILD_URL}/artifact/${lastLog}.gz'>${lastLog}.gz</a></li></ul>"

                createSummary(text: sumtxt, icon: suming_prefix + sumimg + suming_suffix)
            } catch (Throwable ignored) {} // no-op, possibly missing badge plugin
        }

        if (!(dsbc?.keepWs)) {
            // Avoid wasting space on workers; the dynamatrix is not too
            // well suited for inspecting the builds post-mortem reliably
            try {
                cleanWs()
            } catch (Throwable ignored) {
                deleteDir()
            }
        }

        if (shRes == 0) {
            dsbc?.setWorstResult('SUCCESS')
            if (dsbc?.thisDynamatrix) { dsbc.thisDynamatrix.setWorstResult(stageName, 'SUCCESS') }
        } else {
            def msgFail = 'Build-and-check step failed, proceeding to cover the rest of matrix'
            if (dsbc?.thisDynamatrix?.failFast) {
                echo "Raising mustAbort flag to prevent build scenarios which did not yet start from starting, fault detected in stage '${stageName}': executed shell steps failed"
                dsbc.thisDynamatrix.mustAbort = true
            } else {
                echo "Not raising mustAbort flag, because " + (dsbc?.thisDynamatrix ? (dsbc?.thisDynamatrix?.failFast ? "dsbc?.thisDynamatrix.failFast==true (so not sure why not raising the flag...)" : "dsbc?.thisDynamatrix.failFast==false") : "dsbc?.thisDynamatrix is not tracked in this run")
            }

            if (dsbc?.isAllowedFailure) {
                dsbc?.setWorstResult('UNSTABLE')
                if (dsbc?.thisDynamatrix) { dsbc.thisDynamatrix.setWorstResult(stageName, 'UNSTABLE') }
                unstable msgFail
            } else {
                dsbc?.setWorstResult('FAILURE')
                if (dsbc?.thisDynamatrix) { dsbc.thisDynamatrix.setWorstResult(stageName, 'FAILURE') }
                error msgFail
            }
        }
    } // stage for results of the single build scenario

} // buildMatrixCellCI()

def cmdlineBuildLogged(String cmd, String logfile, String stageName, String CI_SLOW_BUILD_FILTERNAME = null, String archPrefix = null) {
    // A little sleep allows "tail" to show the last lines of that build log
    return """ RES=0; touch '${logfile}'
tail -f '${logfile}' &
CILOGPID=\$!
( ${cmd} ) >> '${logfile}' 2>&1 || RES=\$?
sleep 1; echo ''
kill "\$CILOGPID" >/dev/null 2>&1
( # cat helps avoid errors due to expansion of cmd (et al) as shell-parsable code
  set +x
  printf "FINISHED with exit-code \$RES "
  cat << EOF
cmd: ${cmd}
EOF
  cat << 'EOF'
cmdOrig: ${cmd}
...for stageName: ${stageName}
EOF
    if [ -n "${CI_SLOW_BUILD_FILTERNAME}" ] && [ "null" != "${CI_SLOW_BUILD_FILTERNAME}" ]; then
  cat << 'EOF'
...as part of slowBuild filter: ${env.CI_SLOW_BUILD_FILTERNAME}
EOF
    fi
  cat << 'EOF'
...logged into: ${logfile} => ${env.BUILD_URL}/artifact/${logfile}.gz
EOF
  echo "NOTE: Saved big job artifacts for this single build scenario usually have same identifier in the middle of file name"
  if [ -s config.log ] || [ -n "`ls -1 .ci.${archPrefix}.config.log.gz .ci.${archPrefix}.*_config.log.gz 2>/dev/null`" ]; then
    echo "...e.g. a (renamed, compressed) copy of config.log for this build"
    if [ -n "${archPrefix}" ] && [ "${archPrefix}" != null ] ; then
        CONFIG_LOG_URLS=""
        for C in .ci.${archPrefix}.config.log.gz .ci.${archPrefix}.*_config.log.gz ; do
            [ -s "\$C" ] && CONFIG_LOG_URLS="\$CONFIG_LOG_URLS ${env.BUILD_URL}/artifact/\$C"
        done
        [ -n "\$CONFIG_LOG_URLS" ] || CONFIG_LOG_URLS="${env.BUILD_URL}/artifact/.ci.${archPrefix}.config.log.gz"
        echo "...like \${CONFIG_LOG_URLS}"
    fi
  fi
  if [ -s config.nut_report_feature.log ] || [ -n "`ls -1 .ci.${archPrefix}.config.nut_report_feature.log.gz .ci.${archPrefix}.*_config_nut_report_feature.log.gz 2>/dev/null`" ]; then
    echo "...e.g. a (renamed, compressed) copy of config.nut_report_feature.log for this build"
    if [ -n "${archPrefix}" ] && [ "${archPrefix}" != null ] ; then
        CONFIG_LOG_URLS=""
        for C in .ci.${archPrefix}.config.nut_report_feature.log.gz .ci.${archPrefix}.*_config_nut_report_feature.log.gz ; do
            [ -s "\$C" ] && CONFIG_LOG_URLS="\$CONFIG_LOG_URLS ${env.BUILD_URL}/artifact/\$C"
        done
        [ -n "\$CONFIG_LOG_URLS" ] || CONFIG_LOG_URLS="${env.BUILD_URL}/artifact/.ci.${archPrefix}.config.nut_report_feature.log.gz"
        echo "...like \${CONFIG_LOG_URLS}"
    fi
  fi
) >&2
[ \$RES = 0 ] || exit \$RES
"""

    // Initial implementation just piped to `tee` but not all shells support
    // "pipefail" or similar tricks in a consistent manner.
//   return """ ${cmd} 2>&1 | (RES=\$? ; tee -a '${logfile}' || RES=\$? ; exit \$RES )
//"""

    // The original implementation was to just run the command -
    // but warnings-ng only had the whole job log to parse then
//   return """ ${cmd}
//"""

}

