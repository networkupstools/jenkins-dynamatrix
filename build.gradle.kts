// Bits from https://github.com/mkobit/jenkins-pipeline-shared-library-example/blob/master/build.gradle.kts
//import com.mkobit.jenkins.pipelines.http.AnonymousAuthentication
import java.io.ByteArrayOutputStream

import java.net.URL

import org.gradle.api.artifacts.ComponentMetadataContext
import org.gradle.api.artifacts.ComponentMetadataRule
import org.gradle.api.artifacts.maven.PomModuleDescriptor
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.attributes.Attribute
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.jvm.JvmTestSuite
import org.gradle.api.plugins.quality.CodeNarc
import org.gradle.api.tasks.compile.GroovyCompile
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestStackTraceFilter
import org.gradle.kotlin.dsl.invoke
import org.gradle.api.tasks.wrapper.Wrapper
import org.gradle.kotlin.dsl.named

// Jenkins plugin HPI artifacts may have stale Gradle module metadata that omits the
// ARTIFACT_TYPE_ATTRIBUTE / com.mkobit.jenkins.artifact attributes.  Without these
// attributes the mkobit plugin's artifact-view filter returns nothing and the
// jenkinsPluginHpis configuration resolves to 0 files.
class JenkinsHpiVariantFix : ComponentMetadataRule {
    override fun execute(ctx: ComponentMetadataContext) {
        val pom = ctx.getDescriptor(PomModuleDescriptor::class.java) ?: return
        val packaging = pom.packaging
        if (packaging != "hpi" && packaging != "jpi") return
        val id = ctx.details.id
        ctx.details.withVariant("runtime") {
            attributes {
                attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, packaging)
                attribute(Attribute.of("com.mkobit.jenkins.artifact", String::class.java), packaging)
            }
            withFiles {
                removeAllFiles()
                addFile("${id.name}-${id.version}.$packaging")
            }
        }
    }
}

group "org.nut.dynamatrix"
version "1.0-SNAPSHOT"

plugins {
    //id ("groovy")
    //id ("java")

    // Aliases are resolved via gradle/libs.versions.toml:
    // id ("com.mkobit.jenkins.pipelines.shared-library") version "0.11.0" apply true
    alias(libs.plugins.shared.library)

    // Was useful when migrating to newer mkobit library release
    // (see also the "rewrite" rule below):
    //id("org.openrewrite.rewrite") version "6.26.0"
    //alias(libs.plugins.openrewrite)

    // Code styling and static analysis:
//    alias(libs.plugins.spotless)
//    codenarc

    // (OLDER) See also https://docs.gradle.com/enterprise/gradle-plugin/#gradle_6_x_and_later
    //id ("com.gradle.build-scan") version "2.3"
    //id ("com.gradle.enterprise") version "3.7.2"

    // Gradle plugin to discover dependency updates
    // https://github.com/ben-manes/gradle-versions-plugin
    //id ("com.github.ben-manes.versions") version "0.54.0"
    alias(libs.plugins.ben.manes.versions)

    // https://discuss.gradle.org/t/unable-to-resolve-class-when-compiling-jenkins-groovy-script/28153/11
    // https://github.com/mkobit/jenkins-pipeline-shared-libraries-gradle-plugin/issues/65
    //id ("org.jenkins-ci.jpi") version "0.51.0" apply false
    //id ("org.jetbrains.kotlin.jvm") version "1.6.10-RC"
    //id ("org.jetbrains.kotlin.jvm") version "2.1.0-Beta1"

    //kotlin("jvm") version "2.3.21"
    //kotlin("jvm") version libs.versions.kotlin
    alias(libs.plugins.kotlin.jvm)

    // https://github.com/JetBrains/gradle-idea-ext-plugin
    //id ("org.jetbrains.gradle.plugin.idea-ext") version "1.1.9" apply true
    //id ("org.jetbrains.gradle.plugin.idea-ext") version "1.4.1" apply true
    alias(libs.plugins.idea.ext)
}

