package org.nut.dynamatrix;

import com.cloudbees.groovy.cps.NonCPS;
import hudson.AbortException;
import hudson.model.Result;
import hudson.remoting.RequestAbortedException;
import hudson.remoting.RemotingSystemException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.*;

import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException;

import org.nut.dynamatrix.DynamatrixConfig;
import org.nut.dynamatrix.DynamatrixSingleBuildConfig;
import org.nut.dynamatrix.NodeCaps;
import org.nut.dynamatrix.NodeData;
import org.nut.dynamatrix.Utils;
import org.nut.dynamatrix.dynamatrixGlobalState;

/**
 * This class intends to represent one build matrix which can be used
 * to produce several "closely related" build stages re-using the same
 * set of build agents and project configurations.
 */
class Dynamatrix implements Cloneable {
    /**
     * Class-shared cache of the collected {@link #nodeCaps} per label
     * expression, so we only query Jenkins core once for those, even
     * if we prepare many build scenarios.
     */
    private static Map<String, NodeCaps> nodeCapsCache = [:]

    /** Have some defaults, if only to have all expected fields defined */
    private DynamatrixConfig dynacfg
    /** Have some defaults, if only to have all expected fields defined */
    private DynamatrixConfig dynacfgSaved = null
    /** Have some defaults, if only to have all expected fields defined */
    private def script
    /** Have some defaults, if only to have all expected fields defined */
    private final String objectID = Integer.toHexString(hashCode())
    /** Have some defaults, if only to have all expected fields defined */
    public boolean enableDebugTrace = dynamatrixGlobalState.enableDebugTrace
    /** Have some defaults, if only to have all expected fields defined */
    public boolean enableDebugTraceFailures = dynamatrixGlobalState.enableDebugTraceFailures
    /** Have some defaults, if only to have all expected fields defined */
    public boolean enableDebugErrors = dynamatrixGlobalState.enableDebugErrors
    /** Have some defaults, if only to have all expected fields defined */
    public boolean enableDebugMilestones = dynamatrixGlobalState.enableDebugMilestones
    /** Have some defaults, if only to have all expected fields defined */
    public boolean enableDebugMilestonesDetails = dynamatrixGlobalState.enableDebugMilestonesDetails
    /** Have some defaults, if only to have all expected fields defined */
    public boolean enableDebugSysprint = dynamatrixGlobalState.enableDebugSysprint

    /**
     * Store values populated by {@link #prepareDynamatrix} so that a further
     * {@link #generateBuild} and practical {@link #generateBuildConfigSet} calls
     * can use these quickly.
     */
    private NodeCaps nodeCaps

    /**
     * The following Sets contain different levels of processing of data about
     * build agent capabilities proclaimed in their agent labels (via {@link #nodeCaps})
     */
    private Set effectiveAxes = []
    /**
     * The following Sets contain different levels of processing of data about
     * build agent capabilities proclaimed in their agent labels (via {@link #nodeCaps})
     */
    private Set buildLabelCombos = []
    /**
     * The following Sets contain different levels of processing of data about
     * build agent capabilities proclaimed in their agent labels (via {@link #nodeCaps})
     */
    private Set buildLabelCombosFlat = []

    /**
     * This is one useful final result, mapping strings for
     * @{code agent{label 'expr'}} clauses to arrays of label contents
     * (including "composite" labels where @{code key=value}'s are
     * persistently grouped, e.g. @{code "COMPILER=GCC GCCVER=123"},
     * and the original label set e.g. @{code "nut-builder"} used to
     * initialize the set of agents this dynamatrix is interested in).
     */
    private Map<String, Set> buildLabelsAgents = [:]

///////////////////////////////// RESULTS ACCOUNTING ///////////////////

    /**
     * Similar to Jenkins parallel() step's support for aborting builds if
     * a stage fails, but this implementation allows to let already running
     * stages complete. Technically depends on the limited amount of build
     * nodes, so we get build diagnostics from scenarios we have already
     * invested a node into, but would not waste much firepower after we
     * know we failed.<br/>
     * This would still block to get a node{} first, and quickly release
     * it just then as we have the {@link #mustAbort} flag raised.
     */
    public Boolean failFast = null
    /**
     * A flag which helps {@link #failFast} mode track that the current
     * build should wrap up quickly now.
     */
    public boolean mustAbort = false

    // TODO: Derive a class from Jenkins standard Result, with
    //  dynamatrix-specific values added, to simplify usage here.
    /**
     * Track the worst result of all executed dynamatrix stages.
     * @see #getWorstResult
     * @see <a href="https://javadoc.jenkins.io/hudson/model/class-use/Result.html">https://javadoc.jenkins.io/hudson/model/class-use/Result.html</a>
     * @see <a href="https://javadoc.jenkins-ci.org/hudson/model/Result.html">https://javadoc.jenkins-ci.org/hudson/model/Result.html</a>
     */
    private Result dmWorstResult = null

    /**
     * Report the worst result of all executed dynamatrix stages.
     *
     * @see #setWorstResult(String)
     * @see #setWorstResult(String, String)
     * @see #resultFromString
     */
    public Result getWorstResult() { return dmWorstResult }

    /**
     * Count each type of verdict.<br/>
     * Predefine the Map so its print-out happens in same order as in
     * the {@code toString*()} methods defined in the class.
     *
     * @see #toStringStageCount
     * @see #toStringStageCountNonZero
     * @see #toStringStageCountDump
     * @see #toStringStageCountDumpNonZero
     */
    private Map<String, Integer> countStages = [
        'STARTED': 0,
        'RESTARTED': 0,
        'COMPLETED': 0,
        'ABORTED_SAFE': 0,
        'SUCCESS': 0,
        'FAILURE': 0,
        'UNSTABLE': 0,
        'ABORTED': 0,
        'NOT_BUILT': 0
        ]

    /**
     * For each {@code stageName} (map key), track its {@link Result}
     * object value (if set by stage payload)
     */
    private Map<String, Result> trackStageResults = [:]
    /** Plaintext or shortened-hash names of log files and other data saved for stage */
    private Map<String, String> trackStageLogkeys = [:]

    /**
     * Convert a {@link String} into a {@link Result} with added
     * consideration for values defined by the dynamatrix ecosystem.
     * May return {@code null} for states which do not map into
     * a Jenkins standard Result value.
     * @param k A String key, with either one of Jenkins standard
     *  {@link Result} values, or a dynamatrix state machine value:
     *  ['STARTED', 'RESTARTED', 'COMPLETED', 'ABORTED_SAFE']
     * @return  A {@link Result} constant, or {@code null}.
     *
     * @see #getWorstResult
     * @see #setWorstResult(String)
     * @see #setWorstResult(String, String)
     */
    @NonCPS
    public static Result resultFromString(String k) {
        Result r = null
        try {
            switch (k) {
                case ['STARTED', 'RESTARTED', 'COMPLETED']: break;
                case 'ABORTED_SAFE':
                    r = Result.fromString('ABORTED')
                    break
                default:
                    r = Result.fromString(k)
                    break
            }
        } catch (Throwable t) {
            r = null
        }
        return r
    }

    /**
     * Assign the currently tracked worst result among recently executed
     * dynamatrix stages. The verdict may not improve.
     *
     * @param k A String key, with either one of Jenkins standard
     *  {@link Result} values, or a dynamatrix state machine value:
     *  ['STARTED', 'RESTARTED', 'COMPLETED', 'ABORTED_SAFE']
     * @return  Current value of {@link #dmWorstResult} after the
     *  assignment (a {@link Result}, may be {@code null}).
     *
     * @see #getWorstResult
     * @see #resultFromString
     * @see #setWorstResult(String, String)
     */
    @NonCPS
    synchronized public Result setWorstResult(String k) {
        Result r = resultFromString(k)
        if (r != null) {
            if (this.dmWorstResult == null) {
                this.dmWorstResult = r
            } else {
                this.dmWorstResult = this.dmWorstResult.combine(r)
            }
        }

        return this.dmWorstResult
    }

    /**
     * Similar to {@link #setWorstResult(String)}, but also populates
     * {@link #trackStageResults} for stage name "sn" with the verdict
     * from "k".
     *
     * @param k A String key, with either one of Jenkins standard
     *  {@link Result} values, or a dynamatrix state machine value:
     *  ['STARTED', 'RESTARTED', 'COMPLETED', 'ABORTED_SAFE']
     * @param sn A String stage name, used as a key in the
     *  {@link #trackStageResults} map.
     * @return  Current value of verdict for the stage name after the
     *  assignment (a {@link Result}, may be {@code null}).
     *
     * @see #getWorstResult
     * @see #resultFromString
     * @see #setWorstResult(String)
     */
    @NonCPS
    synchronized public Result setWorstResult(String sn, String k) {
        Result res = this.setWorstResult(k)

        if (sn != null) {
            Result r = resultFromString(k)
            if (r != null) {
                if (!this.trackStageResults.containsKey(sn)
                ||   this.trackStageResults[sn] == null
                ) {
                    // Code might have already saved a result by another key
                    // ("stageName" vs "stageName :: sbName") which is either
                    // a sub-set or super-set of the "sn". We want to keep
                    // the longer version to help troubleshooting.
                    String trackedSN = null
                    this.trackStageResults.each {String tsk, Result tsr ->
                        if (tsk.startsWith(sn) || sn.startsWith(tsk)) {
                            trackedSN = tsk
                            return true
                        }
                        return false
                    }

                    if (trackedSN == null) {
                        // Really a new entry
                        this.trackStageResults[sn] = r
                    } else {
                        // Key exists in table by another name we accept...
                        if (trackedSN.startsWith(sn)) {
                            // Table contains the longer key we want to keep - update it
                            this.trackStageResults[trackedSN] = this.trackStageResults[trackedSN].combine(r)
                        } else { // sn.startsWith(trackedSN)
                            // Table contains the shorter key - use it and forget it
                            this.trackStageResults[sn] = this.trackStageResults[trackedSN].combine(r)
                            this.trackStageResults.remove(trackedSN)
                        }
                    }
                } else {
                    // Key exists in table
                    this.trackStageResults[sn] = this.trackStageResults[sn].combine(r)
                }
            }
            res = this.trackStageResults[sn]
        }

        return res
    }

    /**
     * Save a "log key" (plaintext or shortened-hash names of log
     * files and other data saved for stage) for a "stage name".
     *
     * @see #trackStageLogkeys
     * @see #getLogKey
     */
    @NonCPS
    synchronized public void setLogKey(String sn, String lk) {
        trackStageLogkeys[sn] = lk
    }

    /**
     * Return either direct hit, or startsWith (where the tail
     * would be description of the slowBuild scenario group).
     * May be null if no hit.
     *
     * @see #trackStageLogkeys
     * @see #setLogKey
     */
    @NonCPS
    synchronized public String getLogKey(String s) {
        if (trackStageLogkeys.containsKey(s)) {
            return trackStageLogkeys[s]
        }

        String k = null
        trackStageLogkeys.each {String sn, String lk ->
            if (Utils.isStringNotEmpty(sn)
            &&  (sn.startsWith(s) || s.startsWith(sn))
            ) {
                k = lk
                return true
            }
            return false // continue the search
        }
        return k // may be null if no hit
    }

    /**
     * Transposes {@link #trackStageResults} contents, to map each collected
     * {@link Result} values to a {@link Set} of stage names which had this
     * verdict.
     *
     * @return
     */
    @NonCPS
    synchronized public Map<Result, Set<String>> reportStageResults() {
        Map<Result, Set<String>> mapres = [:]
        this.trackStageResults.each {String sn, Result r ->
            if (!mapres.containsKey(r)) {
                mapres[r] = new HashSet<String>()
            }
            mapres[r] << sn
        }
        return mapres
    }

    /** @see #countStagesIncrement(String, String) */
    //@NonCPS
    synchronized public Integer countStagesIncrement(Result r, String sn = null) {
        return this.countStagesIncrement(r?.toString(), sn)
    }

    /**
     * Count each type of verdict (including Jenkins standard
     * {@link Result} values, and dynamatrix state machine values)
     * represented by "k" for a stage name "sn". Also sets the
     * known worst verdict for the dynamatrix overall, and the
     * tracked verdict for that stage name.
     *
     * @see #countStagesIncrement(Result, String)
     * @see #setWorstResult(String, String)
     * @see #countStages
     */
    //@NonCPS
    synchronized public Integer countStagesIncrement(String k, String sn = null) {
        this.script?.echo "countStagesIncrement(" + (k == null ? "<null>" : "'${k}'") + ", '${sn}')"
        if (k == null)
            k = 'UNKNOWN'
        this.setWorstResult(sn, k)
        if (this.countStages.containsKey(k)) {
            this.countStages[k] += 1
        } else {
            this.countStages[k] = 1
        }
        return this.countStages[k]
    }

    /** Helper to treat {@code null} {@link Integer} values as zeroes for counting */
    private Integer intNullZero(Integer i) { if (i == null) { return 0 } else { return i } }

    /** Reporting the accounted values:
     * We started the stage (maybe more than once) */
    public Integer countStagesStarted() { return intNullZero(countStages?.STARTED) + intNullZero(countStages?.RESTARTED) }
    /** Reporting the accounted values:
     * We restarted the stage */
    public Integer countStagesRestarted() { return intNullZero(countStages?.RESTARTED) }
    /** Reporting the accounted values:
     * We know we finished the stage, successfully or with "fex" exception caught */
    public Integer countStagesCompleted() { return intNullZero(countStages?.COMPLETED) }
    /** Reporting the accounted values:
     * We canceled the stage before start of actual work
     * (due to {@link #mustAbort}, after getting a node
     * to execute our logic on)
     */
    public Integer countStagesAbortedSafe() { return intNullZero(countStages?.ABORTED_SAFE) }
    /** Reporting the accounted values: Standard Jenkins build results */
    public Integer countStagesFinishedOK() { return intNullZero(countStages?.SUCCESS) }
    /** Reporting the accounted values: Standard Jenkins build results */
    public Integer countStagesFinishedFailure() { return intNullZero(countStages?.FAILURE) }
    /** Reporting the accounted values: Standard Jenkins build results */
    public Integer countStagesFinishedFailureAllowed() { return intNullZero(countStages?.UNSTABLE) }
    /** Reporting the accounted values: Standard Jenkins build results */
    public Integer countStagesAborted() { return intNullZero(countStages?.ABORTED) }
    /** Reporting the accounted values: Standard Jenkins build results */
    public Integer countStagesAbortedNotBuilt() { return intNullZero(countStages?.NOT_BUILT) }

