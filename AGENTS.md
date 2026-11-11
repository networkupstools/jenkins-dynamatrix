Instructions for AI agents and other contributors:

* Prefer to use ASCII text, e.g. simple minus `-` or double minus `--`
  instead of n-dash and m-dash)
* Do not remove code comments, especially descriptions of intentions
  and problems being solved, links to external sites, or TODO/TOTHINK
  notes, unless asked to or they became obsolete during refactoring.
  Prefer to keep comments up to date with existing implementation.
* Update `README.adoc` regarding consumer-facing changes (what impacts
  pipeline authors)
* Do not modify existing test expectations lightly, prefer to double
  check and/or ask the developer. Assertions and reference resource
  files reflect previously expected behaviors, changes to them can
  cause regressions that they were supposed to catch in the first place.
* Do not remove `import` from Java/Groovy sources, including tests.
  They may be from nominally the same package, but still IDE may have
  issues with code lookup without that hint.

Reference files for pipeline summary tests
-------------------------------------------

The files in `test/resources/` with names matching the pattern
`ci.*Stages-list*.txt` (e.g., `ci.slowBuildStages-list-autotools.txt`,
`ci.spellcheckStages-list-legacy.txt`, `ci.shellcheckStages-list-closure.txt`,
`ci.shellcheckStages-list-legacy.txt`, `ci.shellcheckStages-list-closure.txt`,
`ci.quickcheckStages-list.txt`) are reference outputs used by
`DynamatrixPipelineTest` to detect regressions in generated dynamatrix
summaries.

These files are maintained by developers. When a test run produces a
different summary than the reference copy, it indicates a regression
in the pipeline generation logic -- investigate before updating the
reference file. AI should not modify these reference files unless
explicitly asked to do so. If a new test scenario is added, create the
corresponding reference file in `test/resources/` with the expected
output; the test will then compare against it on subsequent runs.