/*
// Disabled when this is treated as a child project included into "jsl-site-config"
// and not really needed after recipe migration to new mkobit library release
rewrite {
	activeRecipe("com.mkobit.jenkins.pipelines.MigrateSharedLibraryPlugin010To011")
}
*/

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

// https://github.com/mkobit/jenkins-pipeline-shared-library-example/blob/c37690649d10aa7cabdd534062bde5a5560ce852/build.gradle.kts
val scriptsSourceSet =
    sourceSets.create("scripts") {
        groovy.setSrcDirs(listOf("scripts"))
        java.setSrcDirs(emptyList<String>())
        resources.setSrcDirs(emptyList<String>())
    }
tasks.named("compileScriptsGroovy") { enabled = false }

val mainCompileOnly = configurations.named(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME)
val mainImplementation = configurations.named(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME)
configurations.named(scriptsSourceSet.compileOnlyConfigurationName) {
    extendsFrom(mainCompileOnly, mainImplementation)
}

tasks.named("codenarcIntegrationTest") { enabled = false }
tasks.named("codenarcJenkinsMain") { enabled = false }
tasks.named("codenarcMain") { enabled = false }

/*
// https://github.com/mkobit/jenkins-pipeline-shared-library-example/blob/c37690649d10aa7cabdd534062bde5a5560ce852/build.gradle.kts
codenarc {
  toolVersion = libs.versions.codenarc.get()
  configFile = file("config/codenarc/codenarc-src.xml")
  reportFormat = "text"
  sourceSets = sourceSets + listOf(scriptsSourceSet)
}
*/

/*
val commitSha: String by lazy {
    ByteArrayOutputStream().use {
        project.exec {
            commandLine("git", "rev-parse", "HEAD")
            standardOutput = it
        }
        it.toString(Charsets.UTF_8.name()).trim()
    }
}
*/

/*
buildScan {
    termsOfServiceAgree = "yes"
    termsOfServiceUrl = "https://gradle.com/terms-of-service"
    //link("GitHub", "https://github.com/mkobit/jenkins-pipeline-shared-library-example")
    value("Revision", commitSha)
}
*/

