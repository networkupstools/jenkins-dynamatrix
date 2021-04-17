import java.util.ArrayList;
import java.util.Arrays.*;
import java.util.regex.*;

import org.nut.dynamatrix.DynamatrixConfig;
import org.nut.dynamatrix.NodeCaps;
import org.nut.dynamatrix.NodeData;
import org.nut.dynamatrix.dynamatrixGlobalState;

/*
 * Example agents and call signature from README:

* "testoi":
----
COMPILER=CLANG COMPILER=GCC
CLANGVER=8 CLANGVER=9
GCCVER=10 GCCVER=4.4.4 GCCVER=4.9 GCCVER=6 GCCVER=7
OS=openindiana
ARCH=x86_64 ARCH=x86
nut-builder zmq-builder
----
* "testdeb":
----
COMPILER=GCC
GCCVER=4.8 GCCVER=4.9 GCCVER=5 GCCVER=7
OS=linux
ARCH=x86_64 ARCH=x86 ARCH=armv7l
nut-builder zmq-builder linux-kernel-builder
----

// Prepare parallel stages for the dynamatrix:
def parallelStages = prepareDynamatrix(
    commonLabelExpr: 'nut-builder',
    compilerType: 'C',
    compilerLabel: 'COMPILER',
    compilerTools: ['CC', 'CXX', 'CPP'],
    dynamatrixAxesLabels: ['OS', '${COMPILER}VER', 'ARCH'],
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

/* Returns a map of stages */
def call(dynacfgOrig = [:], Closure body = null) {
    println "[WARNING] NOT FULLY IMPLEMENTED: prepareDynamatrix.groovy"

    // Have some defaults, if only to have all expected fields defined
    DynamatrixConfig dynacfg = new DynamatrixConfig()

    def sanityRes = dynacfg.initDefault(dynacfgOrig)
    if (sanityRes != true) {
        if (sanityRes instanceof String) {
            println sanityRes
        }
        // Assumed non-fatal - fields seen that are not in standard config
    }

    sanityRes = dynacfg.sanitycheckDynamatrixAxesLabels()
    if (sanityRes != true) {
        if (sanityRes instanceof String) {
            println sanityRes
        } else {
            println "No 'dynamatrixAxesLabels' were provided, nothing to generate"
        }
        return null
    }

    // TODO: Cache as label-mapped hash in dynamatrixGlobals so re-runs for
    // other configs for same builder would not query and parse real Jenkins
    // worker labels again and again.
    NodeCaps nodeCaps = new NodeCaps(this, dynacfg.commonLabelExpr, dynamatrixGlobalState.enableDebugTrace, dynamatrixGlobalState.enableDebugErrors)
    nodeCaps.optionalPrintDebug()

    // Original request could have regexes or groovy-style substitutions
    // to expand. The effectiveAxes is generally a definitive set of
    // sets of exact axis names, e.g. ['ARCH', 'CLANGVER', 'OS'] and
    // ['ARCH', 'GCCVER', 'OS'] as expanded from '${COMPILER}VER' part:
    Set effectiveAxes = []
    for (axis in dynacfg.dynamatrixAxesLabels) {
        TreeSet effAxis = nodeCaps.resolveAxisName(axis).sort()
        println "[DEBUG] prepareDynamatrix(): converted axis argument '${axis}' into: " + effAxis
        effectiveAxes << effAxis
    }
    effectiveAxes = effectiveAxes.sort()
    println "[DEBUG] prepareDynamatrix(): Initially detected effectiveAxes: " + effectiveAxes

    // By this point, a request for ['OS', '${COMPILER}VER', ~/ARC.+/]
    // yields [[OS], [ARCH], [CLANGVER, GCCVER]] from which we want to
    // get a set with two sets of axes that can do our separate builds:
    // [ [OS, ARCH, CLANGVER], [OS, ARCH, GCCVER] ]
    // ...and preferably really sorted :)
    effectiveAxes = infra.cartesianSquared(effectiveAxes).sort()
    println "[DEBUG] prepareDynamatrix(): Final detected effectiveAxes: " + effectiveAxes

    //nodeCaps.enableDebugTrace = true
    // Prepare all possible combos of requested axes (meaning we can
    // request a build agent label "A && B && C" and all of those
    // would work with our currently defined agents). The buildLabels
    // are expected to provide good uniqueness thanks to the SortedSet
    // of effectiveAxes and their values that we would look into.
    Set buildLabelCombos = []
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
                    println "[DEBUG] prepareDynamatrix(): ignored buildLabelCombos collected for node ${node} with requested axis set ${axisSet}: only got " + axisCombos
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
    println "[DEBUG] prepareDynamatrix(): Initially detected buildLabelCombos (still grouped per node): " + buildLabelCombos
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

    def buildLabelCombosFlat = []
    for (nodeResults in buildLabelCombos) {
        for (nodeAxisCombos in nodeResults) {
            // this nodeResults contains the set of sets of label values
            // supported for one of the original effectiveAxes requirements,
            // where each of nodeAxisCombos contains a set of axisValues
            println "[DEBUG] prepareDynamatrix(): Expanding : " + nodeAxisCombos
            def tmp = infra.cartesianSquared(nodeAxisCombos).sort()
            println "[DEBUG] prepareDynamatrix(): Expanded into : " + tmp
            // Add members of tmp (many sets of unique key=value combos
            // for each axis) as direct members of buildLabelCombosFlat
            buildLabelCombosFlat += tmp
        }
    }

    // Convert Sets of Sets of strings in buildLabelCombos into the
    // array of strings we can feed to the agent steps in pipeline:
    Set<String> buildLabels = []
    for (combo in buildLabelCombosFlat) {
        String ble = String.join(" && ", combo)
        buildLabels << ble
        println "[DEBUG] prepareDynamatrix(): detected buildLabels expression: " + ble
    }
    println "[DEBUG] prepareDynamatrix(): detected buildLabels: " + buildLabels

    return true;
}
