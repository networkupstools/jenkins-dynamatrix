import java.util.ArrayList;
import java.util.Arrays.*;
import java.util.regex.*;

import org.nut.dynamatrix.NodeCaps;
import org.nut.dynamatrix.NodeData;
import org.nut.dynamatrix.dynamatrixGlobal;

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
    mayFail: [[~/OS=.+/, ~/GCCVER=4\..+/], ['ARCH=armv7l'], [~/std.+=.+89/]],
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
def call(Map<Object, Object> dynacfg = [:], Closure body = null) {
    println "[WARNING] NOT FULLY IMPLEMENTED: prepareDynamatrix.groovy"

    try { // Check if field exists
        if (dynacfg.dynamatrixAxesLabels == null) {
            dynacfg.dynamatrixAxesLabels = null
        }
    } catch (MissingPropertyException e) {
        dynacfg.dynamatrixAxesLabels = null
    }

    if (dynacfg.dynamatrixAxesLabels != null) {
        if (dynacfg.dynamatrixAxesLabels.getClass() in [ArrayList, List, Set, Object[]]) {
        } else if (dynacfg.dynamatrixAxesLabels.getClass() in [String, java.lang.String, GString]) {
            if (dynacfg.dynamatrixAxesLabels.equals("")) {
                dynacfg.dynamatrixAxesLabels = null
            } else {
                dynacfg.dynamatrixAxesLabels = [dynacfg.dynamatrixAxesLabels]
            }
        } else if (dynacfg.dynamatrixAxesLabels.getClass() in [java.util.regex.Pattern]) {
            dynacfg.dynamatrixAxesLabels = [dynacfg.dynamatrixAxesLabels]
        } else {
            println "Not sure what type 'dynamatrixAxesLabels' is: " + dynacfg.dynamatrixAxesLabels.getClass() + " : " + dynacfg.dynamatrixAxesLabels.toString()
            dynacfg.dynamatrixAxesLabels = null
        }
    }
    if (dynacfg.dynamatrixAxesLabels == null) {
        println "No 'dynamatrixAxesLabels' were provided, nothing to generate"
        return null
    }

    try { // Check if field exists
        if (dynacfg.commonLabelExpr == null || dynacfg.commonLabelExpr.equals("")) {
            dynacfg.commonLabelExpr = null
        }
    } catch (MissingPropertyException e) {
        dynacfg.commonLabelExpr = null
    }

    // TODO: Cache as label-mapped hash in dynamatrixGlobals so re-runs for
    // other configs for same builder would not query and parse real Jenkins
    // worker labels again and again.
    NodeCaps nodeCaps = new NodeCaps(this, dynacfg.commonLabelExpr, dynamatrixGlobal.enableDebugTrace, dynamatrixGlobal.enableDebugErrors)
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

    Set eff = []
    for (a in effectiveAxes) {
        // Cartesian product, maybe not in most efficient manner
        // but this is not too hot a codepath
        println "[DEBUG] prepareDynamatrix(): multiplier: " + a.getClass() + ": " + a
        if (eff.size() == 0) {
            eff = a
        } else {
            eff = cartesianMultiply(eff, a)
        }
    }
    effectiveAxes = eff
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
            // cartesian here?
            buildLabelCombos << nodeAxisCombos
        }
    }
    buildLabelCombos = buildLabelCombos.sort()
    println "[DEBUG] prepareDynamatrix(): detected buildLabelCombos: " + buildLabelCombos

    // Convert Sets of Sets of strings in buildLabelCombos into the
    // array of strings we can feed to the agent steps in pipeline:
    Set<String> buildLabels = []
    for (combo in buildLabelCombos) {
        String ble = String.join(" && ", combo)
        buildLabels << ble
        println "[DEBUG] prepareDynamatrix(): detected buildLabels expression: " + ble
    }
    println "[DEBUG] prepareDynamatrix(): detected buildLabels: " + buildLabels

    return true;
}

static Iterable cartesianMultiply(Iterable a, Iterable b) {
    // Inspired by https://rosettacode.org/wiki/Cartesian_product_of_two_or_more_lists#Groovy
    assert [a,b].every { it != null }
    def (m,n) = [a.size(),b.size()]
    return ( (0..<(m*n)).inject([]) { prod, i -> prod << [a[i.intdiv(n)], b[i%n]].flatten().sort() } )
}