dependencies {
    components {
        all<JenkinsHpiVariantFix>()
    }
    implementation(libs.jakarta.servlet.api)

/*
    // NOTE: https://stackoverflow.com/questions/65731542/why-is-there-no-kotlin-stdlib-jdk11
    implementation ("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    // FIXME: relocated to JAKARTA.servlet-api
    implementation ("javax.servlet:javax.servlet-api:4.0.1")
    testImplementation ("javax.servlet:javax.servlet-api:4.0.1")

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

    // NOTE: https://stackoverflow.com/questions/52502189/java-11-package-javax-xml-bind-does-not-exist
    //testImplementation ("javax.xml.bind:javaxb-api")
    // Avoid java.lang.NoClassDefFoundError: com/sun/activation/registries/LogSupport
    // https://github.com/jakartaee/mail-api/issues/627
    testImplementation ("javax.activation:activation:1.1.1")
    // Alternatively: https://github.com/jakartaee/jaf-api/issues/60
    //testImplementation ("com.sun.activation:jakarta.activation:2.0.0")
    testImplementation ("jakarta.xml.bind:jakarta.xml.bind-api")
    testImplementation ("com.sun.xml.bind:jaxb-impl")

    testImplementation ("org.junit.jupiter:junit-jupiter-api:5.11.2")
    testRuntimeOnly ("org.junit.jupiter:junit-jupiter-engine:5.11.2")
 */

/*
    //testImplementation ("com.mkobit.jenkins.pipelines:jenkins-pipeline-shared-libraries-gradle-plugin:0.11.0")
    jenkinsPlugin("org.jenkinsci.plugins:pipeline-model-definition")
 */

/*
    // https://mvnrepository.com/artifact/com.cloudbees/groovy-cps
    implementation ("com.cloudbees:groovy-cps:3964.v0767b_4b_a_0b_fa_")
    testImplementation ("com.cloudbees:groovy-cps:3964.v0767b_4b_a_0b_fa_")

    // 3.3.3 is currently latest but buggy, some components
    // refer to snapshot versions inconsistently
    implementation ("org.eclipse.hudson:hudson-core:3.2.1")
    testImplementation ("org.eclipse.hudson:hudson-core:3.2.1")

    // NOTE: Need a version with https://github.com/jenkinsci/http-request-plugin/pull/120 code in it!
    // https://mvnrepository.com/artifact/org.jenkins-ci.plugins/http_request (older releases)
    //implementation ("org.jenkins-ci.plugins:http_request:1.8.27")
    //testImplementation ("org.jenkins-ci.plugins:http_request:1.8.27")
    // https://repo.jenkins-ci.org/incrementals/org/jenkins-ci/plugins/http_request/1.17-rc492.f4a_b_5b_1a_43c3/
    implementation ("org.jenkins-ci.plugins:http_request:1.19")
    testImplementation ("org.jenkins-ci.plugins:http_request:1.19")

    implementation ("org.jenkins-ci.main:jenkins-test-harness:2289.vfd344a_6d1660")
    testRuntimeOnly ("org.jenkins-ci.plugins:matrix-project:831.v084e85a_b_4ea_d")
    testRuntimeOnly ("org.jenkins-ci.plugins.workflow:workflow-step-api:678.v3ee58b_469476")
    // https://mvnrepository.com/artifact/org.jenkins-ci.plugins/pipeline-utility-steps
    testRuntimeOnly("org.jenkins-ci.plugins:pipeline-utility-steps:2.16.2")
    // Avoid   40.920 [id=44]	SEVERE	jenkins.InitReactorRunner$1#onTaskFailed:
    //   Failed Loading plugin Pipeline Utility Steps v2.13.0 (pipeline-utility-steps)
    //   java.io.IOException: Failed to load: Pipeline Utility Steps (pipeline-utility-steps 2.13.0)
    //   - Update required: Pipeline: Groovy (workflow-cps 2.72) to be updated to 2660.vb_c0412dc4e6d or higher
    testRuntimeOnly("org.jenkins-ci.plugins.workflow:workflow-cps:3961.ve48ee2c44a_b_3")
    testImplementation ("com.cloudbees:groovy-cps:3624.v43b_a_38b_62b_b_7")
    implementation ("org.jenkins-ci.main:remoting:3131.vf2b_b_798b_ce99")
    implementation ("org.jenkins-ci.plugins:git:5.1.0")
    implementation ("org.jenkins-ci.plugins:github-branch-source:1701.v00cc8184df93")
    testImplementation("org.assertj:assertj-core:3.12.2")
*/
/*
  val spock = "org.spockframework:spock-core:1.2-groovy-2.4"
  testImplementation(spock)
  testImplementation("org.assertj:assertj-core:3.12.2")
  integrationTestImplementation(spock)
*/
}

// Jenkins test harness (UnitTestSupportingPluginManager.loadBundledPlugins) discovers plugins via
// ClassLoader.getResource("/test-dependencies/index"), not by scanning individual HPI classpath
// entries (that was the old URLClassLoader approach which broke on Java 9+).  The index file lists
// plugin short names one per line; for each name the harness constructs the URL
//   new URL(indexUrl, "<shortName>.jpi")
// and copies the plugin archive to JENKINS_HOME/plugins/.  This is the same convention that the
// Maven JPI plugin creates under target/test-classes/test-dependencies/.
val prepareJenkinsTestDeps by tasks.registering {
    val jthResDir = layout.buildDirectory.dir("jth-test-resources")

    // Use a Provider<FileCollection> so doLast never captures a NamedDomainObjectProvider —
    // capturing it caused a config-cache deserialization type-mismatch on the second run.
    inputs.files(
        configurations.named("jenkinsPluginHpis").map { cfg ->
            cfg.incoming.artifactView { isLenient = true }.artifacts.artifactFiles
        }
    )
    outputs.dir(jthResDir)

    doLast {
        val testDepsDir = jthResDir.get().dir("test-dependencies").asFile
        testDepsDir.mkdirs()

        val names = mutableListOf<String>()
        inputs.files
            .filter { it.name.endsWith(".hpi") || it.name.endsWith(".jpi") }
            .forEach { file ->
                // Jenkins plugin version strings always start with a digit followed by a dot
                // (e.g. -1.5 or -4.5.14-...), so strip from the first such suffix.
                // This correctly handles names that contain digits, e.g.
                //   apache-httpcomponents-client-4-api-4.5.14-....hpi → apache-httpcomponents-client-4-api
                val shortName = file.nameWithoutExtension.replace(Regex("-\\d+\\..*"), "")
                    .ifEmpty { file.nameWithoutExtension }
                file.copyTo(File(testDepsDir, "$shortName.jpi"), overwrite = true)
                names.add(shortName)
            }

        File(testDepsDir, "index").writeText(names.sorted().joinToString("\n"))
        logger.lifecycle("prepareJenkinsTestDeps: wrote ${names.size} entries to test-dependencies/index")
    }
}

