# AutoBoost

As this is initial commit, things are a bit messy. They will be re-organized and updated later.  

## Environment used for development 

- OS: MacOS (Big Sur version 11.2.3)
- JDK: OpenJDK 1.8

## Folders 

### AutoBoost 

It stores the main project that is responsible for calculating the rank for each test and ranking the tests at the end. 

## JUnitRunner 

It stores the project for instrumenting the plausible patches, executing tests on them, and generate results regarding: 1. test outcome, 2. test coverage  

Scripts for execution will be added later. 

## resources 

It contains two sub-folder. 

#### 1. pre-AutoBoost 

It contains patches and fault localisation ranking for defect4j projects **BEFORE** AutoBoost is used. (i.e. using original test suites)

#### 2. post-AutoBoost 
It contains fault localisation ranking for defects4j projects **AFTER** AutoBoost is used. (i.e. Top X tests ranked by AutoBoost is added to test suite)