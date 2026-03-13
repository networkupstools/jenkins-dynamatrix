package org.nut.dynamatrix;

import com.cloudbees.groovy.cps.NonCPS;
import hudson.model.Result;

import org.nut.dynamatrix.Utils;
import org.nut.dynamatrix.Dynamatrix;
import org.nut.dynamatrix.dynamatrixGlobalState;

/**
 * This class intends to represent one single build configuration
 * with its agent label, further "virtual labels", env, opts and
 * other matrix-provided tunables derived from DynamatrixConfig
 * and Dynamatrix class field values.
 */
class DynamatrixSingleBuildConfig implements Cloneable {
    private final String objectID = Integer.toHexString(hashCode())
    private def script = null

    /**
     * Typically may be assigned something like
     *    <pre>stageNameFunc = DynamatrixSingleBuildConfig.&C_StageNameTagFunc</pre>
     */
    Closure stageNameFunc = null

    public boolean enableDebugTrace = dynamatrixGlobalState.enableDebugTrace
    public boolean enableDebugTraceFailures = dynamatrixGlobalState.enableDebugTraceFailures
    public boolean enableDebugErrors = dynamatrixGlobalState.enableDebugErrors

    /** By default, we wipe workspaces after a build */
    public boolean keepWs = false

    /**
     * Most of our builds require a build agent, usually one with
     * specific capabilities as selected by label expression below,
     * to run some programs in that OS. For generality's sake, there
     * is a case for no-node mode, but that may only call pipeline
     * steps (to schedule a build, maybe analyze, etc.)
     */
    public Boolean requiresBuildNode = true

    /**
     * The {@code `expr`} part requested in {@code `agent {label 'expr'}`}
     * for the generated build stage, originates from capabilities declared
     * by build nodes or those explicitly requested by caller.
     */
    public String buildLabelExpression

    /**
     * Original set of requested labels for the agent needed for
     * this particular build, which can include usual labels like
     * {@code "ARCH=armv7l"} and composite labels, which are useful
     * for grouped values like {@code "COMPILER=GCC GCCVER=1.2.3"}.
     * Can be interpreted by the actual building closure (e.g. to
     * generate options for configure script), such as converting
     * {@code ARCH_BITS=64}" into a {@code CFLAGS="$CLFAGS -m32"}
     * addition.
     */
    public Set buildLabelSet

    /**
     * Additional variation from the caller, such as revision of
     * C/C++ standard to use for this particular run. Interpreted
     * by the actual building closure (e.g. to generate options
     * for configure script).<br/>
     *
     * Provided as a set of Strings with key=value pairs like:
     * <pre>
     *   ["CSTDVERSION=17", "CSTDVARIANT=gnu"]
     * </pre>
     * or
     * <pre>
     *   ["CSTDVERSION_c=11", "CSTDVERSION_cxx=14", "CSTDVARIANT=c"]
     * </pre>
     */
    public Set virtualLabelSet

    /**
     * Exported additional environment variables for this build, e.g.:
     * <pre>
     *   ["TZ=UTC", "LANG=C"]
     * </pre>
     */
    public Set envvarSet

    /**
     * Additional command-line options for this build (as interpreted
     * by the actual building closure - perhaps options for a configure
     * script), e.g.:
     * <pre>
     *   ["CFLAGS=-stdc=gnu99", "CXXFLAGS=-stdcxx=g++99", "--without-docs"]
     * </pre>
     * WARNING: Not currently used by common build cell logic (autotools or
     * ci_build), and better-targeted envvars like {@code CONFIG_OPTS} set
     * via {@link DynamatrixConfig#dynamatrixAxesCommonEnv} are recommended.
     */
    public Set clioptSet

    public Boolean isExcluded
    public Boolean isAllowedFailure

    /** The build config can reference which dynamatrix group of build scenarios
     * it would be tracked in, e.g. for failFastSafe support */
    public Dynamatrix thisDynamatrix = null

    /** Help Dynamatrix accounting track intentional error()/unstable()/...
     * exits from each build scenario. See also setWorstResult() below. */
    public Result dsbcResult = null
    /** If this is 'UNKNOWN', exception summary, etc. we may want to retry the
     * build scenario which did not necessarily fail due to bad code. */
    public String dsbcResultInterim = null
    public Integer startCount = 0