    /**
     * Roll a new text entry in the build overview page.<br/>
     * Note that with current badge plugin releases,
     * we can't later remove or replace this entry,
     * like we can with {@link #updateProgressBadge}
     * for "yellow boxes".
     */
    // Must be CPS - calls pipeline script steps
    synchronized
    Boolean createSummary(String txt, String icon = 'info.gif', String objId = null) {
        Boolean res = null

        if (this.script && Utils.isStringNotEmpty(txt)) {
            try {
                this.script.createSummary(icon: icon, text: txt, id: "Build-progress-summary@" + (objId == null ? this.objectID : objId))
                if (res == null) res = true
            } catch (Throwable t) {
                this.script.echo "WARNING: Tried to createSummary() for 'Build-progress-summary@${this.objectID}', but failed to; are the Groovy Postbuild plugin and jenkins-badge-plugin installed?"
                if (this.shouldDebugTrace()) {
                    this.script.echo (t.toString())
                }
                res = false
            }
        }

        return res
    }

    /**
     * Roll a text entry in the "yellow boxes" of the left column (job build
     * current+history list) in the classic Jenkins user interface - page for
     * a job definition overview.<br/>
     *
     * This method populates the "yellow box" (identified by {@link #objectID})
     * with {@link #countStages} summarized by one of the {@code toString*()}
     * methods defined in this class (depending on which of them produces
     * non-trivial output).<br/>
     *
     * Note that with current badge plugin releases, we can usually remove or
     * replace these entries (with rare mis-fires), unlike what we can manage
     * with {@link #createSummary} for build overview page. Also unlike that
     * method, this one does not accept arbitrary text and other metadata
     * arguments.<br/>
     *
     * @see #toStringStageCount
     * @see #toStringStageCountNonZero
     * @see #toStringStageCountDump
     * @see #toStringStageCountDumpNonZero
     */
    // Must be CPS - calls pipeline script steps
    synchronized
    Boolean updateProgressBadge(Boolean removeOnly = false) {
        if (!this.script)
            return null

        try {
            this.script.removeBadges(id: "Build-progress-badge@" + this.objectID)
        } catch (Throwable tOK) { // ok if missing
            this.script.echo "WARNING: Tried to removeBadges() for 'Build-progress-badge@${this.objectID}', but failed to; are the Groovy Postbuild plugin and jenkins-badge-plugin installed?"
            if (this.shouldDebugTrace()) {
                this.script.echo (t.toString())
            }
        }

/*
        // Seems we can not "remove" a summary entry, despite what the docs say
        try {
            this.script.removeBadges(id: "Build-progress-summary@" + this.objectID)
        } catch (Throwable tOK) { // ok if missing
            this.script.echo "WARNING: Tried to removeBadges() for 'Build-progress-summary@${this.objectID}', but failed to; are the Groovy Postbuild plugin and jenkins-badge-plugin installed?"
            if (this.shouldDebugTrace()) {
                this.script.echo (t.toString())
            }
        }
*/
        if (removeOnly) return true

        // Stage finished, update the rolling progress via GPBP steps (with id)
        String txt = this.toStringStageCountNonZero()
        if (!(Utils.isStringNotEmpty(txt))) {
            txt = this.toStringStageCountDumpNonZero()
        }
        if (!(Utils.isStringNotEmpty(txt))) {
            txt = this.toStringStageCountDump()
        }
        if (!(Utils.isStringNotEmpty(txt))) {
            txt = this.toStringStageCount()
        }
        txt = "Build in progress: " + txt

        Boolean res = null
        try {
            // Note: not "addInfoBadge()" which is rolled-up and small (no text except when hovered)
            // Update: although this seems to have same effect, not that of addShortText (that has no "id")
            // Update2: checking with a "null" icon if that would work as addShortText in effect (seems so by build.xml markup)
            this.script.addBadge(icon: null, text: txt, id: "Build-progress-badge@" + this.objectID)
            res = true
        } catch (Throwable t) {
            this.script.echo "WARNING: Tried to addBadge() for 'Build-progress-badge@${this.objectID}', but failed to; are the Groovy Postbuild plugin and jenkins-badge-plugin installed?"
            if (this.shouldDebugTrace()) {
                this.script.echo (t.toString())
            }
            res = false
        }

/*
        def resSummary = this.createSummary(txt)
        if (res == null || resSummary == false) res = resSummary
*/

        return res
    }

////////////////////////// END OF RESULTS ACCOUNTING ///////////////////

    public Dynamatrix(Object script) {
        this.script = script
        this.dynacfg = new DynamatrixConfig(script)
        this.enableDebugTrace = dynamatrixGlobalState.enableDebugTrace
        this.enableDebugTraceFailures = dynamatrixGlobalState.enableDebugTraceFailures
        this.enableDebugErrors = dynamatrixGlobalState.enableDebugErrors
        this.enableDebugSysprint = dynamatrixGlobalState.enableDebugSysprint
    }

    public boolean canEqual(Object other) {
        return other instanceof Dynamatrix
    }

    @Override
    public Dynamatrix clone() throws CloneNotSupportedException {
        return (Dynamatrix) super.clone();
    }

    /** Clone current {@link #dynacfg} into {@link #dynacfgSaved}.
     * @see #restoreDynacfg
     */
    public boolean saveDynacfg() {
        this.dynacfgSaved = null // GC
        this.dynacfgSaved = this.dynacfg.clone()
        return true
    }

    /** Clone back current {@link #dynacfgSaved} (if not null)
     * into {@link #dynacfg}.
     * @see #saveDynacfg
     */
    public boolean restoreDynacfg() {
        if (this.dynacfgSaved != null) {
            this.dynacfg = null // GC
            this.dynacfg = this.dynacfgSaved.clone()
            return true
        }
        return false
    }

    /**
     * Report amounts of stages which have certain verdicts,
     * zero or not, in layman wording.
     */
    public String toStringStageCount() {
        return "countStagesStarted:${countStagesStarted()} " +
            "(of which countStagesRestarted:${countStagesRestarted()}) " +
            "countStagesCompleted:${countStagesCompleted()} " +
            "countStagesAbortedSafe:${countStagesAbortedSafe()} " +
            "countStagesFinishedOK:${countStagesFinishedOK()} " +
            "countStagesFinishedFailure:${countStagesFinishedFailure()} " +
            "countStagesFinishedFailureAllowed:${countStagesFinishedFailureAllowed()} " +
            "countStagesAborted:${countStagesAborted()} " +
            "countStagesAbortedNotBuilt:${countStagesAbortedNotBuilt()}"
    }

    /**
     * Report amounts of stages which have certain verdicts,
     * greater than zero, in layman wording.
     */
    public String toStringStageCountNonZero() {
        String s = ""
        Integer i

        if ( (i = countStagesStarted()) > 0)
            s += "countStagesStarted:${i} "

        if ( (i = countStagesRestarted()) > 0)
            s += "(of which countStagesRestarted:${i}) "

        if ( (i = countStagesCompleted()) > 0)
            s += "countStagesCompleted:${i} "

        if ( (i = countStagesAbortedSafe()) > 0)
            s += "countStagesAbortedSafe:${i} "

        if ( (i = countStagesFinishedOK()) > 0)
            s += "countStagesFinishedOK:${i} "

        if ( (i = countStagesFinishedFailure()) > 0)
            s += "countStagesFinishedFailure:${i} "

        if ( (i = countStagesFinishedFailureAllowed()) > 0)
            s += "countStagesFinishedFailureAllowed:${i} "

        if ( (i = countStagesAborted()) > 0)
            s += "countStagesAborted:${i} "

        if ( (i = countStagesAbortedNotBuilt()) > 0)
            s += "countStagesAbortedNotBuilt:${i} "

        return s.trim()
    }

    /**
     * Report amounts of stages which have certain verdicts,
     * greater than zero, in debug-friendly wording (using
     * key names from {@link #countStages} map).
     */
    public String toStringStageCountDumpNonZero() {
        Map<String, Integer> m = [:]
        countStages.each {String k, Integer v ->
            if (v > 0) m[k] = v
        }
        return m.toString()
    }

    /** Returns a clone of current {@link #countStages} map contents. */
    public Map<String, Integer> getCountStages() {
        return (Map<String, Integer>)(countStages.clone())
    }

    /** Returns stringification of current {@link #countStages} map contents. */
    public String toStringStageCountDump() {
        return countStages.toString()
    }

    /**
     * Returns cached {@link NodeCaps} from {@link #nodeCapsCache}
     * for the specified "labelExpr".
     * @param labelExpr Jenkins node matching labels expression
     * @return  A cached or new {@link NodeCaps} value
     */
    public NodeCaps getNodeCaps(String labelExpr = null) {
        if (labelExpr == null) {
            labelExpr = ""
        } else {
            labelExpr = labelExpr.trim()
        }

        if (!nodeCapsCache.containsKey(labelExpr)) {
            nodeCapsCache[labelExpr] = new NodeCaps(this, dynacfg.commonLabelExpr, dynamatrixGlobalState.enableDebugTrace, dynamatrixGlobalState.enableDebugErrors)
        }

        return nodeCapsCache[labelExpr]
    }

    @NonCPS
    public boolean shouldDebugTrace() {
        return (this.enableDebugTrace && this.script != null)
    }

    @NonCPS
    public boolean shouldDebugTraceFailures() {
        return (this.enableDebugTraceFailures && this.script != null)
    }

    @NonCPS
    public boolean shouldDebugErrors() {
        return ( (this.enableDebugErrors || this.enableDebugTraceFailures || this.enableDebugTrace) && this.script != null)
    }

    @NonCPS
    public boolean shouldDebugMilestones() {
        return ( (this.enableDebugMilestones || this.enableDebugMilestonesDetails || this.enableDebugTrace || this.enableDebugErrors || this.enableDebugTraceFailures) && this.script != null)
    }

    @NonCPS
    public boolean shouldDebugMilestonesDetails() {
        return ( (this.enableDebugMilestonesDetails || this.enableDebugTrace) && this.script != null)
    }

    /**
     * Extreme debugging, from groovy to system printouts (e.g. to
     * Jenkins server logs); this.script is not required in this case.
     */
    @NonCPS
    public boolean shouldDebugSysprint() {
        return ( this.enableDebugSysprint )
    }


/*
 * Example agents and call signature from README:

* "testoi":
----
COMPILER=CLANG COMPILER=GCC
CLANGVER=8 CLANGVER=9
GCCVER=10 GCCVER=4.4.4 GCCVER=4.9 GCCVER=6 GCCVER=7
OS_FAMILY=illumos OS_DISTRO=openindiana
ARCH_BITS=64 ARCH_BITS=32
ARCH64=x86_64 ARCH32=x86
nut-builder zmq-builder
----
* "testdeb":
----
COMPILER=GCC
GCCVER=4.8 GCCVER=4.9 GCCVER=5 GCCVER=7
OS_FAMILY=linux OS_DISTRO=debian-10
ARCH_BITS=64 ARCH_BITS=32
ARCH64=x86_64 ARCH32=x86 ARCH32=armv7l
nut-builder zmq-builder linux-kernel-builder
----

// Prepare parallel stages for the dynamatrix:
def parallelStages = prepareDynamatrix(
    commonLabelExpr: 'nut-builder',
    compilerType: 'C',
    compilerLabel: 'COMPILER',
    compilerTools: ['CC', 'CXX', 'CPP'],
    dynamatrixAxesLabels: [~/^OS_DIS.+/, '${COMPILER}VER', 'ARCH${ARCH_BITS}'],
    dynamatrixAxesCommonEnv: [['LANG=C', 'TZ=UTC'], ['LANG=ru_RU']],
    dynamatrixAxesCommonOpts: [
        ['"CFLAGS=-stdc=gnu99" CXXFLAGS="-stdcxx=g++99"', '"CFLAGS=-stdc=c89" CXXFLAGS="-stdcxx=c++89"'],
        ['-m32', '-m64'] ],
    dynamatrixRequiredLabelCombos: [[~/OS=bsd/, ~/CLANG=12/]],
    allowedFailure: [[~/OS=.+/, ~/GCCVER=4\..+/], ['ARCH=armv7l'], [~/std.+=.+89/]],
    excludeCombos: [[~/OS=openindiana/, ~/CLANGVER=9/, ~/ARCH=x86/], [~/GCCVER=4\.[0-7]\..+/, ~/std.+=+(!?89|98|99|03)/], [~/GCCVER=4\.([8-9]|1[0-9]+)\..+/, ~/std.+=+(!?89|98|99|03|11)/]]
) {
    unstash 'preparedSource'
    sh """ ./autogen.sh """
    sh """ ${dynamatrix.commonEnv} CC=${dynamatrix.COMPILER.CC.VER} CXX=${dynamatrix.COMPILER.CXX.VER} ./configure ${dynamatrix.commonOpts} """
    sh """ make -j 4 """
    sh """ make check """
}

 */

    /**
     * Return {@code true} if "dynacfgOrig" argument contains fields used in
     * {@link #prepareDynamatrix} that need recalculation or otherwise
     * would be ignored by {@link #generateBuild}.
     *
     * @see #clearNeedsPrepareDynamatrixClone
     * @see #clearMapNeedsPrepareDynamatrixClone
     * @see #prepareDynamatrix
     */
    def needsPrepareDynamatrixClone(Map dynacfgOrig = [:]) {
        if (Utils.isListNotEmpty(dynacfgOrig?.requiredNodelabels)) {
            if (Utils.isListNotEmpty(dynacfg.requiredNodelabels)) {
                if (dynacfg.requiredNodelabels != dynacfgOrig.requiredNodelabels) {
                    // Different set than before
                    return true;
                }
            } else {
                // There was no constraint before
                return true;
            }
        } // there is no else: if original dynamatrix had the constraint, we do not clear it

        if (Utils.isListNotEmpty(dynacfgOrig?.excludedNodelabels)) {
            if (Utils.isListNotEmpty(dynacfg.excludedNodelabels)) {
                if (dynacfg.excludedNodelabels != dynacfgOrig.excludedNodelabels) {
                    // Different set than before
                    return true;
                }
            } else {
                // There was no constraint before
                return true;
            }
        } // there is no else: if original dynamatrix had the constraint, we do not clear it

        if (Utils.isListNotEmpty(dynacfgOrig?.dynamatrixAxesLabels)) {
            if (Utils.isListNotEmpty(dynacfg.dynamatrixAxesLabels)) {
                if (dynacfg.dynamatrixAxesLabels != dynacfgOrig.dynamatrixAxesLabels) {
                    // Different set than before
                    return true;
                }
            } else {
                // There was no constraint before? Should not get here...
                return true;
            }
        } // there is no else: if original dynamatrix had the constraint, we do not clear it

        if (Utils.isStringNotEmpty(dynacfgOrig?.commonLabelExpr)) {
            if (Utils.isStringNotEmpty(dynacfg.commonLabelExpr)) {
                if (dynacfg.commonLabelExpr != dynacfgOrig.commonLabelExpr) {
                    // Different set than before
                    return true;
                }
            } else {
                // There was no constraint before? Should not get here...
                return true;
            }
        } // there is no else: if original dynamatrix had the constraint, we do not clear it

        // No critical fields redefined
        return false
    }

