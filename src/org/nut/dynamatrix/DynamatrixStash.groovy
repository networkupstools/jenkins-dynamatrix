package org.nut.dynamatrix;

import org.nut.dynamatrix.Utils;
import org.nut.dynamatrix.dynamatrixGlobalState;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/* For Jenkins Swarm build agents that dial in to the controller,
 * which themselves may have or not have access to the SCM server,
 * we offer a way for the controller to push sources of the current
 * tested build as a stash. There is currently no way to cache it
 * however, so multiple builds landing on same agent suffer this
 * push many times (time, traffic, I/O involved).
 *
 * For agents that know they can access the original SCM server
 * (e.g. GitHub with public repos and generally available Internet),
 * it may be more optimal to tell them to just check out locally,
 * especially if optimizations like Git reference repository local
 * to that worker are involved. Such agents can declare this by
 * specifying a node label DYNAMATRIX_UNSTASH_PREFERENCE=... with
 *   ...=unstash(:stashName)
 *   ...=scm(:stashName)
 * The optional ":stashName" suffix allows to limit the preference
 * to builds of a particular project which uses that string, e.g.
 * DYNAMATRIX_UNSTASH_PREFERENCE=scm:nut-ci-src
 * A node may define the default preference and ones customized for
 * the stashName(s), the latter would be preferred. A node should
 * not define two or more preferences for same stashName, behavior
 * would be undefined in that case.
 *
 * The original optional "scmbody" Closure used for stashCleanSrc()
 * with that stashName would be replayed on the agent, if "scm" is
 * preferred.
 *
 * Also, if the current node running unstashCleanSrc() declares a
 * GIT_REFERENCE_REPO_DIR environment variable, its value would be
 * injected into GitSCM configuration for repo cloning operations.
 * For deployments running git-client-plugin with support for fanned
 * out refrepo like /some/path/${GIT_SUBMODULES}, that suffix string
 * '/${GIT_SUBMODULES}' should be verbatim in the expanded envvar:
 *   https://issues.jenkins.io/browse/JENKINS-64383
 *   https://github.com/jenkinsci/git-client-plugin/pull/644
 */

class DynamatrixStash {
    private static def stashCode = [:]

    static void deleteWS(def script) {
        /* clean up our workspace (current directory) */
        script.deleteDir()

        /* clean up tmp directory */
        script.dir("${script.workspace}@tmp") {
            script.deleteDir()
        }

        /* clean up script directory */
        script.dir("${script.workspace}@script") {
            script.deleteDir()
        }
    } // deleteWS()

    static String getGitRefrepoDir(def script) {
        // NOTE: Have this logic defined and extensible in one place
        // Returns the reference repository URL usable by git-client-plugin
        // or null if none was found.

        // Does the current build agent's set of envvars declare the path?
        if (script?.env?.GIT_REFERENCE_REPO_DIR) {
            String refrepo = "${script.env.GIT_REFERENCE_REPO_DIR}"
            script.echo "Got GIT_REFERENCE_REPO_DIR='${refrepo}'"
            if (refrepo != "")
                return refrepo
        }

        // Query Jenkins global config for defaults?..

        // Final answer
        return null
    }

    static def checkoutGit(def script, Map scmParams = [:], String coRef = null) {
        // Helper to produce a git checkout with the parameter array
        // similar to that of the standard checkout() step, which we
        // can tune here. The optional "coRef" can specify the code
        // revision to checkout, as branch name e.g. "master", or as
        // the commit hash identifier, or a tag as "refs/tags/v1.2.3".

        // This routine only intrudes in a few parts of the scmParams
        // spec, if customized by run-time circumstances and not
        // specified by caller: the $class, branches, and extensions
        // for git reference repository.

/* // Example code that caller might directly specify:
    checkout([$class: 'GitSCM', branches: [[name: "refs/tags/v2.7.4"]],
        doGenerateSubmoduleConfigurations: false,
        extensions: [
            [$class: 'SubmoduleOption', disableSubmodules: false,
             parentCredentials: false, recursiveSubmodules: false,
             reference: '', trackingSubmodules: false]
        ],
        submoduleCfg: [],
        userRemoteConfigs: [[url: "https://github.com/networkupstools/nut"]]
        ])
*/

        if (!scmParams.containsKey('$class')) {
            script.echo "scmParams: inject class"
            scmParams << [$class: 'GitSCM']
        }

        if (!scmParams.containsKey('branches') && coRef != null) {
            script.echo "scmParams: inject branches"
            scmParams << [branches: [[ name: coRef ]] ]
        }

        String refrepo = getGitRefrepoDir(script)
        if (refrepo != null) {
            if (scmParams.containsKey('extensions')) {
                def seenClone = false
                def seenSubmodules = false
                // TODO? Just detect, do not change the iterated set?
                scmParams.extensions.each() { extset ->
                    if (extset.containsKey('$class')) {
                        switch (extset['$class']) {
                            case 'CloneOption':
                                if (!extset.containsKey('reference')) {
                                    script.echo "scmParams: inject refrepo to cloneOption"
                                    extset.reference = refrepo
                                }
                                seenClone = true
                                break
                            case 'SubmoduleOption':
                                if (!extset.containsKey('reference')) {
                                    script.echo "scmParams: inject refrepo to submoduleOption"
                                    extset.reference = refrepo
                                }
                                seenSubmodules = true
                                break
                        }
                    }
                }
                if (!seenClone) {
                    script.echo "scmParams: inject refrepo to cloneOption"
                    scmParams.extensions += [$class: 'CloneOption', reference: refrepo]
                }
                if (!seenSubmodules) {
                    script.echo "scmParams: inject refrepo to submoduleOption"
                    scmParams.extensions += [$class: 'SubmoduleOption', reference: refrepo]
                }
            } else {
                script.echo "scmParams: inject extensions for refrepo"
                scmParams.extensions = [
                    [$class: 'CloneOption', reference: refrepo],
                    [$class: 'SubmoduleOption', reference: refrepo]
                ]
            }
        }

        script.echo "checkoutGit: scmParams = ${Utils.castString(scmParams)}"
        return script.checkout(scmParams)
    }