    /**
     * As we progress through the matrix cell build, record the filenames
     * here and eventually add related verdicts (shell exit-codes, Result
     * objects, Dynamatrix extended result strings...)<br/>
     * Being a LinkedHashMap this one retains the order of original inserts
     * as we iterate it.<br/>
     * @see #getLatestDsbcResultLog
     */
    public LinkedHashMap<String, Object> dsbcResultLogs = [:]

    /** Same units as for {@code timeout()} step, e.g.
     * <pre>
     *    dsbc.stageTimeoutSettings = {time: 12, unit: "HOURS"}
     * </pre>
     */
    public Map stageTimeoutSettings = [:]

    public DynamatrixSingleBuildConfig (script) {
        this.script = script
        this.enableDebugTrace = dynamatrixGlobalState.enableDebugTrace
        this.enableDebugTraceFailures = dynamatrixGlobalState.enableDebugTraceFailures
        this.enableDebugErrors = dynamatrixGlobalState.enableDebugErrors
    }

    /**
     * Compare class instances as equal in important fields (e.g. to dedup
     * when adding again to Sets), despite possible variation in some
     * inconsequential fields:
     *   "script", "stageNameFunc",
     *   "enableDebugTrace", "enableDebugTraceFailures", "enableDebugErrors",
     *   "isExcluded", "isAllowedFailure"
     */
    public boolean equals(Object other) {
        if (other == null) return false
        if (this.is(other)) return true
        if (!(other instanceof DynamatrixSingleBuildConfig)) return false
        if (!other.canEqual(this)) return false

        if (buildLabelExpression != other.buildLabelExpression) return false
        if (buildLabelSet != other.buildLabelSet) return false
        if (virtualLabelSet != other.virtualLabelSet) return false
        if (envvarSet != other.envvarSet) return false
        if (clioptSet != other.clioptSet) return false

        // This distinction may be important
        if (requiresBuildNode != other.requiresBuildNode) return false

/*
        if (isExcluded != other.isExcluded) return false
        if (isAllowedFailure != other.isAllowedFailure) return false
*/

        return true
    }

    public boolean canEqual(Object other) {
        return other instanceof DynamatrixSingleBuildConfig
    }

    @Override
    public DynamatrixSingleBuildConfig clone() throws CloneNotSupportedException {
        return (DynamatrixSingleBuildConfig) super.clone();
    }

    @NonCPS
    public boolean shouldDebugTrace() {
        return ( this.enableDebugTrace && this.script != null)
    }

    @NonCPS
    public boolean shouldDebugTraceFailures() {
        return ( this.enableDebugTraceFailures && this.script != null)
    }

    @NonCPS
    public boolean shouldDebugErrors() {
        return ( (this.enableDebugTrace || this.enableDebugTraceFailures || this.enableDebugErrors) && this.script != null)
    }

    /**
     * Helper for build attempt accounting. Notifications etc. are up to the caller.
     * @return  (Probably) the new value of startCount
     */
    @NonCPS
    def startNewAttempt() {
        dsbcResultLogs = [:]
        startCount++
    }

    @NonCPS
    synchronized public Result setWorstResult(String k) {
        // NOTE: This might throw if not a valid string from enum,
        // we propagate that exception
        Result r = Result.fromString(k)

        if (this.shouldDebugTrace()) {
            script.println (
                "[TRACE] Old worst result for this DSBC was: ${this.dsbcResult}" +
                "; new assignment is string '${k}' => Result '${r}'")
        }

        if (this.dsbcResult == null) {
            this.dsbcResult = r
        } else {
            this.dsbcResult = this.dsbcResult.combine(r)
        }

        if (this.shouldDebugTrace()) {
            script.println (
                "[TRACE] New worst result for this DSBC is: ${this.dsbcResult}")
        }

        return this.dsbcResult
    }

    @NonCPS
    @Override
    public String toString() {
        return  "DynamatrixSingleBuildConfig: {" +
                "\n    buildLabelExpression: '${buildLabelExpression}'" +
                ",\n    buildLabelSet: '${buildLabelSet}'" +
                ",\n    virtualLabelSet: '${virtualLabelSet}'" +
                ",\n    envvarSet: '${envvarSet}'" +
                ",\n    clioptSet: '${clioptSet}'" +
                ",\n    requiresBuildNode: '${requiresBuildNode}'" +
                ",\n    isExcluded: '${isExcluded}'" +
                ",\n    isAllowedFailure: '${isAllowedFailure}'" +
                "\n}" ;
    }