tasks.named<Test>("integrationTest").configure {
    dependsOn(prepareJenkinsTestDeps)
    classpath += files(layout.buildDirectory.dir("jth-test-resources"))
}

/*
jenkinsIntegration {
    // For builds with IntelliJ IDEA, may have to specify location of WAR
    // if "Could not initialize class org.jvnet.hudson.test.WarExploder"
    // and it says jenkins-core "is not in the expected location":
    //   -Djth.jenkins-war.path="C:\Users\klimov\.m2\repository\org\jenkins-ci\main\jenkins-war\2.387.1\jenkins-war-2.387.1.war"
    // Hope for a predictable port we can keep open in a browser:
    baseUrl.set(uri("http://localhost:5050").toURL())
    authentication.set(providers.provider { AnonymousAuthentication })
    //downloadDirectory.set(layout.projectDirectory.dir("jenkinsResources"))
    downloadDirectory.set(layout.projectDirectory.dir("resources"))
}
*/

// Hack the separated test/mocklib sources into the test sources
// seen by JenkinsRule as if they are part of JSL itself (the "src"
// registration setup above helps for unit testing of pure groovy,
// not of Jenkins integration which needs to load TestFarmNodeData,
// and that it is easiest if it were part of production JSL -- or
// makes-believe so at testing run time).
val mocklibBaseDir = file("test/mocklib")
val mocklibSrc = file("${mocklibBaseDir}/src")
val mocklibVars = file("${mocklibBaseDir}/vars")
val mocklibResources = file("${mocklibBaseDir}/resources")
val syncMocklibSources by tasks.registering(Copy::class) {
    if (mocklibSrc.isDirectory) {
        from(mocklibSrc) {
            into("src")
            include("**/*.groovy")
        }
    }

    // NOTE: No steps in mocklib so far, but just in case for future overrides:
    if (mocklibVars.isDirectory) {
        from(mocklibVars) {
            into("vars")
            include("**/*.groovy")
        }
    }

    /*  // No test-specific resources there yet, so commented away
        // to avoid potential overwrite of boilerplate-dictated files
        if (mocklibResources.isDirectory) {
            from(mocklibResources) {
                into("resources")
            }
        }
    */

    into(layout.buildDirectory.dir("sharedLibrarySource/jenkins-dynamatrix"))
}

tasks.named("syncSharedLibrarySource").configure {
    // The mkobit plugin may have some logic of its own about this setup,
    // so as OUR finishing touch make sure the mocklib groovy files are
    // where we want them for the JenkinsRule integration testbeds.
    // NOTE: The plugin defines this named task.
    // TOTHINK: Revise with migration to mkobit 0.12.0 or later,
    //  there is a GitHub issue that promises to solve such situations
    //  out of the box.
    finalizedBy(syncMocklibSources)
}

