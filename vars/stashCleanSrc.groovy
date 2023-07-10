// Steps should not be in a package, to avoid CleanGroovyClassLoader exceptions...
// package org.nut.dynamatrix;

import org.nut.dynamatrix.DynamatrixStash;

def call(String stashName, Closure scmbody = null) {
    // Optional closure can fully detail how the code is checked out
    return DynamatrixStash.stashCleanSrc(this, stashName, scmbody)
} // stashCleanSrc()
