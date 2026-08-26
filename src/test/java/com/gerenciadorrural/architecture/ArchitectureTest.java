package com.gerenciadorrural.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

class ArchitectureTest {

    private static JavaClasses productionClasses;

    @BeforeAll
    static void importProductionClasses() {
        productionClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.gerenciadorrural");
    }

    @Test
    void domainMustNotDependOnApplicationOrTechnicalLayers() {
        noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..application..", "..infrastructure..", "..api..", "..observability..")
                .check(productionClasses);
    }

    @Test
    void applicationMustNotDependOnApiOrInfrastructure() {
        noClasses().that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAnyPackage("..api..", "..infrastructure..")
                .check(productionClasses);
    }

    @Test
    void commandAndQueryHandlersMustFollowTheNamingConvention() {
        classes().that().resideInAnyPackage("..application.command..", "..application.query..")
                .and().haveSimpleNameEndingWith("Handler")
                .should().beInterfaces()
                .check(productionClasses);
    }

    @Test
    void packagesMustNotHaveCycles() {
        slices().matching("com.gerenciadorrural.(*)..")
                .should().beFreeOfCycles()
                .check(productionClasses);
    }
}
