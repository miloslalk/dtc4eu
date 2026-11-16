package eu.dtc4eu;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static com.tngtech.archunit.library.freeze.FreezingArchRule.freeze;

/**
 * Architecture boundaries from CONSTRAINTS.md: the per-domain
 * controller → service → repository → entity layering of CLAUDE.md
 * ("Backend packages per domain"), checked across every domain package.
 * Plain Jupiter tests (not the ArchUnit engine) so surefire discovers them.
 *
 * Rules wrapped in {@code freeze} are ratchets: violations that existed when
 * they were adopted are recorded in src/test/resources/archunit_store and
 * tolerated; any new one fails. Fixed violations drop out of the store
 * automatically — never re-record the store to make a change pass.
 */
class ArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importMainClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
                .importPackages("eu.dtc4eu");
    }

    @Test
    void controllersDoNotUseRepositories() {
        noClasses().that().resideInAPackage("..controller..")
                .should().dependOnClassesThat().resideInAPackage("..repository..")
                .because("controllers go through a service, which owns scope checks and auditing")
                .check(classes);
    }

    @Test
    void servicesDoNotDependOnControllers() {
        freeze(noClasses().that().resideInAPackage("..service..")
                .should().dependOnClassesThat().resideInAPackage("..controller.."))
                .check(classes);
    }

    @Test
    void repositoriesStayBelowServices() {
        noClasses().that().resideInAPackage("..repository..")
                .should().dependOnClassesThat().resideInAnyPackage("..service..", "..controller..", "..dto..")
                .check(classes);
    }

    @Test
    void entitiesStayAtTheBottom() {
        noClasses().that().resideInAPackage("..entity..")
                .should().dependOnClassesThat().resideInAnyPackage("..service..", "..controller..", "..dto..")
                .check(classes);
    }

    @Test
    void noCyclesBetweenDomains() {
        freeze(slices().matching("eu.dtc4eu.(*)..").should().beFreeOfCycles()).check(classes);
    }
}
