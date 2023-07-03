package org.nut.dynamatrix;

class dynamatrixGlobalState {
    /** buildMatrixCell* steps populate this with their build analysis results */
    static ArrayList<Object> issueAnalysis = []
    /** buildMatrixCell* steps populate this with their build analysis results */
    static ArrayList<Object> issueAnalysisCppcheck = []

    /**
     * Same as {@link #issueAnalysis}, but try to deduplicate equivalent
     * results received by different tools or in different scenarios.
     */
    static ArrayList<Object> issueAnalysisAggregated = []

    /**
     * Same as {@link #issueAnalysisCppcheck}, but try to deduplicate equivalent
     * results received by different tools or in different scenarios.
     */
    static ArrayList<Object> issueAnalysisAggregatedCppcheck = []

    /**
     * Some steps in a pipeline, such as git checkouts sped up by a local
     * reference repository, should use a worker (or even a definitive one).
     * Due to Pipeline DSL constraints, if we use labels we must use them
     * everywhere and can not juggle if-then to specify "agent any" or some
     * other option instead... only a string for the label expression.
     */
    static String labelDefaultWorker = null
    /** Something preferably with cached sources nearby */
    static String labelCheckoutWorker = null
    /** Something with tools to generate MAN, PDF, HTML... and to spell-check */
    static String labelDocumentationWorker = null

    static Boolean useGitRefrepoDirWS = false

    /** For build quality evolution analysis, which branch is the reference? */
    static String branchDefaultStable = null

    /**
     * These settings can make constructors of {@link NodeCaps} and
     * {@link NodeData} more verbose (when called from wrapping code
     * that passes these args)
     */
    static Boolean enableDebugTrace = false
    /** @see #enableDebugTrace */
    static Boolean enableDebugTraceFailures = true
    /** @see #enableDebugTrace */
    static Boolean enableDebugErrors = true
    /** @see #enableDebugTrace */
    static Boolean enableDebugMilestones = true
    /** @see #enableDebugTrace */
    static Boolean enableDebugMilestonesDetails = false

    /** (Duplicate) Debug of some key troubleshooting
     * points to System.(out|err).println? */
    static Boolean enableDebugSysprint = true

    /**
     * Takes a {@link DynamatrixSingleBuildConfig} object as argument and
     * returns a string. Can be used for definition of "meaningful" short
     * tags by a project, like {@code "gnu99-gcc-freebsd-nowarn"} or
     * {@code "c17-clang-xcode10.2-warn"} instead of a default long list
     * of variables that impact the uniqueness of a build setup. For one
     * reference implementation, see
     *    <pre>dynamatrixGlobalState.stageNameFunc = DynamatrixSingleBuildConfig.&C_StageNameTagFunc</pre>
     */
    static def stageNameFunc = null
}

