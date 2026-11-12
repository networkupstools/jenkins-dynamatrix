package org.nut.dynamatrix

import com.cloudbees.groovy.cps.NonCPS;
import org.nut.dynamatrix.DynamatrixSingleBuildConfig;
import org.nut.dynamatrix.dynamatrixGlobalState;

import static org.nut.dynamatrix.Utils.*;

/**
 * C/C++ version resolution helpers to check or process our data.
 */
class UtilsCStd {
    /**
     * Map of known C language revisions to chronological release year ranks.
     */
    public static final Map<String, Integer> C_STD_RANKS = [
        'ansi': 1989,
        '89':   1989,
        'c89':  1989,
        '90':   1990,
        'c90':  1990,
        '99':   1999,
        'c99':  1999,
        '11':   2011,
        'c11':  2011,
        '17':   2017,
        'c17':  2017,
        '18':   2018,
        'c18':  2018,
        '2x':   2023,
        '23':   2023,
        'c2x':  2023,
        'c23':  2023,
        '2y':   2029,
        '29':   2029,   // Not finalized/published at the time of this writing
        'c2y':  2029,
        'c29':  2029
    ]

    /**
     * Map of known C++ language revisions to chronological release year ranks.
     */
    public static final Map<String, Integer> CXX_STD_RANKS = [
        'ansi':     1998,
        '98':       1998,
        'c++98':    1998,
        'cxx98':    1998,
        '03':       2003,
        'c++03':    2003,
        'cxx03':    2003,
        '11':       2011,
        'c++11':    2011,
        'cxx11':    2011,
        '14':       2014,
        'c++14':    2014,
        'cxx14':    2014,
        '17':       2017,
        'c++17':    2017,
        'cxx17':    2017,
        '2a':       2020,
        '20':       2020,
        'c++20':    2020,
        'cxx20':    2020,
        'c++2a':    2020,
        'cxx2a':    2020,
        '2b':       2023,
        '23':       2023,
        'c++23':    2023,
        'cxx23':    2023,
        'c++2b':    2023,
        'cxx2b':    2023,
        '2c':       2026,
        '26':       2026,   // Not finalized/published at the time of this writing
        'c++26':    2026,
        'cxx26':    2026,
        'c++2c':    2026,
        'cxx2c':    2026,
        '2d':       2029,
        '29':       2029,   // Not finalized/published at the time of this writing
        'c++29':    2029,
        'cxx29':    2029,
        'c++2d':    2029,
        'cxx2d':    2029
    ]

    /**
     * Default likely C and C++ version support combos for different generations
     * of GCC/CLANG compilers (per suffixes they accept to `-std=...` argument).<br/>
     *
     * This is sorted in ascending manner, so that in fallback mode we can handle
     * `MAX` keyword simply by matching the build scenario's compiler version to
     * exclude unsupported rows from the list here, and pick the last remaining
     * line. (Perhaps after sorting the candidates, but keeping this list already
     * sorted minimizes the workload there.)<br/>
     *
     * See {@link #compareCStdVer} about equality and ordinality of ways to spell
     * a C/C++ standard version.
     *
     * @see #resolveExtremeCStdPairs
     */
    static final List<Map<String, String>> candidateCombosDefault = [
        ['c': '89', 'cxx': '98'],   // all gcc, all clang (that we care about at least)
        ['c': '99', 'cxx': '98'],   // gcc 4.5+ (partially since 3.0), all clang (that we care about at least)
        ['c': '11', 'cxx': '11'],   // gcc 4.8.1+ (partial since mid-4.x), clang 3.3+
        ['c': '11', 'cxx': '14'],   // gcc 5+, clang 3.4+
        ['c': '17', 'cxx': '17'],   // gcc 8.1.0+, clang 6+
        ['c': '2x', 'cxx': '2a'],   // C23 draft + C++20 draft: gcc 9+, clang 10+
        ['c': '2x', 'cxx': '2b'],   // C23 draft + C++23 draft: gcc 11+, clang 13+
        ['c': '23', 'cxx': '23'],   // C23 FINAL + C++23 FINAL: gcc 15+, clang not yet
        ['c': '2x', 'cxx': '2c'],   // C23 draft + C++26 draft: gcc 14+, clang 17+
        ['c': '2y', 'cxx': '2a'],   // C29 draft + C++20 draft: gcc 15+, clang 19+
        ['c': '2y', 'cxx': '2b'],   // C29 draft + C++23 draft: gcc 15+, clang 19+
        ['c': '2y', 'cxx': '2c']    // C29 draft + C++26 draft: gcc 16+, clang 23+
    ]

