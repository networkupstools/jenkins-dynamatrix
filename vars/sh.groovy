package org.nut.dynamatrix;

/* For build agents that define a shell wrapper, such as ssh or chroot
 * into the "real" build environment, pass the "sh" argument command
 * through that, to be evaluated on that side.
 *
 * The build agent entry on Jenkins should define a CI_WRAP_SH envvar
 * for a pipe to shell interpreter, like:
 *   `ssh -o SendEnv='*' jenkins-debian11-ppc64el /bin/sh `
 * or
 *   `/bin/sudo /usr/sbin/chroot /srv/libvirt/mycontainer/rootfs /bin/sh `
 * ...and maybe suffix /bin/sh -xe ` for same debug-tracing effect as
 * seen by default on Jenkins.
 *
 * Note that in case of SSH, the server in container should `AcceptEnv *`
 * and permit non-interactive SSH login for the CI account (and have its
 * working area in sync - the agent.jar on host interacts with filesystem);
 * and in case of chroot, the CI account on host should be allowed to sudo
 * the command (and bind-mounts used to pass the homedir).
 *
 * See NUT::docs/ci-farm-lxc-setup.txt for setup details.
 */
import org.nut.dynamatrix.dynamatrixGlobalState;

def ciWrapSh(def script, Map shargs = [:]) {
    if (env?.CI_WRAP_SH) {
        echo "Executing shell step wrapped into: ${env.CI_WRAP_SH}"
        def shcmd = shargs.script
        if (dynamatrixGlobalState.enableDebugTrace) echo "[DEBUG] Wrapped shell code:\n${shcmd}"

        // Be sure homedirs for the worker are in sync, so current workspace
        // path (`pwd`) for the agent is same on the other side.
        shargs.script = """(echo "cd '`pwd`' || exit; " ; cat) <<'__CI_WRAP_SH_EOF__' | ${env.CI_WRAP_SH}
${shcmd}
__CI_WRAP_SH_EOF__
"""
    }
    return script.sh(shargs)
}

def ciWrapSh(def script, String shcmd) {
    Map shargs = [:]
    shargs.script = shcmd
    return ciWrapSh(script, shargs)
}

def call(def shargs) {
    // refer to steps.sh for real implementation
    return ciWrapSh(steps, shargs)
}
