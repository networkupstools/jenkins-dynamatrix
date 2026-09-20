package org.nut.dynamatrix;

import com.cloudbees.groovy.cps.NonCPS;
import hudson.model.Result;

/**
 * Various generic helpers to check or process our data.
 */
class Utils {
    public static final def classesStrings = [String, GString, org.codehaus.groovy.runtime.GStringImpl, java.lang.String]
    public static final def classesRegex = [java.util.regex.Pattern]
    public static final def classesStringOrRegex = classesStrings + classesRegex
    public static final def classesMaps = [Map, LinkedHashMap, HashMap]
    public static final def classesLists = [ArrayList, List, Set, TreeSet, LinkedHashSet, Object[]]
    public static final def classesClosures = [org.jenkinsci.plugins.workflow.cps.CpsClosure2, groovy.lang.Closure, Closure]

    @NonCPS
    public static boolean isString(def obj) {
        if (obj == null) return false;
        if (obj.getClass() in classesStrings) return true;
        if (obj instanceof String) return true;
        if (obj instanceof GString) return true;
        if (obj instanceof org.codehaus.groovy.runtime.GStringImpl) return true;
        return false;
    }

    @NonCPS
    public static boolean isRegex(def obj) {
        if (obj == null) return false;
        if (obj.getClass() in classesRegex) return true;
        if (obj instanceof java.util.regex.Pattern) return true;
        return false;
    }

    @NonCPS
    public static boolean isStringOrRegex(def obj) {
        if (obj == null) return false;
        if (isString(obj) || isRegex(obj)) return true;
        return false;
    }

    @NonCPS
    public static boolean isStringNotEmpty(def obj) {
        if (!isString(obj)) return false;
        if ("".equals(obj)) return false;
        return true;
    }

    @NonCPS
    public static boolean isStringOrRegexNotEmpty(def obj) {
        if (isString(obj)) {
            if ("".equals(obj)) return false;
            return true;
        }
        // No idea how to check for empty regex,
        // or more - if that makes sense :)
        return isRegex(obj)
    }

    @NonCPS
    public static boolean isMap(def obj) {
        if (obj == null) return false;
        if (obj.getClass() in classesMaps) return true;
        if (obj instanceof Map) return true;
        return false;
    }

    @NonCPS
    public static boolean isMapNotEmpty(def obj) {
        if (!isMap(obj)) return false;
        return (obj.size() > 0)
    }

    @NonCPS
    public static boolean isList(def obj) {
        if (obj == null) return false;
        if (obj.getClass() in classesLists) return true;
        if (obj instanceof Set) return true;
        if (obj instanceof List) return true;
        if (obj instanceof ArrayList) return true;
        if (obj instanceof Object[]) return true;
        return false;
    }

    @NonCPS
    public static boolean isListNotEmpty(def obj) {
        if (!isList(obj)) return false;
        return (obj.size() > 0)
    }

    @NonCPS
    public static boolean isNode(def obj) {
        if (obj == null) return false;
        if (obj.getClass() in [hudson.model.Node] || obj in hudson.model.Node) return true;
        if (obj instanceof hudson.model.Node) return true;
        return false;
    }

    @NonCPS
    public static boolean isClosure(def obj) {
        if (obj == null) return false;
        if (obj.getClass() in classesClosures) return true;
        if (obj in org.jenkinsci.plugins.workflow.cps.CpsClosure2) return true;
        if (obj in groovy.lang.Closure) return true;
        if (obj instanceof org.jenkinsci.plugins.workflow.cps.CpsClosure2) return true;
        if (obj instanceof groovy.lang.Closure) return true;
        return false;
    }

    @NonCPS
    public static boolean isClosureNotEmpty(def obj) {
        if (!isClosure(obj)) return false;
        return ( obj != {} )
    }

