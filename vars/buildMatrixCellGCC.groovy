import org.nut.dynamatrix.*;

/*
 * Run one combination of settings in the matrix for chosen compiler, etc.
 */
void call(String GCCVER, String STD, String STDVER, String PLATFORM, String BUILD_WARNOPT) {
    warnError(message: 'Build-and-check step failed, proceeding to cover whole matrix') {
        sh """ echo "Building with GCC-${GCCVER} STD=${STD}${STDVER} WARN=${BUILD_WARNOPT} on ${PLATFORM}"
case "${PLATFORM}" in
    *openindiana*) BUILD_SSL_ONCE=true ; BUILD_LIBGD_CGI=auto ; export BUILD_LIBGD_CGI ;;
    *) BUILD_SSL_ONCE=false ;;
esac
export BUILD_SSL_ONCE

case "${STDVER}" in
    99) STDXXVER="98" ;;
    *) STDXXVER="${STDVER}" ;;
esac

BUILD_TYPE=default-all-errors \
BUILD_WARNOPT="${BUILD_WARNOPT}" BUILD_WARNFATAL=yes \
CFLAGS="-std=${STD}${STDVER}" CXXFLAGS="-std=${STD}++\${STDXXVER}" \
CC=gcc-${GCCVER} CXX=g++-${GCCVER} \
./ci_build.sh
"""
    } // warnError + sh

    script {
        def id = "GCC-${GCCVER}:STD=${STD}${STDVER}:WARN=${BUILD_WARNOPT}@${PLATFORM}"
        def i = scanForIssues tool: gcc(name: id)
//        dynamatrixGlobal.issueAnalysis << i
        publishIssues issues: [i], filters: [includePackage('io.jenkins.plugins.analysis.*')]
    }
} // buildMatrixCellGCC()