    @NonCPS
    public String stageName() {
        if (stageNameFunc != null) {
            // may append arg.defaultStageName() if it wants
            // e.g. just   NUT_MATRIX_TAG="gnu99-clang-xcode7.3-nowarn"
            // or fully    NUT_MATRIX_TAG="gnu99-clang-xcode7.3-nowarn" BUILD_TYPE=default-all-errors CFLAGS="-std=gnu99" CXXFLAGS="-std=gnu++99" CC=clang CXX=clang++
            String sn = stageNameFunc(this)
            return sn
        }
        return defaultStageName()
    }

    /** e.g. {@code
     * CSTDVERSION=99&&CSTDVARIANT=gnu&&COMPILER=clang&&CLANGVER=12 BUILD_TYPE=default-all-errors&&CFLAGS="-std=gnu99"&&CXXFLAGS="-std=gnu++99"&&CC=clang&&CXX=clang++  && TZ=UTC && LANG=C  -m32 --without-docs
     * } */
    @NonCPS
    public String defaultStageName() {

        String sn = buildLabelExpression;

        if (Utils.isListNotEmpty(virtualLabelSet)) {
            // Same as we do in Dynamatrix.mapBuildLabelExpressions() for one combo
            sn += " && " + String.join('&&', virtualLabelSet).replaceAll('\\s+', '&&')
        }

        if (Utils.isListNotEmpty(envvarSet)) {
            sn += "  &&  " + String.join(' && ', envvarSet)
        }

        if (Utils.isListNotEmpty(clioptSet)) {
            sn += "  " + String.join(' ', clioptSet)
        }

        if (isAllowedFailure == true)
            sn += " (isAllowedFailure)"

        // Not likely still alive, but...
        if (isExcluded == true)
            sn += " (isExcluded)"

        return sn;
    } // defaultStageName()

    /**
     * One implementation that pipelines can assign:
     *    <pre>dynamatrixGlobalState.stageNameFunc = DynamatrixSingleBuildConfig.&C_StageNameTagFunc</pre>
     */
    @NonCPS
    public static String C_StageNameTagFunc(DynamatrixSingleBuildConfig dsbc) {
        return 'MATRIX_TAG="' + C_StageNameTagValue(dsbc) + "\" && " + dsbc.defaultStageName()
    }

    /**
     * Helper to learn the latest log, e.g. for notification backref URLs
     * (assuming the last matrix-cell step is what caused the failure we
     * notify about).<br/>
     *
     * TODO: Another helper (or options here) to find earliest/newest FAILED
     *  matrix-cell step, for notifications to be even more relevant.
     *
     * @return  Filename of the log. Caller should prepend the
     *          build artifact location etc. to make an URL of
     *          it, with a prefix like this:
     *          <pre>String buildArtifactUrlPrefixSlashed = env.BUILD_URL?.replaceFirst('/+$', '') + "/artifact/"</pre>
     */
    @NonCPS
    String getLatestDsbcResultLog() {
        String s = null
        // as a LinkedHashMap it has a specific order of entries
        if (Utils.isMapNotEmpty(dsbcResultLogs)) {
            dsbcResultLogs.each {
                s = it.key
            }
        }
        return s
    }

    /**
     * Returns an URL to expected artifact with the log per
     * {@link #getLatestDsbcResultLog} or null in case of
     * problems resolving some sub-strings for such URL.
     * @return String (URL or null)
     */
    @NonCPS
    String getLatestDsbcResultLogUrl() {
        String buildUrl = this.script?.env?.BUILD_URL
        String logFile = getLatestDsbcResultLog()
        if (Utils.isStringNotEmpty(buildUrl) && Utils.isStringNotEmpty(logFile)) {
            return buildUrl.replaceFirst(/\/+$/, '') + "/artifact/" + logFile
        }
        return null
    }