    /**
     * Return a clone of "dynacfgOrig" map, from which certain keys are
     * removed so they can be subsequently re-initialized by the caller.
     *
     * @see #clearNeedsPrepareDynamatrixClone
     * @see #needsPrepareDynamatrixClone
     * @see #prepareDynamatrix
     */
    static def clearMapNeedsPrepareDynamatrixClone(Map dynacfgOrig = [:]) {
        Map dc = dynacfgOrig?.clone()
        if (dc?.commonLabelExpr)
            dc.remove('commonLabelExpr')
        if (dc?.dynamatrixAxesLabels)
            dc.remove('dynamatrixAxesLabels')
        if (dc?.excludedNodelabels)
            dc.remove('excludedNodelabels')
        if (dc?.requiredNodelabels)
            dc.remove('requiredNodelabels')
        return dc
    }

    /**
     * We are reusing a {@link Dynamatrix} object, maybe a clone.
     * Wipe {@link #dynacfg} data points that may impact re-init later
     * with {@link #prepareDynamatrix}.
     *
     * @see #needsPrepareDynamatrixClone
     * @see #clearMapNeedsPrepareDynamatrixClone
     * @see #prepareDynamatrix
     */
    def clearNeedsPrepareDynamatrixClone(Map dynacfgOrig = [:]) {
        boolean debugTrace = this.shouldDebugTrace()
        if (debugTrace) this.script.println "[DEBUG] prepareDynamatrix(): Clearing certain pre-existing data points from dynacfg object"
        this.dynacfg.clearNeedsPrepareDynamatrixClone(dynacfgOrig)
        this.buildLabelsAgents = [:]
        this.effectiveAxes = []
        this.buildLabelCombos = []
        this.buildLabelCombosFlat = []

        // GC this to be sure:
        //this.nodeCaps = null
        nodeCaps = null

        return this
    }

    /**
     * Initialize properties of the current {@link Dynamatrix} object,
     * using entries of the {@code dynacfgOrig} map with certain keys.
     *
     * @see #clearNeedsPrepareDynamatrixClone
     * @see #clearMapNeedsPrepareDynamatrixClone
     * @see #needsPrepareDynamatrixClone
     */
    def prepareDynamatrix(Map dynacfgOrig = [:]) {
        boolean debugErrors = this.shouldDebugErrors()
        boolean debugTrace = this.shouldDebugTrace()
        boolean debugMilestones = this.shouldDebugMilestones()
        boolean debugMilestonesDetails = this.shouldDebugMilestonesDetails()

        //if (debugErrors) this.script.println "[WARNING] NOT FULLY IMPLEMENTED: Dynamatrix.prepareDynamatrix()"

        // Note: in addition to standard contents of class DynamatrixConfig,
        // the Map passed by caller may contain "defaultDynamatrixConfig" as
        // a key for a String value to specify default pre-sets, e.g. "C".
        // It can also contain a special Map to manage merge-mode of custom
        // provided value to "replace" same-named defaults or "merge" with
        // them -- dynacfgOrig.mergeMode["dynacfgFieldNameString"]="merge"

        if (debugTrace) this.script.println "[DEBUG] prepareDynamatrix(): Initial dynacfg: ${Utils.castString(dynacfg)}\nParameter dynacfgOrig: ${Utils.castString(dynacfgOrig)}"
        def sanityRes = dynacfg.initDefault(dynacfgOrig)
        if (sanityRes != true) {
            if (sanityRes instanceof String) {
                if (debugErrors) this.script.println sanityRes
            }
            // Assumed non-fatal - fields seen that are not in standard config
        }
        if (debugTrace) this.script.println "[DEBUG] prepareDynamatrix(): After cfg merge: dynacfg: ${Utils.castString(dynacfg)}\nwith result ${Utils.castString(sanityRes)}"

        sanityRes = dynacfg.sanitycheckDynamatrixAxesLabels()
        if (sanityRes != true) {
            if (sanityRes instanceof String) {
                if (debugErrors) this.script.println "[ERROR] prepareDynamatrix(): ${sanityRes}"
            } else {
                if (debugErrors) this.script.println "[ERROR] prepareDynamatrix(): No 'dynamatrixAxesLabels' were provided, nothing to generate"
            }
            return null
        }
        if (debugTrace) this.script.println "[DEBUG] prepareDynamatrix(): Initially requested dynamatrixAxesLabels: " + dynacfg.dynamatrixAxesLabels

        // TODO: Cache as label-mapped hash in dynamatrixGlobals so re-runs for
        // other configs for same builder would not query and parse real Jenkins
        // worker labels again and again.
        def commonLabelExpr = dynacfg.commonLabelExpr
        if (commonLabelExpr == null) commonLabelExpr = ""
        commonLabelExpr += dynacfg.getConstraintsNodelabels()
        // If we had no/null dynacfg.commonLabelExpr, avoid starting with
        // the "&&" from constraints, if any... and trim() generally:
        commonLabelExpr = commonLabelExpr.trim().replaceFirst(/^ *\&\& */, '').trim()

        // Inheritance in groovy for class fields is weird, so below
        // we use this temporary object as defined in caller's scope
        NodeCaps tmpNodeCaps = new NodeCaps(
            this.script,
            commonLabelExpr,
            debugTrace,
            debugErrors)

        //this.nodeCaps = null // kick GC
        nodeCaps = tmpNodeCaps
        //this.nodeCaps = tmpNodeCaps.clone()
        //this.nodeCaps = nodeCaps
        if (debugTrace) {
            this.script.println "[DEBUG] prepareDynamatrix(): collected nodeCaps: " + Utils.castString(nodeCaps)
            this.script.println "[DEBUG] prepareDynamatrix(): collected nodeCaps.nodeData: " + Utils.castString(nodeCaps.nodeData)
            nodeCaps.printDebug()
            this.script.println "[DEBUG] prepareDynamatrix(): collected this.nodeCaps: " + Utils.castString(this.nodeCaps)
            this.script.println "[DEBUG] prepareDynamatrix(): collected this.nodeCaps.nodeData: " + Utils.castString(this.nodeCaps.nodeData)
            this.nodeCaps.printDebug()
            this.script.println "[DEBUG] prepareDynamatrix(): collected tmpNodeCaps: " + Utils.castString(tmpNodeCaps)
            this.script.println "[DEBUG] prepareDynamatrix(): collected tmpNodeCaps.nodeData: " + Utils.castString(tmpNodeCaps.nodeData)
            tmpNodeCaps.printDebug()
        }

        // Original request could have regexes or groovy-style substitutions
        // to expand. The effectiveAxes is generally a definitive set of
        // sets of exact axis names, e.g. ['ARCH', 'CLANGVER', 'OS'] and
        // ['ARCH', 'GCCVER', 'OS'] as expanded from '${COMPILER}VER' part:
        this.effectiveAxes = []
        dynacfg.dynamatrixAxesLabels.each() {def axis ->
            //TreeSet effAxis = this.nodeCaps.resolveAxisName(axis).sort()
            //TreeSet effAxis = nodeCaps.resolveAxisName(axis).sort()
            TreeSet effAxis = tmpNodeCaps.resolveAxisName(axis).sort()
            if (debugTrace) this.script.println "[DEBUG] prepareDynamatrix(): converted axis argument '${axis}' into: " + effAxis
            this.effectiveAxes << effAxis
        }
        this.effectiveAxes = this.effectiveAxes.sort()
        if (debugTrace) this.script.println "[DEBUG] prepareDynamatrix(): Initially detected effectiveAxes: " + this.effectiveAxes

        // By this point, a request for ['OS', '${COMPILER}VER', ~/ARC.+/]
        // yields [[OS], [ARCH], [CLANGVER, GCCVER]] from which we want to
        // get a set with two sets of axes that can do our separate builds:
        // [ [OS, ARCH, CLANGVER], [OS, ARCH, GCCVER] ]
        // ...and preferably really sorted :)
        this.effectiveAxes = Utils.cartesianSquared(this.effectiveAxes).sort()
        if (this.effectiveAxes.size() > 0) {
            // We want set of sets for processing below
            def listCount = 0
            for (def i = 0; i < this.effectiveAxes.size(); i++) {
                if (Utils.isList(this.effectiveAxes[i])) { listCount++ }
            }

            if (listCount == 0) {
                // If we only got one list/set like [OS, ARCH] - then
                // remake it into a set that contains one set
                this.effectiveAxes = [this.effectiveAxes]
            } else if (listCount != this.effectiveAxes.size()) {
                // Turn any non-list/set items into sets of one entry
                Set arr = []
                for (def i = 0; i < this.effectiveAxes.size(); i++) {
                    if (Utils.isList(this.effectiveAxes[i])) {
                        arr << this.effectiveAxes[i]
                    } else {
                        arr << [this.effectiveAxes[i]]
                    }
                }
                this.effectiveAxes = arr
            }
        }
        if (debugTrace) this.script.println "[DEBUG] prepareDynamatrix(): Final detected effectiveAxes: " + this.effectiveAxes

        //this.nodeCaps.enableDebugTrace = true
        //nodeCaps.enableDebugTrace = true
        //tmpNodeCaps.enableDebugTrace = true
        // Prepare all possible combos of requested axes (meaning we can
        // request a build agent label "A && B && C" and all of those
        // would work with our currently defined agents). The buildLabels
        // are expected to provide good uniqueness thanks to the SortedSet
        // of effectiveAxes and their values that we would look into.
        this.buildLabelCombos = []
        if (debugTrace) {
            //this.script.println "[DEBUG] prepareDynamatrix(): looking for axis values in this.nodeCaps.nodeData.keySet(): " + Utils.castString(this.nodeCaps.nodeData.keySet())
            //this.script.println "[DEBUG] prepareDynamatrix(): looking for axis values in nodeCaps.nodeData.keySet(): " + Utils.castString(nodeCaps.nodeData.keySet())
            this.script.println "[DEBUG] prepareDynamatrix(): looking for axis values in tmpNodeCaps.nodeData.keySet(): " + Utils.castString(tmpNodeCaps.nodeData.keySet())
        }
        //this.nodeCaps.
        //nodeCaps.
        tmpNodeCaps.nodeData.keySet().each() {String nodeName ->
            // Looking at each node separately allows us to be sure that any
            // combo of axis-values (all of which it allegedly provides)
            // can be fulfilled
            ArrayList nodeAxisCombos = []
            this.effectiveAxes.each() {def axisSet ->
                // Now looking at one definitive set of axis names that
                // we would pick supported values for, by current node:
                ArrayList axisCombos = []
                axisSet.each() {def axis ->
                    if (debugTrace) this.script.println "[DEBUG] prepareDynamatrix(): querying values for axis '${Utils.castString(axis)}' collected for node '${Utils.castString(nodeName)}'..."
                    //def tmpset = this.nodeCaps.resolveAxisValues(axis, nodeName, true)
                    //def tmpset = nodeCaps.resolveAxisValues(axis, nodeName, true)
                    def tmpset = tmpNodeCaps.resolveAxisValues(axis, nodeName, true)
                    if (debugTrace) this.script.println "[DEBUG] prepareDynamatrix(): querying values for axis '${Utils.castString(axis)}' collected for node '${Utils.castString(nodeName)}': got tmpset: ${Utils.castString(tmpset)}"
                    // Got at least one usable key=value string?
                    if (tmpset != null && tmpset.size() > 0) {
                        // TODO: Value constraints and classification
                        // (mayFail etc) probably belong here
                        axisCombos << tmpset.sort()
                    }
                }

                if (axisCombos.size() > 0) {
                    // Collect realistic values of each axis for this node
                    if (axisCombos.size() == axisSet.size()) {
                        // Only collect combos which cover all requested axes
                        // e.g. if some build host does not declare the ARCH(es)
                        // it can build for, and we require to know it - ignore
                        // that node
                        // TODO: Something around constraints and classification
                        // (is axis required? etc) might belong here
                        axisCombos = axisCombos.sort()
                        nodeAxisCombos << axisCombos
                    } else {
                        if (debugTrace) this.script.println "[DEBUG] prepareDynamatrix(): ignored buildLabelCombos collected for node ${nodeName} with requested axis set ${axisSet}: only got " + axisCombos
                    }
                }
            }
            if (nodeAxisCombos.size() > 0) {
                // It is okay if several nodes can run a build
                // which matches the given requirements
                nodeAxisCombos = nodeAxisCombos.sort()
                this.buildLabelCombos << nodeAxisCombos
            }
        }
        this.buildLabelCombos = this.buildLabelCombos.sort()
        if (debugTrace) this.script.println "[DEBUG] prepareDynamatrix(): Initially detected buildLabelCombos (still grouped per node): " + this.buildLabelCombos
        // a request for dynamatrixAxesLabels: ['OS', '${COMPILER}VER', ~/ARC.+/]
        // on testbed got us this:
        // [         //### buildLabelCombos itself
        //  [        //###  one node that served all needed labels for both GCCVER and CLANGVER (one of nodeResults)
        //   [[ARCH=amd64, ARCH=i386], [OS=openindiana], [GCCVER=10, GCCVER=4.4.4, GCCVER=4.9, GCCVER=6, GCCVER=7]],
        //   [[ARCH=amd64, ARCH=i386], [OS=openindiana], [CLANGVER=8, CLANGVER=9]]    //### one of nodeAxisCombos, here with 3 sets of axisValues inside
        //  ], [     //###  second node that served all needed labels for at least one combo
        //   [[OS=linux], [ARCH=armv7l, ARCH=i386, ARCH=x86_64], [GCCVER=4.8, GCCVER=4.9, GCCVER=5, GCCVER=7]]
        //  ]        //###  ignored one node that did not declare any ARCH
        // ]

        this.buildLabelCombosFlat = []
        this.buildLabelCombos.each() {def nodeResults ->
            nodeResults.each() {def nodeAxisCombos ->
                // this nodeResults contains the set of sets of label values
                // supported for one of the original effectiveAxes requirements,
                // where each of nodeAxisCombos contains a set of axisValues
                if (debugTrace) this.script.println "[DEBUG] prepareDynamatrix(): Expanding : " + nodeAxisCombos
                List tmp = Utils.cartesianSquared(nodeAxisCombos as Iterable).sort()
                // Revive combos that had only one hit and were flattened
                // into single items (strings) instead of Sets (of Sets)
                if (tmp.size() > 0) {
                    for (Integer i = 0; i < tmp.size(); i++) {
                        if (!Utils.isList(tmp[i])) { tmp[i] = [tmp[i]] }
                    }
                }
                if (debugTrace) this.script.println "[DEBUG] prepareDynamatrix(): Expanded into : " + tmp
                // Add members of tmp (many sets of unique key=value combos
                // for each axis) as direct members of buildLabelCombosFlat
                this.buildLabelCombosFlat += tmp
            }
        }

        // Convert Sets of Sets of strings in buildLabelCombos into the
        // array of strings (keys of the BLA Map) we can feed into the
        // agent requirements of generated pipeline stages:
        this.buildLabelsAgents = mapBuildLabelExpressions(this.buildLabelCombosFlat)

        // Finally, append into BLA keys the constraints from commonLabelExpr
        // (the original "request" for suitable workers -- note that optional
        // further inclusions or exclusions of capability labels are treated
        // later). We append to keep the more "interesting" variable string
        // part seen early in the logs, stage names, etc.
        // Overall, this should help avoid scheduling builds to agents that
        // have e.g. matching tool kits, but not the third-party prerequisite
        // packages preinstalled for a particular project.
        if (Utils.isStringNotEmpty(dynacfg.commonLabelExpr)) {
            if (debugTrace) this.script.println "[DEBUG] prepareDynamatrix(): appending '(${dynacfg.commonLabelExpr}) && ' to buildLabelsAgents combos..."
            Map<String, Set> tmp = [:]
            this.buildLabelsAgents.keySet().each() {String ble ->
                // Note, we only append to the key (node label string for
                // eventual use in a build), not the value (array of K=V's)
                String tmpBle = "(${ble}) && (${dynacfg.commonLabelExpr})"
                tmp[tmpBle] = this.buildLabelsAgents[ble]
            }
            this.buildLabelsAgents = tmp
        }

        String blaStr = ""
        this.buildLabelsAgents.keySet().each() {String ble ->
            blaStr += "\n    '${ble}' => " + this.buildLabelsAgents[ble]
        }
        if (debugTrace) this.script.println "[DEBUG] prepareDynamatrix(): detected ${this.buildLabelsAgents.size()} buildLabelsAgents combos:" + blaStr

        return true
    }

