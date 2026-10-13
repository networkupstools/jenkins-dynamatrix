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

// See README.adoc for real-world pipeline configuration examples and usage details.

/** Returns a map of stages
 * Or a Set, if called from inside a pipeline stage (CPS code).
 */
def call(Map dynacfgOrig = [:], Closure body = null) {
    return call(dynacfgOrig, false, false, body)
}

def call(Map dynacfgOrig = [:], boolean returnSet, Closure body = null) {
    return call(dynacfgOrig, returnSet, false, body)
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
