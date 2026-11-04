package org.nut.dynamatrix

import com.cloudbees.groovy.cps.NonCPS;
import org.nut.dynamatrix.DynamatrixSingleBuildConfig;

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
        'c2x':  2023,
        '23':   2023,
        'c23':  2023
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
        '2b':       2023,
        '23':       2023,
        'c++23':    2023,
        'cxx23':    2023
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
     * Compare two C/C++ standard revisions chronologically.
     */
    @NonCPS
    public static int compareCStdVer(String a, String b, String lang = 'c') {
        int rankA = getCStdRank(a, lang)
        int rankB = getCStdRank(b, lang)
        if (rankA != rankB) {
            return rankA <=> rankB
        }
        return (a ?: '') <=> (b ?: '')
    }

    /**
     * Sort a collection of C or C++ standard version strings chronologically.
     */
    @NonCPS
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
     * C or C++ language standard revisions for a given compiler label (e.g. "GCCVER=4.8.2" or "CLANGVER=10"),
     * taking into account exclusion combos.
     * Returns a list of matching standard version strings (empty list if none match).
     */
    @NonCPS
    public static List<String> resolveExtremeCStdVersions(
        String compilerLabel,
        String mode,
        Collection candidateVersions = [],
        Collection excludeCombos = [],
        String lang = 'c'
    ) {
        List<String> candidates = []
        if (isListNotEmpty(candidateVersions)) {
            candidates = candidateVersions.collect { it?.toString()?.trim() }.findAll { isStringNotEmpty(it) }
        } else {
            if (lang in ['cxx', 'cpp', 'c++']) {
                candidates = ['98', '03', '11', '14', '17', '2a', '2b']
            } else {
                candidates = ['89', '99', '11', '17', '2x']
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
            if (m in ['MIN', 'MAX', 'EXTREME', 'EXTREMES', 'MIN_MAX']) {
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
        } else {
            return []
        }
    }

    /**
     * Resolve extreme C or C++ language standard revision for a given compiler label.
     */
    @NonCPS
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
     * Resolve extreme paired C and C++ language standard revisions (for CSTDVERSION_${KEY})
     * for a given compiler label and excludeCombos.
     */
    @NonCPS
    public static List<Map<String, String>> resolveExtremeCStdPairs(
        String compilerLabel,
        String mode,
        Collection candidatePairs = [],
        Collection excludeCombos = []
    ) {
        List<Map<String, String>> candidates = []
        if (isListNotEmpty(candidatePairs)) {
            candidatePairs.each { def item ->
                if (item instanceof Map) {
                    Map<String, String> m = [:]
                    item.each { k, v -> m[k.toString()] = v?.toString() }
                    candidates << m
                }
            }
        }
        if (candidates.isEmpty()) {
            candidates = [
                ['c': '89', 'cxx': '98'],
                ['c': '99', 'cxx': '98'],
                ['c': '11', 'cxx': '11'],
                ['c': '17', 'cxx': '17'],
                ['c': '11', 'cxx': '14'],
                ['c': '2x', 'cxx': '2a'],
                ['c': '2x', 'cxx': '2b']
            ]
        }

        List<Map<String, String>> supportedPairs = []
        candidates.each { Map<String, String> pair ->
            boolean isExcluded = false
            if (isListNotEmpty(excludeCombos) && isStringNotEmpty(compilerLabel)) {
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

        String m = mode ? mode.trim() : "MAX"
        if (supportedPairs.size() <= 1) {
            if (m in ['MIN', 'MAX', 'EXTREME', 'EXTREMES', 'MIN_MAX']) {
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
        } else {
            return []
        }
    }
}
