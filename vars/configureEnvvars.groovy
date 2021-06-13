import org.nut.dynamatrix.*

import org.nut.dynamatrix.DynamatrixSingleBuildConfig
import org.nut.dynamatrix.Utils
import org.nut.dynamatrix.dynamatrixGlobalState;

// TODO: make dynacfgPipeline a class?

/*
// Example config for this part of code:

 */

def sanityCheckDynacfgPipeline(dynacfgPipeline = [:]) {
    // Sanity-check the pipeline options, these are relevant for several
    // build systems alike (autotools, ci_build, probably cmake, etc...)

    if (!dynacfgPipeline.containsKey('configureEnvvars')) {
        dynacfgPipeline['configureEnvvars'] = """ {
USE_COMPILER=""
USE_COMPILER_VERSION_SUFFIX=""
if [ -n "\${CLANGVER}" ] && [ -z "\${COMPILER}" -o "\${COMPILER}" = "clang" -o "\${COMPILER}" = "CLANG" ]; then
    USE_COMPILER="clang"
    USE_COMPILER_VERSION_SUFFIX="-\${CLANGVER}"
else if [ -n "\${GCCVER}" ] && [ -z "\${COMPILER}" -o "\${COMPILER}" = "gcc" -o "\${COMPILER}" = "GCC" ]; then
    USE_COMPILER="gcc"
    USE_COMPILER_VERSION_SUFFIX="-\${GCCVER}"
fi

# Possibly: default system compiler, version not specified?
if [ -z "\${USE_COMPILER}" ]; then
    if [ "\${COMPILER}" = "clang" -o "\${COMPILER}" = "CLANG" ]; then
        USE_COMPILER="clang"
    else if [ "\${COMPILER}" = "gcc" -o "\${COMPILER}" = "GCC" ]; then
        USE_COMPILER="gcc"
    fi
fi

case "\${CONFIG_OPTS}" in
    *" CC="*) ;;
    CC=*) ;;
    *)  case "\${USE_COMPILER}" in
            clang)  CONFIG_OPTS="\${CONFIG_OPTS} CC=clang\${USE_COMPILER_VERSION_SUFFIX}" ;;
            gcc)    CONFIG_OPTS="\${CONFIG_OPTS} CC=gcc\${USE_COMPILER_VERSION_SUFFIX}" ;;
        esac
        ;;
esac

case "\${CONFIG_OPTS}" in
    *" CXX="*) ;;
    CXX=*) ;;
    *)  case "\${USE_COMPILER}" in
            clang)  CONFIG_OPTS="\${CONFIG_OPTS} CXX=clang++\${USE_COMPILER_VERSION_SUFFIX}" ;;
            gcc)    CONFIG_OPTS="\${CONFIG_OPTS} CXX=g++\${USE_COMPILER_VERSION_SUFFIX}" ;;
        esac
        ;;
esac

case "\${CONFIG_OPTS}" in
    *" CPP="*) ;;
    CPP=*) ;;
    *)  case "\${USE_COMPILER}" in
          clang)
            if command -v "clang-cpp-\${CLANGVER}" >/dev/null ; then
                CONFIG_OPTS="\${CONFIG_OPTS} CPP=clang-cpp-\${CLANGVER}"
            else
                if command -v "clang-cpp" >/dev/null ; then
                    CONFIG_OPTS="\${CONFIG_OPTS} CPP=clang-cpp"
                fi
            fi
            ;;
          gcc)
            if command -v "cpp-\${GCCVER}" >/dev/null ; then
                CONFIG_OPTS="\${CONFIG_OPTS} CPP=cpp-\${GCCVER}"
            fi
            ;;
          # else let config script find some "cpp" it would like
        esac
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

export CONFIG_OPTS STDARG STDXXARG

} """
    } // configureEnvvars

    return dynacfgPipeline
}
