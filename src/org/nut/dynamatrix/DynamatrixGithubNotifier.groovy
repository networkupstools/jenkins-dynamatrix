package org.nut.dynamatrix;

import com.cloudbees.groovy.cps.NonCPS;
import com.cloudbees.jenkins.GitHubRepositoryName;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.UserRemoteConfig;
import hudson.plugins.git.util.BuildData;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMRevisionAction;
import jenkins.scm.api.SCMSource;
import org.jenkinsci.plugins.github_branch_source.GitHubSCMSource;
import org.jenkinsci.plugins.github_branch_source.PullRequestSCMRevision;
import org.kohsuke.github.GHCommitState;
import org.kohsuke.github.GHCommitStatus;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.PagedIterable;
import org.nut.dynamatrix.dynamatrixGlobalState;
import org.nut.dynamatrix.Utils;

import java.util.regex.Pattern;

/** Data and methods to create/update github status reports
 *  for Jenkins-Dynamatrix library.<br/>
 *
 *  Relies on https://github.com/jenkinsci/github-plugin
 *   and https://github.com/hub4j/github-api/
 */
class DynamatrixGithubNotifier {
    /** When we rebuild/replay a job for the same commit, there can be
     *  pre-existing reports about a failed dynamatrix stage, e.g.
     *  <pre>
     *    <b><u>slowbuild-run/MATRIX_TAG="mingw-ubuntu-plucky-make-shellcheck"</u></b>
     *    — 'slow build' stage for ...
     *  </pre>
     *  which can not be removed via GitHub API, only replaced.
     *  At the start of a build we can list known statuses and replace
     *  them as "pending", to eventually replace with a "success" (which
     *  we do not normally report, as there would be too many entries).<br/>
     *
     *  This would also let developers know if a stage that failed with
     *  an earlier iteration was not revisited in any later build of same
     *  commit.  Since there can be crafted replays for just the failed
     *  build agents etc., we do not reset known successful reports.<br/>
     *
     *  Maps [repo][sha][context]=>state<br/>
     *
     *  We currently claim statuses whose context matches
     *   {@link #patternOurGithubStatusContexts} as our own.
     */
    final Map<GHRepository, Map<String, Map<String, GHCommitState>>> preexistingGithubStatusContexts = new HashMap<>()