    /** Take {@code blcSet[]} which is a Set of Sets (equivalent to field
     * {@link #buildLabelCombosFlat} in the class), with contents like this:
     * <pre>
     * [ [ARCH_BITS=64 ARCH64=amd64, COMPILER=CLANG CLANGVER=9, OS_DISTRO=openindiana],
     *   [ARCH_BITS=32 ARCH32=armv7l, COMPILER=GCC GCCVER=4.9, OS_DISTRO=debian] ]
     * </pre>
     * ...and convert into a Map where keys are agent label expression strings.
     */
    static Map<String, Set> mapBuildLabelExpressions(Set<Set> blcSet) {
        /** Equivalent to buildLabelsAgents in the class */
        Map<String, Set> blaMap = [:]
        blcSet.each() {Set combo ->
            // Note that labels can be composite, e.g. "COMPILER=GCC GCCVER=1.2.3"
            // ble == build label expression
            String ble = String.join('&&', combo).replaceAll('\\s+', '&&')
            blaMap[ble] = combo
        }
        return blaMap
    }

    /** Returns a set of (unique) {@link DynamatrixSingleBuildConfig} items */
//    @NonCPS
    Set<DynamatrixSingleBuildConfig> generateBuildConfigSet(Map dynacfgOrig = [:]) {
        boolean debugErrors = this.shouldDebugErrors()
        boolean debugTrace = this.shouldDebugTrace()
        boolean debugMilestones = this.shouldDebugMilestones()
        boolean debugMilestonesDetails = this.shouldDebugMilestonesDetails()

        // Use a separate copy of the configuration for this build
        // since different scenarios may be customized - although
        // they all take off from the same baseline setup...
        DynamatrixConfig dynacfgBuild = this.dynacfg.clone()
        dynacfgBuild.initDefault(dynacfgOrig)

        if (this.buildLabelsAgents.size() == 0 && dynacfgBuild.dynamatrixRequiredLabelCombos.size() == 0) {
            if (debugErrors) this.script.println "[ERROR] generateBuildConfigSet() : should call prepareDynamatrix() first, or that found nothing usable"
            return null
        }

        // We will generate build stages for each of the agent labels
        // referenced in this Map's keys. Some labels are announced
        // by workers themselves (nodeCaps), others may be required
        // additionally by the callers at their discretion, and then
        // filtered with excludeCombos in the end:
        Map buildLabelsAgentsBuild = this.buildLabelsAgents
        if (dynacfgBuild.dynamatrixRequiredLabelCombos.size() > 0) {
            // dynamatrixRequiredLabelCombos: convert from a Set of
            // key=value pairs into a Map, to process similar to
            // labels announced by build nodes, including use in
            // `agent { label 'expr' }` clauses
            if (debugTrace) this.script.println "[DEBUG] generateBuildConfigSet(): buildLabelsAgentsBuild before requiredLabelCombos: ${buildLabelsAgentsBuild}"
            buildLabelsAgentsBuild += mapBuildLabelExpressions(dynacfgBuild.dynamatrixRequiredLabelCombos)
        }
        if (debugTrace) this.script.println "[DEBUG] generateBuildConfigSet(): buildLabelsAgentsBuild: ${buildLabelsAgentsBuild}"

        // Here we will collect axes that come from optional dynacfg fields
        Set<Set> virtualAxes = []

        // Process the map of "virtual axes": dynamatrixAxesVirtualLabelsMap
        if (dynacfgBuild.dynamatrixAxesVirtualLabelsMap.size() > 0) {
            // Map of "axis: [array, of, values]"
            if (debugTrace) this.script.println "[DEBUG] generateBuildConfigSet(): dynamatrixAxesVirtualLabelsMap: ${dynacfgBuild.dynamatrixAxesVirtualLabelsMap}"
            Set dynamatrixAxesVirtualLabelsCombos = []
            dynacfgBuild.dynamatrixAxesVirtualLabelsMap.keySet().each() {def k ->
                // Keys of the map, e.g. 'CSTDVARIANT' for strings ('c', 'gnu')
                // or 'CSTDVERSION_${KEY}' for submaps (['c': '99', 'cxx': '98'])
                def vals = dynacfgBuild.dynamatrixAxesVirtualLabelsMap[k]
                if (!Utils.isList(vals) || vals.size() == 0) {
                    if (debugTrace) this.script.println "[DEBUG] generateBuildConfigSet(): dynamatrixAxesVirtualLabelsMap: SKIPPED key '${k}': its value is not a list: ${Utils.castString(vals)}"
                    return // continue
                }

                // Collect possible values of this one key
                Set keyvalues = []
                vals.each() {def v ->
                    // Store each value of the provided axis as a set with
                    // one item (if a string) or more (if an expanded map)
                    Set vv = []
                    if (Utils.isMap(v) && k.contains('${KEY}')) {
                        v.keySet().each() {def subk ->
                            def dk = k.replaceFirst(/\$\{KEY\}/, subk)
                            vv << "${dk}=${v[subk]}"
                        }
                    }

                    if (vv.size() == 0) {
                        def dk = k.replaceFirst(/\$\{KEY\}/, 'DEFAULT')
                        vv = ["${dk}=${v}"]
                    }

                    keyvalues << vv
                }

                if (debugTrace) this.script.println "[DEBUG] generateBuildConfigSet(): combining dynamatrixAxesVirtualLabelsCombos: ${dynamatrixAxesVirtualLabelsCombos}\n    with keyvalues: ${keyvalues}"
                dynamatrixAxesVirtualLabelsCombos = new HashSet(Utils.cartesianProduct(dynamatrixAxesVirtualLabelsCombos, keyvalues) as Collection)
            }

            if (debugTrace) this.script.println "[DEBUG] generateBuildConfigSet(): " +
                "combining dynamatrixAxesVirtualLabelsCombos: ${dynamatrixAxesVirtualLabelsCombos}" +
                "\n    with virtualAxes: ${virtualAxes}" +
                "\ndynacfgBuild.dynamatrixAxesVirtualLabelsMap.size()=${dynacfgBuild.dynamatrixAxesVirtualLabelsMap.size()} " +
                "virtualAxes.size()=${virtualAxes.size()} " +
                "dynamatrixAxesVirtualLabelsCombos.size()=${dynamatrixAxesVirtualLabelsCombos.size()}"

            // TODO: Will we have more virtualAxes inputs, or might just use assignment here?
            virtualAxes = new HashSet<Set>(Utils.cartesianProduct(dynamatrixAxesVirtualLabelsCombos, virtualAxes) as Collection)

            if (debugTrace) this.script.println "[DEBUG] generateBuildConfigSet(): ended up with virtualAxes: ${virtualAxes}"
        }

        // dynamatrixAxesCommonEnv + dynamatrixAxesCommonEnvCartesian
        if (dynacfgBuild.dynamatrixAxesCommonEnvCartesian.size() > 0) {
            dynacfgBuild.dynamatrixAxesCommonEnv += Utils.cartesianSquared(dynacfgBuild.dynamatrixAxesCommonEnvCartesian)
        }

        // dynamatrixAxesCommonOpts + dynamatrixAxesCommonOptsCartesian
        if (dynacfgBuild.dynamatrixAxesCommonOptsCartesian.size() > 0) {
            dynacfgBuild.dynamatrixAxesCommonOpts += Utils.cartesianSquared(dynacfgBuild.dynamatrixAxesCommonOptsCartesian)
        }

        // Uncomment this to enforce debugging from this point on:
        //this.enableDebugTrace = true
        //dynamatrixGlobalState.enableDebugTrace = true
        //debugTrace = this.shouldDebugTrace()
        //debugErrors = this.shouldDebugErrors()
        //debugMilestones = this.shouldDebugMilestones()
        //debugMilestonesDetails = this.shouldDebugMilestonesDetails()

        if (true) { // scope
            def countCombos = 1;
            if (buildLabelsAgentsBuild.size() > 0) {
                countCombos *= buildLabelsAgentsBuild.size()
            }
            if (virtualAxes.size() > 0) {
                countCombos *= virtualAxes.size()
            }
            if (dynacfgBuild.dynamatrixAxesCommonEnv.size() > 0) {
                countCombos *= dynacfgBuild.dynamatrixAxesCommonEnv.size()
            }
            if (dynacfgBuild.dynamatrixAxesCommonOpts.size() > 0) {
                countCombos *= dynacfgBuild.dynamatrixAxesCommonOpts.size()
            }
            if (countCombos > 1) {
                if (debugMilestonesDetails) {
                    this.script.println "[DEBUG] generateBuildConfigSet(): " +
                        "expecting at most ${countCombos} combinations with: " +
                        buildLabelsAgentsBuild.size() + " buildLabelsAgentsBuild, " +
                        virtualAxes.size() + " virtualAxes, " +
                        dynacfgBuild.dynamatrixAxesCommonEnv.size() + " dynamatrixAxesCommonEnv, " +
                        dynacfgBuild.dynamatrixAxesCommonOpts.size() + " dynamatrixAxesCommonOpts"
                } else {
                    this.script.println "generateBuildConfigSet: " +
                        "expecting to process ${countCombos} combinations " +
                        "of requested matrix axis values, against " +
                        "${dynacfgBuild.excludeCombos.size()} excludeCombos and " +
                        "${dynacfgBuild.requiredNodelabels.size()} requiredNodelabels and " +
                        "${dynacfgBuild.excludedNodelabels.size()} excludedNodelabels and " +
                        "${dynacfgBuild.allowedFailure.size()} allowedFailure cases. " +
                        "This can take some time."
                }
            }
        }

        // FIXME: Clean up unresolvable combos that appeared from expansion
        // like "${COMPILER}VER" axis and "CLANGVER=11" announced by an agent
        // but that agent does not announce a "COMPILER=CLANG". Such build
        // candidates should be ignored; currently they hang waiting for a
        // worker to implement them. Note this concept differs from some
        // requiredCombos we might use to have Jenkins boot some workers.

        // Quick safe pre-filter, in case that user-provided constraints
        // only impact one type of axis:
        if (dynacfgBuild.excludeCombos.size() > 0) {
            if (debugMilestonesDetails) this.script.println "[DEBUG] generateBuildConfigSet(): quick cleanup: excludeCombos: ${dynacfgBuild.excludeCombos}"

            def removed = 0
            if (buildLabelsAgentsBuild.size() > 0) {
                DynamatrixSingleBuildConfig dsbc = new DynamatrixSingleBuildConfig(this.script)
                def tmp = buildLabelsAgentsBuild.clone()
                tmp.keySet().each() {String ble ->
                    dsbc.buildLabelExpression = ble
                    dsbc.buildLabelSet = buildLabelsAgentsBuild[ble]
                    if (dsbc.matchesConstraints(dynacfgBuild.excludeCombos)) {
                        buildLabelsAgentsBuild.remove(ble)
                        if (debugTrace) this.script.println "[DEBUG] generateBuildConfigSet(): quick cleanup removed: ble: ${ble}"
                        removed++
                    }
                }
                if (debugTrace) this.script.println "[DEBUG] generateBuildConfigSet(): remains after quick cleanup: buildLabelsAgentsBuild(ble's): ${buildLabelsAgentsBuild.size()}"
            }

            if (virtualAxes.size() > 0) {
                DynamatrixSingleBuildConfig dsbc = new DynamatrixSingleBuildConfig(this.script)
                def tmp = virtualAxes.clone()
                tmp.each() {Set virtualLabelSet ->
                    dsbc.virtualLabelSet = virtualLabelSet
                    if (dsbc.matchesConstraints(dynacfgBuild.excludeCombos)) {
                        virtualAxes.remove(virtualLabelSet)
                        if (debugTrace) this.script.println "[DEBUG] generateBuildConfigSet(): quick cleanup removed: virtualLabelSet: ${virtualLabelSet}"
                        removed++
                    }
                }
                if (debugTrace) this.script.println "[DEBUG] generateBuildConfigSet(): remains after quick cleanup: virtualAxes(virtualLabelSet's): ${virtualAxes.size()}"
            }

            if (dynacfgBuild.dynamatrixAxesCommonEnv.size() > 0) {
                DynamatrixSingleBuildConfig dsbc = new DynamatrixSingleBuildConfig(this.script)
                def tmp = dynacfgBuild.dynamatrixAxesCommonEnv.clone()
                tmp.each() {Set envvarSet ->
                    dsbc.envvarSet = envvarSet
                    if (dsbc.matchesConstraints(dynacfgBuild.excludeCombos)) {
                        dynacfgBuild.dynamatrixAxesCommonEnv.remove(envvarSet)
                        if (debugTrace) this.script.println "[DEBUG] generateBuildConfigSet(): quick cleanup removed: envvarSet: ${envvarSet}"
                        removed++
                    }
                }
                if (debugTrace) this.script.println "[DEBUG] generateBuildConfigSet(): remains after quick cleanup: dynamatrixAxesCommonEnv(envvarSet's): ${dynacfgBuild.dynamatrixAxesCommonEnv.size()}"
            }

            if (dynacfgBuild.dynamatrixAxesCommonOpts.size() > 0) {
                DynamatrixSingleBuildConfig dsbc = new DynamatrixSingleBuildConfig(this.script)
                def tmp = dynacfgBuild.dynamatrixAxesCommonOpts.clone()
                tmp.each() {Set clioptSet ->
                    dsbc.clioptSet = clioptSet
                    if (dsbc.matchesConstraints(dynacfgBuild.excludeCombos)) {
                        dynacfgBuild.dynamatrixAxesCommonOpts.remove(clioptSet)
                        if (debugTrace) this.script.println "[DEBUG] generateBuildConfigSet(): quick cleanup removed: clioptSet: ${clioptSet}"
                        removed++
                    }
                }
                if (debugTrace) this.script.println "[DEBUG] generateBuildConfigSet(): remains after quick cleanup: dynamatrixAxesCommonOpts(clioptSet's): ${dynacfgBuild.dynamatrixAxesCommonOpts.size()}"
            }

            if (removed > 0) {
                if (debugMilestones)
                    this.script.println "[DEBUG] generateBuildConfigSet(): quick pass over excludeCombos[] removed ${removed} direct hits from original axis values"

                def countCombos = 1;
                if (buildLabelsAgentsBuild.size() > 0) {
                    countCombos *= buildLabelsAgentsBuild.size()
                }
                if (virtualAxes.size() > 0) {
                    countCombos *= virtualAxes.size()
                }
                if (dynacfgBuild.dynamatrixAxesCommonEnv.size() > 0) {
                    countCombos *= dynacfgBuild.dynamatrixAxesCommonEnv.size()
                }
                if (dynacfgBuild.dynamatrixAxesCommonOpts.size() > 0) {
                    countCombos *= dynacfgBuild.dynamatrixAxesCommonOpts.size()
                }
                if (countCombos > 1)
                    if (debugMilestones)
                        this.script.println "[DEBUG] generateBuildConfigSet(): " +
                            "expecting at most ${countCombos} combinations with: " +
                            buildLabelsAgentsBuild.size() + " buildLabelsAgentsBuild, " +
                            virtualAxes.size() + " virtualAxes, " +
                            dynacfgBuild.dynamatrixAxesCommonEnv.size() + " dynamatrixAxesCommonEnv, " +
                            dynacfgBuild.dynamatrixAxesCommonOpts.size() + " dynamatrixAxesCommonOpts"

            }

        }

        // Uncomment here to trace just the exclusions
        //this.enableDebugTrace = true
        //debugTrace = this.shouldDebugTrace()
        //debugErrors = this.shouldDebugErrors()
        //debugMilestones = this.shouldDebugMilestones()
        //debugMilestonesDetails = this.shouldDebugMilestonesDetails()

        // Finally, combine all we have (and remove what we do not want to have)
        Integer removedTotal = 0
        Integer allowedToFailTotal = 0
        Set<DynamatrixSingleBuildConfig> dsbcSet = []
        buildLabelsAgentsBuild.keySet().each() {String ble ->
            // We can generate numerous build configs below that
            // would all require this (or identical) agent by its
            // build label expression, so prepare the shared part:
            DynamatrixSingleBuildConfig dsbcBle = new DynamatrixSingleBuildConfig(this.script)
            Set<DynamatrixSingleBuildConfig> dsbcBleSet = []
            Integer removedBle = 0
            Integer allowedToFailBle = 0

            // One of (several possible) combinations of node labels:
            dsbcBle.buildLabelExpression = ble
            dsbcBle.buildLabelSet = buildLabelsAgentsBuild[ble]
            if (dynacfgBuild.stageNameFunc != null) {
                dsbcBle.stageNameFunc = dynacfgBuild.stageNameFunc
            } else {
                if (dynamatrixGlobalState.stageNameFunc != null)
                    dsbcBle.stageNameFunc = dynamatrixGlobalState.stageNameFunc
            }

            // Roll the snowball, let it grow!
            if (dynacfgBuild.dynamatrixAxesCommonOpts.size() > 0) {
                Set<DynamatrixSingleBuildConfig> dsbcBleSetTmp = []
                dynacfgBuild.dynamatrixAxesCommonOpts.each() {Set clioptSet ->
                    DynamatrixSingleBuildConfig dsbcBleTmp = dsbcBle.clone()
                    dsbcBleTmp.clioptSet = clioptSet
                    dsbcBleSetTmp += dsbcBleTmp
                }
                dsbcBleSet = dsbcBleSetTmp
            } else {
                dsbcBleSet = [dsbcBle]
            }

            if (dynacfgBuild.dynamatrixAxesCommonEnv.size() > 0) {
                Set<DynamatrixSingleBuildConfig> dsbcBleSetTmp = []
                dynacfgBuild.dynamatrixAxesCommonEnv.each() {Set envvarSet ->
                    dsbcBleSet.each() {DynamatrixSingleBuildConfig tmp ->
                        DynamatrixSingleBuildConfig dsbcBleTmp = tmp.clone()
                        dsbcBleTmp.envvarSet = envvarSet
                        dsbcBleSetTmp += dsbcBleTmp
                    }
                }
                dsbcBleSet = dsbcBleSetTmp
            }

            if (virtualAxes.size() > 0) {
                Set<DynamatrixSingleBuildConfig> dsbcBleSetTmp = []
                if (debugTrace) this.script.println "[DEBUG] generateBuildConfigSet(): COMBINING: virtualAxes: ${Utils.castString(virtualAxes)}\nvs. dsbcBleSet: ${dsbcBleSet}"
                virtualAxes.each() {Set virtualLabelSet ->
                    //if (debugTrace) this.script.println "[DEBUG] generateBuildConfigSet(): checking virtualLabelSet: ${Utils.castString(virtualLabelSet)}"
                    dsbcBleSet.each() {DynamatrixSingleBuildConfig tmp ->
                        DynamatrixSingleBuildConfig dsbcBleTmp = tmp.clone()
                        //if (debugTrace) this.script.println "[DEBUG] generateBuildConfigSet(): checking virtualLabelSet: ${Utils.castString(virtualLabelSet)} with ${dsbcBleTmp}"
                        dsbcBleTmp.virtualLabelSet = virtualLabelSet
                        dsbcBleSetTmp += dsbcBleTmp
                    }
                }
                dsbcBleSet = dsbcBleSetTmp
            }

            if (debugMilestonesDetails) {
                this.script.println "[DEBUG] generateBuildConfigSet(): BEFORE EXCLUSIONS: collected ${dsbcBleSet.size()} combos for individual builds with agent build label expression '${ble}'"
                dsbcBleSet.each() {DynamatrixSingleBuildConfig dsbcBleTmp ->
                        this.script.println "[DEBUG] generateBuildConfigSet(): selected combo candidate: ${dsbcBleTmp}"
                }
            }

            // filter away excludeCombos, and possibly cases of allowedFailure
            // (if runAllowedFailure==false)
            // Note that we DO NOT PREFILTER much, because some user-provided
            // exclusion combinations might only make sense in some corner
            // cases (e.g. don't want this "compiler + Crevision" on that OS,
            // but want it on another)
            if (dynacfgBuild.excludeCombos.size() > 0) {
                def tmp = dsbcBleSet.clone()
                tmp.each() {DynamatrixSingleBuildConfig dsbcBleTmp ->
                    if (dsbcBleTmp.matchesConstraints(dynacfgBuild.excludeCombos)) {
                        // TODO: track isExcluded or just delete items?
                        dsbcBleTmp.isExcluded = true
                        dsbcBleSet.remove(dsbcBleTmp)
                        removedBle++
                        if (debugMilestonesDetails) this.script.println "[DEBUG] generateBuildConfigSet(): excluded combo: ${dsbcBleTmp}\nwith ${dynacfgBuild.excludeCombos}"
                    }
                }
            }

            if (dynacfgBuild.allowedFailure.size() > 0) {
                def tmp = dsbcBleSet.clone()
                tmp.each() {DynamatrixSingleBuildConfig dsbcBleTmp ->
                    if (dsbcBleTmp.matchesConstraints(dynacfgBuild.allowedFailure)) {
                        allowedToFailBle++
                        if (dynacfgBuild.runAllowedFailure) {
                            dsbcBleTmp.isAllowedFailure = true
                        } else {
                            dsbcBleSet.remove(dsbcBleTmp)
                            removedBle++
                            if (debugMilestonesDetails) this.script.println "[DEBUG] generateBuildConfigSet(): excluded combo: ${dsbcBleTmp}\nwith ${dynacfgBuild.allowedFailure} (because we do not runAllowedFailure this time)"
                        }
                    } else {
                        dsbcBleTmp.isAllowedFailure = false
                    }
                }
            }

            dsbcSet += dsbcBleSet
            removedTotal += removedBle
            allowedToFailTotal += allowedToFailBle

            if (debugMilestonesDetails) {
                if (removedBle > 0) {
                    this.script.println "[DEBUG] generateBuildConfigSet(): excludeCombos[] matching removed ${removedBle} direct hits from candidate builds for label ${ble}" +
                        ( (!dynacfgBuild.runAllowedFailure && (allowedToFailBle > 0)) ? " including ${allowedToFailBle} items allowed to fail which we would not run" : "" )
                }
                if (dynacfgBuild.runAllowedFailure && allowedToFailBle > 0) {
                    this.script.println "[DEBUG] generateBuildConfigSet(): excludeCombos[] matching marked ${allowedToFailBle} direct hits from candidate builds for label ${ble} as allowed failures"
                }
                this.script.println "[DEBUG] generateBuildConfigSet(): ${dsbcBleSet.size()} candidates collected for label ${ble} which should succeed"
            }

        }

        // Do some post-processing related to requiredNodelabels and
        // excludedNodelabels: if dynacfg.getConstraintsNodelabels()
        // returns a not-empty string, we should loop over all above
        // proposed build combos and see how many nodes match them
        // with the bigger label expression. If there is one or more
        // still matching, append the constraint to that combo; but
        // if there are zero matches with the constraint considered,
        // remove the combo from proposals.
        def constraintsNodelabels = dynacfgBuild.getConstraintsNodelabels()
        if (Utils.isStringNotEmpty(constraintsNodelabels)) {
            if (debugMilestonesDetails) this.script.println "[DEBUG] generateBuildConfigSet(): post-processing selected combos with additional Node Labels constraints: (per-DSBC-label) ${constraintsNodelabels}"
            def removedCNL = 0

            // Avoid deleting from the Set instance that we are iterating
            Set<DynamatrixSingleBuildConfig> tmp = []
            // Avoid spamming the log about same label constraints
            // (several single-build configs can share those strings)
            Map<String, List> nodeListCache = [:]
            dsbcSet.each() {DynamatrixSingleBuildConfig dsbc ->
                String blec = (dsbc.buildLabelExpression + constraintsNodelabels).trim().replaceFirst(/^null/, '').replaceFirst(/^ *\&\& */, '').trim()
                List nodeList
                if (nodeListCache.containsKey(blec)) {
                    nodeList = nodeListCache[blec]
                } else {
                    nodeList = this.script.nodesByLabel (label: blec, offline: true)
                    nodeListCache[blec] = nodeList
                }

                if (nodeList.size() > 0) {
                    // This combo can work with the constraints
                    dsbc.buildLabelExpression += constraintsNodelabels
                    tmp += dsbc
                } else {
                    // Drop this combo as we can not run it anyway
                    dsbc.isExcluded = true
                    removedCNL++
                    if (debugMilestonesDetails) this.script.println "[DEBUG] generateBuildConfigSet(): excluded combo: ${dsbc}\nwith Node Labels constraints: (${dsbc.buildLabelExpression}) ${constraintsNodelabels}"
                }
            }
            dsbcSet = tmp

            if (debugMilestonesDetails) this.script.println "[DEBUG] generateBuildConfigSet(): excluded ${removedCNL} build combos due to additional Node Labels constraints"
        }

        if (Utils.isMapNotEmpty(dynacfgBuild.dsbcStageTimeoutSettings)) {
            dsbcSet.each() {DynamatrixSingleBuildConfig dsbc ->
                dsbc.stageTimeoutSettings = dynacfgBuild.dsbcStageTimeoutSettings
            }
        }

        // Uncomment here to just detail the collected combos:
        //this.enableDebugMilestonesDetails = true
        //debugTrace = this.shouldDebugTrace()
        //debugErrors = this.shouldDebugErrors()
        //debugMilestones = this.shouldDebugMilestones()
        //debugMilestonesDetails = this.shouldDebugMilestonesDetails()

        if (true) { // debugMilestonesDetails) {
            String msg = "generateBuildConfigSet(): collected ${dsbcSet.size()} combos for individual builds"
            if (removedTotal > 0) {
                msg += " with ${removedTotal} hits removed from candidate builds matrix"
            }
            if (allowedToFailTotal > 0) {
                msg += ", including ${allowedToFailTotal} combos allowed to fail - which we would " +
                    (dynacfgBuild.runAllowedFailure ? "" : "not ") + "run"
            }
            this.script.println msg
        }

        if (debugMilestonesDetails) {
            dsbcSet.each() {DynamatrixSingleBuildConfig dsbc ->
                this.script.println "[DEBUG] generateBuildConfigSet(): selected combo final: ${dsbc}"
            }
        }

        return dsbcSet
    } // generateBuildConfigSet()