// https://github.com/mkobit/jenkins-pipeline-shared-libraries-gradle-plugin#changing-the-jenkins-lts-line
sharedLibrary {
/*
    jenkins {
        // TODO: this will need to be altered when auto-mapping functionality is complete
        //coreVersion.set(jenkinsIntegration.downloadDirectory.file("core-version.txt").map { it.asFile.readText().trim() })
        //coreVersion.set("2.303")
        // TODO: retrieve downloaded plugin resource

        //version.set(jenkinsIntegration.downloadDirectory.file("core-version.txt").map { it.asFile.readText().trim() })
        //version.set("2.303")
        //bomVersion.set(jenkinsIntegration.downloadDirectory.file("bom-version.txt").map { it.asFile.readText().trim() })
        version = "2.528.3"                         // Jenkins core version (default: 2.479.1)
        bomVersion = "6398.v1d26a_dd495e2"          // BOM auto-injected into jenkinsPlugin
    }

    pipelineUnitVersion = "1.29"                    // JenkinsPipelineUnit version (test suite)
    libraryName = "my-shared-lib"                   // Jenkins library name (default: project.name)
    autoRegisterLibrary = true                      // generate SharedLibraryAutoRegistrar (default: true)

    // OLD style examples:
    //testHarnessVersion = "2.24" // Removed in 0.11.0 - now managed by the Jenkins BOM; to override, add implementation("org.jenkins-ci.main:jenkins-test-harness:VERSION") in the suite's `dependencies` block
    pluginDependencies {
        workflowCpsGlobalLibraryPluginVersion = "2.8"
        dependency("io.jenkins.blueocean", "blueocean-web", "1.2.4")
    }
 */

    jenkins {
        version = "2.541.3"                         // Jenkins core version (default: 2.479.1)
        bomVersion = "6398.v1d26a_dd495e2"          // BOM auto-injected into jenkinsPlugin
    }

    libraryName = "jenkins-dynamatrix"	        	// For @Library calls in test pipelines

    // Default list of plugins and their versions defined in gradle/jenkins.versions.toml
    // TODO: retrieve downloaded plugin resource from OUR production Jenkins
    //  or some JCasC settings later; see resources/export-jenkins-catalog.groovy
    plugins {
        plugins(jenkinsPlugins.bundles.allPlugins)
    }

/*
    // OLD list, for reference for now:
    pluginDependencies {
        dependency("org.jenkins-ci.plugins", "pipeline-build-step", "540.vb_e8849e1a_b_d8")
        dependency("org.6wind.jenkins", "lockable-resources", "1315.v4ea_8e5159ec8")
        dependency("org.jenkins-ci.plugins", "badge", "2.2")
        dependency("sp.sd", "nexus-artifact-uploader", "2.14")

        val declarativePluginsVersion = "2.2214.vb_b_34b_2ea_9b_83"
        dependency("org.jenkinsci.plugins", "pipeline-model-api", declarativePluginsVersion)
        dependency("org.jenkins-ci.plugins.workflow", "workflow-step-api", "678.v3ee58b_469476")
        dependency("org.jenkins-ci.plugins.workflow", "workflow-cps", "3961.ve48ee2c44a_b_3")
        dependency("org.jenkins-ci.plugins.workflow", "workflow-durable-task-step", "1464.v2d3f5c68f84c")
        dependency("org.jenkinsci.plugins", "pipeline-model-declarative-agent", "1.1.1")
        dependency("org.jenkinsci.plugins", "pipeline-model-definition", declarativePluginsVersion)
        dependency("org.jenkinsci.plugins", "pipeline-model-extensions", declarativePluginsVersion)
        // Jenkins Server startup in tests throws long noisy stack traces without these:
        dependency("org.jenkins-ci.plugins", "git", "5.7.0")
        dependency("org.jenkins-ci.plugins", "git-client", "4.7.0")
        dependency("org.jenkins-ci.plugins", "git-server", "126.v0d945d8d2b_39")
        dependency("org.jenkins-ci.plugins", "ssh-agent", "386.v36cc0c7582f0")
        dependency("org.jenkins-ci.modules", "sshd", "3.322.v159e91f6a_550")
        dependency("org.jenkins-ci.plugins", "http_request", "1.19")

        dependency("org.jenkins-ci.plugins", "matrix-project", "831.v084e85a_b_4ea_d")
        dependency("org.jenkins-ci.plugins", "pipeline-utility-steps", "2.16.2")

        dependency("org.jenkins-ci.plugins", "git", "5.1.0")
        dependency("org.jenkins-ci.plugins", "github-branch-source", "1701.v00cc8184df93")
        dependency("org.jenkins-ci.main", "remoting", "3131.vf2b_b_798b_ce99")

        dependency("javax.activation", "activation", "1.1.1")
        //dependency("com.sun.activation", "jakarta.activation", "2.0.0")

        // https://mvnrepository.com/artifact/org.jenkins-ci.main/jenkins-test-harness
        dependency ("org.jenkins-ci.main", "jenkins-test-harness", "2207.v3b_df04c801d4" /* "2289.vfd344a_6d1660" too new */)

        // Make logs of test Jenkins instances a bit more readable if visited interactively
        dependency("org.jenkins-ci.plugins", "antisamy-markup-formatter", "173.v680e3a_b_69ff3")
        dependency("org.jenkins-ci.plugins", "ansicolor", "1.0.6")
        dependency("org.jenkins-ci.plugins", "timestamper", "1.29")
    }
 */
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

