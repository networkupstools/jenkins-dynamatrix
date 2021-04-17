package org.nut.dynamatrix;

class dynamatrixGlobalState {
    // buildMatrixCell* steps populate this with their build analysis results
    static ArrayList<Object> issueAnalysis = []

    // These settings can make constructors of NodeCaps and NodeData more
    // verbose (when called from wrapping code that passes these args)
    static Boolean enableDebugTrace = false
    static Boolean enableDebugErrors = true
}