    /**
     * @return true if the exception is likely a networking or agent failure
     * that can be retried by Dynamatrix.groovy (based on its catch blocks).
     */
    @NonCPS
    public static boolean isRetryableException(Throwable t) {
        if (t == null) return false
        String ts = t.toString()
        // Match logic in Dynamatrix.groovy (approximate for CPS/non-CPS safety)
        if (ts ==~ /.*(Unexpected termination of the channel|Timeout waiting for agent to come back|The channel is closing down or has closed down|Agent was removed|Node is being removed|was marked offline|Connection was broken|Cannot contact .*: java.lang.InterruptedException|java.nio.channels.ClosedChannelException|ChannelClosedException|hudson.remoting.ProxyException|hudson.remoting.RequestAbortedException|hudson.remoting.Channel.close|hudson.slaves.SlaveComputer.closeChannel|hudson.remoting.Channel.terminate|hudson.remoting.Request.abort).*/)
            return true
        if (ts ==~ /.*(Unable to create live FilePath for|No space left on device|Stale NFS file handle).*/)
            return true
        // Should have caught these above by full names
        if (ts ==~ /.*(ClosedChannelException|ProxyException|RequestAbortedException|Channel.close|SlaveComputer.closeChannel|Channel.terminate|Request.abort).*/)
            return true
        if (ts ==~ /.*(java.io.IOException: SSH channel is closed|Error (fetching|cloning) remote repo|Could not resolve host).*/)
            return true
        // build cell classifier messages
        if (ts ==~ /.*(agent connection|workspace|memory) problem.*/)
            return true
        // legacy fallback
        if (ts ==~ /.*(missing workspace|object directory .* does not exist|check .git\/objects\/info\/alternates|(spawn|fork|exec).*Resource temporarily unavailable).*/)
            return true
        // hudson.remoting.RequestAbortedException, RemotingSystemException, etc.
        if (ts.contains("hudson.remoting.RequestAbortedException") ||
            ts.contains("hudson.remoting.RemotingSystemException") ||
            ts.contains("hudson.remoting.ChannelClosedException"))
            return true
        return false
    }

    @NonCPS
    public static String castString(def obj) {
        return "<${obj?.getClass()}>(${obj?.toString()})"
    }

    /**
     * Strangely, the Set or list classes I needed in dynamatrix did not include
     * a cartesian multiplication seen in many examples and complained about a
     * <pre>
     *    groovy.lang.MissingMethodException: No signature of method:
     *      java.util.ArrayList.multiply() is applicable for argument
     *      types: (java.util.ArrayList) values: ...
     * </pre>
     * Injecting this method into Collection base class is suggested by the
     * articles linked below (using "Iterable.metaClass.mixin newClassName" or
     * "java.util.Collection.metaClass.newFuncName", but I am not sure how to
     * do that via Jenkins shared library once and for all its use-cases.
     * So the next best thing is to call a function to do stuff.<br/>
     *
     * Inspired by https://rosettacode.org/wiki/Cartesian_product_of_two_or_more_lists#Groovy
     * and https://coviello.blog/2013/05/19/adding-a-method-for-computing-cartesian-product-to-groovys-collections/
     *
     * @see #cartesianSquared
     */
    static Iterable cartesianProduct(Iterable a, Iterable b) {
        if (a.size() == 0) return b
        if (b.size() == 0) return a
        assert [a,b].every { it != null }
        def (m,n) = [a.size(),b.size()]
        return ( (0..<(m*n)).inject([]) { List prod, Integer i -> prod << [a[i.intdiv(n)], b[i%n]].flatten().sort() } )
    }

    /**
     * Return a cartesian product of items stored in a single set.
     * This likely can be made more efficient, but this codepath
     * is not too hot in practice anyway.
     *
     * @see #cartesianProduct
     */
    static Iterable cartesianSquared(Iterable arr) {
        Iterable res = []
        arr.each() {def a ->
            // Be more forgiving of parameters that are just arrays of strings, etc.
            if (!(a instanceof java.lang.Iterable)) a = [a]
            if (res.size() == 0) {
                if (arr.size() == 1) {
                    // For a single-element source Set, we still want
                    // to return a Set of Sets to be consistent
                    //res = [a]
                    res = cartesianProduct(a, [])
                } else {
                    // Proceed to multiply below with next arr elements
                    res = a
                }
            } else {
                res = cartesianProduct(res, a)
            }
        }
        return res
    }

