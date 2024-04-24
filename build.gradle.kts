// Bits from https://github.com/mkobit/jenkins-pipeline-shared-library-example/blob/master/build.gradle.kts
import com.mkobit.jenkins.pipelines.http.AnonymousAuthentication
import java.io.ByteArrayOutputStream

plugins {
    id ("groovy")
    id ("java")

    // See https://docs.gradle.com/enterprise/gradle-plugin/#gradle_6_x_and_later
    //id("com.gradle.build-scan") version "2.3"
    //id( "com.gradle.enterprise") version "3.7.2"

    id("com.mkobit.jenkins.pipelines.shared-library") version "0.10.1" apply true
    id("com.github.ben-manes.versions") version "0.21.0"

    // https://discuss.gradle.org/t/unable-to-resolve-class-when-compiling-jenkins-groovy-script/28153/11
    // https://github.com/mkobit/jenkins-pipeline-shared-libraries-gradle-plugin/issues/65
    id("org.jenkins-ci.jpi") version "0.38.0" apply false
    id ("org.jetbrains.kotlin.jvm") version "1.6.10-RC"

    // https://github.com/JetBrains/gradle-idea-ext-plugin
    id ("org.jetbrains.gradle.plugin.idea-ext") version "1.1.7" apply true
}

group "org.nut.dynamatrix"
version "1.0-SNAPSHOT"

val commitSha: String by lazy {
    ByteArrayOutputStream().use {
        project.exec {
            commandLine("git", "rev-parse", "HEAD")
            standardOutput = it
        }
        it.toString(Charsets.UTF_8.name()).trim()
    }
}

/*
buildScan {
    termsOfServiceAgree = "yes"
    termsOfServiceUrl = "https://gradle.com/terms-of-service"
    //link("GitHub", "https://github.com/mkobit/jenkins-pipeline-shared-library-example")
    value("Revision", commitSha)
}
*/