    /**
     * Return the set of (not-null and unique) values represented
     * by whichever is populated of the: build agent labels, virtual
     * labels, and envvars (but not the CLI options). Any composite
     * labels (but not envvars) are split into separate entries.
     * The resulting set is not "guaranteed" to only contain
     * {@code key=value} strings, but is expected to for practical
     * purposes (consumer should check it if important; technically
     * may contain sets of further strings, keys may theoretically
     * be not-strings, etc.)<br/>
     *
     * NOTE: envvars (e.g. from {@code dynamatrixAxesCommonEnv} entries
     * like {@code CONFIG_OPTS=--with-all --without-docs} are fair game
     * to be interpreted as a single {@code CONFIG_OPTS} environment
     * variable which becomes multiple tokens after shell substitution.
     * A quoted {@code CFLAGS="-O2 -g -m64 -Wall"} should remain a
     * single token in Dynamatrix-derived code (may be split into many
     * later on, by {@code configure} scripts or {@code make} programs).
     */
    @NonCPS
    public Set getKVSet() {
        // All labels of the world, unite!
        Set labelSet1 = (buildLabelSet + virtualLabelSet).flatten()
        // Pre-clean
        labelSet1.remove(null)
        labelSet1.remove("")

        // Use (matrix-provided) non-trivial envvar entries "as is"
        Set labelSet = envvarSet?.flatten() ?: []
        // Pre-clean
        labelSet.remove(null)
        labelSet.remove("")

        // Populate with parsed label expressions
        labelSet1.each() {def label ->
            // Split composite labels like "COMPILER=CLANG CLANGVER=9", if any
            // and avoid removing from inside the loop over same Set
            if (Utils.isStringNotEmpty(label?.toString()?.trim())) {
                // do not split intentional multi-token values like CFLAGS="-Wall -Werror...'
                // containing whitespace but encased into quotes (added as is below)
                if (label =~ /\s+/ && !(label =~ /^[^=\s]+=["']/)) {
                    // Split multi-tokens
                    Set<String> tmpSet = ((String)label).split(/\s/)
                    tmpSet.remove(null)
                    tmpSet.remove("")
                    labelSet += tmpSet
                } else {
                    // Use as is
                    labelSet.add(label)
                }
            }
        }

        // Post-clean
        labelSet.remove(null)
        labelSet.remove("")

        return labelSet
    }

    @NonCPS
    public String getObjectID() { return this.@objectID }

    /**
     * Return the map with (not-null and unique) key=values represented
     * by whichever is populated of the: build agent labels, virtual
     * labels, and envvars (but not the CLI options). Any composite
     * labels are split into separate entries.<br/>
     *
     * The resulting Map is "guaranteed" to contain either key=value
     * strings (with "key" string mapped to a "value" string), or
     * "key" string mapped to null if the original label did not have
     * an equality sign (and if storeNulls==true).<br/>
     *
     * Behavior is not defined if several of the original label sets
     * assigned a (different) value to the same label key.
     */
    @NonCPS
    public Map<String, String> getKVMap(boolean storeNulls = false) {
        boolean debugErrors = this.shouldDebugErrors()
        boolean debugTrace = this.shouldDebugTrace()

        // TODO: Refactor with original label mapping, moving to Utils?
        // Probably not: mapping in NodeData is tailored for nested
        // multi-value hits, while mapping here is about unique keys
        // each with one value (representing one selected build combo).

        Set labelSet = getKVSet()
        Map<String, String> labelMap = [:]
        labelSet.each() {def label ->
            // TODO: Handle a Set<String> or other potentially possible values? At least log them?
            if (!Utils.isStringNotEmpty(label)) return  // continue
            label = label.trim()
            try {
                if ("".equals(label)) return  // continue
            } catch (Exception e) {
                String emsg = "Expected key-value string, got label=${Utils.castString(label)}: " + e.toString()
                if (debugErrors) {
                    script.println "[ERROR] Skipped item: ${emsg}"
                    return  // continue
                } else {
                    throw new Exception(emsg)
                }
            }
            def matcher = label =~ ~/^([^=]+)=(.*)$/
            if (matcher.find()) {
                labelMap[(String)(matcher[0][1])] = (String)(matcher[0][2])
            }
            if (storeNulls)
                labelMap[(String)(label)] = null
        }

        if (debugTrace) {
            script.println("Collected labelMap=${Utils.castString(labelMap)}\n" +
                "  from labelSet=${Utils.castString(labelSet)}\n" +
                "  from dsbc=${Utils.castString(this)}\n"
                )
        }

        return labelMap
    }

    /**
     * This routine might be used to construct actual "-std=..."
     * argument for a compiler, or for the stage name tag below.
     * The "useIsCXXforANSI" flag impacts whether we would emit
     * "ansi++" string or not (makes tag friendlier, arg useless)
     */
    @NonCPS
    public static String C_stdarg(String CSTDVARIANT, String CSTDVERSION, boolean isCXX = false, boolean useIsCXXforANSI = false) {
        if (CSTDVERSION == "ansi") {
            if (isCXX && useIsCXXforANSI) {
                // for tag
                return "ansi++"
            } else {
                // for arg
                return "ansi"
            }
        }

        String sn = CSTDVARIANT
        if (sn == null || sn == "") sn = "c"
        if (isCXX) sn += "++"
        sn += CSTDVERSION
        return sn
    }

    /**
     * Expected axis labels below match presets in DynamatrixConfig("C")
     * and agent labels in examples.
     * Parse {@code ARCH_BITS=64&&ARCH64=amd64&&COMPILER=CLANG&&CLANGVER=9&&OS_DISTRO=openindiana && BITS=64&&CSTDVARIANT=c&&CSTDVERSION=99 (isAllowedFailure)}
     * => {@code NUT_MATRIX_TAG="c99-clang-openindiana-amd64-64bit(-warn|nowarn)(-mayFail)"}
     */
    @NonCPS
    public static String C_StageNameTagValue(DynamatrixSingleBuildConfig dsbc) {
        Map<String, String> labelMap = dsbc.getKVMap(false)

        String sn = ""
        if (labelMap.containsKey("CSTDVARIANT")) {
            if (labelMap.containsKey("CSTDVERSION")) {
                sn += C_stdarg(labelMap["CSTDVARIANT"], labelMap["CSTDVERSION"], false)
            } else {
                // e.g. "c99"
                if (labelMap.containsKey("CSTDVERSION_c"))
                    sn += C_stdarg(labelMap["CSTDVARIANT"], labelMap["CSTDVERSION_c"], false)
                if (!sn.equals("")) sn += "-"
                // e.g. "gnu++17"
                if (labelMap.containsKey("CSTDVERSION_cxx"))
                    sn += C_stdarg(labelMap["CSTDVARIANT"], labelMap["CSTDVERSION_cxx"], true, true)

                // e.g. "c++98"... or a bogus "gnuansi" to rectify:
                if ("".equals(sn) && labelMap.containsKey("CSTDVERSION_DEFAULT"))
                    sn += C_stdarg(labelMap["CSTDVARIANT"], labelMap["CSTDVERSION_DEFAULT"], false)
            }
        }

        if (labelMap.containsKey("COMPILER")) {
            if (!sn.equals("")) sn += "-"
            def COMPILER = labelMap["COMPILER"]
            sn += COMPILER.toLowerCase() // e.g. "CLANG" => "clang"
            if (labelMap.containsKey(COMPILER + "VER")) { // => e.g. "clang-9"
                sn += "-" + labelMap[COMPILER + "VER"].toLowerCase()
            }
        }

        if (labelMap.containsKey("OS_DISTRO")) {
            if (!sn.equals("")) sn += "-"
            sn += labelMap["OS_DISTRO"]
        } else if (labelMap.containsKey("OS_FAMILY")) {
            if (!sn.equals("")) sn += "-"
            sn += labelMap["OS_FAMILY"]
        }

        String BITS = null
        if (labelMap.containsKey("ARCH_BITS")) {
            BITS = labelMap["ARCH_BITS"]
            if (!BITS.isInteger()) BITS=null
        }
        if (BITS == null && labelMap.containsKey("BITS")) {
            BITS = labelMap["BITS"]
            if (!BITS.isInteger()) BITS=null
        }

        String ARCH = null
        if (BITS != null && labelMap.containsKey("ARCH" + BITS)) {
            ARCH = labelMap["ARCH" + BITS] // ex. ARCH64=amd64
        } else if (labelMap.containsKey("ARCH")) {
            ARCH = labelMap["ARCH"]        // ex. ARCH=armv7l
        }

        if (ARCH != null) {
            if (!sn.equals("")) sn += "-"
            sn += "${ARCH}"
        } // else
        if (BITS != null) {
            if (!sn.equals("")) sn += "-"
            sn += "${BITS}bit"
        }

        if (labelMap.containsKey("BUILD_TYPE")) {
            switch (labelMap["BUILD_TYPE"]) {
                case "default-all-errors": // NUT/zproject for "all build targets, warnings as errors"
                    if (!sn.equals("")) sn += "-"
                    sn += "nowarn" // do not tolerate warnings
                    break
            }
        }

        if (dsbc.isAllowedFailure == true) {
            if (!sn.equals("")) sn += "-"
            sn += "mayFail"
        }

        return sn
    } // C_StageNameTagValue(dsbc)

    /**
     * One implementation that pipelines can assign:
     * <pre>
     *    dynamatrixGlobalState.stageNameFunc = DynamatrixSingleBuildConfig.&Shellcheck_StageNameTagFunc
     *    dynamatrixGlobalState.stageNameFunc = DynamatrixSingleBuildConfig.&ShellcheckPlatform_StageNameTagFunc
     * </pre>
     * For a platform, in which we can then group-test several shells.
     */
    @NonCPS
    public static String ShellcheckPlatform_StageNameTagFunc(DynamatrixSingleBuildConfig dsbc) {
        return 'MATRIX_TAG="' + DynamatrixSingleBuildConfig.ShellcheckPlatform_StageNameTagValue(dsbc) + '"'
    }

    @NonCPS
    public static String ShellcheckPlatform_StageNameTagValue(DynamatrixSingleBuildConfig dsbc) {
        Map<String, String> labelMap = dsbc.getKVMap(false)
        String sn = ""
        if (labelMap.containsKey("OS_FAMILY"))
            sn += labelMap.OS_FAMILY + "-"
        if (labelMap.containsKey("OS_DISTRO"))
            sn += labelMap.OS_DISTRO + "-"
        if (labelMap.containsKey("MAKE"))
            sn += labelMap.MAKE + "-"
        if (labelMap.containsKey("SHELL_PROG"))
            sn += labelMap.SHELL_PROG + "-"
        return "${sn}shellcheck"
    }

    /** Use if a particular shell (or several) has been chosen for one test stage... */
    @NonCPS
    public static String Shellcheck_StageNameTagFunc(DynamatrixSingleBuildConfig dsbc) {
        return 'MATRIX_TAG="' + DynamatrixSingleBuildConfig.Shellcheck_StageNameTagValue(dsbc) + '"'
    }

    @NonCPS
    public static String Shellcheck_StageNameTagValue(DynamatrixSingleBuildConfig dsbc) {
        Map<String, String> labelMap = dsbc.getKVMap(false)
        String sn = ""
        if (labelMap.containsKey("OS_FAMILY"))
            sn += labelMap.OS_FAMILY + "-"
        if (labelMap.containsKey("OS_DISTRO"))
            sn += labelMap.OS_DISTRO + "-"
        if (labelMap.containsKey("SHELL_PROGS"))
            sn += labelMap.SHELL_PROGS.trim().replaceAll("\\s", "-") + "-"
        return "${sn}shellcheck"
    }

    public boolean matchesConstraintsCombo (ArrayList combo) {
        return matchesConstraintsCombo(new LinkedHashSet(combo))
    }

    /**
     * Returns "true" if the current object hits all constraints
     * in combo, which may be used for detecting (and possibly
     * filtering away) excluded setups or allowed failures.<br/>
     *
     * The `combo` is one case of constraints, like this limitation
     * example that early GCC versions (before 3.x) are deemed unfit
     * for building C standard revisions other than C89/C90:
     * <pre>
     *   [~/GCCVER=[012]([^0-9.]|$|\.[0-9]+)/, ~/CSTDVERSION=(?!89|90)/]
     * </pre>
     * In this example, we have two regexes to check against all the
     * labels, envvars and opts defined for this single build setup.<br/>
     *
     * NOTE: Tight loop below, use primitive boolean type (not class)
     * to be faster.
     */
    public boolean matchesConstraintsCombo (Set combo) {
        boolean debugErrors = this.shouldDebugErrors()
        boolean debugTrace = this.shouldDebugTrace()

        // No combo - no hit
        if (!Utils.isListNotEmpty(combo)) {
            if (debugErrors) this.script.println ("[ERROR] matchesConstraintsCombo(): invalid input: ${Utils.castString(combo)}")
            return false
        }

        // How many constraint hits did we score?
        def hits = 0
        def crit = 0
        combo.each() {def regexConstraint ->
            // Currently we only support regex matches here
            if (!Utils.isRegex(regexConstraint)) {
                if (debugErrors) this.script.println ("[ERROR] matchesConstraintsCombo(): invalid input item type, skipped: ${Utils.castString(regexConstraint)}")
                return // skip
            }
            crit++

            boolean hadHit = false

            buildLabelSet.find {def label ->
                if (label =~ regexConstraint) {
                    if (debugTrace) this.script.println ("[DEBUG] matchesConstraintsCombo(): buildLabelSet for ${Utils.castString(this)} matched ${Utils.castString(label)} - hit with ${regexConstraint}")
                    hadHit = true
                    return true // break
                }
                return false // keep looping
            }

            if (!hadHit) {
                virtualLabelSet.find {def label ->
                    if (label =~ regexConstraint) {
                        if (debugTrace) this.script.println ("[DEBUG] matchesConstraintsCombo(): virtualLabelSet for ${Utils.castString(this)} matched ${Utils.castString(label)} - hit with ${regexConstraint}")
                        hadHit = true
                        return true // break
                    }
                    return false // keep looping
                }
            }

            if (!hadHit) {
                 envvarSet.find {def envvarval ->
                    if (envvarval =~ regexConstraint) {
                        if (debugTrace) this.script.println ("[DEBUG] matchesConstraintsCombo(): envvarSet for ${Utils.castString(this)} matched ${Utils.castString(envvarval)} - hit with ${regexConstraint}")
                        hadHit = true
                        return true // break
                    }
                    return false // keep looping
                }
            }

            if (!hadHit) {
                clioptSet.find {def cliopt ->
                    if (cliopt =~ regexConstraint) {
                        if (debugTrace) this.script.println ("[DEBUG] matchesConstraintsCombo(): clioptSet for ${Utils.castString(this)} matched ${Utils.castString(cliopt)} - hit with ${regexConstraint}")
                        hadHit = true
                        return true // break
                    }
                    return false // keep looping
                }
            }

            // We only count one hit, even if regex could match several fields
            if (hadHit) {
                hits++
            } else {
                if (debugTrace) this.script.println ("[DEBUG] matchesConstraintsCombo(): no field was hit by ${regexConstraint}")
            }
        }

        // true means a valid match: we had some hits, and that was as many
        // hits as our combo had separate criteria listed (of supported type)
        def res = (hits > 0 && hits == crit)
        if (debugTrace) this.script.println (
            "[DEBUG] matchesConstraintsCombo(): ${Utils.castString(this)} " +
            (res ? "matched all of" : "did not match some of" ) +
            " ${Utils.castString(combo)}")
        return res
    } // matchesConstraintsCombo (Set)

    public boolean matchesConstraints (ArrayList combos) {
        return matchesConstraints(new LinkedHashSet(combos))
    }

    /**
     * Call {@link #matchesConstraintsCombo}() for each combo in the
     * larger set of combos (like {@link DynamatrixConfig#allowedFailure}[]
     * or {@link DynamatrixConfig#excludeCombos}[] in the class
     * {@link DynamatrixConfig}) to see if the current object hits any.
     */
    public boolean matchesConstraints (Set combos) {
        boolean debugErrors = this.shouldDebugErrors()
        boolean debugTrace = this.shouldDebugTrace()

        // No combo - no hit
        if (!Utils.isListNotEmpty(combos)) {
            if (debugErrors) this.script.println ("[ERROR] matchesConstraints(): invalid input: ${Utils.castString(combos)}")
            return false
        }

        boolean res = combos.any {def combo ->
            // No combo - no hit, extracted from matchesConstraintsCombo() to log more error details2
            if (Utils.isListNotEmpty(combo)) {
                if (this.matchesConstraintsCombo(combo)) {
                    if (debugTrace)
                        this.script.println ("[DEBUG] matchesConstraints(): ${Utils.castString(this)} " +
                            "matched ${Utils.castString(combo)}")
                    return true // break with a hit
                }
            } else {
                if (debugErrors) this.script.println ("[ERROR] matchesConstraints(): got invalid input item: ${Utils.castString(combo)} while looking at a Set of combos: ${Utils.castString(combos)}")
                return false // continue looping
            }
            return false // continue looping
        }
        if (res) return true

        // None of the combos matched this object
        if (debugTrace)
            this.script.println ("[DEBUG] matchesConstraints(): ${Utils.castString(this)} " +
                "did not match ${Utils.castString(combos)}")
        return false
    } // matchesConstraints (Set)

}
