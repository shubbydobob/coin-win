package com.coinwin.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
    void 규칙4_market과_journal과_ai의_application은_adapter를_참조하지_않는다() {
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

    /**
     * 여섯 규칙 중 어느 것도 빈 매칭을 허용하지 않는다.
     *
     * <p>{@code allowEmptyShould(true)} 는 대상 패키지가 아직 없는 규칙을 통과시키는 임시
     * 플래그였고, Phase 6 에서 마지막 하나(규칙 5)가 빠졌다. 플래그가 켜진 규칙은 <b>아무것도
     * 검사하지 않아도 초록</b>이므로, 새 규칙에 습관적으로 붙는 순간 규칙이 조용히 무력화된다.
     *
     * <p>ArchUnit API 로는 이 상태를 물어볼 수 없어 소스를 읽는다. 우아하지 않지만, 로드맵이
     * 요구한 "제거 후 하나도 남지 않아야 한다" 를 <b>매 빌드마다</b> 재검증하는 방법은 이것뿐이다.
     * 사람의 기억에 맡기면 다음 규칙이 추가될 때 되돌아온다.
     */
    @Test
    void 어떤_규칙도_빈_매칭을_허용하지_않는다() throws IOException {
        Path rules = Path.of("src/test/java/com/coinwin/architecture/ArchitectureRules.java");

        assertThat(rules).exists();
        assertThat(Files.readString(rules))
                .as("allowEmptyShould 가 켜진 규칙은 아무것도 검사하지 않아도 통과한다")
                .doesNotContain("allowEmptyShould(");
    }
}
