package com.coinwin.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 프로덕션 코드가 6개 아키텍처 규칙을 지키는지 검사한다.
 *
 * <p>이 테스트가 초록이라는 것만으로는 규칙이 작동한다는 증거가 되지 않는다.
 * 규칙이 실제로 위반을 잡는지는 {@link ArchitectureRulesViolationTest} 가 증명한다.
 */
class ArchitectureRulesTest {

    private static final String ROOT = "com.coinwin";

    private static JavaClasses productionClasses;

    @BeforeAll
    static void importProductionClasses() {
        productionClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
                .importPackages(ROOT);
    }

    @Test
    void 규칙1_domain은_프레임워크에_의존하지_않는다() {
        ArchitectureRules.domainIsFrameworkFree().check(productionClasses);
    }

    @Test
    void 규칙2_계층_의존은_api에서_application을_거쳐_domain으로만_흐른다() {
        ArchitectureRules.layerDependenciesPointInward(ROOT).check(productionClasses);
    }

    @Test
    void 규칙3_패키지_순환_참조가_없다() {
        ArchitectureRules.noPackageCycles(ROOT).check(productionClasses);
    }

    @Test
    void 규칙4_market과_journal의_application은_adapter를_참조하지_않는다() {
        ArchitectureRules.hexagonalApplicationDoesNotSeeAdapters(ROOT).check(productionClasses);
    }

    @Test
    void 규칙5_backtest는_market_adapter를_참조하지_않는다() {
        ArchitectureRules.backtestConsumesPortsNotAdapters(ROOT).check(productionClasses);
    }

    @Test
    void 규칙6_adapter_out_구현체는_application_port_out_인터페이스를_구현한다() {
        ArchitectureRules.outboundAdaptersImplementPorts(ROOT).check(productionClasses);
    }
}
