[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.interceptor-manager.hooks&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.interceptor-manager.hooks)
[![Bugs](https://sonarcloud.io/api/project_badges/measure?project=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.interceptor-manager.hooks&metric=bugs)](https://sonarcloud.io/summary/new_code?id=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.interceptor-manager.hooks)
[![Code Smells](https://sonarcloud.io/api/project_badges/measure?project=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.interceptor-manager.hooks&metric=code_smells)](https://sonarcloud.io/summary/new_code?id=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.interceptor-manager.hooks)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.interceptor-manager.hooks&metric=coverage)](https://sonarcloud.io/summary/new_code?id=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.interceptor-manager.hooks)
[![Duplicated Lines (%)](https://sonarcloud.io/api/project_badges/measure?project=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.interceptor-manager.hooks&metric=duplicated_lines_density)](https://sonarcloud.io/summary/new_code?id=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.interceptor-manager.hooks)
[![Lines of Code](https://sonarcloud.io/api/project_badges/measure?project=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.interceptor-manager.hooks&metric=ncloc)](https://sonarcloud.io/summary/new_code?id=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.interceptor-manager.hooks)
[![Reliability Rating](https://sonarcloud.io/api/project_badges/measure?project=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.interceptor-manager.hooks&metric=reliability_rating)](https://sonarcloud.io/summary/new_code?id=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.interceptor-manager.hooks)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.interceptor-manager.hooks&metric=security_rating)](https://sonarcloud.io/summary/new_code?id=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.interceptor-manager.hooks)
[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.interceptor-manager.hooks&metric=sqale_rating)](https://sonarcloud.io/summary/new_code?id=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.interceptor-manager.hooks)
[![Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.interceptor-manager.hooks&metric=vulnerabilities)](https://sonarcloud.io/summary/new_code?id=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.interceptor-manager.hooks)

# Hooks for the ch.sbb.polarion.extension.interceptor-manager extension

Hooks in this repository provide possibility to modify and enhance some ALM Polarion actions.

## Build
Hooks can be produced using maven:
```
mvn clean package
```

## Hooks installation
Hooks jar files should be copied to `<polarion_home>/polarion/extensions/interceptor-manager/eclipse/plugins/hooks`
It can be done manually or automated using maven build:
```
mvn clean install -Pinstall-to-local-polarion
```
For automated installation with maven env variable `POLARION_HOME` should be defined and point to folder where Polarion is installed.
Note: after hooks installation they can be discovered without restart of Polarion by using 'Reload hooks list' on the 'Settings' page of the Interceptor-manager.

## Hooks list
This repo contains 4 hooks, each of them should be supposed to be used in conjunction with Interceptor-manager v3.0.0+.

### CheckSafetyHazardHook
Enforces business rules for safety hazard workitems by automatically managing field values, ensuring proper risk documentation compliance before allowing workitems to be saved.

### DeleteDummyWorkitemsHook
Prevents deletion of workitems based on specific conditions.

### InconsistentTestCaseBlockHook
Checks for consistency of Test Case Result and Test Step result(s).

### LiveDocBlockEditHook
Prevents document modification by enforcing specific business rules.