    /**
     * For objects that are like an Array, List or Set, this routine
     * simply appends contents of "addon" to "orig".<br/>
     *
     * For Maps it recurses, so it can process the object which is value
     * in a Map for same key.<br/>
     *
     * For other types, replace orig with addon.<br/>
     *
     * Returns the result of merge (or whatever did happen there).
     */
    static def mergeMapSet(def orig, def addon, boolean debug = false) {
        // Note: debug println() below might not go anywhere
        if (isList(orig)) {
            if (isList(addon)) {
                // Concatenate
                if (debug) println "Both orig and addon are arrays, concatenate:\n  ${orig}\n+ ${addon}\n"
                return (orig + addon)
            }
            // For other types, append as a single new array item
            if (debug) println "The orig is an array, addon is not; append it:\n  ${orig}\n+ ${addon}\n"
            return (orig << addon)
        }

        if (isMap(orig)) {
            if (isMap(addon)) {
                if (debug) println "Both orig and addon are Maps, concatenate recursively:\n  ${orig}\n+ ${addon}\n"
                addon.keySet().each() {def k ->
                    if (orig.containsKey(k)) {
                        if (debug) println "+ Merging orig[${k}]=${orig[k]} with addon[${k}]=${addon[k]}"
                        orig[k] = mergeMapSet(orig[k], addon[k])
                    } else {
                        if (debug) println "+ Adding new orig[${k}] from addon[${k}]=${addon[k]}\n"
                        orig[k] = addon[k]
                    }
                }
                return orig
            }
            throw new Exception("Can not mergeMapSet() a non-Map: ${castString(addon)} into a Map: ${castString(orig)}")
        }

        // For other types, replace with new value
        if (debug) println "Both orig and addon are neither arrays nor maps, replace:\n${orig}\n${addon}\n"
        return addon
    } // mergeMapSet()

    /** Helper for {@link Dynamatrix} class */
    @NonCPS
    static String prepare_MATRIX_TAG(String matrixTag, String stageName) {
        String MATRIX_TAG = matrixTag
        if (MATRIX_TAG == null) {
            MATRIX_TAG = stageName.trim()
            if ("MATRIX_TAG=" in MATRIX_TAG) {
                MATRIX_TAG = MATRIX_TAG - ~/^MATRIX_TAG="*/ - ~/"*$/
            }
        }
        return MATRIX_TAG
    }

    /**
     * Convert a {@link String} into a {@link hudson.model.Result} with added
     * consideration for values defined by the dynamatrix ecosystem.
     * May return {@code null} for states which do not map into
     * a Jenkins standard Result value.
     * @param k A String key, with either one of Jenkins standard
     *  {@link hudson.model.Result} values, or a dynamatrix state machine
     *  and/or enhanced-debugging value:
     *  ['STARTED', 'RESTARTED', 'COMPLETED', 'AGENT_DISCONNECTED', 'AGENT_TIMEOUT'] as {@code null},
     *  ['ABORTED_SAFE'] as {@link Result#ABORTED},
     *  ['DEBUG-EXC-(FAILURE|UNKNOWN):.*', '.*Exception', 'UNKNOWN', 'Throwable'] as {@link Result#FAILURE}.
     * @return  A {@link hudson.model.Result} constant, or {@code null}.
     *
     * @see Dynamatrix#getWorstResult
     * @see Dynamatrix#setWorstResult(String)
     * @see Dynamatrix#setWorstResult(String, String)
     */
    @NonCPS
    static Result resultFromString(String k) {
        Result r = null
        try {
            // To maintain, search this code base for strings passed via:
            //   dsbcResultInterim =
            //   countStagesIncrement
            //   setWorstResult
            switch (k) {
                case ['STARTED', 'RESTARTED', 'COMPLETED', 'AGENT_DISCONNECTED', 'AGENT_TIMEOUT']: break;
                case 'ABORTED_SAFE':
                    r = Result.fromString('ABORTED')
                    break
                case ['UNKNOWN', 'Throwable']:
                    r = Result.FAILURE
                    break
                case ~/^DEBUG-EXC-(FAILURE|UNKNOWN):.*/:
                    r = Result.FAILURE
                    break
                case ~/.*Exception$/:
                    r = Result.FAILURE
                    break
                default:
                    // Note: It seems that any values not recognized by the Result
                    //  class are implicitly cast into a FAILURE!
                    r = Result.fromString(k)
                    break
            }
        } catch (Throwable ignored) {
            r = null
        }
        return r
    }

    /** Helper to treat {@code null} {@link Integer} values as zeroes for counting */
    @NonCPS
    public static Integer intNullZero(Integer i) {
        if (i == null) { return 0 } else { return i }
    }

    /**
     * Parse a version string (like "19", "19.1", "4.4.4", "gcc-4.4.4-illumos")
     * into a list of integers for semver comparison.
     * Assume missing numeric components are zeroes (e.g. "19" = 19.0.0, "19.1" = 19.1.0).
     * Suffixes like "gcc-4.4.4-illumos" are ignored (in practice they do not
     * collide with other installed compilers => treated for now as "4.4.4").
     */
    @NonCPS
    public static List<Integer> parseSemVer(String ver) {
        if (!isStringNotEmpty(ver)) return [0, 0, 0]
        // Suffixes and prefixes like "gcc-4.4.4-illumos" are ignored with a comment
        // that they do not in practice collide with other installed compilers =>
        // treated for now as "4.4.4" (or "gcc version 4.4.4").
        def matcher = ver =~ /(\d+(?:\.\d+)*)/
        if (matcher.find()) {
            String verNum = matcher.group(1)
            List<Integer> parts = verNum.split('\\.').collect { it.isInteger() ? it.toInteger() : 0 }
            while (parts.size() < 3) {
                parts.add(0)
            }
            return parts
        }
        return [0, 0, 0]
    }

