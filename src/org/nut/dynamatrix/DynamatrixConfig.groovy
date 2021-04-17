package org.nut.dynamatrix;

/* This class intends to represent one build request configuration
 * An instance of it can be passed as the set of arguments for the
 * customized run Dynamatrix routines, while some defaults can be
 * applied so needed fields are all "defined" when we look at them.
 */
class DynamatrixConfig {
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

    // Each of these labels can be String, GString or Pattern object:
    //    dynamatrixAxesLabels: ['OS', '${COMPILER}VER', 'ARCH'],
    public Set dynamatrixAxesLabels = []

    // TODO: Need a way to specify optional labels, so that nodes which
    // do not declare any value (e.g. do not state which ARCH(es) they
    // support) can still be used for some unique build combos ("...but
    // that is our only system that runs SUPERCCVER=123").

    // Set of (Sets of envvars to export) - the resulting build matrix
    // picks one of these at a time for otherwise same conditions made
    // from other influences. This example defines two build variants:
    //    dynamatrixAxesCommonEnv: [['LANG=C', 'TZ=UTC'], ['LANG=ru_RU', 'SHELL=ksh']],
    public Set<Set> dynamatrixAxesCommonEnv = []
    // Same goal as above, but builds would get a carthesian multiplication
    // of the variants listed in the sub-sets, e.g. this example:
    //    dynamatrixAxesCommonEnvCarthesian: [['LANG=C', 'LANG=ru_RU'], ['TZ=UTC', 'TZ=CET']]
    // ...should produce four build variants that would feed into the
    // dynamatrixAxesCommonEnv use-case, namely:
    //    [['LANG=C','TZ=UTC'], ['LANG=ru_RU','TZ=UTC'], ['LANG=C','TZ=CET'], ['LANG=ru_RU','TZ=CET']]
    // NOTE: third-layer objects (e.g. meaningful sets not strings) should
    // get plugged into the result "as is" and can help group related options
    // without "internal conflict", e.g.:
    //    dynamatrixAxesCommonEnvCarthesian: [[ ['LANG=C','LC_ALL=C'], 'LANG=ru_RU'], ['TZ=UTC', 'TZ=CET']]
    // should yield such four build variants:
    //    [['LANG=C','LC_ALL=C','TZ=UTC'], ['LANG=ru_RU','TZ=UTC'], ['LANG=C','LC_ALL=C','TZ=CET'], ['LANG=ru_RU','TZ=CET']]
    public Set<Set> dynamatrixAxesCommonEnvCarthesian = []

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
    //    dynamatrixAxesCommonOptsCarthesian: [
    //        [ ["CFLAGS=-stdc=gnu99", "CXXFLAGS=-stdcxx=g++99"],
    //          ["CFLAGS=-stdc=c89", "CXXFLAGS=-stdcxx=c++89"]     ],
    //        ['-m32', '-m64'] ]
    // has two second-layer sets: one varies (grouped sets of) C/C++
    // standards, another varies bitnesses. The result is also to try
    // four build scenarios, but these four would run each combination
    // of language revision and bitness, compactly worded like this:
    //    [ [gnu99,m32], [c89,m32], [gnu99,m64], [c89,m64] ]
    public Set<Set> dynamatrixAxesCommonOptsCarthesian = []

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

/*
 * Example agents and call signature from README:

* "testoi":
----
COMPILER=CLANG COMPILER=GCC
CLANGVER=8 CLANGVER=9
GCCVER=10 GCCVER=4.4.4 GCCVER=4.9 GCCVER=6 GCCVER=7
OS=openindiana
ARCH=x86_64 ARCH=x86
nut-builder zmq-builder
----
* "testdeb":
----
COMPILER=GCC
GCCVER=4.8 GCCVER=4.9 GCCVER=5 GCCVER=7
OS=linux
ARCH=x86_64 ARCH=x86 ARCH=armv7l
nut-builder zmq-builder linux-kernel-builder
----

// Prepare parallel stages for the dynamatrix:
def parallelStages = prepareDynamatrix(
    commonLabelExpr: 'nut-builder',
    compilerType: 'C',
    compilerLabel: 'COMPILER',
    compilerTools: ['CC', 'CXX', 'CPP'],
    dynamatrixAxesLabels: ['OS', '${COMPILER}VER', 'ARCH'],
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

    public DynamatrixConfig() {}

    public DynamatrixConfig(String defaultCfg) {
        this.initDefault(defaultCfg)
    }

    public def initDefault(String defaultCfg) {
        // TODO
        return true
    }

    public DynamatrixConfig(Map dynacfgOrig) {
        this.initDefault(dynacfgOrig)
    }

    public def initDefault(Map dynacfgOrig) {
        // Combine a config with defaults from a Set passed to a groovy call()
        if (dynacfgOrig.size() > 0) {
            if (dynacfgOrig.containsKey('defaultDynamatrixConfig')) {
                this.initDefault(dynacfgOrig[defaultDynamatrixConfig].toString())
                dynacfgOrig.remove('defaultDynamatrixConfig')
            }
        }

        String errs = ""
        if (dynacfgOrig.size() > 0) {
            for (k in dynacfgOrig.keySet()) {
                try {
                    this[k] = dynacfgOrig[k]
                } catch(Exception e) {
                    if (!errs.equals("")) errs += "\n"
                    errs += "[DEBUG] DynamatrixConfig(Set): ingoring unsupported config key from request: '${k}' => " + dynacfgOrig[k]
                }
            }
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
            if (this.dynamatrixAxesLabels.getClass() in [ArrayList, List, Set, TreeSet, LinkedHashSet, Object[]]) {
                // TODO: Match superclass to not list all children of Set etc?
                // TODO: Check entries if this object that they are strings/patterns
                return true
            } else if (this.dynamatrixAxesLabels.getClass() in [String, java.lang.String, GString]) {
                if (this.dynamatrixAxesLabels.equals("")) {
                    this.dynamatrixAxesLabels = null
                } else {
                    this.dynamatrixAxesLabels = [this.dynamatrixAxesLabels]
                }
                return true
            } else if (this.dynamatrixAxesLabels.getClass() in [java.util.regex.Pattern]) {
                this.dynamatrixAxesLabels = [this.dynamatrixAxesLabels]
                return true
            } else {
                if (!errs.equals("")) errs += "\n"
                errs += "Not sure what type 'dynamatrixAxesLabels' is: " + this.dynamatrixAxesLabels.getClass() + " : " + this.dynamatrixAxesLabels.toString()
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
