import org.nut.dynamatrix.*;

/*
 * Run one combination of settings in the matrix for chosen compiler, etc.
 */
void call(dynacfgPipeline = [:], DynamatrixSingleBuildConfig dsbc = null) {
    // Values in env.* inspected below come from the anticipated build
    // matrix settings (agent labels, etc.), configureEnvvars.groovy and
    // tool configuration like autotools.groovy or ci_build.groovy

    // TODO: Pass into build additional hints that may be in dsbc, e.g.
    // clioptSet - but gotta decide to which tool they should go and when
    // (e.g. configure script takes some opts, ci_build normally does not)

    // for analysis part after the build
    def compilerTool = null
    def id = ""
    warnError(message: 'Build-and-check step failed, proceeding to cover the rest of matrix') {
        def msg = "Building with "
        if (env?.COMPILER) {
            id = env.COMPILER.toUpperCase().trim()
            switch (env.COMPILER) {
                case ['gcc', 'GCC']:
                    compilerTool = 'gcc'
                    if (env?.GCCVER) id += "-${env.GCCVER}"
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
            } else if (env?.CLANGVER) {
                id = "CLANG-${env.CLANGVER}"
                compilerTool = 'clang'
            } else {
                echo "WARNING: env got no COMPILER, and no supported {COMPILER}VER... Proceeding by default..."
            }
        }
        if (id != "") msg += "${id} "

        if (env?.STDARG) {
            id += ":${env.STDARG}"
            msg += ("STD=${env.STDARG} " - ~/-std=/)
        }

        if (env?.STDXXARG) {
            id += ":${env.STDXXARG}"
            msg += ("STD=${env.STDXXARG} " - ~/-std=/)
        }

        if (env?.BUILD_WARNOPT) {
            id += ":WARN=${env.BUILD_WARNOPT}"
            msg += "WARN=${env.BUILD_WARNOPT} "
        }

        if (env?.ARCHARG || env?.BITSARG || env?.OS_FAMILY || env?.OS_DISTRO) {
            id += "@"
            msg += "on "
            if (env?.ARCHARG) {
                id += "${env.ARCHARG}"
                msg += ("${env.ARCHARG} " - ~/-arch[ =]/)
                if (env?.BITSARG) { id += ":" }
            }

            if (env?.BITSARG) {
                id += "${env.BITSARG}"
                msg += ("${env.BITSARG} " - ~/-m/) + "-bit "
            }

            if (env?.OS_FAMILY || env?.OS_DISTRO) {
                if (id[-1] != '@') id += ":"
                if (env?.OS_FAMILY && env?.OS_DISTRO) {
                    id += "${env.OS_FAMILY}-${env.OS_DISTRO}"
                    msg += "${env.OS_FAMILY}-${env.OS_DISTRO} "
                } else {
                    // Only one of those strings is present
                    id += "${env.OS_FAMILY}${env.OS_DISTRO}"
                    msg += "${env.OS_FAMILY}${env.OS_DISTRO} "
                }
            }

            msg += "platform"
        }

        id = id.trim().replaceAll(/\\s+/, '_')

        echo msg

        // Build a multiline shell script
        def cmd = """ set +x
"""

        if (dynacfgPipeline?.configureEnvvars) cmd += """ ${dynacfgPipeline?.configureEnvvars}
"""
        if (dynacfgPipeline?.prepconf) cmd += """ ${dynacfgPipeline?.prepconf}
"""
        if (dynacfgPipeline?.configure) cmd += """ ${dynacfgPipeline?.configure}
"""
        if (dynacfgPipeline?.build) cmd += """ ${dynacfgPipeline?.build}
"""
        if (dynacfgPipeline?.check) cmd += """ ${dynacfgPipeline?.check}
"""
        if (dynacfgPipeline?.distcheck) cmd += """ ${dynacfgPipeline?.distcheck}
"""

        sh cmd

    } // warnError + sh

    def i = null
    switch (compilerTool) {
        case 'gcc':
            i = scanForIssues tool: gcc(name: id)
            break
        case 'clang':
            i = scanForIssues tool: clang(name: id)
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
