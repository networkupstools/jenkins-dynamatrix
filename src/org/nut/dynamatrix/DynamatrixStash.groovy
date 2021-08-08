package org.nut.dynamatrix;

import org.nut.dynamatrix.Utils;
import org.nut.dynamatrix.dynamatrixGlobalState;

/* For Jenkins Swarm build agents that dial in to the controller,
 * which themselves may have or not have access to the SCM server,
 * we offer a way for the controller to push sources of the current
 * tested build as a stash. There is currently no way to cache it
 * however, so multiple builds landing on same agent suffer this
 * push many times (time, traffic, I/O involved).
 * For agents that know they can access the original SCM server
 * (e.g. GitHub with public repos and generally available Internet),
 * it may be more optimal to tell them to just check out locally,
 * especially if optimizations like Git reference repository local
 * to that worker are involved.
 */

class DynamatrixStash {

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

    static void checkoutCleanSrc(def script, String stashName, Closure scmbody = null) {
        // Optional closure can fully detail how the code is checked out
        deleteWS(script)

        if (scmbody == null) {
            script.checkout script.scm
        } else {
            scmbody()
        }
    } // checkoutCleanSrc()

    static void stashCleanSrc(def script, String stashName, Closure scmbody = null) {
        // Optional closure can fully detail how the code is checked out
        checkoutCleanSrc(script, stashName, scmbody)

        // Be sure to get also "hidden" files like .* in Unix customs => .git*
        // so avoid default exclude patterns
        script.stash (name: stashName, useDefaultExcludes: false)
    } // stashCleanSrc()

    static void unstashCleanSrc(def script, String stashName) {
        deleteWS(script)
        script.unstash (stashName)
    } // unstashCleanSrc()

} // class DynamatrixStash