    /**
     * Helper for {@link #generateBuild} method to not repeat the
     * same code structure in different handled situations.
     */
    private Closure generatedBuildWrapperLayer2 (String stageName, DynamatrixSingleBuildConfig dsbc, Closure body = null) {
    //private Closure generatedBuildWrapperLayer2 (stageName, dsbc, body = null) {
        boolean debugTrace = this.shouldDebugTrace()

        // For delegation to closure and beyond
        def script = this.script
        body.delegate.script = this.script

        // echo's below are not debug-decorated, in these cases they are the payload
//CLS//
        return { ->
            script.withEnv(dsbc.getKVSet().sort()) { // by side effect, sorting turns the Set into an array
//SCR//                script.script {

                    if (body == null) {
                        if (dsbc.requiresBuildNode) {
                            script.echo "[GENERATED-PARALLELS-INFO] Running stage: ${stageName} with no body, so just showing the resulting environment" // + "\n    for ${Utils.castString(dsbc)}"
                            if (script.isUnix()) {
                                //script.sh "hostname; set"
                                script.sh "hostname; set | egrep '^(ARCH|BITS|COMPILER|CSTD|PWD=|OS|CLANGVER|GCCVER|STAGE|NODE|MATRIX_TAG)'"
                            } else {
                                script.bat "set"
                            }
                        } else {
                            script.echo "[GENERATED-PARALLELS-INFO] Running stage: ${stageName} with no body, and no node{}. So this is it :)"
                        } // if node
                    } else {
                        if (!dsbc.requiresBuildNode) {
                            script.echo "[GENERATED-PARALLELS-WARNING] " +
                                "Running stage: ${stageName} with a body{}, but no node{}. " +
                                "If that body would call some OS interaction, the stage would fail."
                        }

                        if (debugTrace) script.echo "[GENERATED-PARALLELS-DEBUG] " +
                            "Running caller-provided Closure body=${Utils.castString(body)} " +
                            "for stage ${body.delegate.stageName} with " +
                            "dsbc=${Utils.castString(body.delegate.dsbc)}"

                        // Something is very weird with the (ab)use of one
                        // closure instance defined in pipeline for re-use
                        // with many different delegated values. It just
                        // keeps re-using same body.delegate value (or one
                        // of few, which is even less explicable) most of
                        // the time not matching the parallel build scenario.
                        // If the body closure is cloned, it loses even that
                        // way of delegation setup. Passing same delegation
                        // by parameter works, so a clumsy but usable case is:
                        // generatedBuild(...) { delegate ->
                        //     setDelegate(delegate)
                        //     echo "${stageName} ==> ${dsbc.clioptSet.toString()}"
                        // }
                        //NONCLS?// body(body.delegate)
                        body.call(body.delegate)
/*
                        // Alas, this does not work to simplify the pipeline
                        // side of code:
                        def bodyX = { bbody, delegate ->
                            bbody.setDelegate(delegate)
                            bbody()
                        }
                        bodyX(body, body.delegate)
*/

                    } // if body

//SCR//                } // script
            } // withEnv
//CLS//
        } // return a Closure

    }