java {
    //sourceCompatibility = JavaVersion.VERSION_1_8
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

//noinspection JCenterRepository
// https://blog.gradle.org/jcenter-shutdown says it will remain R/O
repositories {
    maven (url = "https://repo.jenkins-ci.org/releases/")
    maven (url = "https://repo.jenkins-ci.org/incrementals/")
    // Mirror not served anymore // maven (url = "https://repo.jenkins-ci.org/public/")
    maven (url = "https://plugins.gradle.org/m2/")

    mavenCentral()

    // Note: this one reports "403 Forbidden" if an URL is bad -
    // check spelling of the artifact (too few/many components etc.)
    maven (url = "https://mvnrepository.com/artifact/")

    jcenter()
}

dependencies {
    // https://mvnrepository.com/artifact/com.cloudbees/groovy-cps
    implementation ("com.cloudbees:groovy-cps:3624.v43b_a_38b_62b_b_7")

    implementation ("org.eclipse.hudson:hudson-core:3.2.1")
    implementation ("javax.servlet:javax.servlet-api:4.0.1")

    // Currently Jenkins CPS-transforms over Groovy 2.4.21 foundations
    // (check over time with `println GroovySystem.version` on our
    // `$JENKINS_URL/script` console)
    implementation ("org.codehaus.groovy:groovy-all:2.4.21")
    //implementation ("org.codehaus.groovy:groovy-all:3.0.9")

    // Java11+ vs. older built libraries (these are not in core anymore?)
    //implementation ("com.sun.xml.ws:jaxws-ri:4.0.0:pom")
    //implementation ("com.sun.xml.bind:jaxb-core:4.0.1")
    //implementation ("javax.xml.bind:jaxb-api:2.4.0-b180830.0359")
    //implementation ("com.sun.xml.bind:jaxb-impl:4.0.1")
    //implementation ("org.glassfish.main.javaee-api:javax.jws:3.1.2.2")

    // NOTE: Need a version with https://github.com/jenkinsci/http-request-plugin/pull/120 code in it!
    // https://mvnrepository.com/artifact/org.jenkins-ci.plugins/http_request (older releases)
    //implementation ("org.jenkins-ci.plugins:http_request:1.8.27")
    //testImplementation ("org.jenkins-ci.plugins:http_request:1.8.27")
    // https://repo.jenkins-ci.org/incrementals/org/jenkins-ci/plugins/http_request/1.17-rc492.f4a_b_5b_1a_43c3/
    implementation ("org.jenkins-ci.plugins:http_request:1.17-rc492.f4a_b_5b_1a_43c3")
    testImplementation ("org.jenkins-ci.plugins:http_request:1.17-rc492.f4a_b_5b_1a_43c3")

    testImplementation ("com.mkobit.jenkins.pipelines:jenkins-pipeline-shared-libraries-gradle-plugin:0.10.1")
    testImplementation ("javax.servlet:javax.servlet-api:4.0.1")

    // NOTE: https://stackoverflow.com/questions/52502189/java-11-package-javax-xml-bind-does-not-exist
    //testImplementation ("javax.xml.bind:javaxb-api")
    // Avoid java.lang.NoClassDefFoundError: com/sun/activation/registries/LogSupport
    // https://github.com/jakartaee/mail-api/issues/627
    testImplementation ("javax.activation:activation:1.1.1")
    // Alternatively: https://github.com/jakartaee/jaf-api/issues/60
    //testImplementation ("com.sun.activation:jakarta.activation:2.0.0")
    testImplementation ("jakarta.xml.bind:jakarta.xml.bind-api")
    testImplementation ("com.sun.xml.bind:jaxb-impl")

    implementation ("org.jenkins-ci.main:jenkins-test-harness:1949.vb_b_37feefe78c")
    testImplementation ("org.eclipse.hudson:hudson-core:3.2.1")
    testImplementation ("org.junit.jupiter:junit-jupiter-api:5.7.2")
    testRuntimeOnly ("org.junit.jupiter:junit-jupiter-engine:5.7.2")
    testRuntimeOnly ("org.jenkins-ci.plugins:matrix-project:1.20")
    testRuntimeOnly ("org.jenkins-ci.plugins.workflow:workflow-step-api:2.24")
    // https://mvnrepository.com/artifact/org.jenkins-ci.plugins/pipeline-utility-steps
    testRuntimeOnly("org.jenkins-ci.plugins:pipeline-utility-steps:2.13.0")
    // Avoid   40.920 [id=44]	SEVERE	jenkins.InitReactorRunner$1#onTaskFailed:
    //   Failed Loading plugin Pipeline Utility Steps v2.13.0 (pipeline-utility-steps)
    //   java.io.IOException: Failed to load: Pipeline Utility Steps (pipeline-utility-steps 2.13.0)
    //   - Update required: Pipeline: Groovy (workflow-cps 2.72) to be updated to 2660.vb_c0412dc4e6d or higher
    testRuntimeOnly("org.jenkins-ci.plugins.workflow:workflow-cps:3624.v43b_a_38b_62b_b_7")
    testImplementation ("com.cloudbees:groovy-cps:3624.v43b_a_38b_62b_b_7")
    implementation ("org.jenkins-ci.main:remoting:3131.vf2b_b_798b_ce99")
    implementation ("org.jenkins-ci.plugins:git:5.1.0")
    implementation ("org.jenkins-ci.plugins:github-branch-source:1701.v00cc8184df93")
    implementation ("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    testImplementation("org.assertj:assertj-core:3.12.2")
/*
  val spock = "org.spockframework:spock-core:1.2-groovy-2.4"
  testImplementation(spock)
  integrationTestImplementation(spock)
*/
}

/*
// https://github.com/mkobit/jenkins-pipeline-shared-libraries-gradle-plugin#configuring-versions
sharedLibrary {
    coreVersion = "2.303"
    testHarnessVersion = "2.24"
    pluginDependencies {
        workflowCpsGlobalLibraryPluginVersion = "2.8"
        dependency("io.jenkins.blueocean", "blueocean-web", "1.2.4")
    }
}
*/

jenkinsIntegration {
    // For builds with IntelliJ IDEA, may have to specify location of WAR
    // if "Could not initialize class org.jvnet.hudson.test.WarExploder"
    // and it says jenkins-core "is not in the expected location":
    //   -Djth.jenkins-war.path="C:\Users\klimov\.m2\repository\org\jenkins-ci\main\jenkins-war\2.387.1\jenkins-war-2.387.1.war"
    baseUrl.set(uri("http://localhost:5050").toURL())
    authentication.set(providers.provider { AnonymousAuthentication })
    //downloadDirectory.set(layout.projectDirectory.dir("jenkinsResources"))
    downloadDirectory.set(layout.projectDirectory.dir("resources"))
}

sharedLibrary {
    // TODO: this will need to be altered when auto-mapping functionality is complete
    coreVersion.set(jenkinsIntegration.downloadDirectory.file("core-version.txt").map { it.asFile.readText().trim() })
    //coreVersion.set("2.303")
    // TODO: retrieve downloaded plugin resource
    pluginDependencies {
        dependency("org.jenkins-ci.plugins", "pipeline-build-step", "2.9")
        dependency("org.6wind.jenkins", "lockable-resources", "2.5")
        val declarativePluginsVersion = "1.3.9"
        dependency("org.jenkinsci.plugins", "pipeline-model-api", declarativePluginsVersion)
        dependency("org.jenkins-ci.plugins.workflow", "workflow-step-api", "2.24")
        dependency("org.jenkins-ci.plugins.workflow", "workflow-cps", "3624.v43b_a_38b_62b_b_7")
        dependency("org.jenkinsci.plugins", "pipeline-model-declarative-agent", "1.1.1")
        dependency("org.jenkinsci.plugins", "pipeline-model-definition", declarativePluginsVersion)
        dependency("org.jenkinsci.plugins", "pipeline-model-extensions", declarativePluginsVersion)
        // Jenkins Server startup in tests throws long noisy stack traces without these:
        dependency("org.jenkins-ci.plugins", "git-client", "3.10.0")
        dependency("org.jenkins-ci.plugins", "git-server", "1.10")
        dependency("org.jenkins-ci.modules", "sshd", "3.1.0")
        //dependency("org.jenkins-ci.plugins", "http_request", "1.8.27")
        dependency("org.jenkins-ci.plugins", "http_request", "1.17-rc492.f4a_b_5b_1a_43c3")

        dependency("org.jenkins-ci.plugins", "matrix-project", "1.20")
        dependency("org.jenkins-ci.plugins", "pipeline-utility-steps", "2.13.0")

        dependency("org.jenkins-ci.plugins", "git", "5.1.0")
        dependency("org.jenkins-ci.plugins", "github-branch-source", "1701.v00cc8184df93")
        dependency("org.jenkins-ci.main", "remoting", "3131.vf2b_b_798b_ce99")

        dependency("javax.activation", "activation", "1.1.1")
        //dependency("com.sun.activation", "jakarta.activation", "2.0.0")

        dependency ("org.jenkins-ci.main", "jenkins-test-harness", "1949.vb_b_37feefe78c")
    }
}

// http://tdongsi.github.io/blog/2018/02/09/intellij-setup-for-jenkins-shared-library-development/
// https://github.com/mkobit/jenkins-pipeline-shared-libraries-gradle-plugin/blob/master/src/main/kotlin/com/mkobit/jenkins/pipelines/SharedLibraryPlugin.kt#L67
/*
sourceSets {
    // Note: paths below are relative to "src" by default, so prefixed by "../"
    main {
        groovy {
            setSrcDirs()
        }
        resources {
            srcDir("${project.rootDir}/../resources")
        }
    }
    create("src") {
        groovy {
            srcDir("${project.rootDir}/../src") // classes
        }
    }
    create("vars") {
        groovy {
            // In IntelliJ IDEA can go to Project Structure / jenkins-dynamatrix / vars
            // => Edit source root
            // => Set package prefix "org.nut.dynamatrix" for better IDE integration
            srcDir("${project.rootDir}/../vars") // steps
        }
    }

    test {
        groovy {
            srcDir("${project.rootDir}/../test")
        }
    }
}
*/

// https://github.com/JetBrains/gradle-idea-ext-plugin/issues/114
fun org.gradle.plugins.ide.idea.model.IdeaModule.settings(configure: org.jetbrains.gradle.ext.ModuleSettings.() -> Unit) =
    (this as ExtensionAware).configure(configure)

val org.jetbrains.gradle.ext.ModuleSettings.packagePrefix: org.jetbrains.gradle.ext.PackagePrefixContainer
    get() = (this as ExtensionAware).the()

idea {
    module {
        settings {
            packagePrefix["vars"] = "org.nut.dynamatrix"
        }
    }
}

// https://github.com/mkobit/jenkins-pipeline-shared-libraries-gradle-plugin/issues/69
tasks {
    wrapper {
        //gradleVersion = "5.5.1"
        gradleVersion = "7.2"
    }

    test {
        useJUnitPlatform()
        // https://stackoverflow.com/a/52745454/4715872
        minHeapSize = "128m" // initial heap size
        maxHeapSize = "4096m" // maximum heap size
        jvmArgs = listOf("-XX:MaxPermSize=256m") // mem argument for the test JVM
    }

    compileGroovy {
        groovyOptions.configurationScript = file("resources/compiler-config.groovy")
        groovyOptions.forkOptions.jvmArgs = listOf("-Xms128m", "-Xmx4g") //, "-XX:MaxPermSize=256m") // Unrecognized VM option 'MaxPermSize=256m'
    }

    // https://github.com/mkobit/jenkins-pipeline-shared-libraries-gradle-plugin/issues/65
    register<org.jenkinsci.gradle.plugins.jpi.TestDependenciesTask>("resolveIntegrationTestDependencies") {
        into {
            val javaConvention = project.convention.getPlugin<JavaPluginConvention>()
            File("${javaConvention.sourceSets.integrationTest.get().output.resourcesDir}/test-dependencies")
        }
        configuration = configurations.integrationTestRuntimeClasspath.get()
    }
    processIntegrationTestResources {
        dependsOn("resolveIntegrationTestDependencies")
    }

    compileKotlin {
        kotlinOptions {
            //jvmTarget = "1.8"
            jvmTarget = "11"
        }
    }
    compileTestKotlin {
        kotlinOptions {
            //jvmTarget = "1.8"
            jvmTarget = "11"
        }
    }
}
