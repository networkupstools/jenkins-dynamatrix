package org.nut.dynamatrix;

import org.nut.dynamatrix.*;

/*
 * Run one combination of settings in the matrix for chosen compiler, etc.
 */
void call(String CLANGVER, String STD, String STDVER, String PLATFORM, String BUILD_WARNOPT) {
    warnError(message: 'Build-and-check step failed, proceeding to cover whole matrix') {
        sh """ echo "Building with CLANG-${CLANGVER} STD=${STD}${STDVER} WARN=${BUILD_WARNOPT} on ${PLATFORM}"
case "${PLATFORM}" in
    *openindiana*) BUILD_SSL_ONCE=true ; BUILD_LIBGD_CGI=auto ; export BUILD_LIBGD_CGI ;;
    *) BUILD_SSL_ONCE=false ;;
esac
export BUILD_SSL_ONCE

# Map aliased or nearby-yearly versions as valid for GCC and CLANG
case "${STDVER}" in
    89|90|99) STDXXVER="98" ;;
    03) STDXXVER="03" ; STDVER="99" ;;
    14) STDXXVER="14" ; STDVER="11" ;;
    2x|20) STDXXVER="2a" ;;
    *) STDXXVER="${STDVER}" ;;
esac

BUILD_TYPE=default-all-errors \
BUILD_WARNOPT="${BUILD_WARNOPT}" BUILD_WARNFATAL=yes \
CFLAGS="-std=${STD}\${STDVER}" CXXFLAGS="-std=${STD}++\${STDXXVER}" \
CC=clang-${CLANGVER} CXX=clang++-${CLANGVER} CPP=clang-cpp \
./ci_build.sh
"""
    } // warnError + sh

    script {
        String id = "CLANG-${CLANGVER}:STD=${STD}${STDVER}:WARN=${BUILD_WARNOPT}@${PLATFORM}"
        def i = scanForIssues tool: clang(name: id)
//        dynamatrixGlobalState.issueAnalysis << i
        publishIssues issues: [i], filters: [includePackage('io.jenkins.plugins.analysis.*')]
    }
} // buildMatrixCellCLANG()