    /** Helper for {@link #generateBuild} method to not repeat the
     * same code structure in different handled situations.<br/>
     *
     * The stageName may be hard to (re-)calculate and/or
     * may be tweaked for some build scenario, so we pass
     * the string around.
     */
    private Closure generatedBuildWrapperLayer1 (String stageName, DynamatrixSingleBuildConfig dsbc, Closure body = null) {
    //private Closure generatedBuildWrapperLayer1 (stageName, dsbc, body = null) {
//CLS//
        return { ->
//            script.stage(stageName) {
                if (dsbc.enableDebugTrace) script.echo "Running stage: ${stageName}" + "\n  for ${Utils.castString(dsbc)}" + (body == null ? "\n  with a NULL body" : "")
                if (dsbc.isAllowedFailure) {
                    script.catchError(
                        message: "Failed stage which is allowed to fail: ${stageName}" + "\n  for ${Utils.castString(dsbc)}",
                        buildResult: 'SUCCESS', stageResult: 'FAILURE'
                    ) {
                        script.withEnv(['CI_ALLOWED_FAILURE=true']) {
                            Closure payloadLayer2 = dsbc.thisDynamatrix.
                                generatedBuildWrapperLayer2(stageName, dsbc, body)//CLS//.call()
                            return payloadLayer2()
                            //return payloadLayer2
                        }
                    } // catchError
                } else {
                    Closure payloadLayer2 = dsbc.thisDynamatrix.
                        generatedBuildWrapperLayer2(stageName, dsbc, body)//CLS//.call()
                    return payloadLayer2()
                    //return payloadLayer2
                } // if allowedFailure
//            } // stage
//CLS//
        } // return a Closure

    }

    // Follow notes from https://gist.github.com/jimklimov/28e480a635c8de2d0cdf2250a4277c4f
    // to help avoid overlap between objects of parallel stages,
    // causing VERY MISLEADING stuff like:
    // Building with GCC-10 STD=c89 STD=c++98 on x86_64 64 linux-debian11
    // platform for (ARCH_BITS=64&&ARCH64=amd64&&COMPILER=CLANG&&CLANGVER=10&&OS_DISTRO=freebsd12&&OS_FAMILY=bsd)
    // && (nut-builder) && BITS=64&&CSTDVARIANT=c&&CSTDVERSION_c=99&&CSTDVERSION_cxx=98
    // && LANG=C && LC_ALL=C && TZ=UTC (isAllowedFailure) :: as part of slowBuild filter:
    // Default autotools driven build with default warning levels (gnu99/gnu++11)
    // gcc-10 -DHAVE_CONFIG_H -I. -I../include -I../include -std=c89 -m64 -Wall -Wextra -Wsign-compare -Wno-error -MT str.lo -MD -MP -MF .deps/str.Tpo -c str.c  -fPIC -DPIC -o .libs/str.o
    // ...
    // FAILED 'Build' for (ARCH_BITS=64&&ARCH64=amd64&&COMPILER=CLANG&&CLANGVER=10&&OS_DISTRO=freebsd12&&OS_FAMILY=bsd) && (nut-builder) && BITS=64&&CSTDVARIANT=c&&CSTDVERSION_c=99&&CSTDVERSION_cxx=98  &&  LANG=C && LC_ALL=C && TZ=UTC (isAllowedFailure)
    // ... so which platform was it really? which test case? expected fault or not?

    // NOTE: Seems that to ensure object locality, arguments SHOULD NOT be
    // declared with "def" or a specific type!

    /**
     * Helper which returns a Closure for optional build-log trace and
     * execution of the payload without a dedicated build agent.
     */
    Closure generateParstageWithoutAgent (def script, DynamatrixSingleBuildConfig dsbc, String stageName, String sbName, Closure payload) {
    // def generateParstageWithoutAgent(script, dsbc, stageName, sbName, payload) {
        return { ->
            if (dsbc.enableDebugTrace) script.echo "Not requesting any node for stage '${stageName}'" + sbName
            payload()
        }
    }

    /**
     * Helper which returns a Closure for optional build-log trace and
     * execution of the payload on a dedicated build agent selected by
     * the {@code dsbc.buildLabelExpression}.
     */
    Closure generateParstageWithAgentBLE(def script, DynamatrixSingleBuildConfig dsbc, String stageName, String sbName, Closure payload) {
    // def generateParstageWithAgentBLE(script, dsbc, stageName, sbName, payload) {
        return { ->
            if (dsbc.enableDebugTrace) script.echo "Requesting a node by label expression '${dsbc.buildLabelExpression}' for stage '${stageName}'" + sbName
            script.node (dsbc.buildLabelExpression) {
                if (dsbc.enableDebugTrace) script.echo "Starting on node '${script.env?.NODE_NAME}' requested by label expression '${dsbc.buildLabelExpression}' for stage '${stageName}'" + sbName
                payload()
            } // node
        }
    }

    /**
     * Helper which returns a Closure for optional build-log trace and
     * execution of the payload on a dedicated build agent selected as
     * "any" currently available one.
     */
    Closure generateParstageWithAgentAnon(def script, DynamatrixSingleBuildConfig dsbc, String stageName, String sbName, Closure payload) {
    // def generateParstageWithAgentAnon(script, dsbc, stageName, sbName, payload) {
        return { ->
            if (dsbc.enableDebugTrace) script.echo "Requesting any node for stage '${stageName}'" + sbName
            script.node {
                if (dsbc.enableDebugTrace) script.echo "Starting on node '${script.env?.NODE_NAME}' requested as 'any' for stage '${stageName}'" + sbName
                payload()
            } // node
        }
    }

    /** @see generateBuild(Map, boolean, Closure) */
    def generateBuild(Map dynacfgOrig = [:], Closure bodyOrig = null) {
        return generateBuild(dynacfgOrig, false, bodyOrig)
    }

