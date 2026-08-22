package com.coinwin.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * DTO 의 <b>단순명</b>이 겹치지 않는지 본다.
 *
 * <p>springdoc 은 컴포넌트 스키마를 단순명으로 키잉한다. 이름이 같은 DTO 가 둘 있으면 하나가
 * 다른 하나를 <b>조용히 덮는다</b> — 문서에는 스키마가 하나만 남고, 그것을 믿은 소비자는
 * 없는 필드를 읽는다.
 *
 * <p>Phase 8 에서 실제로 그랬다. {@code backtest} 와 {@code journal} 에 각각
 * {@code TradeResponse} 가 있었고, 스키마는 <b>백테스트 거래가 계획·상태·결과를 가진
 * 매매 기록</b>이라고 말했다. 생성된 타입도 그렇게 나왔고, 화면은 실제 응답에 없는
 * {@code plan.direction} 을 읽다 죽었다. <b>브라우저로 한 번 돌려 보기 전까지 아무도
 * 몰랐다</b> — 타입은 컴파일됐고 테스트는 초록이었다.
 *
 * <p>이 검사가 스키마 문서가 아니라 <b>클래스</b>를 보는 이유가 그것이다. 문서에는 살아남은
 * 이름 하나만 있으므로, 덮였다는 사실 자체가 문서에서는 보이지 않는다.
 */
class SchemaNameCollisionTest {

    private static final List<String> DTO_SUFFIXES = List.of("Request", "Response", "Params");

    @Test
    void 이름이_겹치는_DTO_가_없다() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.coinwin");

        Map<String, List<String>> byName = classes.stream()
                .filter(SchemaNameCollisionTest::isDto)
                .collect(Collectors.groupingBy(
                        JavaClass::getSimpleName,
                        Collectors.mapping(JavaClass::getName, Collectors.toList())));

        List<String> collisions = byName.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .map(entry -> entry.getKey() + " ← " + entry.getValue())
                .toList();

        assertThat(collisions)
                .describedAs("단순명이 겹치는 DTO — 스키마에서 하나가 다른 하나를 덮는다")
                .isEmpty();
    }

    private static boolean isDto(JavaClass type) {
        return DTO_SUFFIXES.stream().anyMatch(suffix -> type.getSimpleName().endsWith(suffix));
    }
}
