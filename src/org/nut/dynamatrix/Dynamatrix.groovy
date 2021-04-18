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
class Dynamatrix {
    // Class-shared cache of the collected nodeCaps per label expression
    // so we only query Jenkins core once for those, even if we prepare
    // many build scenarios:
    private static Map<String, NodeCaps> nodeCapsCache = [:]

    // Have some defaults, if only to have all expected fields defined
    private DynamatrixConfig dynacfg
    private def script
    public Boolean enableDebugTrace = false
    public Boolean enableDebugErrors = true

    // Store values populated by prepareDynamatrix() so further generateBuild()
    // calls can use these quickly.
    NodeCaps nodeCaps
    // The following Sets contain different levels of processing of data about
    // build agent capabilities proclaimed in their agent labels (via nodeCaps)
    Set effectiveAxes = []
    Set buildLabelCombos = []
    Set buildLabelCombosFlat = []
    // This is one useful final result, mapping strings for `agent{label 'expr'}`
    // clauses to arrays of label contents (including "composite" labels where
    // key=value's are persistently grouped, e.g. "COMPILER=GCC GCCVER=123")
    Map<String, Set> buildLabelsAgents = [:]

    public Dynamatrix(Object script) {
        this.script = script
        this.dynacfg = new DynamatrixConfig()
        this.enableDebugTrace = dynamatrixGlobalState.enableDebugTrace
        this.enableDebugErrors = dynamatrixGlobalState.enableDebugErrors
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
    sh """ make -j4 """
    sh """ make check """
}

 */

    def prepareDynamatrix(dynacfgOrig = [:]) {
        if (this.enableDebugErrors) this.script.println "[WARNING] NOT FULLY IMPLEMENTED: prepareDynamatrix()"

        // Note: in addition to standard contents of class DynamatrixConfig,
        // the Map passed by caller may contain "defaultDynamatrixConfig" as
        // a key for a String value to specify default pre-sets, e.g. "C".
        // It can also contain a special Map to manage merge-mode of custom
        // provided value to "replace" same-named defaults or "merge" with
        // them -- dynacfgOrig.mergeMode["dynacfgFieldNameString"]="merge"
        def sanityRes = dynacfg.initDefault(dynacfgOrig)
        if (sanityRes != true) {
            if (sanityRes instanceof String) {
                if (this.enableDebugErrors) this.script.println sanityRes
            }
            // Assumed non-fatal - fields seen that are not in standard config
        }

        sanityRes = dynacfg.sanitycheckDynamatrixAxesLabels()
        if (sanityRes != true) {
            if (sanityRes instanceof String) {
                if (this.enableDebugErrors) this.script.println sanityRes
            } else {
                if (this.enableDebugErrors) this.script.println "No 'dynamatrixAxesLabels' were provided, nothing to generate"
            }
            return null
        }

        // TODO: Cache as label-mapped hash in dynamatrixGlobals so re-runs for
        // other configs for same builder would not query and parse real Jenkins
        // worker labels again and again.
        nodeCaps = new NodeCaps(
            this.script,
            dynacfg.commonLabelExpr,
            dynamatrixGlobalState.enableDebugTrace,
            dynamatrixGlobalState.enableDebugErrors)
        nodeCaps.optionalPrintDebug()

        // Original request could have regexes or groovy-style substitutions
        // to expand. The effectiveAxes is generally a definitive set of
        // sets of exact axis names, e.g. ['ARCH', 'CLANGVER', 'OS'] and
        // ['ARCH', 'GCCVER', 'OS'] as expanded from '${COMPILER}VER' part:
        effectiveAxes = []
        for (axis in dynacfg.dynamatrixAxesLabels) {
            TreeSet effAxis = nodeCaps.resolveAxisName(axis).sort()
            if (this.enableDebugTrace) this.script.println "[DEBUG] prepareDynamatrix(): converted axis argument '${axis}' into: " + effAxis
            effectiveAxes << effAxis
        }
        effectiveAxes = effectiveAxes.sort()
        if (this.enableDebugTrace) this.script.println "[DEBUG] prepareDynamatrix(): Initially detected effectiveAxes: " + effectiveAxes

        // By this point, a request for ['OS', '${COMPILER}VER', ~/ARC.+/]
        // yields [[OS], [ARCH], [CLANGVER, GCCVER]] from which we want to
        // get a set with two sets of axes that can do our separate builds:
        // [ [OS, ARCH, CLANGVER], [OS, ARCH, GCCVER] ]
        // ...and preferably really sorted :)
        effectiveAxes = Utils.cartesianSquared(effectiveAxes).sort()
        if (this.enableDebugTrace) this.script.println "[DEBUG] prepareDynamatrix(): Final detected effectiveAxes: " + effectiveAxes

        //nodeCaps.enableDebugTrace = true
        // Prepare all possible combos of requested axes (meaning we can
        // request a build agent label "A && B && C" and all of those
        // would work with our currently defined agents). The buildLabels
        // are expected to provide good uniqueness thanks to the SortedSet
        // of effectiveAxes and their values that we would look into.
        buildLabelCombos = []
        for (node in nodeCaps.nodeData.keySet()) {
            // Looking at each node separately allows us to be sure that any
            // combo of axis-values (all of which it allegedly provides)
            // can be fulfilled
            def nodeAxisCombos = []
            for (axisSet in effectiveAxes) {
                // Now looking at one definitive set of axis names that
                // we would pick supported values for, by current node:
                def axisCombos = []
                for (axis in axisSet) {
                    def tmpset = nodeCaps.resolveAxisValues(axis, node, true)
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
                        if (this.enableDebugTrace) this.script.println "[DEBUG] prepareDynamatrix(): ignored buildLabelCombos collected for node ${node} with requested axis set ${axisSet}: only got " + axisCombos
                    }
                }
            }
            if (nodeAxisCombos.size() > 0) {
                // It is okay if several nodes can run a build
                // which matches the given requirements
                nodeAxisCombos = nodeAxisCombos.sort()
                buildLabelCombos << nodeAxisCombos
            }
        }
        buildLabelCombos = buildLabelCombos.sort()
        if (this.enableDebugTrace) this.script.println "[DEBUG] prepareDynamatrix(): Initially detected buildLabelCombos (still grouped per node): " + buildLabelCombos
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