    /**
     * Returns a Map of stages generated according to "dynacfgOrig".
     * Or a Set, if called from inside a pipeline stage (CPS code) --
     * see "returnSet" parameter.
     */
//    @NonCPS
    def generateBuild(Map dynacfgOrig = [:], boolean returnSet, Closure bodyOrig = null) {
        //boolean debugErrors = this.shouldDebugErrors()
        //boolean debugTrace = this.shouldDebugTrace()
        //boolean debugMilestones = this.shouldDebugMilestones()
        boolean debugMilestonesDetails = this.shouldDebugMilestonesDetails()

        if (needsPrepareDynamatrixClone(dynacfgOrig)) {
            if (debugMilestonesDetails
            //|| debugMilestones
            //|| debugErrors
            //|| debugTrace
            ) {
                this.script.println "[DEBUG] generateBuild(): running a configuration that needs a new dynamatrix in a clone"
            }

            Dynamatrix dmClone = this.clone()
            dmClone.clearNeedsPrepareDynamatrixClone(dynacfgOrig)
            dmClone.prepareDynamatrix(dynacfgOrig)
            // Don't forget to clear the config, to not loop on this
            return dmClone.generateBuild(
                clearMapNeedsPrepareDynamatrixClone(dynacfgOrig),
                returnSet, bodyOrig)
        }

        Set<DynamatrixSingleBuildConfig> dsbcSet = generateBuildConfigSet(dynacfgOrig)
        Dynamatrix thisDynamatrix = this
        this.script.println "[DEBUG] generateBuild(): current thisDynamatrix.failFast setting when generating parallelStages: ${thisDynamatrix.failFast}"

        // Consider allowedFailure (if flag runAllowedFailure==true)
        // when preparing the stages below:
        /** `parallelStages` actually contains tuples for mapping <String, Closure> */
        Set<List> parallelStages = []
        dsbcSet.each() {DynamatrixSingleBuildConfig dsbcTmp ->
            // copy a unique object ASAP, otherwise stuff gets mixed up
            DynamatrixSingleBuildConfig dsbc = dsbcTmp.clone()
            dsbc.thisDynamatrix = thisDynamatrix
            String stageName = dsbc.stageName()

            if (dsbc.isExcluded) {
                if (debugMilestonesDetails
                //|| debugMilestones
                //|| debugTrace
                ) {
                    this.script.println "[DEBUG] generateBuild(): selected combo stageName: ${stageName} marked isExcluded, skipping"
                }
                return // continue
            }

            Closure body = null
            if (bodyOrig != null) {
                String sn = "${stageName}" // ensure a copy
                Map bodyData = [ dsbc: dsbc.clone(), stageName: sn ]
                body = bodyOrig.clone().rehydrate(bodyData, this.script, body)
                body.resolveStrategy = Closure.DELEGATE_FIRST
                if (!Utils.isMapNotEmpty(body.delegate))
                    body.delegate = [:]
                body.delegate.dsbc = dsbc
                body.delegate.stageName = stageName
            }

            String matrixTag = null
            if (true) { //scope
                def mtMatcher = stageName =~ ~/^(\S*MATRIX_TAG="[^"]+") .*$/
                if (mtMatcher.find()) {
                    matrixTag = mtMatcher[0][1]?.replaceAll('"', '')?.trim()
                    if ("".equals(matrixTag)) matrixTag = null
                }
            }

            if (debugMilestonesDetails
            //|| debugMilestones
            //|| debugTrace
            ) {
                this.script.println "[DEBUG] generateBuild(): selected combo stageName: ${stageName}"
            }

            // Named closure to call below
            Closure payload = //NONCLS//{
                generatedBuildWrapperLayer1(stageName, dsbc, body)//CLS//.call()
            //NONCLS//}

            // Pattern for code change on the fly:
            if (matrixTag != null) {
                Closure payloadTmp = payload
                payload = { script.withEnv([matrixTag]) { payloadTmp() } }
            }

            String sbName = ""
            if (script?.env?.CI_SLOW_BUILD_FILTERNAME) {
                Closure payloadTmp = payload
                String weStr = "CI_SLOW_BUILD_FILTERNAME=${script.env.CI_SLOW_BUILD_FILTERNAME}"
                payload = { script.withEnv([weStr]) { payloadTmp() } }
                sbName = " :: as part of slowBuild filter: ${script.env.CI_SLOW_BUILD_FILTERNAME}"
            }

            // Used to be '/images/48x48/warning.png', an arg for createSummary()
            // Maybe sourced from https://github.com/jenkinsci/jenkins/blob/master/war/src/main/webapp/images/48x48 :
            def badgeImageDSBCcaughtException = '/images/48x48/aborted.png'	// '/images/svgs/warning.svg'

            // Support optional "failFastSafe" mechanism to raise the "mustAbort" flag
            if (true) { // scoping
                // Note: such implementation effectively relies on the node{}
                // queue, so some stages are in flight and would be allowed
                // to complete, while others not yet started would abort as
                // soon as a node is available to handle them.
                // TODO: If it is possible to cancel them from the queue and
                // not block on waiting, like parallel step "failFast:true"
                // option does, that would be better (cheaper, faster).
                Closure payloadTmp = payload

                payload = {
                    // We allow the setting change to take effect at run-time,
                    // e.g. to generate the parallels and to configure the
                    // "parent" dynamatrix failFast field in any order.
                    // So this is not a conditional redefinition of payload
                    // (which for some reason did not work anyway), but an
                    // "always redefined" conditional way to run the payload.
                    if (dsbc.thisDynamatrix?.failFast) {
                        if (dsbc.thisDynamatrix?.mustAbort || !(script?.currentBuild?.result in [null, 'SUCCESS'])) {
                            script.echo "Aborting single build scenario for stage '${stageName}' due to raised mustAbort flag or known build failure elsewhere"
                            this.script?.echo "countStagesIncrement(): ABORTED_SAFE: failFast + mustAbort"
                            dsbc.thisDynamatrix?.countStagesIncrement('ABORTED_SAFE', stageName + sbName)
                            dsbc.dsbcResultInterim = 'ABORTED_SAFE'
                            throw new FlowInterruptedException(Result.NOT_BUILT)

                            //script?.currentBuild?.result = 'ABORTED'
                            //throw new FlowInterruptedException(Result.ABORTED)
                        }

                        def payloadRes = null
                        try {
                            payloadRes = payloadTmp()
                            if (!(script?.currentBuild?.result in [null, 'SUCCESS'])) {
                                script.echo "Raising mustAbort flag to prevent build scenarios which did not yet start from starting, fault detected after stage '${stageName}': current build result is now at best ${script?.currentBuild?.result}"
                                dsbc.thisDynamatrix?.mustAbort = true
                                // mangle payloadRes ?
                            }
                        } catch (Exception ex) {
                            script.echo "Raising mustAbort flag to prevent build scenarios which did not yet start from starting, fault detected after stage '${stageName}': got exception: ${ex.toString()}"
                            dsbc.thisDynamatrix?.mustAbort = true
                        }
                        return payloadRes
                    } else {
                        payloadTmp()
                    }
                }
            }

            if (Utils.isMapNotEmpty(dsbc.stageTimeoutSettings)) {
                // Further parstageCode optional wrapper which limits how much
                // time a stage may run - primarily to retry if an agent node
                // itself gets stuck/disconnected, but Jenkins won't notice:
                //     dsbc.stageTimeoutSettings = {time: 12, unit: "HOURS"}
                Closure payloadTmp = payload

                payload = {
                    Throwable caught = null
                    try {
                        script.timeout (dsbc.stageTimeoutSettings) {
                            payloadTmp
                        }
                    } catch (FlowInterruptedException fie) {
                        dsbc.dsbcResultInterim = 'AGENT_TIMEOUT'
                        caught = fie
                        // will re-throw for other wraps to handle and account this
                    } catch (Throwable t) {
                        caught = t
                    }
                    if (caught) throw caught
                }
            }

            // Support accounting of slowBuild scenario outcomes
            if (true) { // scoping
                Closure payloadTmp = payload

                payload = {
                    def printStackTraceStderrOptional = { Throwable t ->
                        if (enableDebugSysprint) {
                            StringWriter errors = new StringWriter();
                            t.printStackTrace(new PrintWriter(errors));
                            System.err.println("[${script?.env?.BUILD_TAG}] " +
                                "[DEBUG]: DSBC requested " +
                                "for stage '${stageName}'" + sbName +
                                " threw an exception and got interim " +
                                "verdict '${dsbc.dsbcResultInterim}': " +
                                errors.toString()
                                )
                        }
                    }

                    dsbc.startCount = dsbc.startCount + 1
                    if (dsbc.dsbcResultInterim == null) {
                        dsbc.thisDynamatrix?.countStagesIncrement('STARTED', stageName + sbName)
                    } else {
                        String msgRestart = "[WARNING] Re-starting " +
                            "DSBC requested for stage '" + stageName + sbName +
                            "' which ended with '${dsbc.dsbcResultInterim}' " +
                            "on a previous attempt"
                        script.echo msgRestart
                        if (enableDebugSysprint) System.out.println("[${script?.env?.BUILD_TAG}] " + msgRestart)
                        createSummary(msgRestart, null, "${dsbc.objectID}-restarted-${dsbc.startCount}")
                        dsbc.thisDynamatrix?.countStagesIncrement('RESTARTED', stageName + sbName)
                        dsbc.dsbcResultInterim = null
                    }
                    dsbc.thisDynamatrix?.updateProgressBadge()
                    try {
                        def res = payloadTmp()
                        this.script?.echo "countStagesIncrement(): some verdict after payload, no exception"
                        if (dsbc.dsbcResult != null) {
                            dsbc.thisDynamatrix?.countStagesIncrement(dsbc.dsbcResult, stageName + sbName)
                            dsbc.dsbcResultInterim = dsbcResult.toString()
                        } else {
                            if (dsbc.thisDynamatrix?.trackStageResults?.containsKey(stageName)
                            &&  dsbc.thisDynamatrix?.trackStageResults[stageName] != null
                            ) {
                                dsbc.dsbcResultInterim = dsbc.thisDynamatrix?.trackStageResults[stageName]
                                if (dsbc.dsbcResultInterim != 'ABORTED_SAFE') {
                                    // 'ABORTED_SAFE' was immediately accounted above
                                    dsbc.thisDynamatrix?.countStagesIncrement(dsbc.dsbcResultInterim, stageName + sbName)
                                }
                            } else {
                                dsbc.thisDynamatrix?.countStagesIncrement('SUCCESS', stageName + sbName)
                                dsbc.dsbcResultInterim = 'SUCCESS'
                            }
                        }
                        dsbc.thisDynamatrix?.countStagesIncrement('COMPLETED', stageName + sbName)
                        dsbc.thisDynamatrix?.updateProgressBadge()
                        return res
                    } catch (FlowInterruptedException fex) {
                        dsbc.thisDynamatrix?.countStagesIncrement('COMPLETED', stageName + sbName)
                        if (Utils.isStringNotEmpty(dsbc.dsbcResultInterim)
                        &&  (Utils.isMapNotEmpty(dsbc.stageTimeoutSettings)
                             || dsbc.dsbcResultInterim == 'ABORTED_SAFE')
                        ) {
                            // Can be our stageTimeoutSettings handler, not an abortion
                            if (!(dsbc.thisDynamatrix?.trackStageResults[stageName] == 'ABORTED_SAFE' && dsbc.dsbcResultInterim == 'ABORTED_SAFE')) {
                                this.script?.echo "countStagesIncrement(): some verdict after payload, with exception: fex#1"
                                dsbc.thisDynamatrix?.countStagesIncrement(dsbc.dsbcResultInterim, stageName + sbName)
                            }
                        } else {
                            if (fex == null) {
                                dsbc.thisDynamatrix?.countStagesIncrement('UNKNOWN', stageName + sbName)
                                dsbc.dsbcResultInterim = 'UNKNOWN'
                            } else {
                                String fexres = fex.getResult()
                                if (fexres == null) {
                                    fexres = 'SUCCESS'
                                } else {
                                    StringWriter errors = new StringWriter();
                                    fex.printStackTrace(new PrintWriter(errors));
                                    if (errors.toString().contains('RemovedNodeListener')) {
                                        // at org.jenkinsci.plugins.workflow.support.steps.ExecutorStepExecution$RemovedNodeListener.lambda$onDeleted$3(ExecutorStepExecution.java:288)
                                        fexres = 'AGENT_DISCONNECTED'
                                    }
                                }

                                this.script?.echo "countStagesIncrement(): some verdict after payload, with exception: fex#2"
                                dsbc.thisDynamatrix?.countStagesIncrement(fexres, stageName + sbName)
                                dsbc.dsbcResultInterim = fexres
                            }
                        }
                        dsbc.thisDynamatrix?.updateProgressBadge()
                        printStackTraceStderrOptional(fex)
                        throw fex
                    } catch (AbortException hexA) {
                        // This is thrown by steps like "error" and "unstable" (both)
                        dsbc.thisDynamatrix?.countStagesIncrement('COMPLETED', stageName + sbName)
                        this.script?.echo "countStagesIncrement(): some verdict after payload, with exception: hexA"
                        if (dsbc.dsbcResult != null) {
                            dsbc.thisDynamatrix?.countStagesIncrement(dsbc.dsbcResult, stageName + sbName)
                            dsbc.dsbcResultInterim = dsbc.dsbcResult.toString()
                        } else {
                            if (hexA == null) {
                                dsbc.thisDynamatrix?.countStagesIncrement('UNKNOWN', stageName + sbName)
                                dsbc.dsbcResultInterim = 'UNKNOWN'
                            } else {
                                // Involve localization?..
                                switch (hexA.toString()) {
                                    case ~/.*missing workspace.*/ :
                                        dsbc.thisDynamatrix?.countStagesIncrement('AGENT_DISCONNECTED', stageName + sbName)
                                        dsbc.dsbcResultInterim = 'AGENT_DISCONNECTED'
                                        break

                                    case ~/.*Error cloning remote repo.*/ :
                                        // For now treat these SCM issues similar to
                                        // networking faults - something to retry:
                                        dsbc.thisDynamatrix?.countStagesIncrement('AGENT_DISCONNECTED', stageName + sbName)
                                        dsbc.dsbcResultInterim = 'AGENT_DISCONNECTED'
                                        break

                                    case ~/.*script returned exit code [^0].*/ :
                                        dsbc.thisDynamatrix?.countStagesIncrement('FAILURE', stageName + sbName)
                                        dsbc.dsbcResultInterim = 'FAILURE'
                                        break

                                    default:
                                        String hexAres = "hudson.AbortException: " +
                                            "Message: " + hexA.getMessage() +
                                            "; Cause: " + hexA.getCause() +
                                            "; toString: " + hexA.toString();
                                        dsbc.thisDynamatrix?.countStagesIncrement('FAILURE', stageName + sbName) // could be unstable, learn how to differentiate?
                                        dsbc.dsbcResultInterim = 'hudson.AbortException'

                                        def msgEx =
                                            "A DSBC stage running on node " +
                                            "'${script.env?.NODE_NAME}' requested " +
                                            "for stage '${stageName}'" + sbName +
                                            " completed with an exception:\n" +
                                            hexAres

                                        if (dsbc.enableDebugTraceFailures) {
                                            dsbc.thisDynamatrix?.countStagesIncrement('DEBUG-EXC-FAILURE: ' + hexAres, stageName + sbName) // for debug
                                            StringWriter errors = new StringWriter();
                                            hexA.printStackTrace(new PrintWriter(errors));
                                            script.echo "[DEBUG] " + msgEx +
                                                "\nDetailed trace: " + errors.toString()
                                        } else {
                                            script.echo "[ERROR] " + msgEx
                                        }

                                        createSummary(msgEx, badgeImageDSBCcaughtException, "${dsbc.objectID}-exception-${dsbc.startCount}")
                                        break
                                }
                            }
                        }
                        dsbc.thisDynamatrix?.updateProgressBadge()
                        printStackTraceStderrOptional(hexA)
                        throw hexA
                    } catch (RequestAbortedException rae) {
                        // https://javadoc.jenkins.io/component/remoting/hudson/remoting/RequestAbortedException.html
                        // > Signals that the communication is aborted and thus
                        // > the pending Request will never recover its Response.
                        // hudson.remoting.RequestAbortedException: java.io.IOException:
                        // Unexpected termination of the channel
                        dsbc.thisDynamatrix?.countStagesIncrement('COMPLETED', stageName + sbName)
                        if (rae == null) {
                            dsbc.thisDynamatrix?.countStagesIncrement('UNKNOWN', stageName + sbName)
                            dsbc.dsbcResultInterim = 'UNKNOWN'
                        } else {
                            // Involve localization?..
                            if (rae.toString() ==~ /.*(Unexpected termination of the channel|java.nio.channels.ClosedChannelException).*/
                            ) {
                                dsbc.thisDynamatrix?.countStagesIncrement('AGENT_DISCONNECTED', stageName + sbName)
                                dsbc.dsbcResultInterim = 'AGENT_DISCONNECTED'
                            } else {
                                String raeRes = "hudson.remoting.RequestAbortedException: " +
                                    "Message: " + rae.getMessage() +
                                    "; Cause: " + rae.getCause() +
                                    "; toString: " + rae.toString();
                                dsbc.thisDynamatrix?.countStagesIncrement('UNKNOWN', stageName + sbName) // FAILURE technically, but one we could not classify exactly
                                dsbc.dsbcResultInterim = 'hudson.remoting.RequestAbortedException'

                                def msgEx =
                                    "A DSBC stage running on node " +
                                    "'${script.env?.NODE_NAME}' requested " +
                                    "for stage '${stageName}'" + sbName +
                                    " completed with an exception:\n" +
                                    raeRes

                                if (dsbc.enableDebugTraceFailures) {
                                    dsbc.thisDynamatrix?.countStagesIncrement('DEBUG-EXC-UNKNOWN: ' + raeRes, stageName + sbName) // for debug
                                    StringWriter errors = new StringWriter();
                                    rae.printStackTrace(new PrintWriter(errors));
                                    script.echo "[DEBUG] " + msgEx +
                                        "\nDetailed trace: " + errors.toString()
                                } else {
                                    script.echo "[ERROR] " + msgEx
                                }

                                createSummary(msgEx, badgeImageDSBCcaughtException, "${dsbc.objectID}-exception-${dsbc.startCount}")
                            }
                        }
                        dsbc.thisDynamatrix?.updateProgressBadge()
                        printStackTraceStderrOptional(rae)
                        throw rae
                    } catch (RemotingSystemException rse) {
                        // Tends to happen with networking lags or agent crash, e.g.:
                        //   hudson.remoting.RemotingSystemException:
                        //   java.io.IOException: SSH channel is closed
                        dsbc.thisDynamatrix?.countStagesIncrement('COMPLETED', stageName + sbName)
                        if (rse == null) {
                            dsbc.thisDynamatrix?.countStagesIncrement('UNKNOWN', stageName + sbName)
                            dsbc.dsbcResultInterim = 'UNKNOWN'
                        } else {
                            // Involve localization?..
                            if (rse.toString() ==~ /.*(Unexpected termination of the channel|java.nio.channels.ClosedChannelException).*/
                            ) {
                                dsbc.thisDynamatrix?.countStagesIncrement('AGENT_DISCONNECTED', stageName + sbName)
                                dsbc.dsbcResultInterim = 'AGENT_DISCONNECTED'
                            } else {
                                String rseRes = "hudson.remoting.RemotingSystemException: " +
                                    "Message: " + rse.getMessage() +
                                    "; Cause: " + rse.getCause() +
                                    "; toString: " + rse.toString();
                                dsbc.thisDynamatrix?.countStagesIncrement('UNKNOWN', stageName + sbName) // FAILURE technically, but one we could not classify exactly
                                dsbc.dsbcResultInterim = 'hudson.remoting.RemotingSystemException'

                                def msgEx =
                                    "A DSBC stage running on node " +
                                    "'${script.env?.NODE_NAME}' requested " +
                                    "for stage '${stageName}'" + sbName +
                                    " completed with an exception:\n" +
                                    rseRes

                                if (dsbc.enableDebugTraceFailures) {
                                    dsbc.thisDynamatrix?.countStagesIncrement('DEBUG-EXC-UNKNOWN: ' + rseRes, stageName + sbName) // for debug
                                    StringWriter errors = new StringWriter();
                                    rse.printStackTrace(new PrintWriter(errors));
                                    script.echo "[DEBUG] " + msgEx +
                                        "\nDetailed trace: " + errors.toString()
                                } else {
                                    script.echo "[ERROR] " + msgEx
                                }

                                createSummary(msgEx, badgeImageDSBCcaughtException, "${dsbc.objectID}-exception-${dsbc.startCount}")
                            }
                        }
                        dsbc.thisDynamatrix?.updateProgressBadge()
                        printStackTraceStderrOptional(rse)
                        throw rse
                    } catch (IOException jioe) {
                        // Tends to happen with networking lags or agent crash, e.g.:
                        //   java.io.IOException: Unable to create live FilePath for agentName
                        dsbc.thisDynamatrix?.countStagesIncrement('COMPLETED', stageName + sbName)
                        if (jioe == null) {
                            dsbc.thisDynamatrix?.countStagesIncrement('UNKNOWN', stageName + sbName)
                            dsbc.dsbcResultInterim = 'UNKNOWN'
                        } else {
                            // Involve localization?..
                            if (jioe.toString() ==~ /.*(Unable to create live FilePath for|No space left on device|was marked offline|Connection was broken).*/
                            ) {
                                // Note: "No space left" is not exactly a disconnection, but is
                                // a cause to retry the stage on another agent (or even same one
                                // after someone else cleans up) rather than fail the build.
                                // As for "live FilePath", this can mean a Remoting (comms) error.
                                // Per https://github.com/jenkinsci/workflow-durable-task-step-plugin/blob/master/src/main/java/org/jenkinsci/plugins/workflow/support/steps/FilePathDynamicContext.java
                                // in this case Jenkins would terminate the build agent connection
                                dsbc.thisDynamatrix?.countStagesIncrement('AGENT_DISCONNECTED', stageName + sbName)
                                dsbc.dsbcResultInterim = 'AGENT_DISCONNECTED'
                            } else {
                                String jioeRes = "java.io.IOException: " +
                                    "Message: " + jioe.getMessage() +
                                    "; Cause: " + jioe.getCause() +
                                    "; toString: " + jioe.toString();
                                dsbc.thisDynamatrix?.countStagesIncrement('UNKNOWN', stageName + sbName) // FAILURE technically, but one we could not classify exactly
                                dsbc.dsbcResultInterim = 'java.io.IOException'

                                def msgEx =
                                    "A DSBC stage running on node " +
                                    "'${script.env?.NODE_NAME}' requested " +
                                    "for stage '${stageName}'" + sbName +
                                    " completed with an exception:\n" +
                                    jioeRes

                                if (dsbc.enableDebugTraceFailures) {
                                    dsbc.thisDynamatrix?.countStagesIncrement('DEBUG-EXC-UNKNOWN: ' + jioeRes, stageName + sbName) // for debug
                                    StringWriter errors = new StringWriter();
                                    jioe.printStackTrace(new PrintWriter(errors));
                                    script.echo "[DEBUG] " + msgEx +
                                        "\nDetailed trace: " + errors.toString()
                                } else {
                                    script.echo "[ERROR] " + msgEx
                                }

                                createSummary(msgEx, badgeImageDSBCcaughtException, "${dsbc.objectID}-exception-${dsbc.startCount}")
                            }
                        }
                        dsbc.thisDynamatrix?.updateProgressBadge()
                        printStackTraceStderrOptional(jioe)
                        throw jioe
                    } catch (InterruptedException jlie) {
                        // Tends to happen if e.g. Jenkins restarted during build
                        dsbc.thisDynamatrix?.countStagesIncrement('COMPLETED', stageName + sbName)
                        if (jlie == null) {
                            dsbc.thisDynamatrix?.countStagesIncrement('UNKNOWN', stageName + sbName)
                            dsbc.dsbcResultInterim = 'UNKNOWN'
                        } else {
                            // Involve localization?..
                            if (jlie.toString() ==~ /.*(Unexpected termination of the channel|Cannot contact .*: java.lang.InterruptedException|java.nio.channels.ClosedChannelException).*/
                            ) {
                                dsbc.thisDynamatrix?.countStagesIncrement('AGENT_DISCONNECTED', stageName + sbName)
                                dsbc.dsbcResultInterim = 'AGENT_DISCONNECTED'
                            } else {
                                String jlieRes = "java.lang.InterruptedException: " +
                                    "Message: " + jlie.getMessage() +
                                    "; Cause: " + jlie.getCause() +
                                    "; toString: " + jlie.toString();
                                dsbc.thisDynamatrix?.countStagesIncrement('UNKNOWN', stageName + sbName) // FAILURE technically, but one we could not classify exactly
                                dsbc.dsbcResultInterim = 'java.lang.InterruptedException'

                                def msgEx =
                                    "A DSBC stage running on node " +
                                    "'${script.env?.NODE_NAME}' requested " +
                                    "for stage '${stageName}'" + sbName +
                                    " completed with an exception:\n" +
                                    jlieRes

                                if (dsbc.enableDebugTraceFailures) {
                                    dsbc.thisDynamatrix?.countStagesIncrement('DEBUG-EXC-UNKNOWN: ' + jlieRes, stageName + sbName) // for debug
                                    StringWriter errors = new StringWriter();
                                    jlie.printStackTrace(new PrintWriter(errors));
                                    script.echo "[DEBUG] " + msgEx +
                                        "\nDetailed trace: " + errors.toString()
                                } else {
                                    script.echo "[ERROR] " + msgEx
                                }

                                createSummary(msgEx, badgeImageDSBCcaughtException, "${dsbc.objectID}-exception-${dsbc.startCount}")
                            }
                        }
                        dsbc.thisDynamatrix?.updateProgressBadge()
                        printStackTraceStderrOptional(jlie)
                        throw jlie
                    } catch (Throwable t) {
                        dsbc.thisDynamatrix?.countStagesIncrement('DEBUG-EXC-UNKNOWN: ' + Utils.castString(t), stageName + sbName)
                        dsbc.thisDynamatrix?.updateProgressBadge()
                        dsbc.dsbcResultInterim = 'Throwable'

                        String tRes = "Got a Throwable not classified specifically: " +
                            "Message: " + t.getMessage() +
                            "; Cause: " + t.getCause() +
                            "; toString: " + Utils.castString(t);

                        def msgEx =
                            "[DEBUG] A DSBC stage running on node " +
                            "'${script.env?.NODE_NAME}' requested " +
                            "for stage '${stageName}'" + sbName +
                            " completed with an exception:\n" +
                            tRes

                        if (dsbc.enableDebugTraceFailures) {
                            StringWriter errors = new StringWriter();
                            t.printStackTrace(new PrintWriter(errors));
                            script.echo "[DEBUG] " + msgEx +
                                "\nDetailed trace: " + errors.toString()
                        } else {
                            script.echo "[ERROR] " + msgEx
                        }

                        createSummary(msgEx, badgeImageDSBCcaughtException, "${dsbc.objectID}-exception-${dsbc.startCount}")

                        printStackTraceStderrOptional(t)
                        throw t
                    }
                }
            }

            // Note: non-declarative pipeline syntax inside the generated stages
            // in particular, no steps{}. Note that most of our builds require a
            // build agent, usually a specific one, to run some programs in that
            // OS. For generality there is a case for no-node mode, but that may
            // only call pipeline steps (schedule a build, maybe analyze, etc.)
            String parstageName = null
            Closure parstageCode = null
            if (dsbc.requiresBuildNode) {
                if (Utils.isStringNotEmpty(dsbc.buildLabelExpression)) {
                    parstageName = "WITHAGENT: " + stageName + sbName
                    //parstageName = "NODEWRAP: " + stageName + sbName
                    parstageCode = generateParstageWithAgentBLE(script, dsbc, stageName, sbName, payload)
                } else {
                    parstageName = "WITHAGENT-ANON: " + stageName + sbName
                    //parstageName = "NODEWRAP-ANON: " + stageName + sbName
                    parstageCode = generateParstageWithAgentAnon(script, dsbc, stageName, sbName, payload)
                }
            } else {
                // no "${dsbc.buildLabelExpression}" - so runs on job's
                // default node, if any (or fails if none is set and is needed)
                parstageName = "NO-NODE: " + stageName + sbName
                parstageCode = generateParstageWithoutAgent(script, dsbc, stageName, sbName, payload)
            } // if got agent-label

            // Further parstageCode wrapper which looks at dsbc.dsbcResultInterim
            // and reschedules the earlier payload if the error seems retryable.
            // TODO: May need more work in pipeline accounting code to agree
            // that all build scenarios succeeded in the end, even if we ran
            // more than 100% of originally planned job.
            if (true) { // scoping
                def parstageCodeTmp = parstageCode

                parstageCode = {
                    def parstageCompleted = false
                    while (!parstageCompleted) {
                        try {
                            if (enableDebugSysprint)
                              System.err.println("[${script?.env?.BUILD_TAG}] " +
                                "[DEBUG]: DSBC requested " +
                                "for stage '${stageName}'" + sbName +
                                " starting...")

                            parstageCodeTmp()

                            if (enableDebugSysprint)
                              System.err.println("[${script?.env?.BUILD_TAG}] " +
                                "[DEBUG]: DSBC requested " +
                                "for stage '${stageName}'" + sbName +
                                " finished with verdict " +
                                "'${dsbc.dsbcResultInterim}'")

                            // might still be failed, just not re-thrown:
                            switch (dsbc.dsbcResultInterim) {
                                case [null, 'SUCCESS', 'FAILURE', 'UNSTABLE', 'ABORTED', 'NOT_BUILT',
                                      'STARTED', 'RESTARTED', 'COMPLETED', 'ABORTED_SAFE']:
                                    parstageCompleted = true
                                    break

                                case ['AGENT_DISCONNECTED', 'AGENT_TIMEOUT', 'UNKNOWN']:
                                    script.echo "[DEBUG]: DSBC requested " +
                                        "for stage '${stageName}'" + sbName +
                                        " finished with a verdict classified as " +
                                        "'${dsbc.dsbcResultInterim}' - " +
                                        "will re-schedule"
                                    // continue to loop
                                    break

                                default:
                                    script.echo "[DEBUG]: DSBC requested " +
                                        "for stage '${stageName}'" + sbName +
                                        " finished with unclassified verdict " +
                                        "'${dsbc.dsbcResultInterim}' - " +
                                        "will re-schedule"
                                    // continue to loop
                                    break
                            }
                        } catch (Throwable t) {
                            if (enableDebugSysprint)
                              System.err.println("[${script?.env?.BUILD_TAG}] " +
                                "[DEBUG]: DSBC requested " +
                                "for stage '${stageName}'" + sbName +
                                " finished with verdict " +
                                "'${dsbc.dsbcResultInterim}'" +
                                "; a Throwable was caught: ${Utils.castString(t)}")
                            switch (dsbc.dsbcResultInterim) {
                                case [null, 'SUCCESS', 'FAILURE', 'UNSTABLE', 'ABORTED', 'NOT_BUILT']:
                                    script.echo "[DEBUG]: DSBC requested " +
                                        "for stage '${stageName}'" + sbName +
                                        " finished with standard verdict " +
                                        "'${dsbc.dsbcResultInterim}' but a " +
                                        "Throwable was caught: ${Utils.castString(t)}"
                                    parstageCompleted = true
                                    break

                                case ['STARTED', 'RESTARTED', 'COMPLETED', 'ABORTED_SAFE',
                                      'hudson.remoting.RequestAbortedException',
                                      'hudson.remoting.RemotingSystemException',
                                      'java.io.IOException',
                                      'java.lang.InterruptedException'
                                     ]:
                                    script.echo "[DEBUG]: DSBC requested " +
                                        "for stage '${stageName}'" + sbName +
                                        " finished somehow with unexpected verdict " +
                                        "'${dsbc.dsbcResultInterim}' and a " +
                                        "Throwable was caught: ${Utils.castString(t)}"
                                    parstageCompleted = true
                                    break

                                case ['hudson.AbortException']:
                                    script.echo "[DEBUG]: DSBC requested " +
                                        "for stage '${stageName}'" + sbName +
                                        " finished with abortion verdict " +
                                        "'${dsbc.dsbcResultInterim}' and a " +
                                        "Throwable was caught: ${Utils.castString(t)}"
                                    parstageCompleted = true
                                    break

                                case ['AGENT_DISCONNECTED', 'AGENT_TIMEOUT', 'UNKNOWN']:
                                    script.echo "[DEBUG]: DSBC requested " +
                                        "for stage '${stageName}'" + sbName +
                                        " finished with a verdict classified as " +
                                        "'${dsbc.dsbcResultInterim}' - " +
                                        "will re-schedule"
                                    // continue to loop
                                    break

                                default:
                                    script.echo "[DEBUG]: DSBC requested " +
                                        "for stage '${stageName}'" + sbName +
                                        " finished with unclassified verdict " +
                                        "'${dsbc.dsbcResultInterim}' - " +
                                        "aborting the stage-running loop; a " +
                                        "Throwable was caught: ${Utils.castString(t)}"
                                    // DO NOT continue to loop
                                    parstageCompleted = true
                                    // Forward the diagnosis to Jenkins
                                    throw t
                                    break
                            } // switch
                        } // catch

                        if (!parstageCompleted) {
                            // even if we would loop, not too intensively please
                            script.sleep(10)
                        }
                    } // while
                } // new parstageCode
            }

            // record the new parallelStages[] entry
            parallelStages << [parstageName, parstageCode]
        }

        if (returnSet) {
            return parallelStages
        } else {
            // Scope the Map for sake of CPS conversions where we don't want them
            Map<String, Closure> parallelStages_map = [:]
            parallelStages.each { List tup -> parallelStages_map[(String)(tup[0])] = (Closure)(tup[1]) }
            return parallelStages_map
        }
    } // generateBuild()

}