/*
// https://github.com/mkobit/jenkins-pipeline-shared-library-example/blob/c37690649d10aa7cabdd534062bde5a5560ce852/build.gradle.kts
tasks.named<CodeNarc>("codenarcScripts") {
  config = resources.text.fromFile("config/codenarc/codenarc-scripts.xml")
}
*/

/*
spotless {
  groovy {
    greclipse().configFile("config/greclipse.properties")
    target("src/**/*.groovy", "vars/**/*.groovy", "test/**/*.groovy", "scripts/**/*.groovy")
  }
  java {
    googleJavaFormat()
    target("test/**/*.java")
  }
  kotlin {
    ktlint()
    target("test/**/*.kt")
  }
  kotlinGradle {
    ktlint()
    target("*.gradle.kts")
  }
  toml {
    versionCatalog()
    target("gradle/libs.versions.toml")
  }
}
*/

// https://github.com/mkobit/jenkins-pipeline-shared-library-example/blob/c37690649d10aa7cabdd534062bde5a5560ce852/build.gradle.kts
val kotestParallelism =
    providers
        .gradleProperty("kotest.parallelism")
        .map { it.toInt() }
        .orElse(Runtime.getRuntime().availableProcessors())

testing {
    suites {
        named<JvmTestSuite>("test") {
            useJUnitJupiter(libs.versions.junit.jupiter)
            sources {
                java.srcDirs("test/unit/java")
                groovy.srcDirs("test/unit/groovy", "test/mocklib")
                kotlin.srcDirs("test/unit/kotlin")
            }
            dependencies {
                implementation(libs.spock.core)
                implementation(libs.assertj)
                implementation(libs.kotest.engine)
                runtimeOnly(libs.kotest.runner)
                implementation(libs.kotest.assertions)
                implementation(libs.kotest.decoroutinator)
                implementation(libs.jenkins.pipeline.unit)
            }
        }
        // Configure the mkobit-created default suite to match integrationTestJunit:
        // useJUnitPlatform() + harness so our JUnit 4/5 @Rule tests run properly.
        named<JvmTestSuite>("integrationTest") {
            dependencies {
                // compileOnly so IDEA sees Groovy and configures the Groovy SDK for this module;
                // the task-level groovyClasspath override is invisible during Gradle sync.
                compileOnly(libs.spock.core)
                implementation(libs.junit.jupiter.api)
                implementation(libs.jenkins.test.harness)
                runtimeOnly(libs.junit.jupiter.engine)
                runtimeOnly(libs.junit.platform.launcher)
            }
            targets.all {
                testTask.configure {
                    useJUnitPlatform()
                }
            }
        }
    }
}

