/* For build agents that define a shell wrapper, such as ssh or chroot
 * into the "real" build environment, pass the "sh" argument command
 * through that, to be evaluated on that side.
 * The build agent entry on Jenkins should define a CI_WRAP_SH envvar like:
 *   `ssh -o SendEnv '*' jenkins-debian11-ppc64el /bin/sh `
 */
def sh(shargs) {
    if (env?.CI_WRAP_SH) {
        echo "Executing shell step wrapped into: ${env.CI_WRAP_SH}"
        def shcmd = shargs.script
        shargs.script = """cat <<'EOF' | ${env.CI_WRAP_SH}
${shcmd}
EOF
"""
    }
    return this.sh(shargs)
}
