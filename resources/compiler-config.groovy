withConfig(configuration) {
    imports {
        // https://github.com/mkobit/jenkins-pipeline-shared-libraries-gradle-plugin/issues/79
        normal('com.cloudbees.groovy.cps.NonCPS')
    }
}
