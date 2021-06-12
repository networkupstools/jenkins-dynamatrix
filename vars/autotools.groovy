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
            dynacfgPipeline['configure'] = " ( [ -x configure ] || exit; ./configure \${CONFIG_OPTS} ) "
        }

        if (!dynacfgPipeline.containsKey('configureEnvvars')) {
            dynacfgPipeline['configureEnvvars'] = """ {
case "\${CONFIG_OPTS}" in
    *" CC="*) ;;
    CC=*) ;;
    *)  if [ -n "\${CLANGVER}" ]; then
            CONFIG_OPTS="\${CONFIG_OPTS} CC=clang-\${CLANGVER}"
        else if [ -n "\${GCCVER}" ]; then
            CONFIG_OPTS="\${CONFIG_OPTS} CC=gcc-\${GCCVER}"
        fi; fi
        ;;
esac

case "\${CONFIG_OPTS}" in
    *" CXX="*) ;;
    CXX=*) ;;
    *)  if [ -n "\${CLANGVER}" ]; then
            CONFIG_OPTS="\${CONFIG_OPTS} CXX=clang++-\${CLANGVER}"
        else if [ -n "\${GCCVER}" ]; then
            CONFIG_OPTS="\${CONFIG_OPTS} CXX=g++-\${GCCVER}"
        fi; fi
        ;;
esac

case "\${CONFIG_OPTS}" in
    *" CPP="*) ;;
    CPP=*) ;;
    *)  if [ -n "\${CLANGVER}" ]; then
            if command -v "clang-cpp-\${CLANGVER}" >/dev/null ; then
                CONFIG_OPTS="\${CONFIG_OPTS} CPP=clang-cpp-\${CLANGVER}"
            else
                if command -v "clang-cpp" >/dev/null ; then
                    CONFIG_OPTS="\${CONFIG_OPTS} CPP=clang-cpp"
                fi
            fi
        else if [ -n "\${GCCVER}" ]; then
            if command -v "cpp-\${GCCVER}" >/dev/null ; then
                CONFIG_OPTS="\${CONFIG_OPTS} CPP=cpp-\${GCCVER}"
            fi
        fi; fi
        ;;
esac

# May be a loophole here if user already passed a CFLAGS value,
# just without a standard revision spec... Or with just one of those.
STDARG=""
STDXXARG=""
case "\${CONFIG_OPTS}" in
    *" -std="*) ;;
    "-std="*) ;;
    *)  # Adjust versions used for plain C (noop if only -std=c++... is passed by user)
        if [ -n "\${CSTDVERSION_c}" ]; then
            if [ "\${CSTDVERSION_c}" = ansi ]; then
                STDARG="-std=ansi"
            else
                if [ -n "\${CSTDVARIANT}" ]; then
                    STDARG="-std=\${CSTDVARIANT}\${CSTDVERSION_c}"
                else
                    STDARG="-std=c\${CSTDVERSION_c}"
                fi
            fi
        fi
        ;;
esac

case "\${CONFIG_OPTS}" in
    *" -std="*++*) ;;
    "-std="*++*) ;;
    *)  # Adjust versions used
        if [ -n "\${CSTDVERSION_cxx}" ]; then
            if [ "\${CSTDVERSION_cxx}" = ansi ]; then
                STDXXARG="-std=ansi"
            else
                if [ -n "\${CSTDVARIANT}" ]; then
                    STDXXARG="-std=\${CSTDVARIANT}++\${CSTDVERSION_cxx}"
                else
                    STDXXARG="-std=c++\${CSTDVERSION_cxx}"
                fi
            fi
        fi
        ;;
esac

if [ -n "\${STDARG}" ]; then
    CONFIG_OPTS="\${CONFIG_OPTS} CFLAGS=\${STDARG}"
fi

if [ -n "\${STDXXARG}" ]; then
    CONFIG_OPTS="\${CONFIG_OPTS} CXXFLAGS=\${STDXXARG}"
fi

export CONFIG_OPTS
} """
        } // configureEnvvars

        if (!dynacfgPipeline.containsKey('build')) {
            dynacfgPipeline['build'] = "( \${MAKE} all )"
        }

        if (!dynacfgPipeline.containsKey('check')) {
            dynacfgPipeline['check'] = "( \${MAKE} check )"
        }

        if (!dynacfgPipeline.containsKey('distcheck')) {
            dynacfgPipeline['distcheck'] = "( \${MAKE} distcheck )"
        }
    }

    return dynacfgPipeline
}
