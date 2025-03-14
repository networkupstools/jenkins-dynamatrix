// Steps should not be in a package, to avoid CleanGroovyClassLoader exceptions...
// package org.nut.dynamatrix;

import org.nut.dynamatrix.*;

import org.nut.dynamatrix.DynamatrixSingleBuildConfig;
import org.nut.dynamatrix.Utils;
import org.nut.dynamatrix.dynamatrixGlobalState;

// TODO: make dynacfgPipeline a class?

/*
// Example config for this part of code:
    dynacfgPipeline.buildSystem = 'ci_build'
 */

def sanityCheckDynacfgPipeline(Map dynacfgPipeline = [:]) {
    // Sanity-check the pipeline options for zproject-inspired ci_build.sh
    // stack of scripts tailored specifically for CI builds and managed by
    // value of BUILD_TYPE envvar (and some other BUILD_* can be defined).
    // Generally quite similar to autotools setup. One difference is that
    // ci_build.sh relies on bash syntax for CONFIG_OPTS[@] array support
    // internally, but values placed into it are fed from standard envvars
    // like CC, CFLAGS and others.

    if (dynacfgPipeline?.containsKey('buildSystem')
    &&  Utils.isStringNotEmpty(dynacfgPipeline.buildSystem)
    &&  ((String)(dynacfgPipeline.buildSystem) in ['ci_build', 'ci_build.sh', 'zproject'])
    ) {
        // Not using closures to make sure envvars are expanded during real
        // shell execution and not at an earlier processing stage by Groovy -
        // so below we define many subshelled blocks in parentheses that would
        // be "pasted" into the `sh` steps.

        // Initialize default `make` implementation to use (there are many), etc.:
        if (!dynacfgPipeline.containsKey('defaultTools')) {
            if (dynamatrixGlobalState.enableDebugTrace)
                echo "ci_build.sanityCheckDynacfgPipeline(): prepare empty dynacfgPipeline.defaultTools[]"
            dynacfgPipeline['defaultTools'] = [:]
        }

        if (!dynacfgPipeline['defaultTools'].containsKey('MAKE')) {
            if (dynamatrixGlobalState.enableDebugTrace)
                echo "ci_build.sanityCheckDynacfgPipeline(): populate missing dynacfgPipeline.defaultTools[] with one MAKE"
            dynacfgPipeline['defaultTools'] = [
                'MAKE': 'make'
            ]
        }

        if (!(Utils.isStringNotEmpty(dynacfgPipeline?.defaultTools?.MAKE?.strip()))) {
            echo "WARNING: MAKE is somehow unset or blank, defaulting!"
            dynacfgPipeline['defaultTools'] = [
                'MAKE': 'make'
            ]
        }

        if (!dynacfgPipeline.containsKey('buildPhases')) {
            if (dynamatrixGlobalState.enableDebugTrace)
                echo "ci_build.sanityCheckDynacfgPipeline(): prepare empty dynacfgPipeline.buildPhases[]"
            dynacfgPipeline.buildPhases = [:]
        }

        // Subshell common operations to prepare codebase:
        if (!dynacfgPipeline.buildPhases.containsKey('prepconf')) {
            if (dynamatrixGlobalState.enableDebugTrace)
                echo "ci_build.sanityCheckDynacfgPipeline(): populate missing dynacfgPipeline.buildPhases['prepconf'] = null"
            dynacfgPipeline.buildPhases['prepconf'] = null
        }

        if (!dynacfgPipeline.buildPhases.containsKey('configure')) {
            if (dynamatrixGlobalState.enableDebugTrace)
                echo "ci_build.sanityCheckDynacfgPipeline(): populate missing dynacfgPipeline.buildPhases['configure'] = null"
            dynacfgPipeline.buildPhases['configure'] = null
        }

        if (!dynacfgPipeline.buildPhases.containsKey('build')) {
            if (dynamatrixGlobalState.enableDebugTrace)
                echo "ci_build.sanityCheckDynacfgPipeline(): populate missing dynacfgPipeline.buildPhases['build'] = null"
            dynacfgPipeline.buildPhases['build'] = null
        }

        if (!dynacfgPipeline.buildPhases.containsKey('buildQuiet')) {
            if (dynamatrixGlobalState.enableDebugTrace)
                echo "ci_build.sanityCheckDynacfgPipeline(): populate missing dynacfgPipeline.buildPhases['buildQuiet'] = null"
            dynacfgPipeline.buildPhases['buildQuiet'] = null
        }

        if (!dynacfgPipeline.buildPhases.containsKey('buildQuietCautious')) {
            if (dynamatrixGlobalState.enableDebugTrace)
                echo "ci_build.sanityCheckDynacfgPipeline(): populate missing dynacfgPipeline.buildPhases['buildQuietCautious'] = null"
            dynacfgPipeline.buildPhases['buildQuietCautious'] = null
        }

        // CONFIG_ENVVARS are set by code in configureEnvvars.groovy
        if (!dynacfgPipeline.buildPhases.containsKey('check')) {
            // Note: here even an empty MAKE envvar should not be a problem,
            // the ci_build.sh script should find/guess one
            if (dynamatrixGlobalState.enableDebugTrace)
                echo "ci_build.sanityCheckDynacfgPipeline(): populate missing dynacfgPipeline.buildPhases['prepconf'] with script to call ci_build.sh"
            dynacfgPipeline.buildPhases['check'] = """ (
[ -x ./ci_build.sh ] || exit

eval BUILD_TYPE="\${BUILD_TYPE}" BUILD_WARNOPT="\${BUILD_WARNOPT}" BUILD_WARNFATAL="\${BUILD_WARNFATAL}" MAKE="\${MAKE}" \${CONFIG_ENVVARS} ./ci_build.sh \${CONFIG_OPTS}
) """
        }

        if (!dynacfgPipeline.buildPhases.containsKey('distcheck')) {
            if (dynamatrixGlobalState.enableDebugTrace)
                echo "ci_build.sanityCheckDynacfgPipeline(): populate missing dynacfgPipeline.buildPhases['distcheck'] = null"
            dynacfgPipeline.buildPhases['distcheck'] = null
        }
    } else {
        if (dynamatrixGlobalState.enableDebugTrace)
            echo "ci_build.sanityCheckDynacfgPipeline(): SKIP: dynacfgPipeline.buildSystem is missing or not ['ci_build', 'ci_build.sh', 'zproject']"
    }

    return dynacfgPipeline
}
