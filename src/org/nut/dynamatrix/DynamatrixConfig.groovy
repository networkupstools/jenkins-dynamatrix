package org.nut.dynamatrix;

import org.nut.dynamatrix.Utils;
import org.nut.dynamatrix.dynamatrixGlobalState;

/* This class intends to represent one build request configuration
 * An instance of it can be passed as the set of arguments for the
 * customized run Dynamatrix routines, while some defaults can be
 * applied so needed fields are all "defined" when we look at them.
 */
class DynamatrixConfig implements Cloneable {
    private def script
    public def stageNameFunc = null
    public boolean enableDebugTrace = dynamatrixGlobalState.enableDebugTrace
    public boolean enableDebugErrors = dynamatrixGlobalState.enableDebugErrors

    // Define fields to satisfy the build example below
    //    commonLabelExpr: 'nut-builder',
    public String commonLabelExpr = null

    // TODO: Abstract the compiler/tool related tunables and perhaps methods
    // into a separate class and implement variants as its subclasses.

    //    compilerType: 'C',
    public CompilerTypes compilerType = null

    // This represents the axis (originating from labels declared by build
    // nodes) which defines the different compiler families. It can help
    // to e.g. set specific flags not supported by other compilers:
    //    compilerLabel: 'COMPILER',
    public String compilerLabel = null

    // envvars that would be exported to point to a particular compiler
    // implementation and version for one build scenario:
    //    compilerTools: ['CC', 'CXX', 'CPP'],
    public Set<String> compilerTools = []

    // Each of these labels can be String, GString or Pattern object
    // to match labels (named as key=value pairs) of build agents:
    //    dynamatrixAxesLabels: ['OS', '${COMPILER}VER', 'ARCH'],
    public Set dynamatrixAxesLabels = []

    // TODO: Need a way to specify optional labels, so that nodes which
    // do not declare any value (e.g. do not state which ARCH(es) they
    // support) can still be used for some unique build combos ("...but
    // that is our only system that runs SUPERCCVER=123").

    // Let the caller specify additional dimensions to the matrix which
    // are not based on values of labels declared by current workers, as
    // a map of labels and a set of their values. For example, a matrix
    // for C/C++ builds can declare several standards to check:
    //    dynamatrixAxesVirtualLabelsMap: [
    //      'CSTDVERSION': ['89', '99', '03', '11', '14', '17', '2x'],
    //      'CSTDVARIANT': ['c', 'gnu'],
    //    ]
    // Like other (label) values, these would be ultimately mixed as a
    // cartesian product with values from combos provided into the closure
    // doing the build, subject to "excludeCombos" and "allowedFailure"
    // detailed below, but they should not end up in label expressions
    // to match the build agents by Jenkins.
    // Parsing of dynamatrixAxesVirtualLabelsMap also supports a special
    // syntax to process sub-maps of linked values that should be together:
    // for this mode, the key string in dynamatrixAxesVirtualLabelsMap
    // should contain a verbatim sub-string '${KEY}' which would then be
    // replaced by key of the sub-map value or by 'DEFAULT' if the item
    // is not a map; note that like other labels, these would be exported
    // to shell so compatible names are recommended (e.g. 'cxx' not 'c++'):
    //    dynamatrixAxesVirtualLabelsMap: [
    //      'CSTDVERSION_${KEY}': [ ['c': '89', 'cxx': '98'], ['c': '99', 'cxx': '98'], ['c': '17', 'cxx: '17'] ],
    //      'CSTDVARIANT': ['c', 'gnu'],
    //    ]
    // WARNING: main-map keys that do not contain a '${KEY}' (or have
    // value types that are not a sub-map) would be assigned via toString()
    // and can lead to bogus results like `CSTDVERSION="[c:89, cxx:98]"`.
    public Map dynamatrixAxesVirtualLabelsMap = [:]

