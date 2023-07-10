// Steps should not be in a package, to avoid CleanGroovyClassLoader exceptions...
// package org.nut.dynamatrix;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.*;

import org.nut.dynamatrix.DynamatrixConfig;
import org.nut.dynamatrix.Dynamatrix;
import org.nut.dynamatrix.NodeCaps;
import org.nut.dynamatrix.NodeData;
import org.nut.dynamatrix.Utils;
import org.nut.dynamatrix.dynamatrixGlobalState;

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

prepareDynamatrix([
    commonLabelExpr: 'nut-builder',
    defaultDynamatrixConfig: 'C',
    dynamatrixAxesVirtualLabelsMap: [
        'BITS': [32, 64],
        'CSTDVERSION': ['03', '2a']
        ],
    mergeMode: [ 'dynamatrixAxesVirtualLabelsMap': 'merge', 'excludeCombos': 'merge' ],
    allowedFailure: [ [~/CSTDVARIANT=c/] ],
    runAllowedFailure: true,
    excludeCombos: [ [~/BITS=32/, ~/ARCH_BITS=64/], [~/BITS=64/, ~/ARCH_BITS=32/] ],
    dynamatrixAxesLabels: [~/^OS_DISTRO/, '${COMPILER}VER', 'ARCH${ARCH_BITS}']
//    dynamatrixAxesLabels: ['OS', '${COMPILER}VER', ~/ARC.+/]
//    dynamatrixAxesLabels: ['OS', ~/CLAN.+/]
    ])

 */

/* Returns a map of stages
 * Or a Set, if called from inside a pipeline stage (CPS code).
 */
def call(Map dynacfgOrig = [:], Closure body = null) {
    return call(dynacfgOrig, false, true, body)
}

def call(Map dynacfgOrig = [:], boolean returnSet, Closure body = null) {
    return call(dynacfgOrig, returnSet, true, body)
}

def call(Map dynacfgOrig = [:], boolean returnSet, Boolean rememberClones, Closure body = null) {
    //if (dynamatrixGlobalState.enableDebugErrors) println "[WARNING] NOT FULLY IMPLEMENTED: prepareDynamatrix.groovy step"

    // Have some defaults, if only to have all expected fields defined
    String dynamatrixComment = "prepareDynamatrix() step"
    Dynamatrix dynamatrix = new Dynamatrix(this, dynamatrixComment +
            (dynamatrixGlobalState.enableDebugTrace ? " for ${dynacfgOrig}" : ""))
    if (dynamatrixGlobalState.enableDebugTrace)
        dynamatrix.dynamatrixComment = dynamatrixComment
    dynamatrix.prepareDynamatrix(dynacfgOrig)

    // We use a custom "dynamatrix" instance here, so no further
    // customizations for generateBuild are needed => passing null
    return dynamatrix.generateBuild(null, returnSet, rememberClones, body)
}
