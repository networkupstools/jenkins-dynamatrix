package org.nut.dynamatrix;

import org.nut.dynamatrix.Utils;
import org.nut.dynamatrix.dynamatrixGlobalState;

/* This class intends to represent one single build configuration
 * with its agent label, further "virtual labels", env, opts and
 * other matrix-provided tunables derived from DynamatrixConfig
 * and Dynamatrix class field values.
 */
class DynamatrixSingleBuildConfig implements Cloneable {
    private def script = null
    def stageNameFunc = null
    public boolean enableDebugTrace = dynamatrixGlobalState.enableDebugTrace
    public boolean enableDebugErrors = dynamatrixGlobalState.enableDebugErrors

    // By default, we wipe workspaces after a build
    public boolean keepWs = false

    // Most of our builds require a build agent, usually one with
    // specific capabilities as selected by label expression below,
    // to run some programs in that OS. For generality's sake, there
    // is a case for no-node mode, but that may only call pipeline
    // steps (to schedule a build, maybe analyze, etc.)
    public Boolean requiresBuildNode = true

    // The `expr` requested in `agent { label 'expr' }` for the
    // generated build stage, originates from capabilities declared
    // by build nodes or those explicitly requested by caller
    public String buildLabelExpression

    // Original set of requested labels for the agent needed for
    // this particular build, which can include usual labels like
    // "ARCH=armv7l" and composite labels, which are useful for
    // grouped values like "COMPILER=GCC GCCVER=1.2.3". Can be
    // interpreted by the actual building closure (e.g. to
    // generate options for configure script), such as converting
    // `ARCH_BITS=64` into a `CFLAGS="$CLFAGS -m32"` addition:
    public Set buildLabelSet

    // Additional variation from the caller, such as revision of
    // C/C++ standard to use for this particular run. Interpreted
    // by the actual building closure (e.g. to generate options
    // for configure script).
    // Provided as a set of Strings with key=value pairs like:
    //   ["CSTDVERSION=17", "CSTDVARIANT=gnu"]
    // or
    //   ["CSTDVERSION_c=11", "CSTDVERSION_cxx=14", "CSTDVARIANT=c"]
    public Set virtualLabelSet

    // Exported additional environment variables for this build, e.g.:
    //   ["TZ=UTC", "LANG=C"]
    public Set envvarSet

    // Additional command-line options for this build (as interpreted
    // by the actual building closure - perhaps options for a configure
    // script), e.g.:
    //   ["CFLAGS=-stdc=gnu99", "CXXFLAGS=-stdcxx=g++99", "--without-docs"]
    public Set clioptSet

    public Boolean isExcluded
    public Boolean isAllowedFailure

    public DynamatrixSingleBuildConfig (script) {
        this.script = script
        this.enableDebugTrace = dynamatrixGlobalState.enableDebugTrace
        this.enableDebugErrors = dynamatrixGlobalState.enableDebugErrors
    }