    /** Used for {@link #preexistingGithubStatusContexts}<br/>
     *
     *  We currently claim statuses whose context includes substring
     *  {@code /MATRIX_TAG="} as our own.
     */
    final Pattern patternOurGithubStatusContexts = Pattern.compile(/.*\/MATRIX_TAG=".*/)

    private def script
    private static DynamatrixGithubNotifier defaultInstance = null
    private static final defaultInstanceSync = new Object()

    DynamatrixGithubNotifier(def script) {
        this.script = script
    }

    /** Getter of {@link #defaultInstance} for most callers;
     *  also setter/creator of that instance for pipeline init
     *  (or any first call with non-null {@code script}
     *  argument).<br/>
     *
     *  May return {@code null} if not initialized yet
     *  and called as plain {@code get()}!
     */
    @NonCPS
    static DynamatrixGithubNotifier get(def script = null) {
        // TOTHINK: Map of default instances per script?
        // Which to return for "script==null" aka get() then?
        synchronized(defaultInstanceSync) {
            if (defaultInstance == null && script != null) {
                defaultInstance = new DynamatrixGithubNotifier(script)
            }
            return defaultInstance
        }
    }

    @NonCPS
    private def assertScript() {
        if (!script) {
            throw new UnsupportedOperationException("This DynamatrixGithubNotifier was not yet initialized with a pipeline script context")
            //return null
        }
        return script
    }

    private def echo(def message) {
        return script?.echo(message)
    }

    /** Inspect SCM info associated with "currentBuild" and specified stashName
     *  (may be null) to pick out values we can use for notifications about the
     *  tested code base (assuming a repository which is not "dynamatrix" itself.
     *
     * @see #reportGithubStageStatus
     */
    def getNotificationContext(
        def stashName
    ) {
        assertScript()
        def currentBuild = script.currentBuild
        def scm = script.scm

        boolean doDebug = (
            dynamatrixGlobalState.enableDebugTraceGithubStatusHighlights
                || (dynamatrixGlobalState.enableDebugTrace && dynamatrixGlobalState.enableDebugTraceGithubStatusHighlights != false)
        )

        if (doDebug) {
            echo "[DEBUG] getNotificationContext called; dynamatrixGlobalState.enableGithubStatusHighlights=${dynamatrixGlobalState.enableGithubStatusHighlights}, stashName=${stashName}"
        }

        if (dynamatrixGlobalState.enableGithubStatusHighlights) {
            try {
                String stashNameUsed = null
                Map scmVars = null
                // If this was a first report before/during checkout, so info about
                // it was not stashed yet and we had to discover git information
                // elsewhere - cache that. In particular, this may help "against"
                // pull requests rebuilt automatically as a "merge" job with an
                // ephemeral commit (after the target branch marches on due to
                // other development), and the actual stashed info about the git
                // workspace checked out and recorded would have a tip commit hash
                // unknown to github. So we actually prefer that info if available.
                if (stashName != null) {
                    stashNameUsed = stashName + ":getNotificationContext-orig"
                    scmVars = DynamatrixStash.getSCMVars(stashNameUsed)
                }
                if (scmVars == null) {
                    stashNameUsed = stashName
                    scmVars = DynamatrixStash.getSCMVars(stashNameUsed)
                }
                String scmCommit = scmVars?.GIT_COMMIT
                String scmURL = scmVars?.GIT_URL

                // Most of the time cached info is present, except
                // early in the job run - while the git checkout
                // and stash operation runs in a parallel stage.
                // The cached SCMVars only become known after the
                // appearance of that git workspace, but we send
                // some statuses before that.
/*
    // INVESTIGATION HACKS (via scripting console)
    def j = Jenkins.instance.getItemByFullName("nut/nut/fightwarn")
    println "job: ${j}"
    def build = j.getBuildByNumber(117)

    println build

    def commitHashForBuild(build) {
      def scmAction = null
      build?.actions.each { action ->
        println "action: <${action?.getClass()}>'${action}'"
        if (action instanceof jenkins.scm.api.SCMRevisionAction)
            scmAction = action
      }
      println "scmAction: <${scmAction?.getClass()}>'${scmAction}'"
      println "scmAction.revision: <${scmAction?.revision?.getClass()}>'${scmAction?.revision}'"
      println "scmAction.revision.hash: <${scmAction?.revision?.hash?.getClass()}>'${scmAction?.revision?.hash}'"
      return scmAction?.revision?.hash
    }

    println commitHashForBuild(build)
*/
                if (scmVars == null) {
                    // At least if there's just one SCMSource attached
                    // to the job definition, we can do this (TOCHECK:
                    // what if there are many sources in definition?):
                    SCMSource src = SCMSource.SourceByItem.findSource(currentBuild.rawBuild.getParent())
                    SCMRevision revision = (src != null ? SCMRevisionAction.getRevision(src, currentBuild.rawBuild) : null)

                    if (doDebug) {
                        echo "[DEBUG] getNotificationContext discovering from SCMSource=${Utils.castString(src)} and SCMRevision=${Utils.castString(revision)}"
                    }

                    if (src != null && revision != null
                        && revision instanceof PullRequestSCMRevision
                        && src instanceof GitHubSCMSource
                    ) {
                        // PRs are "<source>+<target> (<ephemeral>)", e.g.
                        // 1aaff29c6706228a1fcae1c933e611f8b6aad441+5dc7970253626986815d79c5c7fa295bf221c876 (2259e6ff57cfb00864beb056f1720bab28cd0a64)
                        String s = (revision.toString().trim() - ~/\+.*$/).trim()
                        scmCommit = s

                        // Use reflection to avoid casting and required
                        // imports and thus plugins installed
                        def methodName = "getRepositoryUrl"
                        scmURL = src."$methodName"()
                    }
                }

                if (scmURL == null && scm != null && scm instanceof GitSCM) {
                    for (UserRemoteConfig c : scm.getUserRemoteConfigs()) {
                        if (doDebug) {
                            echo "[DEBUG] getNotificationContext discovering from scm.getUserRemoteConfigs()): ${Utils.castString(c)}"
                        }

                        if (!("dynamatrix" in c.getUrl())) {
                            scmURL = c.getUrl()
                            //scmCommit = ... ?
                        }
                    }

                    if (scmURL != null && doDebug) {
                        echo "[DEBUG] getNotificationContext discovered for scmURL from scm.getUserRemoteConfigs()): ${scmURL}"
                    }
                }

                if (scmCommit == null) {
                    Set scmRevisionActions = []
                    Set scmBuildDataActions = []
                    currentBuild?.rawBuild?.actions?.each { def action ->
                        if (action instanceof SCMRevisionAction) {
                            // action: <class jenkins.scm.api.SCMRevisionAction>'jenkins.scm.api.SCMRevisionAction@4d7f0122'
                            def hash = action?.revision?.hash
                            if (hash)
                                scmRevisionActions << hash
                            return
                        }

                        if (action instanceof BuildData) {
                            // Be sure to use the tested application project repo, not the dynamatrix library one!
                            // action: <class hudson.plugins.git.util.BuildData>'hudson.plugins.git.util.BuildData@1e28ec57[scmName=,remoteUrls=[https://github.com/networkupstools/jenkins-dynamatrix.git],buildsByBranchName={fightwarn=Build #117 of Revision 324a60a8c515e12e246b3e2dd6006a2e24b92163 (fightwarn), gitcache=Build #73 of Revision 37befb64cf2c1050ee52e953b31f66131b3cdf50 (gitcache), master=Build #109 of Revision 1ebcfae07883a03683f6fd4f9e4f7b57af874b33 (master)},lastBuild=Build #117 of Revision 324a60a8c515e12e246b3e2dd6006a2e24b92163 (fightwarn)]'
                            // action: <class hudson.plugins.git.util.BuildData>'hudson.plugins.git.util.BuildData@ba407b9a[scmName=,remoteUrls=[https://github.com/networkupstools/nut.git],buildsByBranchName={fightwarn=Build #117 of Revision 971f53ef95cb275bae22a691106dff867bdb4c2c (fightwarn)},lastBuild=Build #117 of Revision 971f53ef95cb275bae22a691106dff867bdb4c2c (fightwarn)]'
                            if (!(action.hasBeenBuilt(null)))
                                return

                            Boolean wrongRepo = false
                            action?.getRemoteUrls()?.each {
                                if (wrongRepo) return
                                if (it?.toLowerCase() ==~ /.*dynamatrix.*/)
                                    wrongRepo = true
                                if (scmURL != null && it?.toLowerCase() != scmURL.toLowerCase())
                                    wrongRepo = true
                            }
                            if (wrongRepo)
                                return

                            def hash = action?.lastBuild?.SHA1?.toString()
                            if (hash)
                                scmBuildDataActions << hash
                            return
                        }

                        // CAN'T USE (without reflection): package-private final class MultiSCMRevisionState extends hudson.scm.SCMRevisionState
                        // action: <class org.jenkinsci.plugins.workflow.steps.scm.MultiSCMRevisionState>'MultiSCMRevisionState{git https://github.com/networkupstools/jenkins-dynamatrix.git=hudson.scm.SCMRevisionState$None@739f26cc, git https://github.com/networkupstools/nut.git=hudson.scm.SCMRevisionState$None@739f26cc}'

                        // action: <class io.jenkins.plugins.forensics.git.reference.GitCommitsRecord>'Commits in 'nut/nut/fightwarn #117': 5 (latest: 324a60a8c515e12e246b3e2dd6006a2e24b92163)'
                        // action: <class io.jenkins.plugins.forensics.git.reference.GitCommitsRecord>'Commits in 'nut/nut/fightwarn #117': 200 (latest: 971f53ef95cb275bae22a691106dff867bdb4c2c)'

                        // TOTHINK: Can we tap into discoveries of the competitors? :)
                        // action: <class io.jenkins.plugins.checks.github.GitHubChecksAction>'io.jenkins.plugins.checks.github.GitHubChecksAction@366e9872'
                        // action: <class org.jenkinsci.plugins.githubautostatus.BuildStatusAction>'org.jenkinsci.plugins.githubautostatus.BuildStatusAction@2b24af81'

                        if (Utils.isListNotEmpty(scmBuildDataActions) && scmBuildDataActions.size() == 1) {
                            scmCommit = scmBuildDataActions[0]
                        } else {
                            if (Utils.isListNotEmpty(scmRevisionActions) && scmRevisionActions.size() == 1) {
                                scmCommit = scmRevisionActions[0]
                            }
                        }

                        if (doDebug) {
                            echo("[DEBUG] getNotificationContext discovered for scmCommit: " +
                                "scmBuildDataActions=${scmBuildDataActions} " +
                                "scmRevisionActions=${scmRevisionActions} " +
                                (scmCommit == null ? "and could not pick exactly one" : "and picked ${scmCommit}"
                                )
                            )
                        }
                    }
                }

                if (scmCommit == null) {
                    scmCommit = script.env?.GIT_COMMIT
                }

/*
                // TODO: consider pr/1979/head or pull/1979/head - note
                //  this may march on while the build is running?
                if (scmCommit == null && script.env?.BRANCH_NAME ==~ /^PR-[0-9]+$/ ) {
                    scmCommit = script.env?.GIT_COMMIT
                }
*/

                if (scmURL == null) {
                    scmURL = script.env?.GIT_URL
                }

                if (Utils.isStringNotEmpty(scmURL)
                 && Utils.isStringNotEmpty(scmCommit)
                ) {
                    // See comments above
                    if (scmVars == null && stashName != null) {
                        String scmVarsKey = "${stashName}:getNotificationContext-orig"
                        scmVars = DynamatrixStash.getSCMVarsPrivate()
                        if (!(scmVars.containsKey(scmVarsKey))) {
                            scmVars[scmVarsKey] = [:]
                        }
                        scmVars[scmVarsKey]['GIT_COMMIT'] = scmCommit
                        scmVars[scmVarsKey]['GIT_URL'] = scmURL
                        stashNameUsed = scmVarsKey
                    }
                }

                Map result = [
                    stashNameOrig   : stashName,
                    stashNameUsed   : stashNameUsed,
                    scmVars         : scmVars,
                    scmCommit       : scmCommit,
                    scmURL          : scmURL,
                ]
                if (doDebug) {
                    echo "[DEBUG] getNotificationContext discovery results: ${Utils.castString(result)}"
                }
                return result
            } catch (Throwable t) {
                echo "WARNING: Tried to getNotificationContext() but got an exception; is github-plugin installed and configured (or we may have a trouble with build agent)?"

                if (doDebug) {
                    echo t.toString()
                }

                return null
            }
        }

        return null
    }

    /**
     * Optional reporter of github status events which allows to trace certain
     * but not all stages or situations (e.g. only failures of matrix cells).
     * For Git URL/commit references uses DynamatrixStash.getSCMVars() cached data.
     * Inspired by https://github.com/jenkinsci/github-plugin README examples,
     * https://github.com/jenkinsci/github-plugin/blob/master/src/main/java/org/jenkinsci/plugins/github/status/GitHubCommitStatusSetter.java
     * and https://github.com/jenkinsci/github-branch-source-plugin/blob/master/src/main/java/org/jenkinsci/plugins/github_branch_source/GitHubBuildStatusNotification.java#L337
     * sources. See also https://docs.github.com/rest/commits/statuses#create-a-commit-status
     * https://www.jenkins.io/doc/pipeline/steps/github/#stepclass-githubcommitstatussetter-set-github-commit-status-universal
     *
     * @see #getNotificationContext
     */
    def reportGithubStageStatus(
        def stashName,
        String message,
        String state,
        String messageContext = null,
        String backrefUrl = null
    ) {
        assertScript()

        // May throw an exception in case of failure on its own
        // Writes its own logs, including the returned result, if debug is enabled
        Map notificationContext = getNotificationContext(stashName)
        if (!(Utils.isMapNotEmpty(notificationContext))) {
            echo "WARNING: Tried to use GitHubCommitStatusSetter but getNotificationContext() returned a null or empty map"
            return null
        }

        boolean doDebug = (
            dynamatrixGlobalState.enableDebugTraceGithubStatusHighlights
                || (dynamatrixGlobalState.enableDebugTrace && dynamatrixGlobalState.enableDebugTraceGithubStatusHighlights != false)
        )

        // Honour the limit, 140 chars as last reported for this writing:
        //    Failed to update commit state on GitHub. Ignoring exception
        //    [{"message":"Validation Failed","errors":[{
        //     "resource":"Status","code":"custom","field":"description",
        //     "message":"description is too long (maximum is 140 characters)"}],
        // As of this writing, the plugin step does not return a value
        // in case of an error during posting, just logs it:
        // https://github.com/jenkinsci/github-plugin/blob/309cf75c74ba1f254b9fe09fc43b5d9e08956813/src/main/java/org/jenkinsci/plugins/github/status/GitHubCommitStatusSetter.java#L132
        if (message.length() > 140) {
            message = message.substring(0, 135) - ~/ [^\s]*$/ + "<...>"
        }

        try {
            Map stepArgs = [
                $class            : "GitHubCommitStatusSetter",
                errorHandlers     : [[$class: 'ShallowAnyErrorHandler']],
                //errorHandlers: [[$class: "ChangingBuildStatusErrorHandler", result: "UNSTABLE"]],
                statusResultSource: [
                    $class : "ConditionalStatusResultSource",
                    results: [[
                                  $class : "AnyBuildResult",
                                  message: message,
                                  state  : state
                              ]]
                ]
            ]

            if (Utils.isStringNotEmpty(notificationContext.scmURL) && Utils.isStringNotEmpty(notificationContext.scmCommit)) {
                stepArgs['reposSource'] = [$class: "ManuallyEnteredRepositorySource", url: notificationContext.scmURL]
                stepArgs['commitShaSource'] = [$class: "ManuallyEnteredShaSource", sha: notificationContext.scmCommit]
            }

            // e.g. "ci/jenkins/build-status", "integration" or "build"
            // re-use same context with different status or message as we progress from recognition of a codepath to its verdict
            // use different contexts for different practical job aspects, e.g. spellcheck vs shellcheck
            if (Utils.isStringNotEmpty(messageContext)) {
                stepArgs['contextSource'] = [$class: "ManuallyEnteredCommitContextSource", context: messageContext]
            }

            // e.g. "https://ci.networkupstools.org/job/nut/job/nut/job/PR-2063/69//artifact/.ci.MD5_899dfa229658900e3de07f19c790e888.check.log.gz"
            // Defaults to https://github.com/jenkinsci/github-plugin/blob/master/src/main/java/org/jenkinsci/plugins/github/status/sources/BuildRefBackrefSource.java
            // that uses the Display URL of the build (redirects to one of UI implementation pages)
            if (Utils.isStringNotEmpty(backrefUrl)) {
                stepArgs['statusBackrefSource'] = [$class: "ManuallyEnteredBackrefSource", backref: backrefUrl]
            }

            if (doDebug) {
                echo "[DEBUG] reportGithubStageStatus() with GitHubCommitStatusSetter step:\n\t" +
                    "stashName=${Utils.castString(stashName)}\n\t" +
                    "stashNameUsed=${Utils.castString(notificationContext.stashNameUsed)}\n\t" +
                    "scmVars=${Utils.castString(notificationContext.scmVars)}\n\t" +
                    "scmURL=${Utils.castString(notificationContext.scmURL)}\n\t" +
                    "scmCommit=${Utils.castString(notificationContext.scmCommit)}\n\t" +
                    "stepArgs=${Utils.castString(stepArgs)}"
            }

            script.step(stepArgs);
        } catch (Throwable t) {
            echo "WARNING: Tried to use GitHubCommitStatusSetter but got an exception; is github-plugin installed and configured (or we may have a trouble with build agent)?"

            if (doDebug) {
                echo t.toString()
            }

            // TOTHINK: Retry with github-autostatus-plugin?
        }
    }

    /** Query repo(s) (currently one non-"dynamatrix") associated with this
     *  currently running build to see if any statuses are already known,
     *  and match {@link #patternOurGithubStatusContexts} -- then populate
     *  them into {@link #preexistingGithubStatusContexts}.
     */
    def fetchKnownGithubStatuses() {
        assertScript()

        script.lock(resource: "dynamatrix:fetchKnownGithubStatuses-${defaultInstanceSync.hashCode()}".toString(), quantity: 1) {
            // May throw an exception in case of failure on its own
            // Writes its own logs, including the returned result, if debug is enabled
            Map notificationContext = getNotificationContext(null)
            if (!(Utils.isMapNotEmpty(notificationContext))) {
                echo "WARNING: Tried to fetchKnownGithubStatuses but getNotificationContext() returned a null or empty map"
                return null
            }

            boolean doDebug = (
                dynamatrixGlobalState.enableDebugTraceGithubStatusHighlights
                    || (dynamatrixGlobalState.enableDebugTrace && dynamatrixGlobalState.enableDebugTraceGithubStatusHighlights != false)
            )

            if (!(Utils.isStringNotEmpty(notificationContext.scmURL))
                || !(Utils.isStringNotEmpty(notificationContext.scmCommit))
            ) {
                echo "WARNING: Tried to fetchKnownGithubStatuses but getNotificationContext() returned no URL or Commit info"
                return null
            }

            GitHubRepositoryName ghRepoName = GitHubRepositoryName.create(notificationContext.scmURL)
            Iterable<GHRepository> ghRepos = ghRepoName?.resolve()
            if (ghRepos == null) {
                echo "WARNING: Tried to fetchKnownGithubStatuses but failed to resolve any GHRepository object"
                return null
            }

            ghRepos.each { GHRepository ghRepo ->
                if (doDebug) {
                    echo "[DEBUG] fetchKnownGithubStatuses: checking GHRepository: '${ghRepo}'"
                }

                PagedIterable<GHCommitStatus> repoCommitStatuses = ghRepo.listCommitStatuses(notificationContext.scmCommit)

                Boolean submapExists = false
                repoCommitStatuses.each { GHCommitStatus status ->
                    if (doDebug) {
                        echo "[DEBUG] fetchKnownGithubStatuses: checking GHCommitStatus: '${status}'"
                    }

                    String context = status.getContext()
                    if (!(context ==~ patternOurGithubStatusContexts)) {
                        if (doDebug) {
                            echo "[DEBUG] fetchKnownGithubStatuses: skip not \"our\" context: '${context}'"
                        }
                        return // continue
                    }

                    if (!submapExists) {
                        if (!(preexistingGithubStatusContexts.containsKey(ghRepo))) {
                            Map<String, Map<String, GHCommitState>> subMap = new HashMap<>()
                            preexistingGithubStatusContexts[ghRepo] = subMap
                        }

                        if (!(preexistingGithubStatusContexts[ghRepo].containsKey(notificationContext.scmCommit))) {
                            Map<String, GHCommitState> subMap = new HashMap<>()
                            preexistingGithubStatusContexts[ghRepo][notificationContext.scmCommit] = subMap
                        }

                        submapExists = true
                    }

                    preexistingGithubStatusContexts[ghRepo][notificationContext.scmCommit][context] = status.getState()
                }
            }
        }
    }

    /** For use early in a pipeline: Check if any statuses are already known
     *  as failed (from previous builds) and update them as "pending" */
    def neuterKnownUnsuccessfulGithubStatuses() {
        assertScript()

        if (preexistingGithubStatusContexts.isEmpty()) {
            fetchKnownGithubStatuses()
        }

        preexistingGithubStatusContexts.each { GHRepository repo, Map<String, Map<String, GHCommitState>> repoCommitMap ->
            repoCommitMap.each { String commitSha, Map<String, GHCommitState> commitStates ->
                commitStates.each { String context, GHCommitState state ->
                    if (state in [GHCommitState.ERROR, GHCommitState.FAILURE]) {
                        reportGithubStageStatus(null, "Should retry", "PENDING", context, null)
                    }
                }
            }
        }
    }

    /** Only calls {@link #reportGithubStageStatus} if {@code state}
     *  is not 'SUCCESS', or for successes -- if the {@code messageContext}
     *  is non-null and in {@link #preexistingGithubStatusContexts} */
    def updateGithubStageStatus(
        def stashName,
        String message,
        String state,
        String messageContext = null,
        String backrefUrl = null
    ) {
        GHCommitState ghCommitState = GHCommitState.valueOf(state)
        if (ghCommitState == GHCommitState.SUCCESS) {
            if (preexistingGithubStatusContexts.isEmpty()) {
                fetchKnownGithubStatuses()
            }

            boolean doDebug = (
                dynamatrixGlobalState.enableDebugTraceGithubStatusHighlights
                    || (dynamatrixGlobalState.enableDebugTrace && dynamatrixGlobalState.enableDebugTraceGithubStatusHighlights != false)
            )

            Boolean wasKnown = false
            preexistingGithubStatusContexts.each { GHRepository repo, Map<String, Map<String, GHCommitState>> repoCommitMap ->
                if (wasKnown)
                    return //continue
                repoCommitMap.each { String commitSha, Map<String, GHCommitState> commitStates ->
                    if (commitStates.containsKey(messageContext)) {
                        wasKnown = true
                    }
                }
            }

            if (!wasKnown) {
                if (doDebug) {
                    echo "[DEBUG] updateGithubStageStatus: context '${messageContext}' was not in preexistingGithubStatusContexts"
                }
                return null
            }
        }

        reportGithubStageStatus(stashName, message, state, messageContext, backrefUrl)
    }
}
