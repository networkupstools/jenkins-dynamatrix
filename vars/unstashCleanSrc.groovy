void call(String stashName) {
    /* clean up our workspace */
    deleteDir()
    /* clean up tmp directory */
    dir("${workspace}@tmp") {
        deleteDir()
    }
    /* clean up script directory */
    dir("${workspace}@script") {
        deleteDir()
    }
    unstash stashName
} // unstashCleanSrc()