    /* Compare class instances as equal in important fields (e.g. to dedup
     * when adding again to Sets), despite possible variation in some
     * inconcequential fields:
     *   "script", "stageNameFunc", "enableDebugTrace", "enableDebugErrors",
     *   "isExcluded", "isAllowedFailure"
     */
    public boolean equals(java.lang.Object other) {
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

    public boolean canEqual(java.lang.Object other) {
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
    public boolean shouldDebugErrors() {
        return ( (this.enableDebugTrace || this.enableDebugErrors) && this.script != null)
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

    @NonCPS
    public String defaultStageName() {
        // e.g. CSTDVERSION=99&&CSTDVARIANT=gnu&&COMPILER=clang&&CLANGVER=12 BUILD_TYPE=default-all-errors&&CFLAGS="-std=gnu99"&&CXXFLAGS="-std=gnu++99"&&CC=clang&&CXX=clang++  && TZ=UTC && LANG=C  -m32 --without-docs

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

    // One implementation that pipelines can assign:
    //    dynamatrixGlobalState.stageNameFunc = DynamatrixSingleBuildConfig.&C_StageNameTagFunc
    @NonCPS
    public static String C_StageNameTagFunc(DynamatrixSingleBuildConfig dsbc) {
        return 'MATRIX_TAG="' + DynamatrixSingleBuildConfig.C_StageNameTagValue(dsbc) + "\" && " + dsbc.defaultStageName()
    }

    @NonCPS
    public Set getKVSet() {
        /* Return the set of (not-null and unique) values represented
         * by whichever is populated of the: build agent labels, virtual
         * labels, and envvars (but not the CLI options). Any composite
         * labels are split into separate entries. Resulting set is not
         * "guaranteed" to only contain key=value strings, but is expected
         * to for practical purposes (consumer should check it if important)
         */
        // All labels of the world, unite!
        Set labelSet1 = (buildLabelSet + virtualLabelSet + envvarSet).flatten()
        labelSet1.remove(null)
        labelSet1.remove("")
        Set labelSet = []
        labelSet1.each() {String label ->
            // Split composite labels like "COMPILER=CLANG CLANGVER=9", if any
            // and avoid removing from inside the loop over same Set
            if (label =~ /\s+/) {
                Set tmpSet = label.split(/\s/)
                tmpSet.remove(null)
                tmpSet.remove("")
                labelSet += tmpSet
            } else {
                labelSet.add(label)
            }
        }
        labelSet.remove(null)
        labelSet.remove("")

        return labelSet
    }

    @NonCPS
    public Map getKVMap(boolean storeNulls = false) {
        /* Return the map with (not-null and unique) key=values represented
         * by whichever is populated of the: build agent labels, virtual
         * labels, and envvars (but not the CLI options). Any composite
         * labels are split into separate entries.
         * The resulting Map is "guaranteed" to contain either key=value
         * strings (with "key" string mapped to a "value" string), or
         * "key" string mapped to null if the original label did not have
         * an equality sign (and if storeNulls==true).
         * Behavior is not defined if several of the original label sets
         * assigned a (different) value to the same label key.
         */

        def debugErrors = this.shouldDebugErrors()
        def debugTrace = this.shouldDebugTrace()

        // TODO: Refactor with original label mapping, moving to Utils?
        // Probably not: mapping in NodeData is tailored for nested
        // multi-value hits, while mapping here is about unique keys
        // each with one value (representing one selected build combo).

        def labelSet = getKVSet()
        def labelMap = [:]
        labelSet.each() {String label ->
            if (!Utils.isStringNotEmpty(label)) return
            label = label.trim()
            try {
                if ("".equals(label)) return
            } catch (Exception e) {
                def emsg = "Expected key-value string, got label=${Utils.castString(label)}: " + e.toString()
                if (debugErrors) {
                    script.println "[ERROR] Skipped item: ${emsg}"
                    return
                } else {
                    throw new Exception(emsg)
                }
            }
            def matcher = label =~ ~/^([^=]+)=(.*)$/
            if (matcher.find()) {
                labelMap[matcher[0][1]] = matcher[0][2]
            }
            if (storeNulls)
                labelMap[label] = null
        }

        if (debugTrace) {
            script.println("Collected labelMap=${Utils.castString(labelMap)}\n" +
                "  from labelSet=${Utils.castString(labelSet)}\n" +
                "  from dsbc=${Utils.castString(this)}\n"
                )
        }

        return labelMap
    }

    @NonCPS
    public static String C_stdarg(String CSTDVARIANT, String CSTDVERSION, boolean isCXX = false, boolean useIsCXXforANSI = false) {
        // This routine might be used to construct actual "-std=..."
        // argument for a compiler, or for the stage name tag below.
        // The "useIsCXXforANSI" flag impacts whether we would emit
        // "ansi++" string or not (makes tag friendlier, arg useless)
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

    @NonCPS
    public static String C_StageNameTagValue(DynamatrixSingleBuildConfig dsbc) {
        // Expected axis labels below match presets in DynamatrixConfig("C")
        // and agent labels in examples.
        // Parse ARCH_BITS=64&&ARCH64=amd64&&COMPILER=CLANG&&CLANGVER=9&&OS_DISTRO=openindiana && BITS=64&&CSTDVARIANT=c&&CSTDVERSION=99 (isAllowedFailure)
        // => NUT_MATRIX_TAG="c99-clang-openindiana-amd64-64bit(-warn|nowarn)(-mayFail)"

        def labelMap = dsbc.getKVMap(false)

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

        def BITS = null
        if (labelMap.containsKey("ARCH_BITS")) {
            BITS = labelMap["ARCH_BITS"]
            if (!BITS.isInteger()) BITS=null
        }
        if (BITS == null && labelMap.containsKey("BITS")) {
            BITS = labelMap["BITS"]
            if (!BITS.isInteger()) BITS=null
        }

        def ARCH = null
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

    // One implementation that pipelines can assign:
    //    dynamatrixGlobalState.stageNameFunc = DynamatrixSingleBuildConfig.&Shellcheck_StageNameTagFunc
    //    dynamatrixGlobalState.stageNameFunc = DynamatrixSingleBuildConfig.&ShellcheckPlatform_StageNameTagFunc
    // For platform, in which we can then group-test several shells:
    @NonCPS
    public static String ShellcheckPlatform_StageNameTagFunc(DynamatrixSingleBuildConfig dsbc) {
        return 'MATRIX_TAG="' + DynamatrixSingleBuildConfig.ShellcheckPlatform_StageNameTagValue(dsbc) + '"'
    }

    @NonCPS
    public static String ShellcheckPlatform_StageNameTagValue(DynamatrixSingleBuildConfig dsbc) {
        def labelMap = dsbc.getKVMap(false)
        String sn = ""
        if (labelMap.containsKey("OS_FAMILY"))
            sn += labelMap.OS_FAMILY + "-"
        if (labelMap.containsKey("OS_DISTRO"))
            sn += labelMap.OS_DISTRO + "-"
        return "${sn}shellcheck"
    }

    // If a particular shell (or several) has been chosen for one test stage:
    @NonCPS
    public static String Shellcheck_StageNameTagFunc(DynamatrixSingleBuildConfig dsbc) {
        return 'MATRIX_TAG="' + DynamatrixSingleBuildConfig.Shellcheck_StageNameTagValue(dsbc) + '"'
    }

    @NonCPS
    public static String Shellcheck_StageNameTagValue(DynamatrixSingleBuildConfig dsbc) {
        def labelMap = dsbc.getKVMap(false)
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

    // Tight loop below, use primitive boolean type (not class) to be faster
    public boolean matchesConstraintsCombo (Set combo) {
        /* Returns "true" if the current object hits all constraints
         * in combo, which may be used for detecting (and possibly
         * filtering away) excluded setups or allowed failures.
         * The `combo` is one case of constraints, like this limitation
         * example that early GCC versions (before 3.x) are deemed unfit
         * for building C standard revisions other than C89/C90:
         *   [~/GCCVER=[012]([^0-9.]|$|\.[0-9]+)/, ~/CSTDVERSION=(?!89|90)/]
         * In this example, we have two regexes to check against all the
         * labels, envvars and opts defined for this single build setup.
         */

        def debugErrors = this.shouldDebugErrors()
        def debugTrace = this.shouldDebugTrace()

        // No combo - no hit
        if (!Utils.isListNotEmpty(combo)) {
            if (debugErrors) this.script.println ("[ERROR] matchesConstraintsCombo(): invalid input: ${Utils.castString(combo)}")
            return false
        }

        // How many constraint hits did we score?
        def hits = 0
        def crit = 0
        combo.each() {regexConstraint ->
            // Currently we only support regex matches here
            if (!Utils.isRegex(regexConstraint)) {
                if (debugErrors) this.script.println ("[ERROR] matchesConstraintsCombo(): invalid input item type, skipped: ${Utils.castString(regexConstraint)}")
                return // skip
            }
            crit++

            boolean hadHit = false

            buildLabelSet.find {label ->
                if (label =~ regexConstraint) {
                    if (debugTrace) this.script.println ("[DEBUG] matchesConstraintsCombo(): buildLabelSet for ${Utils.castString(this)} matched ${Utils.castString(label)} - hit with ${regexConstraint}")
                    hadHit = true
                    return true // break
                }
                return false // keep looping
            }

            if (!hadHit) {
                virtualLabelSet.find {label ->
                    if (label =~ regexConstraint) {
                        if (debugTrace) this.script.println ("[DEBUG] matchesConstraintsCombo(): virtualLabelSet for ${Utils.castString(this)} matched ${Utils.castString(label)} - hit with ${regexConstraint}")
                        hadHit = true
                        return true // break
                    }
                    return false // keep looping
                }
            }

            if (!hadHit) {
                 envvarSet.find {envvarval ->
                    if (envvarval =~ regexConstraint) {
                        if (debugTrace) this.script.println ("[DEBUG] matchesConstraintsCombo(): envvarSet for ${Utils.castString(this)} matched ${Utils.castString(envvarval)} - hit with ${regexConstraint}")
                        hadHit = true
                        return true // break
                    }
                    return false // keep looping
                }
            }

            if (!hadHit) {
                clioptSet.find {cliopt ->
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

    public boolean matchesConstraints (Set combos) {
        /* Call matchesConstraintsCombo() for each combo in the larger
         * set of combos (like allowedFailure[] or excludeCombos[] in the
         * class DynamatrixConfig) to see if the current object hits any.
         */

        def debugErrors = this.shouldDebugErrors()
        def debugTrace = this.shouldDebugTrace()

        // No combo - no hit
        if (!Utils.isListNotEmpty(combos)) {
            if (debugErrors) this.script.println ("[ERROR] matchesConstraints(): invalid input: ${Utils.castString(combos)}")
            return false
        }

        boolean res = combos.any {combo ->
            if (this.matchesConstraintsCombo(combo)) {
                if (debugTrace)
                    this.script.println ("[DEBUG] matchesConstraints(): ${Utils.castString(this)} " +
                        "matched ${Utils.castString(combo)}")
                return true // break with a hit
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

