package com.coinwin.architecture;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 6개 규칙이 <b>실제로 위반을 잡는지</b> 증명한다.
 *
 * <p>{@link ArchitectureRulesTest} 가 초록인 것만으로는 규칙이 작동한다는 증거가 못 된다.
 * 패키지 이름에 오타가 나서 아무 클래스도 매칭하지 않는 규칙도 똑같이 통과하기 때문이다.
 * 여기서는 {@code src/archFixture} 의 의도적 위반 코드에 <b>같은 규칙 인스턴스</b>를 적용하고
 * 실패를 단언한다. 규칙이 무력화되면 이 테스트가 먼저 깨진다.
 *
 * <p>픽스처는 규칙별로 루트를 분리했다({@code archfixture.r1} ~ {@code archfixture.r6}).
 * 한 픽스처의 위반이 다른 규칙 검사에 새어들지 않게 하기 위해서다.
 */
class ArchitectureRulesViolationTest {

    private static JavaClasses fixture(String rulePackage) {
        return new ClassFileImporter().importPackages(rulePackage);
    }

    private static void assertRuleRejects(ArchRule rule, String rulePackage, String expectedHint) {
        assertThatThrownBy(() -> rule.check(fixture(rulePackage)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(expectedHint)
                // 규칙이 왜 거부했는지 원문을 남긴다. `-PshowTestOutput` 으로 확인한다.
                .satisfies(e -> System.out.println("[거부 사유] " + e.getMessage()));
    }

    @Test
    @DisplayName("규칙 1 은 domain 의 Spring 의존을 잡는다")
    void 규칙1은_domain의_프레임워크_의존을_잡는다() {
        assertRuleRejects(
                ArchitectureRules.domainIsFrameworkFree(),
                "archfixture.r1",
                "DirtyDomain");
    }

    @Test
    @DisplayName("규칙 2 는 domain → api 역방향 의존을 잡는다")
    void 규칙2는_역방향_계층_의존을_잡는다() {
        assertRuleRejects(
                ArchitectureRules.layerDependenciesPointInward("archfixture.r2"),
                "archfixture.r2",
                "BackwardDomain");
    }

    @Test
    @DisplayName("규칙 3 은 패키지 순환 참조를 잡는다")
    void 규칙3은_패키지_순환_참조를_잡는다() {
        assertRuleRejects(
                ArchitectureRules.noPackageCycles("archfixture.r3"),
                "archfixture.r3",
                "Cycle");
    }

    @Test
    @DisplayName("규칙 4 는 market.application → market.adapter 참조를 잡는다")
    void 규칙4는_application이_adapter를_참조하는_것을_잡는다() {
        assertRuleRejects(
                ArchitectureRules.hexagonalApplicationDoesNotSeeAdapters("archfixture.r4"),
                "archfixture.r4",
                "LeakyService");
    }

    /**
     * 규칙 4 는 두 모듈 이름을 <b>손으로 적어</b> 열거한다. market 픽스처만 있으면 journal 쪽
     * 항목에 오타가 나거나 통째로 빠져도 규칙이 계속 초록이다. 그래서 모듈마다 픽스처를 둔다.
     */
    @Test
    @DisplayName("규칙 4 는 journal.application → journal.adapter 참조도 잡는다")
    void 규칙4는_journal에서도_application이_adapter를_참조하는_것을_잡는다() {
        assertRuleRejects(
                ArchitectureRules.hexagonalApplicationDoesNotSeeAdapters("archfixture.r4j"),
                "archfixture.r4j",
                "LeakyJournalService");
    }

    @Test
    @DisplayName("규칙 5 는 backtest → market.adapter 참조를 잡는다")
    void 규칙5는_backtest가_adapter를_참조하는_것을_잡는다() {
        assertRuleRejects(
                ArchitectureRules.backtestConsumesPortsNotAdapters("archfixture.r5"),
                "archfixture.r5",
                "LeakyEngine");
    }

    @Test
    @DisplayName("규칙 6 은 포트를 구현하지 않는 *Adapter 를 잡는다")
    void 규칙6은_포트를_구현하지_않는_어댑터를_잡는다() {
        assertRuleRejects(
                ArchitectureRules.outboundAdaptersImplementPorts("archfixture.r6"),
                "archfixture.r6",
                "OrphanAdapter");
    }

    /**
     * 규칙 6 의 패턴은 모듈 이름을 가리지 않으므로 journal 은 자동으로 검사 대상이 된다.
     * 이 테스트가 증명하는 것은 <b>그 자동이 실제로 작동한다</b>는 사실이다.
     */
    @Test
    @DisplayName("규칙 6 은 journal 의 고아 어댑터도 잡는다")
    void 규칙6은_journal의_고아_어댑터도_잡는다() {
        assertRuleRejects(
                ArchitectureRules.outboundAdaptersImplementPorts("archfixture.r6j"),
                "archfixture.r6j",
                "OrphanTradeAdapter");
    }
}