val integrationTestJunit =
    testing.suites.register<JvmTestSuite>("integrationTestJunit") {
        sharedLibrary.withJenkins(this)
        sources {
            java.setSrcDirs(listOf("test/integration-junit/java"))
            groovy.setSrcDirs(listOf("test/integration/groovy", "test/mocklib/src"))
        }
        dependencies {
            // TestFarmNodeData in test/mocklib (under src/ as JSL classes go)
            // depends on NodeCaps/NodeData from the main library, so we
            // need the main source output on both compile and runtime classpaths.
            implementation(sourceSets.named("main").get().output)
            // Borrowing Groovy classpath from Spock, which must have it:
            compileOnly(libs.spock.core)
            implementation(libs.junit.jupiter.api)
            runtimeOnly(libs.junit.jupiter.engine)
            runtimeOnly(libs.junit.platform.launcher)

            // ADD THESE to support JUnit 4 tests for now:
            // runtimeOnly(libs.junit.vintage.engine)

            // groovy-all:2.4.21 is the fat JAR (all Groovy modules in one archive).
            // Using the fat jar avoids the Java 9+ BuiltinClassLoader split-package
            // problem: having groovy.jar + groovy-swing.jar on the classpath causes
            // the classloader to cache org.codehaus.groovy.runtime to groovy.jar and
            // then refuse to search groovy-swing.jar for SwingGroovyMethods/Groovysh.
            // It also makes Groovy visible to IDEA for SDK detection (implementation
            // is compile+runtime, whereas runtimeOnly is invisible to IDEA sync).
            implementation(libs.groovy.all)
            //runtimeOnly("org.jenkins-ci.main:jenkins-core:${sharedLibrary.jenkins.version}")
            runtimeOnly("commons-discovery:commons-discovery:0.5")
            runtimeOnly("org.slf4j:slf4j-simple:2.0.16")
            runtimeOnly(jenkinsPlugins.bouncycastle.api)
            runtimeOnly(jenkinsPlugins.display.url.api)
            runtimeOnly(jenkinsPlugins.scm.api)
            runtimeOnly(jenkinsPlugins.workflow.step.api)
            runtimeOnly(jenkinsPlugins.workflow.support)
            runtimeOnly(jenkinsPlugins.workflow.api)
            runtimeOnly(jenkinsPlugins.workflow.basic.steps)
            runtimeOnly(jenkinsPlugins.workflow.cps)
            runtimeOnly(jenkinsPlugins.workflow.job)
            runtimeOnly(jenkinsPlugins.workflow.durable.task.step)
            runtimeOnly(jenkinsPlugins.durable.task)
            runtimeOnly(jenkinsPlugins.workflow.scm.step)
            runtimeOnly(jenkinsPlugins.junit)
            runtimeOnly(jenkinsPlugins.matrix.project)
            runtimeOnly(jenkinsPlugins.branch.api)
            runtimeOnly(jenkinsPlugins.cloudbees.folder)
            runtimeOnly(jenkinsPlugins.structs)
            runtimeOnly(jenkinsPlugins.script.security)
            runtimeOnly(jenkinsPlugins.pipeline.model.definition)
            runtimeOnly(jenkinsPlugins.credentials)
        }
        targets.all {
            testTask.configure {
                useJUnitPlatform()
                dependsOn(prepareJenkinsTestDeps, syncMocklibSources)
                classpath += files(layout.buildDirectory.dir("jth-test-resources"))
            }
        }
    }

val integrationTestSpock =
    testing.suites.register<JvmTestSuite>("integrationTestSpock") {
        sharedLibrary.withJenkins(this)
        sources {
            groovy.setSrcDirs(listOf("test/integration-spock/groovy"))
        }
        dependencies {
            implementation(libs.spock.core)
        }
        targets.all {
            testTask.configure {
                dependsOn(prepareJenkinsTestDeps)
                classpath += files(layout.buildDirectory.dir("jth-test-resources"))
            }
        }
    }