    /**
     * Get the chronological release year rank for a C or C++ standard revision.
     */
    @NonCPS
    public static int getCStdRank(String version, String lang = 'c') {
        if (!isStringNotEmpty(version)) return 0
        String v = version.trim().toLowerCase()
        if (lang in ['cxx', 'cpp', 'c++']) {
            if (CXX_STD_RANKS.containsKey(v)) return CXX_STD_RANKS[v]
        } else {
            if (C_STD_RANKS.containsKey(v)) return C_STD_RANKS[v]
        }
        if (CXX_STD_RANKS.containsKey(v)) return CXX_STD_RANKS[v]
        if (C_STD_RANKS.containsKey(v)) return C_STD_RANKS[v]
        if (v =~ /^\d+$/) {
            int num = v.toInteger()
            if (num >= 80 && num <= 99) return 1900 + num
            if (num < 80) return 2000 + num
            return num
        }
        return 0
    }

    /**
     * Compare two C/C++ standard revisions chronologically
     * and by completeness (final spec is "greater than" draft).
     * @returns <ul><li>-1 : (a > b)</li>
                    <li> 0 : (a == b)</li>
                    <li> 1 : (a < b)</li>
                </ul>
     */
    @NonCPS
    public static int compareCStdVer(String a, String b, String lang = 'c') {
        // ranks are chronological years corresponding to the standard name/tag:
        int rankA = getCStdRank(a, lang)
        int rankB = getCStdRank(b, lang)
        if (rankA != rankB) {
            return rankA <=> rankB
        }
        // else: rankA == rankB

        // Finalized support for '23'/'c23' should be "greater" than partial '2x'/'c2x', etc.
        // Given equal ranks, the clue is the key ending in a digit or not
        boolean aNumeric = (a ==~ /^.*\d$/)
        boolean bNumeric = (b ==~ /^.*\d$/)
        if ((aNumeric && bNumeric) || (!aNumeric && !bNumeric)) {
            // either both are numeric or neither is =>
            // practically identical, sort by key string
            // (may be equal or not)
            return (a ?: '') <=> (b ?: '')
        }

        // Exactly one key is numeric, prefer it as "greater" for comparison
        return (aNumeric ? 1 : -1)
    }

    /**
     * Sort a collection of C or C++ standard version strings chronologically.
     *
     * NOTE: Cannot be @NonCPS because it uses a closure comparator in list.sort()
     * which can be serialized by CPS when this method is called from CPS context,
     * causing "expected to call ArrayList.sort but wound up catching CpsClosure2.call"
     * mismatch errors.
     */
    public static List<String> sortCStdVer(Collection versions, String lang = 'c', boolean ascending = true) {
        if (versions == null) return []
        List<String> list = new ArrayList<String>(versions.collect { it?.toString() }.findAll { isStringNotEmpty(it) })
        list.sort { String a, String b ->
            int cmp = compareCStdVer(a, b, lang)
            return ascending ? cmp : -cmp
        }
        return list
    }

