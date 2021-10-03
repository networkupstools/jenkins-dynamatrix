package org.nut.dynamatrix;

import java.util.ArrayList;
import java.util.Arrays.*;
import java.util.regex.*;

import org.nut.dynamatrix.DynamatrixConfig;
import org.nut.dynamatrix.DynamatrixSingleBuildConfig;
import org.nut.dynamatrix.NodeCaps;
import org.nut.dynamatrix.NodeData;
import org.nut.dynamatrix.Utils;
import org.nut.dynamatrix.dynamatrixGlobalState;

/* This class intends to represent one build matrix which can be used
 * to produce several "closely related" build stages re-using the same
 * set of build agents and project configurations.
 */
class Dynamatrix implements Cloneable {
    // Class-shared cache of the collected nodeCaps per label expression
    // so we only query Jenkins core once for those, even if we prepare
    // many build scenarios:
    private static Map<String, NodeCaps> nodeCapsCache = [:]

    // Have some defaults, if only to have all expected fields defined
    private DynamatrixConfig dynacfg
    private def script
    public boolean enableDebugTrace = dynamatrixGlobalState.enableDebugTrace
    public boolean enableDebugErrors = dynamatrixGlobalState.enableDebugErrors
    public boolean enableDebugMilestones = dynamatrixGlobalState.enableDebugMilestones
    public boolean enableDebugMilestonesDetails = dynamatrixGlobalState.enableDebugMilestonesDetails

    // Store values populated by prepareDynamatrix() so further generateBuild()
    // and practical generateBuildConfigSet() calls can use these quickly.
    NodeCaps nodeCaps
    // The following Sets contain different levels of processing of data about
    // build agent capabilities proclaimed in their agent labels (via nodeCaps)
    Set effectiveAxes = []
    Set buildLabelCombos = []
    Set buildLabelCombosFlat = []
    // This is one useful final result, mapping strings for `agent{label 'expr'}`
    // clauses to arrays of label contents (including "composite" labels where
    // key=value's are persistently grouped, e.g. "COMPILER=GCC GCCVER=123",
    // and the original label set e.g. "nut-builder" used to initialize the
    // set of agents this dynamatrix is interested in)
    Map<String, Set> buildLabelsAgents = [:]

    public Dynamatrix(Object script) {
        this.script = script
        this.dynacfg = new DynamatrixConfig(script)
        this.enableDebugTrace = dynamatrixGlobalState.enableDebugTrace
        this.enableDebugErrors = dynamatrixGlobalState.enableDebugErrors
    }

    public boolean canEqual(java.lang.Object other) {
        return other instanceof Dynamatrix
    }