    // Set of (Sets of envvars to export) - the resulting build matrix
    // picks one of these at a time for otherwise same conditions made
    // from other influences. This example defines two build variants:
    //    dynamatrixAxesCommonEnv: [['LANG=C', 'TZ=UTC'], ['LANG=ru_RU', 'SHELL=ksh']],
    public Set<Set> dynamatrixAxesCommonEnv = []
    // Same goal as above, but builds would get a Cartesian multiplication
    // of the variants listed in the sub-sets, e.g. this example:
    //    dynamatrixAxesCommonEnvCartesian: [['LANG=C', 'LANG=ru_RU'], ['TZ=UTC', 'TZ=CET']]
    // ...should produce four build variants that would feed into the
    // dynamatrixAxesCommonEnv use-case, namely:
    //    [['LANG=C','TZ=UTC'], ['LANG=ru_RU','TZ=UTC'], ['LANG=C','TZ=CET'], ['LANG=ru_RU','TZ=CET']]
    // NOTE: third-layer objects (e.g. meaningful sets not strings) should
    // get plugged into the result "as is" and can help group related options
    // without "internal conflict", e.g.:
    //    dynamatrixAxesCommonEnvCartesian: [[ ['LANG=C','LC_ALL=C'], 'LANG=ru_RU'], ['TZ=UTC', 'TZ=CET']]
    // should yield such four build variants:
    //    [['LANG=C','LC_ALL=C','TZ=UTC'], ['LANG=ru_RU','TZ=UTC'], ['LANG=C','LC_ALL=C','TZ=CET'], ['LANG=ru_RU','TZ=CET']]
    public Set<Set> dynamatrixAxesCommonEnvCartesian = []

    // Set of (Sets of args to build tool into its command-line options).
    // Similar approach as above:
    // This defines four builds, two of these would set two build options
    // each (for trying two C/C++ standards), and the other two would set
    // different build bitness (with other default configuration settings):
    //    dynamatrixAxesCommonOpts: [
    //        ["CFLAGS=-stdc=gnu99", "CXXFLAGS=-stdcxx=gnu++99"],
    //        ["CFLAGS=-stdc=c89", "CXXFLAGS=-stdcxx=c++89"],
    //        ['-m32'], ['-m64'] ]
    public Set<Set> dynamatrixAxesCommonOpts = []
    // ...and this (also with third-layer support) example:
    //    dynamatrixAxesCommonOptsCartesian: [
    //        [ ["CFLAGS=-stdc=gnu99", "CXXFLAGS=-stdcxx=g++99"],
    //          ["CFLAGS=-stdc=c89", "CXXFLAGS=-stdcxx=c++89"]     ],
    //        ['-m32', '-m64'] ]
    // has two second-layer sets: one varies (grouped sets of) C/C++
    // standards, another varies bitnesses. The result is also to try
    // four build scenarios, but these four would run each combination
    // of language revision and bitness, compactly worded like this:
    //    [ [gnu99,m32], [c89,m32], [gnu99,m64], [c89,m64] ]
    // WARNING: This is an illustrative example, -mXX should belong inside
    // the CFLAGS, CXXFLAGS and LDFLAGS values, so the closure doing some
    // build in an ultimate shell command should take care of that.
    public Set<Set> dynamatrixAxesCommonOptsCartesian = []

    // On top of combinations discovered from existing nodes, add the
    // following combinations as required for a build. While these are
    // not labels (values or even names) proclaimed by the running agents,
    // in some Jenkins agent plugins the pressure to have requested type
    // of workers can get them tailored to order and deployed:
    //    dynamatrixRequiredLabelCombos: [["OS=bsd", "CLANG=12"]],
    public Set dynamatrixRequiredLabelCombos = []

    // During a build, configure variants which match these optional
    // conditions as something that may fail and not be fatal to the
    // overall job - like experimental builds on platforms we know we
    // do not yet support well, but already want to keep watch on:
    //    allowedFailure: [[~/OS=.+/, ~/GCCVER=4\..+/], ['ARCH=armv7l'], [~/std.+=.+89/]],
    public Set allowedFailure = []

    // Flag to conserve resources of CI farm - only run the builds in
    // the allowedFailure list then this flag is "true" (on demand),
    // but otherwise (false) treat them as excludeCombos and do not
    // run them at all - that's by default.
    public Boolean runAllowedFailure = false