val integrationTestKotest =
    testing.suites.register<JvmTestSuite>("integrationTestKotest") {
        sharedLibrary.withJenkins(this)
        useJUnitJupiter(libs.versions.junit.jupiter)
        sources {
            kotlin.setSrcDirs(listOf("test/integration-kotest/kotlin"))
        }
        dependencies {
            implementation(libs.kotest.engine)
            runtimeOnly(libs.kotest.runner)
            implementation(libs.kotest.assertions)
            implementation(libs.kotest.decoroutinator)
            implementation(libs.coroutines.core)
        }
        targets.all {
            testTask.configure {
                systemProperty("kotest.framework.parallelism", kotestParallelism)
                dependsOn(prepareJenkinsTestDeps)
                classpath += files(layout.buildDirectory.dir("jth-test-resources"))
            }
        }
    }

// https://github.com/JetBrains/gradle-idea-ext-plugin/issues/114
fun org.gradle.plugins.ide.idea.model.IdeaModule.settings(configure: org.jetbrains.gradle.ext.ModuleSettings.() -> Unit) =
    (this as ExtensionAware).configure(configure)

val org.jetbrains.gradle.ext.ModuleSettings.packagePrefix: org.jetbrains.gradle.ext.PackagePrefixContainer
    get() = (this as ExtensionAware).the()

idea {
    module {
        settings {
            packagePrefix["src"]  = "org.nut.dynamatrix"
            packagePrefix["vars"] = "org.nut.dynamatrix"
        }

        // Ensure IDEA recognizes Groovy
        isDownloadSources = true
        isDownloadJavadoc = true
    }
}

// https://github.com/mkobit/jenkins-pipeline-shared-libraries-gradle-plugin/issues/69
tasks {
    wrapper {
        gradleVersion = "9.4.1"
        distributionType = Wrapper.DistributionType.ALL
    }

    withType<AbstractTestTask>().configureEach {
        failOnNoDiscoveredTests = false
    }

    withType<Test>().configureEach {
        systemProperty("kotest.framework.config.fqn", "testsupport.kotest.ProjectConfig")
        maxParallelForks = 1
        testLogging {
            events("failed", "skipped")
            showExceptions = true
            showCauses = true
            showStackTraces = true
            exceptionFormat = TestExceptionFormat.FULL
            stackTraceFilters = setOf(TestStackTraceFilter.TRUNCATE)
        }
    }

    named<GroovyCompile>("compileIntegrationTestGroovy") {
        groovyClasspath = configurations.getByName("integrationTestSpockCompileClasspath")
    }

    named<GroovyCompile>("compileIntegrationTestJunitGroovy") {
        // Borrowing Groovy classpath from Spock, which must have it:
        groovyClasspath = configurations.getByName("integrationTestSpockCompileClasspath")
    }

    named<GroovyCompile>("compileIntegrationTestSpockGroovy") {
        groovyClasspath = configurations.getByName("integrationTestSpockCompileClasspath")
    }

    check {
        dependsOn(integrationTestJunit, integrationTestSpock, integrationTestKotest)
    }

    // No unit tests exist yet (test/unit/ is absent), so wire the standard `test` task to
    // trigger all integration suites. finalizedBy avoids a cycle with mkobit's
    // mustRunAfter(tasks.test) constraint set on every withJenkins() suite.
    named<Test>("test") {
        finalizedBy(integrationTestJunit, integrationTestSpock, integrationTestKotest)
    }
}

/*
spotless {
  groovy {
    greclipse().configFile("config/greclipse.properties")
    target("src/**/*.groovy", "vars/**/*.groovy", "test/**/*.groovy", "scripts/**/*.groovy")
  }
  java {
    googleJavaFormat()
    target("test/**/*.java")
  }
  kotlin {
    ktlint()
    target("test/**/*.kt")
  }
  kotlinGradle {
    ktlint()
    target("*.gradle.kts")
  }
  toml {
    versionCatalog()
    target("gradle/libs.versions.toml")
  }
}
*/

tasks.named("codenarcIntegrationTestJunit") { enabled = false }
tasks.named("codenarcIntegrationTestSpock") { enabled = false }
