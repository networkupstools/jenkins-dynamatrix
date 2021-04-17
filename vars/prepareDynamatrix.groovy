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
    println "NOT IMPLEMENTED: prepareDynamatrix.groovy"

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

    NodeCaps nodeCaps = new NodeCaps(this, dynacfg.commonLabelExpr, dynamatrixGlobal.enableDebugTrace, dynamatrixGlobal.enableDebugErrors)
    nodeCaps.optionalPrintDebug()

    Set effectiveAxes = []
    for (axis in dynacfg.dynamatrixAxesLabels) {
        effectiveAxes << nodeCaps.resolveAxisName(axis)
    }
    effectiveAxes = effectiveAxes.flatten()

    println "[DEBUG] prepareDynamatrix(): Detected effectiveAxes: " + effectiveAxes

    def buildLabels = []

    return true;
}