    /**
     * Resolve MIN, MAX, NOT_MIN, !MIN, NOT_MAX, !MAX, EXTREME, EXTREMES, MIN_MAX,
     * !EXTREME, !EXTREMES, !MIN_MAX, NOT_EXTREME, NOT_EXTREMES, or NOT_MIN_MAX
     * C or C++ language standard revisions for a given compiler label (e.g.
     * "GCCVER=4.8.2" or "CLANGVER=10"), taking into account exclusion combos.<br/>
     *
     * Additionally supports MAX11, MAX17, MAX23 tokens that limit the considered
     * candidates to respectively C11/C++11, C17/C++17, and C23/C++23 standards.
     * For MAX23, only compilers with finished C23/C++23 support (e.g. GCC 15+)
     * are considered, not those with draft support like 2x/2b.
     *
     * @returns A list of matching standard version strings (empty list if none match).
     */
    public static List<String> resolveExtremeCStdVersions(
        String compilerLabel,
        String mode,
        Collection candidateVersions = [],
        Collection excludeCombos = [],
        String lang = 'c'
    ) {
        List<String> candidates = []
        boolean hadExtremeInCandidates = false
        if (isListNotEmpty(candidateVersions)) {
            hadExtremeInCandidates = candidateVersions.any {
                it?.toString()?.trim() in ['MAX', 'MIN', 'NOT_MAX', '!MAX', 'NOT_MIN', '!MIN', 'EXTREME', 'EXTREMES', 'MIN_MAX', '!EXTREME', '!EXTREMES', '!MIN_MAX', 'NOT_EXTREME', 'NOT_EXTREMES', 'NOT_MIN_MAX', 'MAX11', 'MAX17', 'MAX23']
            }
            if (!hadExtremeInCandidates) {
                candidates = candidateVersions.collect { it?.toString()?.trim() }.findAll { isStringNotEmpty(it) }
            }
        }
        if (candidates.isEmpty() || hadExtremeInCandidates) {
            if (lang in ['cxx', 'cpp', 'c++']) {
                candidates = ['98', '03', '11', '14', '17', '2a', '2b', '2c', '2d']
            } else {
                candidates = ['89', '99', '11', '17', '2x', '2y']
            }
        }
        if (candidates.isEmpty()) return []

        List<String> supportedVersions = []
        candidates.each { String ver ->
            boolean isExcluded = false
            if (isListNotEmpty(excludeCombos) && isStringNotEmpty(compilerLabel)) {
                DynamatrixSingleBuildConfig testDsbc = new DynamatrixSingleBuildConfig(null)
                testDsbc.buildLabelSet = [compilerLabel] as Set
                testDsbc.virtualLabelSet = ["CSTDVERSION=${ver}", "CSTDVERSION_${lang}=${ver}"] as Set
                if (testDsbc.matchesConstraints(excludeCombos as Set)) {
                    isExcluded = true
                }
            }
            if (!isExcluded) {
                supportedVersions << ver
            }
        }

        List<String> sorted = sortCStdVer(supportedVersions, lang, true)
        if (sorted.isEmpty()) return []

        String m = mode ? mode.trim() : "MAX"
        if (sorted.size() <= 1) {
            if (m in ['MIN', 'MAX', 'EXTREME', 'EXTREMES', 'MIN_MAX', 'MAX11', 'MAX17', 'MAX23']) {
                return sorted
            } else {
                return []
            }
        }

        if (m == 'MIN') {
            return [sorted[0]]
        } else if (m == 'MAX') {
            return [sorted[sorted.size() - 1]]
        } else if (m in ['NOT_MIN', '!MIN']) {
            return sorted.subList(1, sorted.size())
        } else if (m in ['NOT_MAX', '!MAX']) {
            return sorted.subList(0, sorted.size() - 1)
        } else if (m in ['EXTREME', 'EXTREMES', 'MIN_MAX']) {
            return [sorted[0], sorted[sorted.size() - 1]].unique()
        } else if (m in ['!EXTREME', '!EXTREMES', '!MIN_MAX', 'NOT_EXTREME', 'NOT_EXTREMES', 'NOT_MIN_MAX']) {
            if (sorted.size() >= 3) {
                return sorted.subList(1, sorted.size() - 1)
            } else {
                return []
            }
        } else if (m == 'MAX11') {
            // MAX11: Find the highest standard up to C11/C++11
            List<String> filtered = sorted.findAll { String ver ->
                getCStdRank(ver, lang) <= getCStdRank('11', lang)
            }
            return filtered.isEmpty() ? [] : [filtered[filtered.size() - 1]]
        } else if (m == 'MAX17') {
            // MAX17: Find the highest standard up to C17/C++17
            List<String> filtered = sorted.findAll { String ver ->
                getCStdRank(ver, lang) <= getCStdRank('17', lang)
            }
            return filtered.isEmpty() ? [] : [filtered[filtered.size() - 1]]
        } else if (m == 'MAX23') {
            // MAX23: Find the highest standard up to C23/C++23,
            // but only consider finalized standards.
            // For C, exact 23 is the target; for C++, 23 is the target.
            // Drafts like 2x/2b/2a/2c/2d/2y are excluded entirely.
            List<String> filtered = sorted.findAll { String ver ->
                int rank = getCStdRank(ver, lang)
                int targetRank = getCStdRank('23', lang)
                // Only include standards with rank <= 23 rank
                if (rank > targetRank) return false
                // Exclude all drafts - a draft ends with a letter (e.g., 2x, 2b, 2a, 2c, 2d, 2y)
                // Only finalized standards (ending in a digit) are included
                return ver ==~ /.*\d$/
            }
            return filtered.isEmpty() ? [] : [filtered[filtered.size() - 1]]
        } else {
            return []
        }
    }