    static def checkoutSCM(def script, def scmParams, String coRef = null) {
        // Similar to checkoutGit() above, but for an SCM class; inspired by
        // https://issues.jenkins.io/browse/JENKINS-43894?focusedCommentId=332158
        Field field = null

        if (scmParams instanceof hudson.plugins.git.GitSCM) {
            if (coRef != null) {
                if (scmParams.hasProperty('branches')) {
                    if (scmParams.branches) {
// Example: <class java.util.Collections$SingletonList>([fightwarn])
                        script.print("has branches: ${Utils.castString(scmParams.branches)}")
/*
                        java.util.Collections$SingletonList newb = new java.util.Collections$SingletonList()
                        newb.add(new hudson.plugins.git.BranchSpec(coRef))
                        //java.util.List newb = [new hudson.plugins.git.BranchSpec(coRef)]
*/

                        // Changes type to java.util.List and seems ignored in practice,
                        // maybe some other fields also set what is checked out?..
                        scmParams.branches = [new hudson.plugins.git.BranchSpec(coRef)]
                        //scmParams.branches[0] = new hudson.plugins.git.BranchSpec(coRef)

/*
                        field = scmParams.class.getDeclaredField("branches")
                        Field modifiersField = Field.class.getDeclaredField("modifiers")
                        modifiersField.setAccessible(true)
                        modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL)
                        field.setAccessible(true)
                        field.set(scmParams.branches, newb)
*/

                        script.print("replaced branches with: ${Utils.castString(scmParams.branches)} (WARNING: This may be ignored by later checkout(), investigating...)")
                    } else {
                        script.echo "checkoutSCM: failed to set a custom Git checkout: branches field is empty"
                    }
                } else {
                    script.echo "checkoutSCM: failed to set a custom Git checkout: no branches field is found"
                }
            }

            String refrepo = getGitRefrepoDir(script)
            if (refrepo != null) {
                try {
                    if (scmParams.hasProperty('extensions')) {
                        if (scmParams.extensions) {
                            script.print('has extensions')
                            def extensions = scmParams.extensions

                            for (int i = 0; i < extensions.size(); i++) {
                                def extension = extensions[i]
// Example object name and spec (from a stacktrace): 'private final
//   java.lang.String hudson.plugins.git.extensions.impl.CloneOption.reference'
// with class 'java.lang.reflect.Field'
                                if (extension.hasProperty('reference') && extension.reference instanceof String) {
                                    def originalReference = extension.reference
                                    script.print('replacing reference: ' +
                                        originalReference +
                                        ' with: ' + refrepo)

                                    // https://gist.github.com/pditommaso/263721865d84dee6ebaf
                                    field = extension.class.getDeclaredField("reference")
                                    Field modifiersField = Field.class.getDeclaredField("modifiers")
                                    modifiersField.setAccessible(true)
                                    modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL)
                                    field.setAccessible(true)
                                    field.set(extension, refrepo)
                                }
                            }
                        } else {
                            script.echo "checkoutSCM: failed to set a custom Git refrepo: extensions field is empty (additions NOT IMPLEMENTED)"
/*
                            // Add the extensions property contents for clone and submodule
                            field = scmParams.class.getDeclaredField("extensions")
                            Field modifiersField = Field.class.getDeclaredField("modifiers")
                            modifiersField.setAccessible(true)
                            modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL)
                            field.setAccessible(true)
                            field.set(extension, refrepo)
*/
                        }
                    } else {
                        script.echo "checkoutSCM: failed to set a custom Git refrepo: no extensions field found"
                    }
                } catch (Throwable t) {
                    script.echo "checkoutSCM: failed to set a custom Git refrepo: ${t.toString()}"
                }
            }
        }

