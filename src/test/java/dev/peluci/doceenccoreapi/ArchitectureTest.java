package dev.peluci.doceenccoreapi;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.repository.Repository;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Architecture rules. Features that do not exist yet (category, ingredient, recipe, product,
 * settings) are already listed, so the dependency direction is enforced from the first class
 * of each one. Those rules use allowEmptyShould because the packages are still empty.
 * <p>
 * Plain JUnit + ArchUnit core (not archunit-junit5), so it does not depend on the ArchUnit
 * test engine being compatible with the JUnit version managed by Spring Boot.
 */
public class ArchitectureTest {

    private static final String ROOT = "dev.peluci.doceenccoreapi";

    private static JavaClasses mainClasses;

    @BeforeAll
    static void importMainClasses() {
        mainClasses = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages(ROOT);
    }

    private static String pkg(String feature) {
        return ROOT + "." + feature + "..";
    }

    @Test
    void sharedDesNotDependOnAnyFeature() {
        ArchRule rule = noClasses().that().resideInAPackage(pkg("shared"))
                .should().dependOnClassesThat().resideInAnyPackage(
                        pkg("auth"), pkg("category"), pkg("ingredient"),
                        pkg("recipe"), pkg("product"), pkg("settings"));
        rule.allowEmptyShould(true).check(mainClasses);
    }

    @Test
    void authDoesNotDependOnOtherFeatures() {
        ArchRule rule = noClasses().that().resideInAPackage(pkg("auth"))
                .should().dependOnClassesThat().resideInAnyPackage(
                        pkg("category"), pkg("ingredient"), pkg("recipe"), pkg("product"), pkg("settings"));
        rule.allowEmptyShould(true).check(mainClasses);
    }

    @Test
    void categoryDoesNotDependOnIngredientReceiptOrProduct() {
        ArchRule rule = noClasses().that().resideInAPackage(pkg("category"))
                .should().dependOnClassesThat().resideInAnyPackage(
                        pkg("ingredient"), pkg("recipe"), pkg("product"));
        rule.allowEmptyShould(true).check(mainClasses);
    }

    @Test
    void ingredientDoesNotDependOnRecipeOrProduct() {
        ArchRule rule = noClasses().that().resideInAPackage(pkg("ingredient"))
                .should().dependOnClassesThat().resideInAnyPackage(pkg("recipe"), pkg("product"));
        rule.allowEmptyShould(true).check(mainClasses);
    }

    @Test
    void recipeDoesNotDependOnProduct() {
        ArchRule rule = noClasses().that().resideInAPackage(pkg("recipe"))
                .should().dependOnClassesThat().resideInAPackage(pkg("product"));
        rule.allowEmptyShould(true).check(mainClasses);
    }

    @Test
    void settingsDoesNotDependOnIngredientRecipeOrProduct() {
        ArchRule rule = noClasses().that().resideInAPackage(pkg("settings"))
                .should().dependOnClassesThat().resideInAnyPackage(
                        pkg("ingredient"), pkg("recipe"), pkg("product"));
        rule.allowEmptyShould(true).check(mainClasses);
    }

    @Test
    void featuresAreFreeOfCycle() {
        slices().matching(ROOT + ".(*)..").should().beFreeOfCycles().check(mainClasses);
    }

    @Test
    void controllerDoNotAccessRepositoriesDirectly() {
        noClasses().that().areAnnotatedWith(RestController.class)
                .should().dependOnClassesThat().areAssignableTo(Repository.class)
                .check(mainClasses);
    }

    @Test
    void controllersDoNotExposeJpaEntities() {
        noClasses().that().areAnnotatedWith(RestController.class)
                .should().dependOnClassesThat().areAnnotatedWith(Entity.class)
                .check(mainClasses);
    }

    @Test
    void dependenciesAreInjectedThroughConstructors() {
        noFields().should().beAnnotatedWith(Autowired.class).check(mainClasses);
    }

    @Test
    void noFloatingPointFields() {
        noFields().should().haveRawType(double.class)
                .orShould().haveRawType(Double.class)
                .orShould().haveRawType(float.class)
                .orShould().haveRawType(Float.class)
                .because("money and quantities use BigDecimal with the scales defined in Scale")
                .check(mainClasses);
    }

    @Test
    void noStandardStreamsAndNoJavaUtilLogging() {
        NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS.check(mainClasses);
        NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING.check(mainClasses);
    }

}
