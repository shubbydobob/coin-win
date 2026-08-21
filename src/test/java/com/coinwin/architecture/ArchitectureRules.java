package com.coinwin.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.Architectures;

/**
 * .claude/docs/architecture.md 가 정의한 6개 아키텍처 규칙.
 *
 * <p>규칙은 루트 패키지를 파라미터로 받는다. 그래야 정상 코드(com.coinwin)와 위반
 * 픽스처(archfixture)에 <b>완전히 동일한 규칙 인스턴스</b>를 적용할 수 있다. 규칙을 복사해서
 * 두 벌 두면 위반 테스트가 아무것도 증명하지 못한다.
 *
 * <p><b>allowEmptyShould(true) 에 대하여.</b> 아직 존재하지 않는 패키지를 대상으로 하는 규칙은
 * 매칭 클래스가 0건이고, ArchUnit 은 이때 기본적으로 실패한다. 그래서 해당 규칙만 허용으로
 * 열어 두었다. 그 대가로 "아무것도 검사하지 않는 규칙"이 조용히 통과할 수 있는데, 바로 그것을
 * 막는 장치가 {@link ArchitectureRulesViolationTest} 다. 위반 픽스처에서 규칙이 실패하지 않으면
 * 빌드가 깨진다.
 *
 * <p><b>이 플래그는 임시다. 대상 패키지가 생기는 Phase 에서 반드시 제거한다.</b>
 *
 * <ul>
 *   <li>규칙 1·3 — Phase 0 에서 제거 완료 ({@code common.domain} / {@code common.config} 존재)
 *   <li>규칙 4·6 — <b>Phase 3 에서 제거 완료</b> ({@code market.application} 과
 *       {@code market.adapter.out} 의 어댑터 다섯이 실제로 검사 대상이 됐다)
 *   <li>규칙 5 — Phase 6 에서 제거 ({@code backtest} 생성 시). <b>마지막 플래그다.</b>
 * </ul>
 *
 * @see ArchitectureRulesTest 정상 코드가 규칙을 지키는지
 * @see ArchitectureRulesViolationTest 규칙이 위반을 실제로 잡는지
 */
public final class ArchitectureRules {

    private ArchitectureRules() {
    }

    /**
     * 규칙 1 — domain 패키지의 Spring / JPA / Jackson 의존 금지.
     *
     * <p>Spring Boot 4 는 Jackson 3(tools.jackson)를 기본으로 쓰고 Jackson 2
     * (com.fasterxml.jackson)를 병행 제공한다. 둘 다 막지 않으면 구멍이 뚫린다.
     */
    public static ArchRule domainIsFrameworkFree() {
        return noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.persistence..",
                        "com.fasterxml.jackson..",
                        "tools.jackson..",
                        "org.hibernate..",
                        "io.swagger..")
                .as("규칙 1: domain 패키지는 Spring / JPA / Jackson 에 의존하지 않는다");
    }

    /**
     * 규칙 2 — 계층 의존 방향 {@code (api|adapter) → application → domain}.
     *
     * <p>{@code adapter} 를 {@code api} 와 별개의 바깥 층으로 둔다. Phase 3 이전에는
     * {@code adapter.in} 만 {@code Api} 층에 얹혀 있었는데, 그 상태로 {@code adapter.out} 이
     * 생기면 <b>어느 층에도 속하지 않은 클래스</b>가 되어 application·domain 접근이 전부
     * 위반으로 잡힌다. 아웃바운드 어댑터가 포트를 구현하는 것은 헥사고날의 정의 그 자체이므로
     * 규칙 쪽이 틀린 것이었다.
     *
     * <p>바깥 두 층은 <b>아무에게도 참조되지 않는다.</b> {@code Adapter} 에 걸린 이 조건이
     * 규칙 4(application 이 adapter 를 모른다)를 계층 차원에서 한 번 더 받친다.
     */
    public static ArchRule layerDependenciesPointInward(String root) {
        return Architectures.layeredArchitecture()
                .consideringOnlyDependenciesInAnyPackage(root + "..")
                .layer("Api").definedBy(root + "..api..")
                .layer("Adapter").definedBy(root + "..adapter..")
                .layer("Application").definedBy(root + "..application..")
                .layer("Domain").definedBy(root + "..domain..")
                .withOptionalLayers(true)
                .whereLayer("Api").mayNotBeAccessedByAnyLayer()
                .whereLayer("Adapter").mayNotBeAccessedByAnyLayer()
                .whereLayer("Application").mayOnlyBeAccessedByLayers("Api", "Adapter")
                .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Api", "Adapter")
                .as("규칙 2: 계층 의존은 (api|adapter) → application → domain 방향으로만 흐른다");
    }

    /** 규칙 3 — 패키지 순환 참조 0건. */
    public static ArchRule noPackageCycles(String root) {
        return slices()
                .matching(root + ".(*)..")
                .should().beFreeOfCycles()
                .as("규칙 3: 패키지 순환 참조가 없다");
    }

    /**
     * 규칙 4 — market / journal 의 application 이 adapter 를 참조하지 않는다.
     *
     * <p>architecture.md: "4번과 5번이 없으면 헥사고날이 이름만 남고 계층형으로 무너진다."
     */
    public static ArchRule hexagonalApplicationDoesNotSeeAdapters(String root) {
        return noClasses()
                .that().resideInAnyPackage(
                        root + ".market.application..",
                        root + ".journal.application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        root + ".market.adapter..",
                        root + ".journal.adapter..")
                .as("규칙 4: market / journal 의 application 은 adapter 를 알지 못한다");
    }

    /** 규칙 5 — backtest 는 market 의 포트만 소비하고 어댑터를 직접 참조하지 않는다. */
    public static ArchRule backtestConsumesPortsNotAdapters(String root) {
        return noClasses()
                .that().resideInAPackage(root + ".backtest..")
                .should().dependOnClassesThat().resideInAPackage(root + ".market.adapter..")
                .as("규칙 5: backtest 는 market.adapter 를 참조하지 않는다 (포트만 허용)")
                .allowEmptyShould(true);
    }

    /** 규칙 6 — adapter.out 의 *Adapter 는 반드시 application.port.out 인터페이스를 구현한다. */
    public static ArchRule outboundAdaptersImplementPorts(String root) {
        return com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes()
                .that().resideInAPackage(root + "..adapter.out..")
                .and().haveSimpleNameEndingWith("Adapter")
                .and().areNotInterfaces()
                .and().areNotMemberClasses()
                .should(implementAnOutboundPort())
                .as("규칙 6: adapter.out 구현체는 application.port.out 인터페이스를 구현한다");
    }

    private static ArchCondition<JavaClass> implementAnOutboundPort() {
        return new ArchCondition<>("application.port.out 의 인터페이스를 구현") {
            @Override
            public void check(JavaClass adapter, ConditionEvents events) {
                boolean implementsPort = adapter.getAllRawInterfaces().stream()
                        .anyMatch(i -> i.getPackageName().contains(".application.port.out"));
                events.add(new SimpleConditionEvent(
                        adapter,
                        implementsPort,
                        "%s 는 application.port.out 인터페이스를 구현하지 않는다"
                                .formatted(adapter.getName())));
            }
        };
    }
}
