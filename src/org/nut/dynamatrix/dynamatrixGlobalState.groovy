package org.nut.dynamatrix;

class dynamatrixGlobalState {
    // buildMatrixCell* steps populate this with their build analysis results
    static ArrayList<Object> issueAnalysis = []

    // These settings can make constructors of NodeCaps and NodeData more
    // verbose (when called from wrapping code that passes these args)
    static Boolean enableDebugTrace = false
    static Boolean enableDebugErrors = true

    // Takes a (DynamatrixSingleBuildConfig) object as argument and returns
    // a string. Can be used for definition of "meaningful" short tags by a
    // project, like "gnu99-gcc-freebsd-nowarn" or "c17-clang-xcode10.2-warn"
    // instead of a default long list of variables that impact the uniqueness
    // of a build setup.
    static def stageNameFunc = null
}

