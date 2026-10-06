package fr.daliush.shardbound.api;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * The layers, checked at every build: controller → domain ← adapter → dao. The domain owns its ports and depends
 * on no other layer; the controller never reaches the data layer.
 */
@AnalyzeClasses(packages = "fr.daliush.shardbound.api", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule layers = layeredArchitecture().consideringOnlyDependenciesInLayers()
            .layer("Controller").definedBy("..api.controller..")
            .layer("Domain").definedBy("..api.domain..")
            .layer("Adapter").definedBy("..api.adapter..")
            .layer("Dao").definedBy("..api.dao..")
            .layer("Config").definedBy("..api.config..")
            .whereLayer("Controller").mayOnlyBeAccessedByLayers("Config")
            .whereLayer("Domain").mayOnlyBeAccessedByLayers("Controller", "Adapter", "Config")
            .whereLayer("Adapter").mayNotBeAccessedByAnyLayer()
            .whereLayer("Dao").mayOnlyBeAccessedByLayers("Adapter", "Config");

    @ArchTest
    static final ArchRule domainKnowsNoTransportNorJson = noClasses().that().resideInAPackage("..api.domain..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework.web..", "jakarta.servlet..",
                    "tools.jackson..", "com.fasterxml.jackson..");
}