        buildLabelCombosFlat = []
        for (nodeResults in buildLabelCombos) {
            for (nodeAxisCombos in nodeResults) {
                // this nodeResults contains the set of sets of label values
                // supported for one of the original effectiveAxes requirements,
                // where each of nodeAxisCombos contains a set of axisValues
                if (this.enableDebugTrace) this.script.println "[DEBUG] prepareDynamatrix(): Expanding : " + nodeAxisCombos
                def tmp = Utils.cartesianSquared(nodeAxisCombos).sort()
                if (this.enableDebugTrace) this.script.println "[DEBUG] prepareDynamatrix(): Expanded into : " + tmp
                // Add members of tmp (many sets of unique key=value combos
                // for each axis) as direct members of buildLabelCombosFlat
                buildLabelCombosFlat += tmp
            }
        }

        // Convert Sets of Sets of strings in buildLabelCombos into the
        // array of strings (keys of the BLA Map) we can feed into the
        // agent requirements of generated pipeline stages:
        buildLabelsAgents = mapBuildLabelExpressions(buildLabelCombosFlat)
        def blaStr = ""
        for (ble in buildLabelsAgents.keySet()) {
            blaStr += "\n    '${ble}' => " + buildLabelsAgents[ble]
        }
        if (this.enableDebugTrace) this.script.println "[DEBUG] prepareDynamatrix(): detected ${buildLabelsAgents.size()} buildLabelsAgents combos:" + blaStr

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
        for (combo in blcSet) {
            // Note that labels can be composite, e.g. "COMPILER=GCC GCCVER=1.2.3"
            // ble == build label expression
            String ble = String.join('&&', combo).replaceAll('\\s+', '&&')
            blaMap[ble] = combo
        }
        return blaMap
    }

    def generateBuild(dynacfgOrig = [:], Closure body = null) {
        /* Returns a map of stages */
        if (buildLabelsAgents.size() == 0) {
            this.script.println "[ERROR] generateBuild() : should call prepareDynamatrix() first, or that found nothing usable"
            return null
        }

        // Use a separate copy of the configuration for this build
        // since different scenarios may be customized - although
        // they all take off from the same baseline setup...
        DynamatrixConfig dynacfgBuild = this.dynacfg
        dynacfgBuild.initDefault(dynacfgOrig)

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
            buildLabelsAgentsBuild += mapBuildLabelExpressions(dynacfgBuild.dynamatrixRequiredLabelCombos)
        }

        // Here we will collect axes that come from optional dynacfg fields
        Set virtualAxes = []

        // Process the map of "virtual axes": dynamatrixAxesVirtualLabelsMap
        if (dynacfgBuild.dynamatrixAxesVirtualLabelsMap.size() > 0) {
            // Map of "axis: [array, of, values]"
            if (this.enableDebugTrace) this.script.println "[DEBUG] generateBuild(): dynamatrixAxesVirtualLabelsMap: ${dynacfgBuild.dynamatrixAxesVirtualLabelsMap}"
            Set dynamatrixAxesVirtualLabelsCombos = []
            for (k in dynacfgBuild.dynamatrixAxesVirtualLabelsMap.keySet()) {
                def vals = dynacfgBuild.dynamatrixAxesVirtualLabelsMap[k]
                if (!Utils.isList(vals) || vals.size() == 0) continue

                // Collect possible values of this one key
                Set keyvalues = []
                for (v in vals) {
                    // Store each value of the provided axis as a set with one item
                    Set vv = ["${k}=${v}"]
                    keyvalues << vv
                }

                if (this.enableDebugTrace) this.script.println "[DEBUG] generateBuild(): combining dynamatrixAxesVirtualLabelsCombos: ${dynamatrixAxesVirtualLabelsCombos}\n    with keyvalues: ${keyvalues}"
                dynamatrixAxesVirtualLabelsCombos = Utils.cartesianProduct(dynamatrixAxesVirtualLabelsCombos, keyvalues)
            }

            if (this.enableDebugTrace) this.script.println "[DEBUG] generateBuild(): " +
                "combining dynamatrixAxesVirtualLabelsCombos: ${dynamatrixAxesVirtualLabelsCombos}" +
                "\n    with virtualAxes: ${virtualAxes}" +
                "\ndynacfgBuild.dynamatrixAxesVirtualLabelsMap.size()=${dynacfgBuild.dynamatrixAxesVirtualLabelsMap.size()} " +
                "virtualAxes.size()=${virtualAxes.size()} " +
                "dynamatrixAxesVirtualLabelsCombos.size()=${dynamatrixAxesVirtualLabelsCombos.size()}"

            // TODO: Will we have more virtualAxes inputs, or might just use assignment here?
            virtualAxes = Utils.cartesianProduct(dynamatrixAxesVirtualLabelsCombos, virtualAxes)

            if (this.enableDebugTrace) this.script.println "[DEBUG] generateBuild(): ended up with virtualAxes: ${virtualAxes}"
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

        // Quick safe pre-filter, in case that user-provided constraints
        // only impact one type of axis:
        if (dynacfgBuild.excludeCombos.size() > 0) {
            def removed = 0
            if (buildLabelsAgentsBuild.size() > 0) {
                DynamatrixSingleBuildConfig dsbc = new DynamatrixSingleBuildConfig(this.script)
                for (ble in buildLabelsAgentsBuild.keySet()) {
                    dsbc.buildLabelExpression = ble
                    dsbc.buildLabelSet = buildLabelsAgentsBuild[ble]
                    if (dsbc.matchesConstraints(dynacfgBuild.excludeCombos)) {
                        buildLabelsAgentsBuild.remove(ble)
                        removed++
                    }
                }
            }

            if (virtualAxes.size() > 0) {
                DynamatrixSingleBuildConfig dsbc = new DynamatrixSingleBuildConfig(this.script)
                for (virtualLabelSet in virtualAxes) {
                    dsbc.virtualLabelSet = virtualLabelSet
                    if (dsbc.matchesConstraints(dynacfgBuild.excludeCombos)) {
                        virtualAxes -= virtualLabelSet
                        removed++
                    }
                }
            }

            if (dynacfgBuild.dynamatrixAxesCommonEnv.size() > 0) {
                DynamatrixSingleBuildConfig dsbc = new DynamatrixSingleBuildConfig(this.script)
                for (envvarSet in dynacfgBuild.dynamatrixAxesCommonEnv) {
                    dsbc.envvarSet = envvarSet
                    if (dsbc.matchesConstraints(dynacfgBuild.excludeCombos)) {
                        dynacfgBuild.dynamatrixAxesCommonEnv -= envvarSet
                        removed++
                    }
                }
            }

            if (dynacfgBuild.dynamatrixAxesCommonOpts.size() > 0) {
                DynamatrixSingleBuildConfig dsbc = new DynamatrixSingleBuildConfig(this.script)
                for (clioptSet in dynacfgBuild.dynamatrixAxesCommonOpts) {
                    dsbc.clioptSet = clioptSet
                    if (dsbc.matchesConstraints(dynacfgBuild.excludeCombos)) {
                        dynacfgBuild.dynamatrixAxesCommonOpts -= clioptSet
                        removed++
                    }
                }
            }

            if (removed > 0) {
                //if (this.enableDebugTrace)
                    this.script.println "[DEBUG] generateBuild(): quick pass over excludeCombos[] removed ${removed} direct hits from original axis values"
            }

        }

        // Finally, combine all we have (and remove what we do not want to have)
        def removedTotal = 0
        Set<DynamatrixSingleBuildConfig> dsbcSet = []
        for (ble in buildLabelsAgentsBuild.keySet()) {
            // We can generate numerous build configs below that
            // would all require this (or identical) agent by its
            // build label expression, so prepare the shared part:
            DynamatrixSingleBuildConfig dsbcBle = new DynamatrixSingleBuildConfig(this.script)
            Set<DynamatrixSingleBuildConfig> dsbcBleSet = []
            def removedBle = 0

            // One of (several possible) combinations of node labels:
            dsbcBle.buildLabelExpression = ble
            dsbcBle.buildLabelSet = buildLabelsAgentsBuild[ble]

            // Roll the snowball, let it grow!
            if (dynacfgBuild.dynamatrixAxesCommonOpts.size() > 0) {
                Set<DynamatrixSingleBuildConfig> dsbcBleSetTmp = []
                for (clioptSet in dynacfgBuild.dynamatrixAxesCommonOpts) {
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
                for (envvarSet in dynacfgBuild.dynamatrixAxesCommonEnv) {
                    for (DynamatrixSingleBuildConfig tmp in dsbcBleSet) {
                        DynamatrixSingleBuildConfig dsbcBleTmp = tmp.clone()
                        dsbcBleTmp.envvarSet = envvarSet
                        dsbcBleSetTmp += dsbcBleTmp
                    }
                }
                dsbcBleSet = dsbcBleSetTmp
            }

            if (virtualAxes.size() > 0) {
                Set<DynamatrixSingleBuildConfig> dsbcBleSetTmp = []
                if (this.enableDebugTrace) this.script.println "[DEBUG] generateBuild(): COMBINING: virtualAxes: ${Utils.castString(virtualAxes)}\nvs. dsbcBleSet: ${dsbcBleSet}"
                for (virtualLabelSet in virtualAxes) {
                    //if (this.enableDebugTrace) this.script.println "[DEBUG] generateBuild(): checking virtualLabelSet: ${Utils.castString(virtualLabelSet)}"
                    for (DynamatrixSingleBuildConfig tmp in dsbcBleSet) {
                        DynamatrixSingleBuildConfig dsbcBleTmp = tmp.clone()
                        //DynamatrixSingleBuildConfig dsbcBleTmp = new DynamatrixSingleBuildConfig(tmp)
                        //dsbcBleTmp = tmp
                        if (this.enableDebugTrace) this.script.println "[DEBUG] generateBuild(): checking virtualLabelSet: ${Utils.castString(virtualLabelSet)} with ${dsbcBleTmp}"
                        dsbcBleTmp.virtualLabelSet = virtualLabelSet
                        dsbcBleSetTmp += dsbcBleTmp
                    }
                }
                dsbcBleSet = dsbcBleSetTmp
            }

            if (this.enableDebugTrace) {
                this.script.println "[DEBUG] generateBuild(): BEFORE EXCLUSIONS: collected ${dsbcBleSet.size()} combos for individual builds with agent build label expression '${ble}'"
                for (DynamatrixSingleBuildConfig dsbcBleTmp in dsbcBleSet) {
                        this.script.println "[DEBUG] generateBuild(): selected combo: ${dsbcBleTmp}"
                }
            }

            // filter away excludeCombos, and possibly cases of allowedFailure
            // (if runAllowedFailure==false)
            // Note that we DO NOT PREFILTER much, because some user-provided
            // exclusion combinations might only make sense in some corner
            // cases (e.g. don't want this "compiler + Crevision" on that OS,
            // but want it on another)
            if (dynacfgBuild.excludeCombos.size() > 0) {
                for (DynamatrixSingleBuildConfig dsbcBleTmp in dsbcBleSet) {
                    if (dsbcBleTmp.matchesConstraints(dynacfgBuild.excludeCombos)) {
                        dsbcBleSet -= dsbcBleTmp
                        removedBle++
                        dsbcBleTmp.isExcluded = true
                        // TODO: track isExcluded?
                        if (this.enableDebugTrace) this.script.println "[DEBUG] generateBuild(): excluded combo: ${dsbcBleTmp}\nwith ${dynacfgBuild.excludeCombos}"
                    }
                }
            }

            if (dynacfgBuild.allowedFailure.size() > 0) {
                for (DynamatrixSingleBuildConfig dsbcBleTmp in dsbcBleSet) {
                    if (dsbcBleTmp.matchesConstraints(dynacfgBuild.allowedFailure)) {
                        dsbcBleSet -= dsbcBleTmp
                        removedBle++
                        dsbcBleTmp.isAllowedFailure = true
                        if (dynacfgBuild.runAllowedFailure) {
                            dsbcBleSet += dsbcBleTmp
                        } else {
                            if (this.enableDebugTrace) this.script.println "[DEBUG] generateBuild(): excluded combo: ${dsbcBleTmp}\nwith ${dynacfgBuild.allowedFailure} (because we do not runAllowedFailure this time)"
                        }
                    }
                }
            }

            dsbcSet += dsbcBleSet
            removedTotal += removedBle

            if (removedBle > 0) {
                if (this.enableDebugTrace)
                    this.script.println "[DEBUG] generateBuild(): excludeCombos[] matching removed ${removedBle} direct hits from candidate builds for label ${ble}"
            }

        }

        if (removedTotal > 0) {
            //if (this.enableDebugTrace)
                this.script.println "[DEBUG] generateBuild(): excludeCombos[] matching removed ${removedTotal} direct hits from candidate builds matrix"
        }

        //if (this.enableDebugTrace)
            this.script.println "[DEBUG] generateBuild(): collected ${dsbcSet.size()} combos for individual builds"
        for (DynamatrixSingleBuildConfig dsbcBleTmp in dsbcSet) {
            if (this.enableDebugTrace)
                this.script.println "[DEBUG] generateBuild(): selected combo: ${dsbcBleTmp}"
        }

        // Consider allowedFailure (if flag runAllowedFailure==true)
        // when preparing the stages below:
        Map parallelStages = [:]
        return parallelStages
    }

}

