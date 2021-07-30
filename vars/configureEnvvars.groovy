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

    // TODO: Handle ARCH as "-(m)arch=x86_64" for C(XX)FLAGS and
    //  "-melf_x86_64" for LDFLAGS
    // TODO: Handle cross-compiler names like "arm-linux-gnueabi-gcc(-10)"
    //  and their different flags like "-mbe32" instead of "-m32"
    // Perhaps those more complicated setups should be passed with (virtual?)
    // axes and corresponding exported envvars, and handle the possibility
    // of explicit CC, CFLAGS and so on below.

    // Note that some distributions name their binaries e.g. "gcc-10" while
    // others use "gcc10"; USE_COMPILER_VERSION_SUFFIX below tries to adapt

    if (!dynacfgPipeline.containsKey('configureEnvvars')) {
        dynacfgPipeline['configureEnvvars'] = """ {
USE_COMPILER=""
USE_COMPILER_VERSION_SUFFIX=""
if [ -n "\${CLANGVER}" ] && [ -z "\${COMPILER}" -o "\${COMPILER}" = "clang" -o "\${COMPILER}" = "CLANG" ]; then
        USE_COMPILER="clang"
        if command -v "\${USE_COMPILER}\${CLANGVER}" > /dev/null ; then
            USE_COMPILER_VERSION_SUFFIX="\${CLANGVER}"
        else
            USE_COMPILER_VERSION_SUFFIX="-\${CLANGVER}"
        fi
else
    if [ -n "\${GCCVER}" ] && [ -z "\${COMPILER}" -o "\${COMPILER}" = "gcc" -o "\${COMPILER}" = "GCC" ]; then
        USE_COMPILER="gcc"
        if command -v "\${USE_COMPILER}\${GCCVER}" > /dev/null ; then
            USE_COMPILER_VERSION_SUFFIX="\${GCCVER}"
        else
            USE_COMPILER_VERSION_SUFFIX="-\${GCCVER}"
        fi
    fi
fi

# Possibly: default system compiler, version not specified?
if [ -z "\${USE_COMPILER}" ]; then
    if [ "\${COMPILER}" = "clang" -o "\${COMPILER}" = "CLANG" ]; then
            USE_COMPILER="clang"
    else
        if [ "\${COMPILER}" = "gcc" -o "\${COMPILER}" = "GCC" ]; then
            USE_COMPILER="gcc"
        fi
    fi
fi

case "\${CONFIG_ENVVARS}" in
    *" CC="*) ;;
    CC=*) ;;
    *)  case "\${USE_COMPILER}" in
            clang)  CONFIG_ENVVARS="\${CONFIG_ENVVARS} CC=clang\${USE_COMPILER_VERSION_SUFFIX}" ;;
            gcc)    CONFIG_ENVVARS="\${CONFIG_ENVVARS} CC=gcc\${USE_COMPILER_VERSION_SUFFIX}" ;;
        esac
        ;;
esac

case "\${CONFIG_ENVVARS}" in
    *" CXX="*) ;;
    CXX=*) ;;
    *)  case "\${USE_COMPILER}" in
            clang)  CONFIG_ENVVARS="\${CONFIG_ENVVARS} CXX=clang++\${USE_COMPILER_VERSION_SUFFIX}" ;;
            gcc)    CONFIG_ENVVARS="\${CONFIG_ENVVARS} CXX=g++\${USE_COMPILER_VERSION_SUFFIX}" ;;
        esac
        ;;
esac

case "\${CONFIG_ENVVARS}" in
    *" CPP="*) ;;
    CPP=*) ;;
    *)  case "\${USE_COMPILER}" in
          clang)
            if command -v "clang-cpp-\${CLANGVER}" >/dev/null ; then
                CONFIG_ENVVARS="\${CONFIG_ENVVARS} CPP=clang-cpp-\${CLANGVER}"
            else
                if command -v "clang-cpp" >/dev/null ; then
                    CONFIG_ENVVARS="\${CONFIG_ENVVARS} CPP=clang-cpp"
                fi
            fi
            ;;
          gcc)
            if command -v "cpp-\${GCCVER}" >/dev/null ; then
                CONFIG_ENVVARS="\${CONFIG_ENVVARS} CPP=cpp-\${GCCVER}"
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
case "\${CONFIG_ENVVARS}" in
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

case "\${CONFIG_ENVVARS}" in
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

BITSARG=""
case "\${CONFIG_ENVVARS}" in
    *m16*|*m32*|*m64*|*m128*) ;;
    *)
        if [ -n "\${ARCH_BITS}" ] && [ "\${ARCH_BITS}" -gt 0 ] ; then
                BITSARG="-m\${ARCH_BITS}"
        else
            if [ -n "\${BITS}" ] && [ "\${BITS}" -gt 0 ] ; then
                BITSARG="-m\${BITS}"
            fi
        fi
        ;;
esac

if [ -n "\${STDARG}" ] || [ -n "\${BITSARG}" ]; then
    case "\${CONFIG_ENVVARS}" in
        *" CFLAGS="*|CFLAGS=*)
            CONFIG_ENVVARS="`echo "\${CONFIG_ENVVARS}" | sed "s,CFLAGS=,CFLAGS='\${STDARG} \${BITSARG} ',"`" ;;
        *) CONFIG_ENVVARS="\${CONFIG_ENVVARS} CFLAGS='\${STDARG} \${BITSARG}'" ;;
    esac
fi

if [ -n "\${STDXXARG}" ] || [ -n "\${BITSARG}" ]; then
    case "\${CONFIG_ENVVARS}" in
        *" CXXFLAGS="*|CXXFLAGS=*)
            CONFIG_ENVVARS="`echo "\${CONFIG_ENVVARS}" | sed "s,CXXFLAGS=,CXXFLAGS='\${STDXXARG} \${BITSARG} ',"`" ;;
        *) CONFIG_ENVVARS="\${CONFIG_ENVVARS} CXXFLAGS='\${STDXXARG} \${BITSARG}'" ;;
    esac
fi

if [ -n "\${BITSARG}" ]; then
    case "\${CONFIG_ENVVARS}" in
        *" LDFLAGS="*|LDFLAGS=*)
            CONFIG_ENVVARS="`echo "\${CONFIG_ENVVARS}" | sed "s,LDFLAGS=,LDFLAGS='\${BITSARG} ',"`" ;;
        *) CONFIG_ENVVARS="\${CONFIG_ENVVARS} LDFLAGS='\${BITSARG}'" ;;
    esac
fi

export CONFIG_ENVVARS STDARG STDXXARG BITSARG CC CXX CFLAGS CXXFLAGS LDFLAGS

} """
    } // configureEnvvars

    return dynacfgPipeline
}