    // After defining a build matrix from combinations which did match
    // various requirements, drop combinations we know would be doomed,
    // like trying the latest modern revision of C++ on the 20 year old
    // version of the compiler.
    //    excludeCombos: [[~/OS=openindiana/, ~/CLANGVER=9/, ~/ARCH=x86/], [~/GCCVER=4\.[0-7]\..+/, ~/std.+=+(!?89|98|99|03)/], [~/GCCVER=4\.([8-9]|1[0-9]+)\..+/, ~/std.+=+(!?89|98|99|03|11)/]]
    public Set excludeCombos = []

    // Beside values that are permutated in the matrix combos, we can
    // also use other labels declared by build agents to filter further.
    // This differs from dynamatrixRequiredLabelCombos above which add
    // combinations on top of filtered permutations and might cause some
    // build agents to be deployed, etc. to fulfill that requirement.
    // Here we *filter away* combos that would land to nodes that *have*
    // labels which match any of excludedNodelabels, or that *do not have*
    // labels which match all of requiredNodelabels.
    public Set requiredNodelabels = []
    public Set excludedNodelabels = []

    @NonCPS
    @Override
    public String toString() {
        return  "DynamatrixConfig: {" +
                "\n    commonLabelExpr: '${commonLabelExpr}'" +
                ",\n    compilerType: '${compilerType}'" +
                ",\n    compilerLabel: '${compilerLabel}'" +
                ",\n    compilerTools: '${compilerTools}'" +
                ",\n    dynamatrixAxesLabels: '${dynamatrixAxesLabels}'" +
                ",\n    dynamatrixAxesVirtualLabelsMap: '${dynamatrixAxesVirtualLabelsMap}'" +
                ",\n    dynamatrixAxesCommonEnv: '${dynamatrixAxesCommonEnv}'" +
                ",\n    dynamatrixAxesCommonEnvCartesian: '${dynamatrixAxesCommonEnvCartesian}'" +
                ",\n    dynamatrixAxesCommonOpts: '${dynamatrixAxesCommonOpts}'" +
                ",\n    dynamatrixAxesCommonOptsCartesian: '${dynamatrixAxesCommonOptsCartesian}'" +
                ",\n    dynamatrixRequiredLabelCombos: '${dynamatrixRequiredLabelCombos}'" +
                ",\n    allowedFailure: '${allowedFailure}'" +
                ",\n    runAllowedFailure: '${runAllowedFailure}'" +
                ",\n    excludeCombos: '${excludeCombos}'" +
                ",\n    requiredNodelabels: '${requiredNodelabels}'" +
                ",\n    excludedNodelabels: '${excludedNodelabels}'" +
                "\n}" ;
    }

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
    sh """ make -j4 """
    sh """ make check """
}

 */

    public DynamatrixConfig(Object script) {
        this.script = script
    }

    @NonCPS
    public boolean shouldDebugTrace() {
        return ( this.enableDebugTrace && this.script != null)
    }

    @NonCPS
    public boolean shouldDebugErrors() {
        return ( (this.enableDebugErrors || this.enableDebugTrace) && this.script != null)
    }

    public DynamatrixConfig(Object script, String defaultCfg) {
        this.script = script
        this.initDefault(defaultCfg)
    }

    public boolean canEqual(java.lang.Object other) {
        return other instanceof DynamatrixConfig
    }

    @Override
    public DynamatrixConfig clone() throws CloneNotSupportedException {
        return (DynamatrixConfig) super.clone();
    }

    public def initDefault(String defaultCfg) {
        def debugTrace = this.shouldDebugTrace()

        if (debugTrace) this.script.println("[DEBUG] DynamatrixConfig(String): called with defaultCfg = ${Utils.castString(defaultCfg)}")

        switch (defaultCfg) {
            case null:
            case '':
                break;

            case ['C', 'C-all', 'CXX', 'CXX-all', 'C+CXX', 'C+CXX-all']:
                this.compilerType = 'C'
                this.compilerLabel = 'COMPILER'
                this.compilerTools = ['CC', 'CXX', 'CPP']
                this.dynamatrixAxesLabels = ['OS_DISTRO', '${COMPILER}VER', 'ARCH${ARCH_BITS}']

                // Note: the clause(s) doing the build should differentiate
                // some nuances here to make some options equivalent by year:
                // * there are C99 and C++98 (and no C++89/C++90 nor C++99 at all)
                // * C++98 and C++03 are the same (both GCC and CLANG); there is no C03 at all
                // * ansi means C89 and C++98, possibly with other constraints to follow
                //   standard dialect (e.g. __STRICT_ANSI__ like '-std=c*' options do)
                // * plain C11 is equivalent to C17 (both are C11 with error fixes)
                // * C++11, C++14 and C++17 do differ; note there is no C14 at all
                // * plain C2x is experimental in GCC (absent in CLANG by some accounts like v13 CLI opts, since 9.x by others)
                // * C++20 (2a) vs C++23 (2b) also are highly experimental, present in both
                this.dynamatrixAxesVirtualLabelsMap = [
                    'CSTDVARIANT': ['c', 'gnu']
                    ]

                // We support different ways to pre-set the C/C++ language
                // revisions. As commented elsewhere around, the numbers
                // and code-names of those do not match well, making it an
                // exercise for either the user (if they do want a single
                // version string to mean both languages, and not just a
                // single-language build), or for us - like done with the
                // sub-map below. Also, quite a few of the named standard
                // revisions are in fact identical or close to that; others
                // are still highly experimental; so while the '*-all'
                // suffixed options below allow to use all currently known
                // values, default lists aim to conserve CI farm resources
                // and only select meaningful build combinations (and also
                // having fewer candidate choices to filter later).
                // All variants below should end up populating envvars and
                // dynamic labels 'CSTDVERSION_c' and/or 'CSTDVERSION_cxx':
                switch (defaultCfg) {
                    case 'C-all':
                        // Add just the "extra" choices for C standard
                        // revisions and fall through to add common ones:
                        this.dynamatrixAxesVirtualLabelsMap['CSTDVERSION_c'] = ['17', '2x']
                        // __FALL_THROUGH__

                    case 'C':
                        if (!this.dynamatrixAxesVirtualLabelsMap.containsKey('CSTDVERSION_c')) {
                            this.dynamatrixAxesVirtualLabelsMap['CSTDVERSION_c'] = []
                        }
                        this.dynamatrixAxesVirtualLabelsMap['CSTDVERSION_c'] += ['89', '99', '11']
                        break

                    case 'CXX-all':
                        // Add just the "extra" choices for C++ standard
                        // revisions and fall through to add common ones:
                        this.dynamatrixAxesVirtualLabelsMap['CSTDVERSION_cxx'] = ['03', '2a', '2b']
                        // __FALL_THROUGH__

                    case 'CXX':
                        if (!this.dynamatrixAxesVirtualLabelsMap.containsKey('CSTDVERSION_cxx')) {
                            this.dynamatrixAxesVirtualLabelsMap['CSTDVERSION_cxx'] = []
                        }
                        this.dynamatrixAxesVirtualLabelsMap['CSTDVERSION_cxx'] += ['98', '11', '14', '17']
                        break

                    case 'C+CXX-all':
                        // This list with sub-map groups "contemporary"
                        // versions of the two C/C++ standards together,
                        // and omits versions that are effectively the
                        // same (at least in gcc and clang implementations).
                        // Note sub-map key name "cxx" not "c++" so we
                        // can safely export resulting envvar names like
                        // CSTDVERSION_cxx to shells of actual builds:

                        // Add just the "extra" choices for C/C++ standard
                        // revisions and fall through to add common ones;
                        // note the literal magic sub-string '${KEY}' here:
                        this.dynamatrixAxesVirtualLabelsMap['CSTDVERSION_${KEY}'] = [
                            ['c': '11', 'cxx': '14'],
                            ['c': '2x', 'cxx': '2a'],
                            ['c': '2x', 'cxx': '2b']
                        ]
                        // __FALL_THROUGH__

                    case 'C+CXX':
                        if (!this.dynamatrixAxesVirtualLabelsMap.containsKey('CSTDVERSION_${KEY}')) {
                            this.dynamatrixAxesVirtualLabelsMap['CSTDVERSION_${KEY}'] = []
                        }

                        this.dynamatrixAxesVirtualLabelsMap['CSTDVERSION_${KEY}'] += [
                            ['c': '89', 'cxx': '98'], // no C++ before 98; no "ansi" handling here so far
                            ['c': '99', 'cxx': '98'],
                            // no "c03", skip "c++03" same as "c++98"
                            ['c': '11', 'cxx': '11'],
                            ['c': '17', 'cxx': '17'] // note c17 is same as c11, but c++17 differs from c++11
                        ]
                        break

                    default:
                        // Should not get here; let caller figure out
                        // how to ignore some of the other choices, e.g.
                        // drop 'c' vs 'gnu' dialect
                        this.dynamatrixAxesVirtualLabelsMap['CSTDVERSION_${KEY}'] += [ 'c': 'ansi', 'cxx': 'ansi' ]
                        break
                } // switch choice of dialects

                // Default filter of C/C++ revisions having *decent* support
                // in/since certain versions of compiler toolkits.
                // https://en.wikipedia.org/wiki/C17_(C_standard_revision) et al
                // For GCC support, see:
                //      https://gcc.gnu.org/onlinedocs/gcc/Standards.html
                //      https://gcc.gnu.org/projects/cxx-status.html
                //          (C++98 C++11 C++14 C++17 C++20 C++23)
                //          C++98/C++03 : all versions
                //          C++11 : since mid-4.x, mostly 4.8+, finished by 4.8.1; good in 4.9+
                //          C++14 : complete since v5
                //          C++17 : almost complete by v7, one fix in v8
                //          C++20 (2a) : experimental adding a lot in v8/v9/v10/v11
                //          C++23 (2b) : experimental since v11
                //      https://gcc.gnu.org/onlinedocs/gcc/C-Dialect-Options.html
                //          C89=C90 : "forever"?
                //          C99 : "substantially supported" since v4.5, largely since 3.0+ (https://gcc.gnu.org/c99status.html)
                //          C11 : since 4.6(partially?)
                //          C17 : 8.1.0+
                //          C2x : v9+
                // For CLANG support, see:
                //      https://clang.llvm.org/docs/CommandGuide/clang.html
                //      https://clang.llvm.org/cxx_status.html - *complete* C++ version support begins with:
                //          C++98/C++03 : all versions
                //          C++11 : v3.3+
                //          C++14 : v3.4+
                //          C++17 : v5+
                //          C++20 (2a) : brewing; keyword became official since v10+
                //          C++23 (2b) : brewing since v13+
                //      https://releases.llvm.org/6.0.0/tools/clang/docs/ReleaseNotes.html and similar
                //          C89 : ???
                //          C99 : ???
                //          C11 : since 3.1
                //          C17 : v6+
                //          C2x : v9+

                // Consider following ${COMPILER}VER numbers not sufficiently
                // compatible with language versions from the table above, at
                // least for the practical purpose of not scheduling builds.
                // Combinations below that DO match keys=values of generated
                // build candidate will remove that candidate from queue list.

                // Recurrent ([^0-9.]|$|\.[0-9]+) means to have end of version
                // or optional next numbered component, to cater for recent
                // releases' single- or at most double-numbered releases except
                // some special cases.

                this.excludeCombos = [

                    [~/GCCVER=[012]([^0-9.]|$|\.[0-9]+)/, ~/CSTDVERSION[^=]*=(?!89|90)/],
                    [~/GCCVER=3([^0-9.]|$|\.[0-9]+)/, ~/CSTDVERSION[^=]*=(?!89|90|98|99)/],
                    [~/GCCVER=4([^0-9.]|$)/, ~/CSTDVERSION[^=]*=(?!89|90|98|99)/],
                    [~/GCCVER=4\.[0-4]([^0-9.]|$|\.[0-9]+)/, ~/CSTDVERSION[^=]*=(?!89|90|98|99)/],
                    [~/GCCVER=4\.([5-7]([^0-9.]|$|\.[0-9]+)|8(\.0|[^0-9.]|$))/, ~/CSTDVERSION[^=]*=(?!89|90|98|99|03)/],
                    [~/GCCVER=4\.(8\.[1-9][0-9]*|(9|1[0-9]+)([^0-9.]|$|\.[0-9]+))/, ~/CSTDVERSION[^=]*=(?!89|98|99|03|11)/],
                    [~/GCCVER=([5-7]|8\.0)([^0-9.]|$|\.[0-9]+)/, ~/CSTDVERSION[^=]*=(?!89|98|99|03|11|14)/],
                    [~/GCCVER=8([^0-9.]|$)/, ~/CSTDVERSION[^=]*=(?!89|90|98|99|03|11|14)/],
                    [~/GCCVER=(8\.[1-9][0-9]*)([^0-9.]|$|\.[0-9]+)/, ~/CSTDVERSION[^=]*=(?!89|98|99|03|11|14|17)/],
                    [~/GCCVER=(9|[1-9][0-9]+)([^0-9.]|$|\.[0-9]+)/, ~/CSTDVERSION[^=]*=(?!89|98|99|03|11|14|17|2x)/],

                    [~/CLANGVER=[012]\..+/, ~/CSTDVERSION[^=]*=(?!89|90|98|99)/],
                    [~/CLANGVER=3\.[012]([^0-9.]|$|\.[0-9]+)/, ~/CSTDVERSION[^=]*=(?!89|90|98|99)/],
                    [~/CLANGVER=3\.3([^0-9.]|$|\.[0-9]+)/, ~/CSTDVERSION[^=]*=(?!89|90|98|99|11)/],
                    [~/CLANGVER=3\.([4-9]|[1-9][0-9]+)([^0-9]|$|\.[0-9]+)/, ~/CSTDVERSION[^=]*=(?!89|90|98|99|11|14)/],
                    [~/CLANGVER=4([^0-9.]|$|\.[0-9]+)/, ~/CSTDVERSION[^=]*=(?!89|90|98|99|11|14)/],
                    [~/CLANGVER=[5-8]([^0-9.]|$|\.[0-9]+)/, ~/CSTDVERSION[^=]*=(?!89|90|98|99|11|14|17)/],
                    [~/CLANGVER=(9|[1-9][0-9.]+)([^0-9]|$|\.[0-9]+)/, ~/CSTDVERSION[^=]*=(?!89|90|98|99|11|14|17|2x)/]

                ] // excludeCombos

                if (debugTrace) this.script.println("[DEBUG] DynamatrixConfig(String): initialized with defaultCfg = ${Utils.castString(defaultCfg)} to ${Utils.castString(this)}")

                break;

            default:
                return "[WARNING] DynamatrixConfig(String): unrecognized default pre-set: ${defaultCfg}"
        }
        return true
    }

    public DynamatrixConfig(Object script, Map dynacfgOrig) {
        this.script = script
        this.initDefault(dynacfgOrig)
    }

    public def initDefault(Map dynacfgOrig) {
        def debugErrors = this.shouldDebugErrors()
        def debugTrace = this.shouldDebugTrace()

        if (debugTrace) this.script.println("[DEBUG] DynamatrixConfig(Map): called with dynacfgOrig = ${Utils.castString(dynacfgOrig)}")

        // Quick no-op
        if (dynacfgOrig == null || dynacfgOrig.size() == 0) return true

        // Combine a config with defaults from a Set passed to a groovy call()
        if (dynacfgOrig.size() > 0) {
            // Be sure to only mutilate a copy below, not the original object
            dynacfgOrig = dynacfgOrig.clone()

            // Note: in addition to standard contents of class DynamatrixConfig,
            // the Map passed by caller may contain "defaultDynamatrixConfig" as
            // a key for a String value to specify default pre-sets, e.g. "C".
            if (dynacfgOrig.containsKey('defaultDynamatrixConfig')) {
                def str = dynacfgOrig['defaultDynamatrixConfig'].toString()
                if (debugTrace) this.script.println("[DEBUG] DynamatrixConfig(Map): calling initDefault(${str}) first")
                this.initDefault(str)
                dynacfgOrig.remove('defaultDynamatrixConfig')
            }

            if (dynacfgOrig.containsKey('stageNameFunc')) {
                this.stageNameFunc = dynacfgOrig.stageNameFunc
                dynacfgOrig.remove('stageNameFunc')
            }
        }

        String errs = ""
        if (dynacfgOrig.size() > 0) {
            dynacfgOrig.keySet().each() {k ->
                if (debugTrace) this.script.println("[DEBUG] DynamatrixConfig(Map): checking dynacfgOrig[${k}] = ${Utils.castString(dynacfgOrig[k])}")

                if (k.equals("mergeMode")) return // continue
                try {
                    def mergeMode = "replace"
                    try {
                        // Expected optional value "replace" or "merge"
                        mergeMode = dynacfgOrig.mergeMode[k].trim()
                        dynacfgOrig.mergeMode.remove(k)
                    } catch (Throwable t) {} // keep default setting if no action requested

                    if (debugTrace) this.script.println("[DEBUG] DynamatrixConfig(Map): mergeMode for k='${k}' is '${mergeMode}'")
                    switch ("${mergeMode}") {
                        case "merge":
                            if (debugTrace) this.script.println("[DEBUG] DynamatrixConfig(Map): merging: ${this[k]}\n    with: ${dynacfgOrig[k]}")
                            this[k] = Utils.mergeMapSet(this[k], dynacfgOrig[k])
                            if (debugTrace) this.script.println("[DEBUG] DynamatrixConfig(Map): result of merge: ${this[k]}")
                            break

                        case "replace":
                            if (debugTrace) this.script.println("[DEBUG] DynamatrixConfig(Map): replacing with: ${dynacfgOrig[k]}")
                            this[k] = dynacfgOrig[k]
                            //if (debugTrace) this.script.println("[DEBUG] DynamatrixConfig(Map): result of replacement: ${this[k]}")
                            break

                        default:
                            if (debugTrace) this.script.println("[DEBUG] DynamatrixConfig(Map): defaulting to replace with: ${dynacfgOrig[k]}")
                            this[k] = dynacfgOrig[k]
                            //if (debugTrace) this.script.println("[DEBUG] DynamatrixConfig(Map): result of replacement: ${this[k]}")
                            break
                    }
                } catch(Exception e) {
                    if (!errs.equals("")) errs += "\n"
                    def str = "[DEBUG] DynamatrixConfig(Map): ignoring unsupported config key from request: '${k}' => " + dynacfgOrig[k] + " : " + e.toString()
                    errs += str
                    if (debugTrace) this.script.println str
                }
            }
        } else {
            // Nothing left after special keys were removed?
            if (debugTrace) this.script.println("[DEBUG] DynamatrixConfig(Map): dynacfgOrig was empty (except special keys)")
        }

        // was not null, but at least one unused setting passed
        if (!errs.equals("")) return errs

        return true
    }

    public def sanitycheckDynamatrixAxesLabels() {
        // Our callers expect a few specific data constructs here:
        // either a single string or pattern (that will be remade into
        // a single-element array), or an array/list/set/... of such types
        String errs = ""
        if (this.dynamatrixAxesLabels != null) {
            if (Utils.isList(this.dynamatrixAxesLabels)) {
                // TODO: Match superclass to not list all children of Set etc?
                // TODO: Check entries if this object that they are strings/patterns
                if (this.dynamatrixAxesLabels.size() > 0)
                    return true
                // Else no automagic... but maybe got strict requirements?
                if (this.dynamatrixRequiredLabelCombos.size() > 0)
                    return true
                errs += "Initially requested dynamatrixAxesLabels and dynamatrixRequiredLabelCombos are both empty"
            } else if (Utils.isString(this.dynamatrixAxesLabels)) {
                if (this.dynamatrixAxesLabels.equals("")) {
                    this.dynamatrixAxesLabels = null
                } else {
                    this.dynamatrixAxesLabels = [this.dynamatrixAxesLabels]
                }
                return true
            } else if (Utils.isRegex(this.dynamatrixAxesLabels)) {
                this.dynamatrixAxesLabels = [this.dynamatrixAxesLabels]
                return true
            } else {
                //if (!errs.equals("")) errs += "\n"
                errs += "Not sure what type 'dynamatrixAxesLabels' is: ${Utils.castString(this.dynamatrixAxesLabels)}"
                //this.dynamatrixAxesLabels = null
            }
        }

        // was not null, but at least one unused setting passed
        if (!errs.equals("")) return errs

        // is null
        return false
    }

} // end of class DynamatrixConfig

enum CompilerTypes {
//    null,
    C
}
