// Steps should not be in a package, to avoid CleanGroovyClassLoader exceptions...
// package org.nut.dynamatrix;

import org.nut.dynamatrix.DynamatrixStash;

void call(String stashName) {
    DynamatrixStash.unstashCleanSrc(this, stashName)
} // unstashCleanSrc()