        script.echo "checkoutSCM: scmParams = ${Utils.castString(scmParams)}"
        return script.checkout(scmParams)
    }

    static def checkoutCleanSrc(def script, Closure scmbody = null) {
        // Optional closure can fully detail how the code is checked out
        deleteWS(script)

        // Per https://plugins.jenkins.io/workflow-scm-step/ the common
        // "scm" is a Map maintained by the pipeline, so we can tweak it
        def scm = script.scm

        def res = null

        if (scmbody == null) {
            script.echo "checkoutCleanSrc: scm = ${Utils.castString(scm)}"
            if (Utils.isMap(scm)
                && scm.containsKey('$class')
                && scm['$class'] in ['GitSCM']
            ) {
                res = checkoutGit(script, scm)
            } else {
                res = checkoutSCM(script, scm)
                //res = script.checkout (scm)
            }
        } else {
            // Per experience, that body defined in the pipeline script
            // sees its methods and vars, no delegation etc. required.
            // It can help the caller to use DynamatrixStash.checkoutGit()
            // in their custom scmbody with the custom scmParams arg...
            script.echo "checkoutCleanSrc: calling scmbody = ${Utils.castString(scmbody)}"
            res = scmbody()
        }

        // If we made a git checkout with a refrepo, untie it before stashing
        // https://stackoverflow.com/questions/2248228/how-to-detach-alternates-after-git-clone-reference
        // TODO: Would a `git gc` reduce footprint to stash?
        if (script.isUnix()) {
            script.sh label:"Untie the git checkout from reference repo (if any)", script:"""
sync || true
git status || true
echo "[DEBUG] Checked-out workspace size (Kb): `du -ks .`"
if test -e .git/objects/info/alternates ; then
    git repack -a -d || exit
    rm -f .git/objects/info/alternates || exit
    sync || true
    echo "[DEBUG] Checked-out workspace size after dissociate and repack (Kb): `du -ks .`"
    git gc --aggressive --prune=now --quiet --keep-largest-pack || true
    sync || true
    echo "[DEBUG] Checked-out workspace size after garbage-collector (Kb): `du -ks .`"
fi
git status || true
echo "[DEBUG] Files in `pwd`: `find . -type f | wc -l` and all FS objects under: `find . | wc -l`" || true
"""
        } // node isUnix(), can sh

        return res
    } // checkoutCleanSrc()

    static void checkoutCleanSrcNamed(def script, String stashName, Closure scmbody = null) {
        // Different name because groovy gets lost with parameter count
        // when some can be defaulted

        // Optional closure can fully detail how the code is checked out
        // Remember last used method for this stashName,
        // we may have to replay it on some workers
        script.echo "Saving scmbody for ${stashName}: ${Utils.castString(scmbody)}"
        stashCode[stashName] = scmbody
        script.echo "Calling actual checkoutCleanSrc()"
        checkoutCleanSrc(script, scmbody)
    } // checkoutCleanSrc()

    static void stashCleanSrc(def script, String stashName, Closure scmbody = null) {
        // Optional closure can fully detail how the code is checked out
        checkoutCleanSrcNamed(script, stashName, scmbody)

        // Be sure to get also "hidden" files like .* in Unix customs => .git*
        // so avoid default exclude patterns
        if (script.isUnix()) {
            script.sh label:"Debug git checkout contents before stash()", script:"""
sync || true
echo "[DEBUG before stash()] Files in `pwd`: `find . -type f | wc -l` and all FS objects under: `find . | wc -l`" || true
git status || true
"""
        }
        script.stash (name: stashName, includes: '**,.*,*,.git,.git/**,.git/refs', excludes: '', useDefaultExcludes: false)
    } // stashCleanSrc()

    static void unstashCleanSrc(def script, String stashName) {
        deleteWS(script)
        if (script?.env?.NODE_LABELS) {
            def useMethod = null
            script.env.NODE_LABELS.split('[ \r\n\t]+').each() { KV ->
                if (KV =~ /^DYNAMATRIX_UNSTASH_PREFERENCE=.*$/) {
                    (key, val) = KV.split('=')
                    if (val == "scm:${stashName}") {
                        useMethod = 'scm'
                    }
                    if (val == "unstash:${stashName}") {
                        useMethod = 'unstash'
                    }
                    if (val in ["scm", "unstash"]) {
                        if (useMethod == null) {
                            useMethod = val
                        }
                    }
                }
            }

            switch (useMethod) {
                case 'scm':
                    // The stashCode[stashName] should be defined, maybe null,
                    // by an earlier stashCleanSrc() with same stashName.
                    // We error out otherwise, same as would for unstash().
                    checkoutCleanSrc(script, stashCode[stashName])
                    return
                // case 'unstash', null, etc: fall through
            }
        }

        script.unstash (stashName)
        if (script.isUnix()) {
            // Try a workaround with `git init` per https://issues.jenkins.io/browse/JENKINS-56098?focusedCommentId=380303
            script.sh label:"Debug git checkout contents after unstash()", script:"""
sync || true
echo "[DEBUG] Unstashed workspace size (Kb): `du -ks .`" || true
git status || if test -e .git ; then
    echo "(Re-)init unstashed git workspace:"
    git init
    git status || true
fi
echo "[DEBUG] Files in `pwd`: `find . -type f | wc -l` and all FS objects under: `find . | wc -l`" || true
"""
        }
    } // unstashCleanSrc()

} // class DynamatrixStash
