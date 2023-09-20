// Steps should not be in a package, to avoid CleanGroovyClassLoader exceptions...
// package org.nut.dynamatrix;

import org.nut.dynamatrix.*;

import org.nut.dynamatrix.DynamatrixSingleBuildConfig;
import org.nut.dynamatrix.Utils;
import org.nut.dynamatrix.dynamatrixGlobalState;

// TODO: make dynacfgPipeline a class?

/*
// Example config for this part of code:

 */

def sanityCheckDynacfgPipeline(Map dynacfgPipeline = [:]) {
    // Sanity-check the pipeline options

    if (dynacfgPipeline.containsKey('buildSystem')
    &&  'autotools' == dynacfgPipeline.buildSystem
    ) {
        // Not using closures to make sure envvars are expanded during real
        // shell execution and not at an earlier processing stage by Groovy -
        // so below we define many subshelled blocks in parentheses that would
        // be "pasted" into the `sh` steps.

        // Initialize default `make` implementation to use (there are many), etc.:
        if (!dynacfgPipeline.containsKey('defaultTools')) {
            dynacfgPipeline['defaultTools'] = [:]
        }

        if (!dynacfgPipeline['defaultTools'].containsKey('MAKE')) {
            dynacfgPipeline['defaultTools'] = [
                'MAKE': 'make'
            ]
        }

        if (!dynacfgPipeline.containsKey('buildPhases')) {
            dynacfgPipeline.buildPhases = [:]
        }

        // Subshell common operations to prepare codebase:
        if (!dynacfgPipeline.buildPhases.containsKey('prepconf')) {
            dynacfgPipeline.buildPhases['prepconf'] = "( if [ -x ./autogen.sh ]; then ./autogen.sh || exit; else if [ -s configure.ac ] ; then mkdir -p config && autoreconf --install --force --verbose -I config || exit ; fi; fi ; [ -x configure ] || exit )"
        }

        if (!dynacfgPipeline.buildPhases.containsKey('configure')) {
            dynacfgPipeline.buildPhases['configure'] = " ( [ -x configure ] || exit; eval \${CONFIG_ENVVARS} time ./configure \${CONFIG_OPTS} ) "
        }

        if (!dynacfgPipeline.buildPhases.containsKey('build')) {
            dynacfgPipeline.buildPhases['build'] = "( eval time \${MAKE} \${MAKE_OPTS} -k all )"
        }

        // Note: here and below, the "first parallel" run tries to not
        // emit any log messages, unless there are issues (including
        // non-fatal warnings), so the Jenkins saved logs are not in
        // gigabytes range per dynamatrix run :)
        if (!dynacfgPipeline.buildPhases.containsKey('buildQuiet')) {
            dynacfgPipeline.buildPhases['buildQuiet'] = """( echo "First running a quiet parallel build..." >&2; eval time \${MAKE} \${MAKE_OPTS} VERBOSE=0 V=0 -s -k -j 4 all >/dev/null && echo "SUCCESS" && exit 0; echo "First attempt failed (\$?), retrying to log what did:"; eval time \${MAKE} \${MAKE_OPTS} -k all )"""
        }

        if (!dynacfgPipeline.buildPhases.containsKey('buildQuietCautious')) {
            dynacfgPipeline.buildPhases['buildQuietCautious'] = """( echo "First running a quiet parallel build..." >&2; eval time \${MAKE} \${MAKE_OPTS} VERBOSE=0 V=0 -s -k -j 4 all >/dev/null && echo "Seemingly a SUCCESS" ; echo "First attempt finished (\$?), retrying to log what fails (if any):"; eval time \${MAKE} \${MAKE_OPTS} -k all )"""
        }

        if (!dynacfgPipeline.buildPhases.containsKey('check')) {
            dynacfgPipeline.buildPhases['check'] = "( eval time \${MAKE} \${MAKE_OPTS} check )"
        }

        if (!dynacfgPipeline.buildPhases.containsKey('distcheck')) {
            dynacfgPipeline.buildPhases['distcheck'] = """( eval \${CONFIG_ENVVARS} time \${MAKE} \${MAKE_OPTS} distcheck DISTCHECK_CONFIGURE_FLAGS="\${CONFIG_OPTS}" )"""
        }
    }

    return dynacfgPipeline
}