    @Override
    public Dynamatrix clone() throws CloneNotSupportedException {
        return (Dynamatrix) super.clone();
    }

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
        return ( this.enableDebugTrace && this.script != null)
    }

    @NonCPS
    public boolean shouldDebugErrors() {
        return ( (this.enableDebugErrors || this.enableDebugTrace) && this.script != null)
    }

    @NonCPS
    public boolean shouldDebugMilestones() {
        return ( (this.enableDebugMilestones || this.enableDebugMilestonesDetails || this.enableDebugTrace || this.enableDebugErrors) && this.script != null)
    }

    @NonCPS
    public boolean shouldDebugMilestonesDetails() {
        return ( (this.enableDebugMilestonesDetails || this.enableDebugTrace) && this.script != null)
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

    def needsPrepareDynamatrixClone(dynacfgOrig = [:]) {
        // Return true if dynacfgOrig contains fields used in
        // prepareDynamatrix() that need recalculation or otherwise
        // would be ignored by generateBuild()

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

    static def clearMapNeedsPrepareDynamatrixClone(dynacfgOrig = [:]) {
        def dc = dynacfgOrig.clone()
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

    def clearNeedsPrepareDynamatrixClone(dynacfgOrig = [:]) {
        // We are reusing a Dynamatrix object, maybe a clone
        // Wipe dynacfg data points that may impact re-init below
        def debugTrace = this.shouldDebugTrace()
        if (debugTrace) this.script.println "[DEBUG] prepareDynamatrix(): Clearing certain pre-existing data points from dynacfg object"
        this.dynacfg.clearNeedsPrepareDynamatrixClone(dynacfgOrig)
        this.buildLabelsAgents = [:]
        this.effectiveAxes = []
        this.buildLabelCombos = []
        this.buildLabelCombosFlat = []
        return this
    }

    def prepareDynamatrix(dynacfgOrig = [:]) {
        def debugErrors = this.shouldDebugErrors()
        def debugTrace = this.shouldDebugTrace()
        def debugMilestones = this.shouldDebugMilestones()
        def debugMilestonesDetails = this.shouldDebugMilestonesDetails()

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

        this.nodeCaps = new NodeCaps(
            this.script,
            commonLabelExpr,
            debugTrace,
            debugErrors)

        if (debugTrace) {
            this.nodeCaps.printDebug()
            this.script.println "[DEBUG] prepareDynamatrix(): collected nodeCaps: " + Utils.castString(this.nodeCaps)
            this.script.println "[DEBUG] prepareDynamatrix(): collected nodeCaps.nodeData: " + Utils.castString(this.nodeCaps.nodeData)
        }

        // Original request could have regexes or groovy-style substitutions
        // to expand. The effectiveAxes is generally a definitive set of
        // sets of exact axis names, e.g. ['ARCH', 'CLANGVER', 'OS'] and
        // ['ARCH', 'GCCVER', 'OS'] as expanded from '${COMPILER}VER' part:
        this.effectiveAxes = []
        dynacfg.dynamatrixAxesLabels.each() {axis ->
            TreeSet effAxis = this.nodeCaps.resolveAxisName(axis).sort()
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
                def arr = []
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
        // Prepare all possible combos of requested axes (meaning we can
        // request a build agent label "A && B && C" and all of those
        // would work with our currently defined agents). The buildLabels
        // are expected to provide good uniqueness thanks to the SortedSet
        // of effectiveAxes and their values that we would look into.
        this.buildLabelCombos = []
        if (debugTrace) this.script.println "[DEBUG] prepareDynamatrix(): looking for axis values in nodeCaps.nodeData.keySet(): " + Utils.castString(this.nodeCaps.nodeData.keySet())
        this.nodeCaps.nodeData.keySet().each() {nodeName ->
            // Looking at each node separately allows us to be sure that any
            // combo of axis-values (all of which it allegedly provides)
            // can be fulfilled
            def nodeAxisCombos = []
            this.effectiveAxes.each() {axisSet ->
                // Now looking at one definitive set of axis names that
                // we would pick supported values for, by current node:
                def axisCombos = []
                axisSet.each() {axis ->
                    if (debugTrace) this.script.println "[DEBUG] prepareDynamatrix(): querying values for axis '${Utils.castString(axis)}' collected for node '${Utils.castString(nodeName)}'..."
                    def tmpset = this.nodeCaps.resolveAxisValues(axis, nodeName, true)
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
        this.buildLabelCombos.each() {nodeResults ->
            nodeResults.each() {nodeAxisCombos ->
                // this nodeResults contains the set of sets of label values
                // supported for one of the original effectiveAxes requirements,
                // where each of nodeAxisCombos contains a set of axisValues
                if (debugTrace) this.script.println "[DEBUG] prepareDynamatrix(): Expanding : " + nodeAxisCombos
                def tmp = Utils.cartesianSquared(nodeAxisCombos).sort()
                // Revive combos that had only one hit and were flattened
                // into single items (strings) instead of Sets (of Sets)
                if (tmp.size() > 0) {
                    for (def i = 0; i < tmp.size(); i++) {
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
            def tmp = [:]
            this.buildLabelsAgents.keySet().each() {ble ->
                // Note, we only append to the key (node label string for
                // eventual use in a build), not the value (array of K=V's)
                tmp["(${ble}) && (${dynacfg.commonLabelExpr})"] = this.buildLabelsAgents[ble]
            }
            this.buildLabelsAgents = tmp
        }

        def blaStr = ""
        this.buildLabelsAgents.keySet().each() {ble ->
            blaStr += "\n    '${ble}' => " + this.buildLabelsAgents[ble]
        }
        if (debugTrace) this.script.println "[DEBUG] prepareDynamatrix(): detected ${this.buildLabelsAgents.size()} buildLabelsAgents combos:" + blaStr

        return true
    }

    static Map mapBuildLabelExpressions(Set<Set> blcSet) {
        // Take blcSet[] which is a Set of Sets (equivalent to field
        // buildLabelCombosFlat in the class), with contents like this:
        // [ [ARCH_BITS=64 ARCH64=amd64, COMPILER=CLANG CLANGVER=9, OS_DISTRO=openindiana],
        //   [ARCH_BITS=32 ARCH32=armv7l, COMPILER=GCC GCCVER=4.9, OS_DISTRO=debian] ]
        // ...and convert into a Map where keys are agent label expression strings

        // Equivalent to buildLabelsAgents in the class
        def blaMap = [:]
        blcSet.each() {combo ->
            // Note that labels can be composite, e.g. "COMPILER=GCC GCCVER=1.2.3"
            // ble == build label expression
            String ble = String.join('&&', combo).replaceAll('\\s+', '&&')
            blaMap[ble] = combo
        }
        return blaMap
    }

//    @NonCPS
    def generateBuildConfigSet(dynacfgOrig = [:]) {
        /* Returns a set of (unique) DynamatrixSingleBuildConfig items */
        def debugErrors = this.shouldDebugErrors()
        def debugTrace = this.shouldDebugTrace()
        def debugMilestones = this.shouldDebugMilestones()
        def debugMilestonesDetails = this.shouldDebugMilestonesDetails()

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
        Set virtualAxes = []

        // Process the map of "virtual axes": dynamatrixAxesVirtualLabelsMap
        if (dynacfgBuild.dynamatrixAxesVirtualLabelsMap.size() > 0) {
            // Map of "axis: [array, of, values]"
            if (debugTrace) this.script.println "[DEBUG] generateBuildConfigSet(): dynamatrixAxesVirtualLabelsMap: ${dynacfgBuild.dynamatrixAxesVirtualLabelsMap}"
            Set dynamatrixAxesVirtualLabelsCombos = []
            dynacfgBuild.dynamatrixAxesVirtualLabelsMap.keySet().each() {k ->
                // Keys of the map, e.g. 'CSTDVARIANT' for strings ('c', 'gnu')
                // or 'CSTDVERSION_${KEY}' for submaps (['c': '99', 'cxx': '98'])
                def vals = dynacfgBuild.dynamatrixAxesVirtualLabelsMap[k]
                if (!Utils.isList(vals) || vals.size() == 0) {
                    if (debugTrace) this.script.println "[DEBUG] generateBuildConfigSet(): dynamatrixAxesVirtualLabelsMap: SKIPPED key '${k}': its value is not a list: ${Utils.castString(vals)}"
                    return // continue
                }

                // Collect possible values of this one key
                Set keyvalues = []
                vals.each() {v ->
                    // Store each value of the provided axis as a set with
                    // one item (if a string) or more (if an expanded map)
                    Set vv = []
                    if (Utils.isMap(v) && k.contains('${KEY}')) {
                        v.keySet().each() {subk ->
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
                dynamatrixAxesVirtualLabelsCombos = Utils.cartesianProduct(dynamatrixAxesVirtualLabelsCombos, keyvalues)
            }

            if (debugTrace) this.script.println "[DEBUG] generateBuildConfigSet(): " +
                "combining dynamatrixAxesVirtualLabelsCombos: ${dynamatrixAxesVirtualLabelsCombos}" +
                "\n    with virtualAxes: ${virtualAxes}" +
                "\ndynacfgBuild.dynamatrixAxesVirtualLabelsMap.size()=${dynacfgBuild.dynamatrixAxesVirtualLabelsMap.size()} " +
                "virtualAxes.size()=${virtualAxes.size()} " +
                "dynamatrixAxesVirtualLabelsCombos.size()=${dynamatrixAxesVirtualLabelsCombos.size()}"

            // TODO: Will we have more virtualAxes inputs, or might just use assignment here?
            virtualAxes = Utils.cartesianProduct(dynamatrixAxesVirtualLabelsCombos, virtualAxes)

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
                tmp.keySet().each() {ble ->
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
                tmp.each() {virtualLabelSet ->
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
                tmp.each() {envvarSet ->
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
                tmp.each() {clioptSet ->
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
        def removedTotal = 0
        def allowedToFailTotal = 0
        Set<DynamatrixSingleBuildConfig> dsbcSet = []
        buildLabelsAgentsBuild.keySet().each() {ble ->
            // We can generate numerous build configs below that
            // would all require this (or identical) agent by its
            // build label expression, so prepare the shared part:
            DynamatrixSingleBuildConfig dsbcBle = new DynamatrixSingleBuildConfig(this.script)
            Set<DynamatrixSingleBuildConfig> dsbcBleSet = []
            def removedBle = 0
            def allowedToFailBle = 0

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
                dynacfgBuild.dynamatrixAxesCommonOpts.each() {clioptSet ->
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
                dynacfgBuild.dynamatrixAxesCommonEnv.each() {envvarSet ->
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
                virtualAxes.each() {virtualLabelSet ->
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
            def tmp = []
            // Avoid spamming the log about same label constraints
            // (several single-build configs can share those strings)
            def nodeListCache = [:]
            dsbcSet.each() {DynamatrixSingleBuildConfig dsbc ->
                def blec = (dsbc.buildLabelExpression + constraintsNodelabels).trim().replaceFirst(/^null/, '').replaceFirst(/^ *\&\& */, '').trim()
                def nodeList
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

        // Uncomment here to just detail the collected combos:
        //this.enableDebugMilestonesDetails = true
        //debugTrace = this.shouldDebugTrace()
        //debugErrors = this.shouldDebugErrors()
        //debugMilestones = this.shouldDebugMilestones()
        //debugMilestonesDetails = this.shouldDebugMilestonesDetails()

        if (true) { // debugMilestonesDetails) {
            def msg = "generateBuildConfigSet(): collected ${dsbcSet.size()} combos for individual builds"
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

    private Closure generatedBuildWrapperLayer2 (String stageName, DynamatrixSingleBuildConfig dsbc, Closure body = null) {
        /* Helper for generatedBuild() below to not repeat the
         * same code structure in different handled situations
         */
        def debugTrace = this.shouldDebugTrace()

        // echo's below are not debug-decorated, in these cases they are the payload
//CLS//        return {
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
                        body(body.delegate)
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
//CLS//        } // return a Closure

    }

    private Closure generatedBuildWrapperLayer1 (String stageName, DynamatrixSingleBuildConfig dsbc, Closure body = null) {
        /* Helper for generatedBuild() below to not repeat the
         * same code structure in different handled situations.
         * The stageName may be hard to (re-)calculate and/or
         * may be tweaked for some build scenario, so we pass
         * the string around.
         */

//CLS//        return {
//            script.stage(stageName) {
                if (dsbc.enableDebugTrace) script.echo "Running stage: ${stageName}" + "\n  for ${Utils.castString(dsbc)}"
                if (dsbc.isAllowedFailure) {
                    script.catchError(
                        message: "Failed stage which is allowed to fail: ${stageName}" + "\n  for ${Utils.castString(dsbc)}",
                        buildResult: 'SUCCESS', stageResult: 'FAILURE'
                    ) {
                        script.withEnv(['CI_ALLOWED_FAILURE=true']) {
                            generatedBuildWrapperLayer2(stageName, dsbc, body)//CLS//.call()
                        }
                    } // catchError
                } else {
                    generatedBuildWrapperLayer2(stageName, dsbc, body)//CLS//.call()
                } // if allowedFailure
//            } // stage
//CLS//        } // return a Closure

    }

    def generateBuild(Map dynacfgOrig = [:], Closure bodyOrig = null) {
        return generateBuild(dynacfgOrig, false, bodyOrig)
    }

//    @NonCPS
    def generateBuild(Map dynacfgOrig = [:], boolean returnSet, Closure bodyOrig = null) {
        /* Returns a map of stages.
         * Or a Set, if called from inside a pipeline stage (CPS code).
         */
        //def debugErrors = this.shouldDebugErrors()
        //def debugTrace = this.shouldDebugTrace()
        //def debugMilestones = this.shouldDebugMilestones()
        def debugMilestonesDetails = this.shouldDebugMilestonesDetails()

        if (needsPrepareDynamatrixClone(dynacfgOrig)) {
            if (debugMilestonesDetails
            //|| debugMilestones
            //|| debugErrors
            //|| debugTrace
            ) {
                this.script.println "[DEBUG] generateBuild(): running a configuration that needs a new dynamatrix in a clone"
            }

            def dmClone = this.clone()
            dmClone.clearNeedsPrepareDynamatrixClone(dynacfgOrig)
            dmClone.prepareDynamatrix(dynacfgOrig)
            // Don't forget to clear the config, to not loop on this
            return dmClone.generateBuild(
                clearMapNeedsPrepareDynamatrixClone(dynacfgOrig),
                returnSet, bodyOrig)
        }

        def dsbcSet = generateBuildConfigSet(dynacfgOrig)

        // Consider allowedFailure (if flag runAllowedFailure==true)
        // when preparing the stages below:
        Set parallelStages = []
        dsbcSet.each() {DynamatrixSingleBuildConfig dsbcTmp ->
            // copy a unique object ASAP, otherwise stuff gets mixed up
            DynamatrixSingleBuildConfig dsbc = dsbcTmp.clone()
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
                def sn = "${stageName}" // ensure a copy
                def bodyData = [ dsbc: dsbc.clone(), stageName: sn ]
                body = bodyOrig.clone().rehydrate(bodyData, this.script, body)
                body.resolveStrategy = Closure.DELEGATE_FIRST
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
            def payload = {
                generatedBuildWrapperLayer1(stageName, dsbc, body)//CLS//.call()
            }

            // Pattern for code change on the fly:
            if (matrixTag != null) {
                def payloadTmp = payload
                payload = { script.withEnv([matrixTag]) { payloadTmp() } }
            }

            def sbName = ""
            if (script?.env?.CI_SLOW_BUILD_FILTERNAME) {
                def payloadTmp = payload
                def weStr = "CI_SLOW_BUILD_FILTERNAME=${script.env.CI_SLOW_BUILD_FILTERNAME}"
                payload = { script.withEnv([weStr]) { payloadTmp() } }
                sbName = " :: as part of slowBuild filter: ${script.env.CI_SLOW_BUILD_FILTERNAME}"
            }

            // Note: non-declarative pipeline syntax inside the generated stages
            // in particular, no steps{}. Note that most of our builds require a
            // build agent, usually a specific one, to run some programs in that
            // OS. For generality there is a case for no-node mode, but that may
            // only call pipeline steps (schedule a build, maybe analyze, etc.)
            if (dsbc.requiresBuildNode) {

                if (Utils.isStringNotEmpty(dsbc.buildLabelExpression)) {

                    parallelStages << ["WITHAGENT: " + stageName + sbName, {
//                        script.stage("NODEWRAP: " + stageName + sbName) {
                            if (dsbc.enableDebugTrace) script.echo "Requesting a node by label expression '${dsbc.buildLabelExpression}' for stage '${stageName}'" + sbName
                            script.node (dsbc.buildLabelExpression) {
                                payload()
                            } // node
//                        } // stage needed to house the "node"
                    }] // new parallelStages[] entry

                } else {

                    parallelStages << ["WITHAGENT-ANON: " + stageName + sbName, {
//                        script.stage("NODEWRAP-ANON: " + stageName) {
                            if (dsbc.enableDebugTrace) script.echo "Requesting any node for stage '${stageName}'" + sbName
                            script.node {
                                payload()
                            } // node
//                        } // stage needed to house the "node"
                    }] // new parallelStages[] entry

                }
            } else {

                // no "${dsbc.buildLabelExpression}" - so runs on job's
                // default node, if any (or fails if none is set and is needed)
                parallelStages << ["NO-NODE: " + stageName + sbName, {
                    if (dsbc.enableDebugTrace) script.echo "Not requesting any node for stage '${stageName}'" + sbName
                    payload()
                }] // new parallelStages[] entry

            } // if got agent-label

        }

        if (returnSet) {
            return parallelStages
        } else {
            // Scope the Map for sake of CPS conversions where we don't want them
            def parallelStages_map = [:]
            parallelStages.each {tup -> parallelStages_map[tup[0]] = tup[1] }
            return parallelStages_map
        }
    } // generateBuild()

}

