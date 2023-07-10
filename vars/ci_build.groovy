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

    if (dynacfgPipeline.containsKey('buildSystem')
    &&  Utils.isStringNotEmpty(dynacfgPipeline.buildSystem)
    &&  ((String)(dynacfgPipeline.buildSystem) in ['ci_build', 'ci_build.sh', 'zproject'])
    ) {
        // Not using closures to make sure envvars are expanded during real
        // shell execution and not at an earlier processing stage by Groovy -
        // so below we define many subshelled blocks in parentheses that would
        // be "pasted" into the `sh` steps.

        // Initialize default `make` implementation to use (there are many), etc.:
        if (!dynacfgPipeline.containsKey('defaultTools')) {
            dynacfgPipeline['defaultTools'] = [
                'MAKE': 'make'
            ]
        }

        if (!dynacfgPipeline.containsKey('buildPhases')) {
            dynacfgPipeline.buildPhases = [:]
        }

        // Subshell common operations to prepare codebase:
        if (!dynacfgPipeline.buildPhases.containsKey('prepconf')) {
            dynacfgPipeline.buildPhases['prepconf'] = null
        }

        if (!dynacfgPipeline.buildPhases.containsKey('configure')) {
            dynacfgPipeline.buildPhases['configure'] = null
        }

        if (!dynacfgPipeline.buildPhases.containsKey('build')) {
            dynacfgPipeline.buildPhases['build'] = null
        }

        if (!dynacfgPipeline.buildPhases.containsKey('buildQuiet')) {
            dynacfgPipeline.buildPhases['buildQuiet'] = null
        }

        if (!dynacfgPipeline.buildPhases.containsKey('buildQuietCautious')) {
            dynacfgPipeline.buildPhases['buildQuietCautious'] = null
        }

        // CONFIG_ENVVARS are set by code in configureEnvvars.groovy
        if (!dynacfgPipeline.buildPhases.containsKey('check')) {
            dynacfgPipeline.buildPhases['check'] = """ (
[ -x ./ci_build.sh ] || exit

eval BUILD_TYPE="\${BUILD_TYPE}" BUILD_WARNOPT="\${BUILD_WARNOPT}" BUILD_WARNFATAL="\${BUILD_WARNFATAL}" MAKE="\${MAKE}" \${CONFIG_ENVVARS} ./ci_build.sh \${CONFIG_OPTS}
) """
        }

        if (!dynacfgPipeline.buildPhases.containsKey('distcheck')) {
            dynacfgPipeline.buildPhases['distcheck'] = null
        }
    }

    return dynacfgPipeline
}
