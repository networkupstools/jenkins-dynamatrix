import org.nut.dynamatrix.*

import org.nut.dynamatrix.DynamatrixSingleBuildConfig
import org.nut.dynamatrix.Utils
import org.nut.dynamatrix.dynamatrixGlobalState;

// TODO: make dynacfgPipeline a class?

/*
// Example config for this part of code:

 */

def sanityCheckDynacfgPipeline(dynacfgPipeline = [:]) {
    // Sanity-check the pipeline options

    if (dynacfgPipeline.containsKey('buildSystem') &&
        dynacfgPipeline.buildSystem.equals('autotools')
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

        // Subshell common operations to prepare codebase:
        if (!dynacfgPipeline.containsKey('prepconf')) {
            dynacfgPipeline['prepconf'] = "( if [ -x ./autogen.sh ]; then ./autogen.sh || exit; else if [ -s configure.ac ] ; then mkdir -p config && autoreconf --install --force --verbose -I config || exit ; fi; fi ; [ -x configure ] || exit )"
        }

        if (!dynacfgPipeline.containsKey('configure')) {
            dynacfgPipeline['configure'] = " ( [ -x configure ] || exit; eval \${CONFIG_ENVVARS} ./configure \${CONFIG_OPTS} ) "
        }

        if (!dynacfgPipeline.containsKey('build')) {
            dynacfgPipeline['build'] = "( eval \${MAKE} \${MAKE_OPTS} all )"
        }

        if (!dynacfgPipeline.containsKey('check')) {
            dynacfgPipeline['check'] = "( eval \${MAKE} \${MAKE_OPTS} check )"
        }

        if (!dynacfgPipeline.containsKey('distcheck')) {
            dynacfgPipeline['distcheck'] = "( eval \${MAKE} \${MAKE_OPTS} distcheck )"
        }
    }

    return dynacfgPipeline
}
