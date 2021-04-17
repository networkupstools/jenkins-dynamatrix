import java.util.ArrayList;
import java.util.Arrays.*;
import java.util.regex.*;

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
    dynamatrixRequiredLabelCombos: [[/OS=bsd/, /CLANG=12/]],
    mayFail: [[/OS=.+/, /GCCVER=4\..+/], ['ARCH=armv7l'], [/std.+=.+89/]],
    skip: [[/OS=openindiana/, /CLANGVER=9/, /ARCH=x86/], [/GCCVER=4\.[0-7]\..+/, /std.+=+(!?89|98|99|03)/], [/GCCVER=4\.([8-9]|1[0-9]+)\..+/, /std.+=+(!?89|98|99|03|11)/]]
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
        } else if (dynacfg.dynamatrixAxesLabels in [String, GString]) {
            if (dynacfg.dynamatrixAxesLabels.equals("")) {
                dynacfg.dynamatrixAxesLabels = null
            } else {
                dynacfg.dynamatrixAxesLabels = [dynacfg.dynamatrixAxesLabels]
            }
        } else if (dynacfg.dynamatrixAxesLabels in [java.util.regex.Pattern]) {
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

    def nodeCaps = null
    def buildLabels = []

    if (dynacfg.commonLabelExpr != null) {
        nodeCaps = infra.detectCapabilityLabelsForBuilders(dynacfg.commonLabelExpr)
    } else {
        nodeCaps = infra.detectCapabilityLabelsForBuilders()
    }
    //infra.printNodeCaps(nodeCaps)

    Set effectiveAxes = []
    for (axis in dynacfg.dynamatrixAxesLabels) {
        effectiveAxes << resolveAxisName(dynacfg, nodeCaps, axis)
    }
    effectiveAxes = effectiveAxes.flatten()

    println "[DEBUG] prepareDynamatrix(): Detected effectiveAxes: " + effectiveAxes

    return true;
}

def resolveAxisName(Map<Object, Object> dynacfg, Map<Object, Object> nodeCaps, Object axis) {
    // The "axis" may be a fixed string to return quickly, or
    // a regex to match among nodeCaps.nodeData[].labelMap[] keys
    // or a groovy expansion to look up value(s) of another axis
    // which is not directly used in the resulting build set.
    // Recurse until (a flattened Set of) fixed strings with
    // axis names (keys of labelMap[] above) can be returned.
    Set res = []

    // If caller has a Set to check, they should iterate it on their own
    // TODO: or maybe provide a helper wrapper?..
    if (axis == null || (!axis in [String, GString, java.util.regex.Pattern]) || axis.equals("")) {
        println "[DEBUG] resolveAxisName(): invalid input value or class: " + axis.toString()
        return res;
    }

    println "[DEBUG] resolveAxisName(): " + axis.getClass() + " : " + axis.toString()

    if (axis in String || axis in GString) {
        // NOTE: No support for nested request like '${COMPILER${VENDOR}}VER'
        def matcher = axis =~ /\$\{([^\}]+)\}/
        if (matcher.find()) {
            // Substitute values of one expansion and recurse -
            // if there are more dollar-braces, they will be
            // expanded in the nested layer(s)
            def varAxis = matcher[0][1]

            // This layer of recursion gets us fixed-string name
            // of the variable axis itself (like fixed 'COMPILER'
            // string for variable part '${COMPILER}' in originally
            // requested axis name '${COMPILER}VAR').
            for (expandedAxisName in resolveAxisName(dynacfg, nodeCaps, varAxis)) {
                if (expandedAxisName == null || (!expandedAxisName in String && !expandedAxisName in GString) || expandedAxisName.equals("")) continue;

                // This layer of recursion gets us fixed-string name
                // variants of the variable axis (like 'GCC' and
                // 'CLANG' for variable '${COMPILER}' in originally
                // requested axis name '${COMPILER}VAR').
                // Pattern looks into nodeCaps.
                for (expandedAxisValue in resolveAxisValues(dynacfg, nodeCaps, expandedAxisName)) {
                    if (expandedAxisValue == null || (!expandedAxisValue in String && !expandedAxisValue in GString) || expandedAxisValue.equals("")) continue;

                    // In the original axis like '${COMPILER}VER' apply current item
                    // from expandedAxisValue like 'GCC' (or 'CLANG' in next loop)
                    // and yield 'GCCVER' as the axis name for original request:
                    String tmpAxis = axis.replaceFirst(/\$\{${varAxis}\}/, expandedAxisValue)

                    // Now resolve the value of "axis" with one substituted
                    // expansion variant - if it is a fixed string by now,
                    // this will end quickly:
                    res << resolveAxisName(dynacfg, nodeCaps, tmpAxis)
                }
            }
            return res.flatten()
        } else {
            res = [axis]
            return res
        }
    }

    if (axis in java.util.regex.Pattern) {
        // Return label keys which match the expression
        for (node in nodeCaps.nodeData.keySet()) {
            if (node == null) continue

            for (String label : nodeCaps.nodeData[node].labelMap.keySet()) {
                if (axis in java.util.regex.Pattern) {
                    if (label =~ axis) {
                        res << label
                    }
                }
            }
        }
    }

    return res.flatten()
}

def resolveAxisValues(Map<Object, Object> dynacfg, Map<Object, Object> nodeCaps, Object axis) {
    // For a fixed-string name or regex pattern, return a flattened Set of
    // values which have it as a key in nodeCaps.nodeData[].labelMap[]

    Set res = []
    if (axis == null || (!axis in [String, GString, java.util.regex.Pattern]) || axis.equals("")) {
        println "[DEBUG] resolveAxisValues(): invalid input value or class: " + axis.toString()
        return res;
    }

    println "[DEBUG] resolveAxisValues(): " + axis.getClass() + " : " + axis.toString()

    for (node in nodeCaps.nodeData.keySet()) {
        if (node == null) continue

        for (String label : nodeCaps.nodeData[node].labelMap.keySet()) {
            if (axis in [String, GString]) {
                if (axis.equals(label)) {
                    res << nodeCaps.nodeData[node].labelMap[label]?.trim()
                }
            }
            if (axis in java.util.regex.Pattern) {
                if (label =~ axis) {
                    res << nodeCaps.nodeData[node].labelMap[label]?.trim()
                }
            }
        }
    }

    return res.flatten()
}
