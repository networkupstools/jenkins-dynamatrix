/* For build agents that define a shell wrapper, such as ssh or chroot
 * into the "real" build environment, pass the "sh" argument command
 * through that, to be evaluated on that side.
 *
 * The build agent entry on Jenkins should define a CI_WRAP_SH envvar like:
 *   `ssh -o SendEnv '*' jenkins-debian11-ppc64el /bin/sh `
 * or
 *   `/bin/sudo /usr/sbin/chroot /srv/libvirt/mycontainer/rootfs /bin/sh `
 *
 * Note that in case of SSH, the server in container should `AcceptEnv *`
 * and permit non-interactive SSH login for the CI account (and have its
 * working area in sync - the agent.jar on host interacts with filesystem);
 * and in case of chroot, the CI account on host should be allowed to sudo
 * the command (and bind-mounts used to pass the homedir).
 *
 * See NUT::docs/ci-farm-lxc-setup.txt for setup details.
 */
def call(Map shargs = [:]) {
    if (env?.CI_WRAP_SH) {
        echo "Executing shell step wrapped into: ${env.CI_WRAP_SH}"
        def shcmd = shargs.script
        shargs.script = """cat <<'EOF' | ${env.CI_WRAP_SH}
${shcmd}
EOF
"""
    }
    return owner.sh(shargs)
}

def call(String shcmd) {
    Map shargs = [:]
    shargs.script = shcmd
    return sh(shargs)
}
