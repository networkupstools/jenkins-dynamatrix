// Steps should not be in a package, to avoid CleanGroovyClassLoader exceptions...
// package org.nut.dynamatrix;

import org.nut.dynamatrix.dynamatrixGlobalState;
import org.nut.dynamatrix.Dynamatrix;
import org.nut.dynamatrix.DynamatrixSingleBuildConfig;
import org.nut.dynamatrix.*;

import com.cloudbees.groovy.cps.NonCPS;
import hudson.model.Result;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;

/** This is a stage payload used by dynamatrixPipeline to
 *  generate the slow build matrix.
 *  It takes a {@link Dynamatrix} object and a configuration
 *  map as input and modifies some as sees fit (e.g. adds
 *  discovered stage sets into each map "sb" entry).<br/>
 *
 * The "slowBuild" is a set of Maps, each of them describes
 * a dynamatrix selection filter. Having a series of those
 * with conditions known to developer of the pipeline (and
 * project it represents) can be more efficient than making
 * a huge matrix of virtual or agent-driven labels and then
 * filtering away lots of "excludeCombos" from that.
 */
Map call(Dynamatrix dynamatrix, Map dynacfgPipeline, Set<String> changedFiles) {
    Map stagesBinBuild = [:]
    Integer countFiltersSeen = 0
    Integer countFiltersSkipped = 0

    if (dynacfgPipeline?.failFastSafe) {
        dynamatrix.failFast = (dynacfgPipeline?.failFast ? true : false)
        dynamatrix.mustAbort = false
    }

    dynamatrix.dynamatrixGithubNotificationContext = "slowbuild-run"
    dynamatrix.saveDynacfg()
    infra.reportGithubStageStatus(dynacfgPipeline.get("stashnameSrc"),
        'Discover slow build matrix',
        'PENDING', "slowbuild-discover")
    dynacfgPipeline.slowBuild.each { Map sb ->
        stage("Inspect SBF Cfg" + (sb?.name ? ": " + sb.name : "")) {
            if (dynamatrixGlobalState.enableDebugTrace) {
                echo "Inspecting a slow build filter configuration: " + Utils.castString(sb)
            } else if (sb?.name) {
                echo "Inspecting a slow build filter configuration: ${sb.name}"
            }

            sb.tuplesParStages = null
            sb.mapParStages = null
            if (!(Utils.isClosureNotEmpty(sb?.getParStages))) {
                if (dynamatrixGlobalState.enableDebugTrace || sb?.name)
                    echo "SKIP: No (valid) slow build filter definition in this entry" + (sb?.name ? ": " + sb.name : "")
                countFiltersSkipped++
                return // continue
            }

            // else: if getParStages is useful:
            countFiltersSeen++
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

                String _CHANGE_TARGET = null
                try {
                    // May be not defined
                    _CHANGE_TARGET = CHANGE_TARGET
                } catch (Throwable ignored) {
                    try {
                        // May be not defined
                        _CHANGE_TARGET = env.CHANGE_TARGET
                    } catch (Throwable ignore) {
                    }
                }

                if (Utils.isStringNotEmpty(_CHANGE_TARGET)
                && (!(_CHANGE_TARGET ==~ sb.branchRegexTarget))
                ) {
                    if (dynamatrixGlobalState.enableDebugTrace || sb?.name)
                        echo "SKIP: Target branch name '${_CHANGE_TARGET}' did not match the pattern ~/${sb.branchRegexTarget}/ for this slow build filter configuration" + (sb?.name ? ": " + sb.name : "")
                    countFiltersSkipped++
                    return // continue
                }

                if (!Utils.isStringNotEmpty(env?.CHANGE_TARGET)
                &&  !Utils.isStringNotEmpty(_CHANGE_TARGET)
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
            // waste of round-trip time and compute resources.
            if (Utils.isRegex(sb?.appliesToChangedFilesRegex)) {
                if (dynamatrixGlobalState.enableDebugTrace)
                    echo "[DEBUG] Analysing the changedFiles=${changedFiles.toString()} list against the pattern appliesToChangedFilesRegex='${sb.appliesToChangedFilesRegex.toString()}' ..."

                if (changedFiles.size() > 0) {
                    Boolean skip = true

                    for (String cf in changedFiles) {
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
            withEnv(["CI_SLOW_BUILD_FILTERNAME=" + ((sb?.name) ? sb.name.toString().trim() : "N/A")]) {
                // First we aim to collect tuples, so we remember the DSBC details
                // mapped to the stage name and closure, to dedup later:
                Boolean defaultBak = dynamatrix.generateBuildReturnSetDefault
                dynamatrix.generateBuildReturnSetDefault = true

                // Use unique clones of "dynamatrix.dynacfg" below,
                // to avoid polluting their applied dynacfg based
                // just on order of slowBuild scenario parsing;
                // typical sb.getParStages{} calls dynamatrix.generateBuild():
                dynamatrix.restoreDynacfg()
                def psRet
                if (Utils.isClosure(sb?.bodyParStages)) {
                    // body may be empty {}, if user wants so
                    psRet = sb.getParStages.call(dynamatrix, sb.bodyParStages)
                } else {
                    if (Utils.isClosure(dynacfgPipeline?.slowBuildDefaultBody)) {
                        psRet = sb.getParStages.call(dynamatrix, dynacfgPipeline.slowBuildDefaultBody)
                    } else {
                        psRet = sb.getParStages.call(dynamatrix, null)
                    }
                }
                dynamatrix.generateBuildReturnSetDefault = defaultBak

                if (psRet != null) {
                    if (psRet instanceof Set) {
                        if (psRet.empty)
                            echo "WARNING: sb.getParStages{} returned an empty Set" + (sb?.name ? " for: " + sb.name : "")
                        else
                            echo "INFO: sb.getParStages{} returned a Set with ${psRet.size()} entries" + (sb?.name ? " for: " + sb.name : "")

                        sb.tuplesParStages = psRet
                        sb.mapParStages = [:]
                        sb.tuplesParStages.each { List tup -> sb.mapParStages[(String) (tup[0])] = (Closure) (tup[1]) }
                    } else if (psRet instanceof Map) {
                        if (psRet.empty)
                            echo "WARNING: sb.getParStages{} returned an empty Map" + (sb?.name ? " for: " + sb.name : "")
                        else
                            echo "INFO: sb.getParStages{} returned a Map with ${psRet.size()} entries" + (sb?.name ? " for: " + sb.name : "")

                        sb.mapParStages = psRet
                    } else {
                        echo "WARNING: sb.getParStages{} returned an unexpected type" + (sb?.name ? " for: " + sb.name : "")
                    }
                } else {
                    echo "WARNING: sb.getParStages{} returned null" + (sb?.name ? " for: " + sb.name : "")
                }
            }
        } // stage for one SBF Cfg
    } // dynacfgPipeline.slowBuild.each { sb -> ... }

    // TODO: Analyze collected scenarios for effective duplicates, remove extras
    // stage('Dedup effectively same scenarios') { ... }

    stage('Produce final result') {
        // Update the ultimate `parallel stagesBinBuild` contents:
        Map stageNameToDSBC = [:]
        dynacfgPipeline.slowBuild.each { Map sb ->
            if (!(sb?.mapParStages))
                return // continue

            stagesBinBuild += sb.mapParStages

            try {
                sb.tuplesParStages?.each { List tup ->
                    String stageName = (String) (tup[0])
                    if (!(stageNameToDSBC.containsKey(stageName))) {
                        stageNameToDSBC[stageName] = new HashSet<>()
                    }
                    stageNameToDSBC[stageName] << (DynamatrixSingleBuildConfig) tup[2]
                }
            } catch (Throwable t) {
                echo "FAILED to collect stageNameToDSBC, ignoring: ${t}"
            }
        }

        String sbSummarySuffix = "'slow build' configurations over ${countFiltersSeen} filter definition(s) tried " +
            "(${countFiltersSkipped} dynacfgPipeline.slowBuild elements were skipped due to build circumstances or as invalid)"
        String sbSummary = null
        String sbSummaryCount = "" // non-null string in any case
        if (stagesBinBuild.size() == 0) {
            sbSummary = "Did not discover any ${sbSummarySuffix}"
            // Limited by 140 chars
            infra.reportGithubStageStatus(dynacfgPipeline.get("stashnameSrc"),
                "Did not discover any " +
                    "'slow build' configurations over " +
                    "${countFiltersSeen} filter " +
                    "definition(s) tried",
                "SUCCESS", // nothing blew up?.. //( (stagesBinBuild.size() == 0) ? 'FAILURE' : 'SUCCESS'),
                "slowbuild-discover")
        } else {
            sbSummary = "Discovered ${stagesBinBuild.size()} ${sbSummarySuffix}"
            infra.reportGithubStageStatus(dynacfgPipeline.get("stashnameSrc"),
                "Discovered ${stagesBinBuild.size()} " +
                    "'slow build' configurations over " +
                    "${countFiltersSeen} filter " +
                    "definition(s) tried",
                "SUCCESS",
                "slowbuild-discover")
            dynacfgPipeline.slowBuild.each { Map sb ->
                if (sb?.mapParStages) {
                    // Note: Char sequence at start of string is parsed for badge markup below
                    sbSummaryCount += "\n\t* ${sb.mapParStages.size()} hits for: " +
                        (Utils.isStringNotEmpty(sb?.name) ? sb.name : Utils.castString(sb))
                }
            }

            try {
                // TODO: Something similar but with each stage's
                //  own buildResult verdicts after the build...
                String txt = "${sbSummary}\nfor this run ${env?.BUILD_URL} :\n\n"
                // This maps String (stage name) to Closure, list these names:
                stagesBinBuild.keySet().sort().each {
                    txt += "${it}"
                    if (stageNameToDSBC.containsKey(it))
                        txt += "\nDSBC details : ${stageNameToDSBC[it]}"
                    txt += "\n\n"
                }
                txt += sbSummaryCount
                writeFile(file: ".ci.slowBuildStages-list.txt", text: txt)
                archiveArtifacts(artifacts: ".ci.slowBuildStages-list.txt", allowEmptyArchive: true)

                try {
                    def sumText = "Saved the list of slowBuild stages into a text artifact " +
                        "<a href='${env.BUILD_URL}/artifact/.ci.slowBuildStages-list.txt'>.ci.slowBuildStages-list.txt</a>"
                    def sumIcon = '/images/svgs/notepad.svg'    // '/images/48x48/notepad.png'
                    try {
                        // Badge API v2.x; TOTHINK: Use ioicons not images URI?
                        addSummary(text: sumText, icon: sumIcon)
                    } catch (Throwable ignored) {
                        // Older Badge API
                        createSummary(text: sumText, icon: sumIcon)
                    }
                } catch (Throwable ts) {
                    echo "WARNING: Tried to createSummary(), but failed to; is the jenkins-badge-plugin installed?"
                    if (dynamatrixGlobalState.enableDebugTrace) echo ts.toString()
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
            try {
                // Badge v2.x API, with style
                addInfoBadge(text: sbSummary, id: "Discovery-counter",
                    cssClass: "badge-jenkins-dynamatrix-Baseline badge-jenkins-dynamatrix-QuickTest-DiscoveryCounter"
                )
            } catch (Throwable ignored) {
/*
                try {
                    addInfoBadge(text: sbSummary, id: "Discovery-counter")
                } catch (Throwable ignored2) {
                    // NOTE: Was temporarily used INSTEAD of badge plugin (1.x) code
*/
                    // FIXME: While we add temporarily and remove one badge,
                    //  GPBP is okay (for some reason, Badge plugin leaves
                    //  ugly formatting in job's main page with list of builds):
                    manager.addInfoBadge(sbSummary)
/*
                }
*/
            }

            // Add a line to the build's info page too (note the
            // path here is somewhat relative to /static/hexhash/
            // that Jenkins adds):
            if (sbSummaryCount != "") {
                // Note: replace goes by regex so '\*'
                sbSummaryCount = sbSummaryCount.replaceAll('\n\t\\* ', '</li><li>').replaceFirst('</li>', '<p>Detailed hit counts:<ul>') + '</li></ul></p>'
            }

            def sumText = sbSummary + sbSummaryCount
            def sumIcon = '/images/svgs/notepad.svg'    // '/images/48x48/notepad.png'
            try {
                // Badge API v2.x; TOTHINK: Use ioicons not images URI?
                addSummary(text: sumText, icon: sumIcon)
            } catch (Throwable ignored) {
                // Older Badge API
                createSummary(text: sumText, icon: sumIcon)
            }
        } catch (Throwable t) {
            echo "WARNING: Tried to addInfoBadge() and createSummary(), but failed to; is the jenkins-badge-plugin installed?"
            if (dynamatrixGlobalState.enableDebugTrace) echo t.toString()
        }

        try {
            // Badge v2.x API, with style
            addBadge(text: sbSummary + "; waiting for quick-tests to complete",
                cssClass: "badge-jenkins-dynamatrix-Baseline badge-jenkins-dynamatrix-QuickTest-WaitingCompletion"
            )
        } catch (Throwable ignored) {
            try {
                manager.addShortText(sbSummary + "; waiting for quick-tests to complete")
            } catch (Throwable ignore) {
            }   // no-op
        }
    } // stage('Produce final result')

    return stagesBinBuild
}
