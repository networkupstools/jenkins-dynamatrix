/* A little PoC to test the excludeCombos in-vivo with Jenkins console */
def call(Boolean debug = false) {
    def CSTDVERSION = ['89', '99', '03', '11', '14', '17', '2x']

    def excludeCombos = [
        // GCC - C++
        //          C++98/C++03 : all versions
        //          C++11 : since mid-4.x, mostly 4.8+, finished by 4.8.1; good in 4.9+
        //          C++14 : complete since v5
        //          C++17 : almost complete by v7, one fix in v8
        //          C++20 : experimental adding a lot in v8/v9/v10/v11
        //          C++23 : experimental since v11
        // GCC - C
        //          C89=C90 : "forever"?
        //          C99 : "substantially supported" since v4.5, largely since 3.0+ (https://gcc.gnu.org/c99status.html)
        //          C11 : since 4.6(partially?)
        //          C17 : 8.1.0+
        //          C2x : v9+

        // Recurrent ([^0-9.]|$|\.[0-9]+) means to have end of version
        // or optional next numbered component, to cater for recent
        // releases' single- or at most double-numbered releases except
        // some special cases.

          [~/GCCVER=[012]([^0-9.]|$|\.[0-9]+)/, ~/CSTDVERSION=(?!89|90)/],
          [~/GCCVER=3([^0-9.]|$|\.[0-9]+)/, ~/CSTDVERSION=(?!89|90|98|99)/],
          [~/GCCVER=4([^0-9.]|$)/, ~/CSTDVERSION=(?!89|90|98|99)/],
          [~/GCCVER=4\.[0-4]([^0-9.]|$|\.[0-9]+)/, ~/CSTDVERSION=(?!89|90|98|99)/],
          [~/GCCVER=4\.([5-7]([^0-9.]|$|\.[0-9]+)|8(\.0|[^0-9.]|$))/, ~/CSTDVERSION=(?!89|90|98|99|03)/],
          [~/GCCVER=4\.(8\.[1-9][0-9]*|(9|1[0-9]+)([^0-9.]|$|\.[0-9]+))/, ~/CSTDVERSION=(?!89|98|99|03|11)/],
          [~/GCCVER=([5-7]|8\.0)([^0-9.]|$|\.[0-9]+)/, ~/CSTDVERSION=(?!89|98|99|03|11|14)/],
          [~/GCCVER=8([^0-9.]|$)/, ~/CSTDVERSION=(?!89|90|98|99|03|11|14)/],
          [~/GCCVER=(8\.[1-9][0-9]*)([^0-9.]|$|\.[0-9]+)/, ~/CSTDVERSION=(?!89|98|99|03|11|14|17)/],
          [~/GCCVER=(9|[1-9][0-9]+)([^0-9.]|$|\.[0-9]+)/, ~/CSTDVERSION=(?!89|98|99|03|11|14|17|2x)/],

        // CLANG - C++
        //          C++98/C++03 : all versions
        //          C++11 : v3.3+
        //          C++14 : v3.4+
        //          C++17 : v5+
        //          C++20 (2a) : brewing; keyword became official since v10+
        //          C++23 (2b) : brewing since v13+
        // CLANG - C
        //          C89 : ???
        //          C99 : ???
        //          C11 : since 3.1
        //          C17 : v6+
        //          C2x : v9+

          [~/CLANGVER=[012]\..+/, ~/CSTDVERSION=(?!89|90|98|99)/],
          [~/CLANGVER=3\.[012]([^0-9.]|$|\.[0-9]+)/, ~/CSTDVERSION=(?!89|90|98|99)/],
          [~/CLANGVER=3\.3([^0-9.]|$|\.[0-9]+)/, ~/CSTDVERSION=(?!89|90|98|99|11)/],
          [~/CLANGVER=3\.([4-9]|[1-9][0-9]+)([^0-9]|$|\.[0-9]+)/, ~/CSTDVERSION=(?!89|90|98|99|11|14)/],
          [~/CLANGVER=4([^0-9.]|$|\.[0-9]+)/, ~/CSTDVERSION=(?!89|90|98|99|11|14)/],
          [~/CLANGVER=[5-8]([^0-9.]|$|\.[0-9]+)/, ~/CSTDVERSION=(?!89|90|98|99|11|14|17)/],
          [~/CLANGVER=(9|[1-9][0-9.]+)([^0-9]|$|\.[0-9]+)/, ~/CSTDVERSION=(?!89|90|98|99|11|14|17|2x)/]

    ]

    println "start:"
    int v1, v2, v3
    for (String COMPILER in ['GCC', 'CLANG']) {
        for (v1 = 3; v1 < 12; v1++) {
            for (v2 = -1; v2 < 10; v2++) {
                for (v3 = -1; v3 < 3; v3++) {
                    String label = "${COMPILER}VER=${v1}"
                    if (v2 >= 0) {
                        label += ".${v2}"
                        if (v3 >= 0) label += ".${v3}"
                    }

                    //println "label: ${label}"
                    Set supports = []
                    // Track variants which had a hit in the exclusion array:
                    Set notSupports = []
                    for (stdverNum in CSTDVERSION) {
                        String STDVER = "CSTDVERSION=${stdverNum}"
                        for (ecArr in excludeCombos) {
                            def crit = 0
                            def hits = 0
                            for (ec in ecArr) {
                                crit++
                                // In production this would work with array of selected build
                                // variables, not "label" or "STDVER" explicitly
                                if (label =~ ec) {
                                    hits++
                                    if (debug) println "Hit ${label} with ${ec}"
                                } else if (STDVER =~ ec) {
                                    hits++
                                    if (debug) println "Hit ${STDVER} with ${ec}"
                                }
                            }
                            if (hits == crit && hits > 0) {
                                if (debug) println "Excluded ${label} for std: ${STDVER}"
                                notSupports << "${stdverNum}"
                            }
                        }
                    }
                    println "label: ${label} supports: ${CSTDVERSION - notSupports}"
                    if (v2 == -1) break
                }
            }
        }
    }
    println "end"
} // call

//call()
