# Fault Localization

FL rankings calculated by GZoltar v1.7.2 (latest release).

This folder store Fault localization results of all projects in 2 scenarios: 
1. Before adding new tests (Directly copied from defects4j)
2. Adding tests selected from running AutoBoost on another buggy version 

The top-level folder (under this level) would be named by the buggy version where tests are selected from (e.g. *[./original](./original)* stores Fault Localization results of all buggy versions *without adding tests*, [./Math_95](./Math_95) stores Fault localization of adding tests selected by ranking tests generated for project *Math*, bug version *95*). The second-level folder name represents the test undertaking, a control test is one that just add all tests generated for the buggy version too all buggy versions . 