    /**
     * Compare two semantic versions according to semver rules.
     * Missing numeric components are zeroes, e.g. "19" (19.0.0) < "19.1" (19.1.0).
     */
    @NonCPS
    public static int compareSemVer(String v1, String v2) {
        List<Integer> p1 = parseSemVer(v1)
        List<Integer> p2 = parseSemVer(v2)
        int maxLen = Math.max(p1.size(), p2.size())
        for (int i = 0; i < maxLen; i++) {
            int c1 = i < p1.size() ? p1[i] : 0
            int c2 = i < p2.size() ? p2[i] : 0
            if (c1 != c2) {
                return c1 <=> c2
            }
        }
        return 0
    }

    /**
     * Sort a collection of version strings according to semantic versioning.
     */
    @NonCPS
    public static List<String> sortSemVer(Collection versions, boolean ascending = true) {
        if (versions == null) return []
        List<String> list = new ArrayList<String>(versions.collect { it?.toString() }.findAll { isStringNotEmpty(it) })
        list.sort { String a, String b ->
            int cmp = compareSemVer(a, b)
            return ascending ? cmp : -cmp
        }
        return list
    }

    /**
     * Resolve MIN or MAX semantic version for a compiler or interpreter key,
     * taking into account C or CXX standard version constraints and exclusion combos.
     */
    @NonCPS
    public static String resolveExtremeVersion(
        String compilerKey,
        String mode,
        Collection candidateVersions,
        Collection standardConstraints = [],
        Collection excludeCombos = []
    ) {
        if (!isListNotEmpty(candidateVersions)) return null
        List<String> versions = candidateVersions.collect { it?.toString()?.trim() }.findAll { isStringNotEmpty(it) }
        if (versions.isEmpty()) return null

        List<String> validVersions = []
        if (isListNotEmpty(standardConstraints) && isListNotEmpty(excludeCombos)) {
            versions.each { String ver ->
                boolean isExcluded = false
                // Check if this compiler version with standard constraints hits any excludeCombos
                DynamatrixSingleBuildConfig testDsbc = new DynamatrixSingleBuildConfig(null)
                testDsbc.buildLabelSet = ["${compilerKey}=${ver}"] as Set
                testDsbc.virtualLabelSet = (standardConstraints as Set)
                if (testDsbc.matchesConstraints(excludeCombos as Set)) {
                    isExcluded = true
                }
                if (!isExcluded) {
                    validVersions << ver
                }
            }
        }

        List<String> targetList = (!validVersions.isEmpty()) ? validVersions : versions
        List<String> sorted = sortSemVer(targetList, true)
        if (sorted.isEmpty()) return null
        if ("MIN".equalsIgnoreCase(mode)) {
            return sorted[0]
        } else {
            return sorted[sorted.size() - 1]
        }
    }

    /** Take {@code blcSet[]} which is a Set of Sets (equivalent to field
     * {@link Dynamatrix#buildLabelCombosFlat} in the class), with contents like this:
     * <pre>
     * [ [ARCH_BITS=64 ARCH64=amd64, COMPILER=CLANG CLANGVER=9, OS_DISTRO=openindiana],
     *   [ARCH_BITS=32 ARCH32=armv7l, COMPILER=GCC GCCVER=4.9, OS_DISTRO=debian] ]
     * </pre>
     * ...and convert into a Map where keys are agent label expression strings.
     */
    // WARNING: NOT @NonCPS here, some code treats args as ArrayList and CPS helps it find this implementation
    static Map<String, Set> mapBuildLabelExpressions(Set<Set> blcSet) {
        /** Equivalent to buildLabelsAgents in the class */
        Map<String, Set> blaMap = [:]
        blcSet.each() {Set combo ->
            // Note that labels can be composite, e.g. "COMPILER=GCC GCCVER=1.2.3"
            // ble == build label expression
            String ble = String.join('&&', combo).replaceAll('\\s+', '&&')
            blaMap[ble] = combo
        }
        return blaMap
    }
}