    /**
     * Resolve extreme C or C++ language standard revision for a given compiler label.
     */
    public static String resolveExtremeCStdVersion(
        String compilerLabel,
        String mode,
        Collection candidateVersions = [],
        Collection excludeCombos = [],
        String lang = 'c'
    ) {
        List<String> vers = resolveExtremeCStdVersions(compilerLabel, mode, candidateVersions, excludeCombos, lang)
        if (vers.isEmpty()) return null
        return vers[0]
    }

    /**
     * Resolve extreme paired C and C++ language standard revisions
     * (for CSTDVERSION_${KEY}) for a given compiler label and excludeCombos.
     *
     * Additionally supports MAX11, MAX17, MAX23 tokens that limit the considered
     * candidates to respectively C11/C++11, C17/C++17, and C23/C++23 standards.
     * For MAX23, only compilers with finished C23/C++23 support (e.g. GCC 15+)
     * are considered, not those with draft support like 2x/2b.
     */
    public static List<Map<String, String>> resolveExtremeCStdPairs(
        String compilerLabel,
        String mode,
        Collection candidatePairs = [],
        Collection excludeCombos = []
    ) {
        List<Map<String, String>> candidates = []
        boolean hadExtremeInCandidates = false
        if (isListNotEmpty(candidatePairs)) {
            candidatePairs.each { def item ->
                if (item instanceof Map) {
                    Map<String, String> m = [:]
                    item.each { k, v -> m[k.toString()] = v?.toString() }
                    boolean hasExtreme = m.values().any {
                        it in ['MAX', 'MIN', 'NOT_MAX', '!MAX', 'NOT_MIN', '!MIN', 'EXTREME', 'EXTREMES', 'MIN_MAX', '!EXTREME', '!EXTREMES', '!MIN_MAX', 'NOT_EXTREME', 'NOT_EXTREMES', 'NOT_MIN_MAX', 'MAX11', 'MAX17', 'MAX23']
                    }
                    if (hasExtreme) {
                        hadExtremeInCandidates = true
                    } else {
                        candidates << m
                    }
                }
            }
        }

        if (candidates.isEmpty()) {
            candidates = candidateCombosDefault
        } else if (hadExtremeInCandidates) {
            // When explicit candidates contain extreme sentinels (MAX/MIN/etc.),
            // merge them with the hardcoded defaults so that extreme modes can
            // select from the full range of standards. The concrete pairs represent
            // explicit user intent and are preserved, while the defaults provide
            // the range for MAX/MIN/EXTREME selection.

            // Merge: add defaults that are not already in candidates
            candidateCombosDefault.each { Map<String, String> defPair ->
                boolean exists = candidates.any { Map<String, String> c -> c == defPair }
                if (!exists) {
                    candidates << defPair
                }
            }
        }

        List<Map<String, String>> supportedPairs = []
        candidates.each { Map<String, String> pair ->
            boolean isExcluded = false
            if (isListNotEmpty(excludeCombos) && isStringNotEmpty(compilerLabel)) {
                // Use DSBC to exclude standard-candidates per compiler version
                DynamatrixSingleBuildConfig testDsbc = new DynamatrixSingleBuildConfig(null)
                testDsbc.buildLabelSet = [compilerLabel] as Set
                Set vSet = []
                pair.each { langKey, stdVer ->
                    vSet << "CSTDVERSION_${langKey}=${stdVer}"
                    vSet << "CSTDVERSION=${stdVer}"
                }
                testDsbc.virtualLabelSet = vSet
                if (testDsbc.matchesConstraints(excludeCombos as Set)) {
                    isExcluded = true
                }
            }
            if (!isExcluded) {
                supportedPairs << pair
            }
        }

        if (supportedPairs.isEmpty()) return []

        supportedPairs.sort { Map<String, String> a, Map<String, String> b ->
            int cmpC = compareCStdVer(a['c'], b['c'], 'c')
            if (cmpC != 0) return cmpC
            return compareCStdVer(a['cxx'], b['cxx'], 'cxx')
        }

        if (!hadExtremeInCandidates) {
            if (dynamatrixGlobalState.enableDebugTraceResolver) {
                println("resolveExtremeCStdPairs(): hadExtremeInCandidates==false, would likely return empty hit sets now")
            }
        }

        String m = mode ? mode.trim() : "MAX"
        if (supportedPairs.size() <= 1) {
            if (m in ['MIN', 'MAX', 'EXTREME', 'EXTREMES', 'MIN_MAX', 'MAX11', 'MAX17', 'MAX23']) {
                return supportedPairs
            } else {
                return []
            }
        }

        if (m == 'MIN') {
            return [supportedPairs[0]]
        } else if (m == 'MAX') {
            return [supportedPairs[supportedPairs.size() - 1]]
        } else if (m in ['NOT_MIN', '!MIN']) {
            return supportedPairs.subList(1, supportedPairs.size())
        } else if (m in ['NOT_MAX', '!MAX']) {
            return supportedPairs.subList(0, supportedPairs.size() - 1)
        } else if (m in ['EXTREME', 'EXTREMES', 'MIN_MAX']) {
            return [supportedPairs[0], supportedPairs[supportedPairs.size() - 1]].unique()
        } else if (m in ['!EXTREME', '!EXTREMES', '!MIN_MAX', 'NOT_EXTREME', 'NOT_EXTREMES', 'NOT_MIN_MAX']) {
            if (supportedPairs.size() >= 3) {
                return supportedPairs.subList(1, supportedPairs.size() - 1)
            } else {
                return []
            }
        } else if (m == 'MAX11') {
            // MAX11: Find the highest pair where both C and C++ are <= C11/C++11
            List<Map<String, String>> filtered = supportedPairs.findAll { Map<String, String> pair ->
                int cRank = getCStdRank(pair['c'], 'c')
                int cxxRank = getCStdRank(pair['cxx'], 'cxx')
                int c11Rank = getCStdRank('11', 'c')
                int cxx11Rank = getCStdRank('11', 'cxx')
                return cRank <= c11Rank && cxxRank <= cxx11Rank
            }
            return filtered.isEmpty() ? [] : [filtered[filtered.size() - 1]]
        } else if (m == 'MAX17') {
            // MAX17: Find the highest pair where both C and C++ are <= C17/C++17
            List<Map<String, String>> filtered = supportedPairs.findAll { Map<String, String> pair ->
                int cRank = getCStdRank(pair['c'], 'c')
                int cxxRank = getCStdRank(pair['cxx'], 'cxx')
                int c17Rank = getCStdRank('17', 'c')
                int cxx17Rank = getCStdRank('17', 'cxx')
                return cRank <= c17Rank && cxxRank <= cxx17Rank
            }
            return filtered.isEmpty() ? [] : [filtered[filtered.size() - 1]]
        } else if (m == 'MAX23') {
            // MAX23: Find the highest pair where both C and C++ are <= C23/C++23,
            // but only consider finalized standards (not drafts like 2x/2b).
            List<Map<String, String>> filtered = supportedPairs.findAll { Map<String, String> pair ->
                int cRank = getCStdRank(pair['c'], 'c')
                int cxxRank = getCStdRank(pair['cxx'], 'cxx')
                int c23Rank = getCStdRank('23', 'c')
                int cxx23Rank = getCStdRank('23', 'cxx')

                // Check ranks are within bounds
                if (cRank > c23Rank || cxxRank > cxx23Rank) return false

                // For standards at the same rank as 23, only include finalized versions
                if (cRank == c23Rank) {
                    if (!(pair['c'] ==~ /.*\d$/)) return false
                }
                if (cxxRank == cxx23Rank) {
                    if (!(pair['cxx'] ==~ /.*\d$/)) return false
                }
                return true
            }
            return filtered.isEmpty() ? [] : [filtered[filtered.size() - 1]]
        } else {
            return []
        }
    }
}
