import org.nut.dynamatrix.*;

/*
 * Run one combination of settings in the matrix for chosen compiler, etc.
 */
void call(dynacfgPipeline = [:], DynamatrixSingleBuildConfig dsbc = null, String stageName = null) {
    // Values in env.* inspected below come from the anticipated build
    // matrix settings (agent labels, etc.), configureEnvvars.groovy and
    // tool configuration like autotools.groovy or ci_build.groovy

    // NOTE: Currently the values set ONLY by configureEnvvars.groovy
    // are not yet set and so not exposed in env[] by the time we build
    // the ID string below. In fact, it may be too complex to reliably
    // extract CONFIG_OPTS set in shell back to groovy and shell again.

    // TODO: Separate CONFIG_OPTS that so far set options for ./configure
    // script envvar-style and can be prefixed to ./ci_build.sh script,
    // vs a list of "real" options that would be suffixed on command line?

    // TODO: Pass into build additional hints that may be in dsbc, e.g.
    // clioptSet - but gotta decide to which tool they should go and when
    // (e.g. configure script takes some opts, ci_build normally does not)

    // for analysis part after the build
    def compilerTool = null
    // Allowed elements are characters, digits, dashes and underscores
    // (more precisely, the ID must match the regular expression `\p{Alnum}[\p{Alnum}-_]*`
    def id = ""
    warnError(message: 'Build-and-check step failed, proceeding to cover the rest of matrix') {
        def msg = "Building with "
        if (env?.COMPILER) {
            id = env.COMPILER.toUpperCase().trim()
            switch (env.COMPILER) {
                // TODO: Handle alternately-named cross-compilers like packaged
                // like "arm-linux-gnueabi-gcc(-10)" or third-party snapshots
                // named like "arm-linux-gnueabi-20140823-20131011-gcc-4.8.1"
                // Note that would also need some cooperation from agent labels
                // and generic "${COMPILER}VER" support, possibly some use of
                // dynamatrixAxesCommonOpts and/or dynamatrixAxesCommonEnv
                case ['gcc', 'GCC']:
                    compilerTool = 'gcc'
                    if (env?.GCCVER) {
                        id += "-${env.GCCVER}"
                        try {
                            if (env?.GCCVER.replaceAll(/\..*$/, '').toInteger() < 4) {
                                compilerTool = 'gcc3'
                            }
                        } catch (Throwable n) {}
                    }
                    break
                case ['clang', 'CLANG']:
                    compilerTool = 'clang'
                    if (env?.CLANGVER) id += "-${env.CLANGVER}"
                    break
                default:
                    try {
                        if (env["${env.COMPILER}VER"]) {
                            id += "-" + env["${env.COMPILER}VER"]
                        }
                    } catch (Throwable t) {} // ignore
                    break
            }
        } else {
            if (env?.GCCVER) {
                id = "GCC-${env.GCCVER}"
                compilerTool = 'gcc'
                try {
                    if (env?.GCCVER.replaceAll(/\..*$/, '').toInteger() < 4) {
                        compilerTool = 'gcc3'
                    }
                } catch (Throwable n) {}
            } else if (env?.CLANGVER) {
                id = "CLANG-${env.CLANGVER}"
                compilerTool = 'clang'
            } else {
                echo "WARNING: env got no COMPILER, and no supported {COMPILER}VER... Proceeding by default..."
            }
        }
        if (id != "") msg += "${id} "

        if (env?.CSTDVERSION_c) {
            def tmp = DynamatrixSingleBuildConfig.C_stdarg(env?.CSTDVARIANT, env?.CSTDVERSION_c, false)
            id += "_${tmp}"
            msg += "STD=${tmp} "
        } else {
            if (env?.STDARG) {
                id += "_${env.STDARG}"
                msg += ("STD=${env.STDARG} " - ~/-std=/)
            }
        }

        if (env?.CSTDVERSION_cxx) {
            def tmp = DynamatrixSingleBuildConfig.C_stdarg(env?.CSTDVARIANT, env?.CSTDVERSION_cxx, true, true)
            id += "_${tmp}".replaceAll(/\+/, 'x')
            msg += "STD=${tmp} "
        } else {
            if (env?.STDXXARG) {
                id += "_${env.STDXXARG}"
                msg += ("STD=${env.STDXXARG} " - ~/-std=/)
            }
        }

        if (env?.BUILD_WARNOPT) {
            id += "_WARN=${env.BUILD_WARNOPT}"
            msg += "WARN=${env.BUILD_WARNOPT} "
        }

        if (env?.ARCHARG || env?.BITSARG || env?.ARCH_BITS || env?.BITS || env["ARCH${ARCH_BITS}"] || env["ARCH${BITS}"] || env?.OS_FAMILY || env?.OS_DISTRO) {
            msg += "on "
            if (env["ARCH${ARCH_BITS}"]) {
                id += "_${env["ARCH${ARCH_BITS}"]}"
                msg += "${env["ARCH${ARCH_BITS}"]} "
            } else if (env["ARCH${BITS}"]) {
                id += "_${env["ARCH${BITS}"]}"
                msg += "${env["ARCH${BITS}"]} "
            } else {
                if (env?.ARCHARG) {
                    id += "_${env.ARCHARG}"
                    msg += ("${env.ARCHARG} " - ~/-arch[ =]/)
                }
            }

            if (env?.ARCH_BITS) {
                id += "_${env.ARCH_BITS}"
                msg += "${env.ARCH_BITS} "
            } else if (env?.BITS) {
                id += "_${env.BITS}"
                msg += "${env.BITS} "
            } else {
                if (env?.BITSARG) {
                    id += "_${env.BITSARG}"
                    msg += ("${env.BITSARG} " - ~/-m/) + "-bit "
                }
            }

            if (env?.OS_FAMILY || env?.OS_DISTRO) {
                if (env?.OS_FAMILY && env?.OS_DISTRO) {
                    id += "_${env.OS_FAMILY}-${env.OS_DISTRO}"
                    msg += "${env.OS_FAMILY}-${env.OS_DISTRO} "
                } else {
                    // Only one of those strings is present
                    id += "_${env.OS_FAMILY}${env.OS_DISTRO}"
                    msg += "${env.OS_FAMILY}${env.OS_DISTRO} "
                }
            }

            msg += "platform"
        }

        id = id.trim().replaceAll(/\\s+/, '_')
        if (stageName)
            msg = msg.trim() + " for ${stageName}"

        echo msg

        def cmdLabel = ""
        // Build a multiline shell script
        // TOTHINK: Split that into many shell steps (each with configureEnvvars
        // re-run or some re-import of the first generated value, if needed),
        // and/or sequential stages to visualize in BO UI build progress?
        def cmd = """ """
        if (!dynacfgPipeline?.traceBuildShell) cmd = """ set +x
"""

        if (dynacfgPipeline?.buildSystem) {
            cmdLabel = "With ${dynacfgPipeline.buildSystem}: "
        }

        if (dynacfgPipeline?.configureEnvvars) {
            cmd += """ ${dynacfgPipeline?.configureEnvvars}
"""
            cmdLabel += "configureEnvvars "
        }

        if (dynacfgPipeline?.prepconf) {
            cmd += """ ${dynacfgPipeline?.prepconf}
"""
            cmdLabel += "prepconf "
        }

        if (dynacfgPipeline?.configure) {
            cmd += """ ${dynacfgPipeline?.configure}
"""
            cmdLabel += "configure "
        }

        if (dynacfgPipeline?.build) {
            cmd += """ ${dynacfgPipeline?.build}
"""
            cmdLabel += "build "
        }

        if (dynacfgPipeline?.check) {
            cmd += """ ${dynacfgPipeline?.check}
"""
            cmdLabel += "check "
        }

        if (dynacfgPipeline?.distcheck) {
            cmd += """ ${dynacfgPipeline?.distcheck}
"""
            cmdLabel += "distcheck "
        }

        if (stageName)
            cmdLabel = cmdLabel.trim() + " for ${stageName}"
        sh (script: cmd, label: cmdLabel.trim())

    } // warnError + sh

    def i = null
    switch (compilerTool) {
        case 'gcc':
            i = scanForIssues tool: gcc(id: id)
            break
        case 'gcc3':
            i = scanForIssues tool: gcc3(id: id)
            break
        case 'clang':
            i = scanForIssues tool: clang(id: id)
            break
    }
    if (i != null) {
        if (dynacfgPipeline?.delayedIssueAnalysis) {
            // job should call doSummarizeIssues() in the end
            dynamatrixGlobalState.issueAnalysis << i
        } else {
            publishIssues issues: [i], filters: [includePackage('io.jenkins.plugins.analysis.*')]
        }
    }
} // buildMatrixCellCI()
