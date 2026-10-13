// Originally a comment in vars/dynamatrixPipeline.groovy source:

// For in-place tests as Replay pipeline:
@Library('jenkins-dynamatrix') _
import org.nut.dynamatrix.dynamatrixGlobalState;
import org.nut.dynamatrix.*;

    def dynacfgBase = [:]
    def dynacfgPipeline = [:]

    dynacfgPipeline['stylecheck'] = false //true
    dynacfgPipeline['spellcheck'] = false //true
    dynacfgPipeline['shellcheck'] = true
    dynacfgPipeline['NUT-shellcheck'] = [
        //'stageNameFunc': null,
        'single': '( \${MAKE} shellcheck )',
        'multi': '(cd tests && SHELL_PROGS="$SHELL_PROGS" ./nut-driver-enumerator-test.sh )',
        'multiLabel': 'SHELL_PROGS',
        'skipShells': [ 'zsh', 'tcsh', 'csh' ]
    ]

    dynacfgBase['commonLabelExpr'] = 'nut-builder'
    dynacfgBase['dynamatrixAxesLabels'] = //[~/^OS_.+/]
        ['OS_FAMILY', 'OS_DISTRO', '${COMPILER}VER', 'ARCH${ARCH_BITS}']

    // This closure can be used for slowBuild detailed below, if you just want
    // some same rituals to happen in all executed cases, to avoid repetitive
    // copy-pasting of a closure (or a named reference to one). The body can
    // use delegated dynamatrix variables such as "dsbc" and "stageName" as
    // well as exported envvars for the shell code (label values etc.), e.g.:
    //dynacfgPipeline.slowBuildDefaultBody = { delegate -> setDelegate(delegate)
    //  echo "Running default custom build for '${stageName}' ==> ${dsbc.toString()}"
    //  sh """ hostname; date -u; echo "\${MATRIX_TAG}"; set | sort -n """ }
    // or something like this for a realistic build
    //dynacfgPipeline.slowBuildDefaultBody = { delegate -> setDelegate(delegate)
    //    withEnvOptional(dynacfgPipeline.defaultTools) {
    //        unstashCleanSrc(dynacfgPipeline.get("stashnameSrc"))
    //        buildMatrixCellCI(dynacfgPipeline, dsbc)
    //    }
    //  }

    // Set of dynamatrix configuration selection filters and actual building
    // and/or testing closures to prepare some "slow build" matrix cells,
    // which may adhere or not to the same code pattern (body closure).
    dynacfgPipeline.slowBuild = [
      [name: 'Optional name to help in debug tracing',
       //disabled: true,
       getParStages: { dynamatrix, Closure body ->
            return dynamatrix.generateBuild([
                //commonLabelExpr: dynacfgBase.commonLabelExpr,
                //defaultDynamatrixConfig: dynacfgBase.defaultDynamatrixConfig,

                dynamatrixAxesVirtualLabelsMap: [
                    'BITS': [32, 64],
                    // 'CSTDVERSION': ['03', '2a'],
                    //'CSTDVERSION_${KEY}': [ ['c': '03', 'cxx': '03'], ['c': '99', 'cxx': '98'], ['c': '17', 'cxx': '2a'], 'ansi' ],
                    //'CSTDVERSION_${KEY}': [ ['c': '03', 'cxx': '03'], ['c': '99', 'cxx': '98'], ['c': '17', 'cxx': '2a'] ],
                    'CSTDVERSION_${KEY}': [ ['c': '99', 'cxx': '11'] ],
                    'CSTDVARIANT': ['gnu']
                    ],

                mergeMode: [ 'dynamatrixAxesVirtualLabelsMap': 'merge', 'excludeCombos': 'merge' ],
                allowedFailure: [ [~/CSTDVARIANT=c/] ],
                runAllowedFailure: true,
                //dynamatrixAxesLabels: [~/^OS_DISTRO/, '${COMPILER}VER', 'ARCH${ARCH_BITS}'],
                //dynamatrixAxesLabels: ['OS_FAMILY', 'OS_DISTRO', '${COMPILER}VER', 'ARCH${ARCH_BITS}'],
                //dynamatrixAxesLabels: [~/^OS/, '${COMPILER}VER', 'ARCH${ARCH_BITS}'],
                excludeCombos: [ [~/BITS=32/, ~/ARCH_BITS=64/], [~/BITS=64/, ~/ARCH_BITS=32/] ]
                ], body)
            }, // getParStages
        //branchRegexSource: ~/^PR-.+$/,
        //branchRegexTarget: ~/^(master|main|stable)$/,
        bodyParStages: null
        //bodyParStages: {}
      ] // one slowBuild filter configuration
    ]

    dynacfgPipeline.bodyStashCmd = { git (url: "/home/abuild/nut-DMF", branch: "fightwarn") }

