package com.coinwin.ai.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.common.domain.Percentage;
import com.coinwin.common.domain.Price;
import com.coinwin.position.domain.Direction;
import com.coinwin.position.domain.InvalidPositionPlanException;
import com.coinwin.position.domain.PositionPlan;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 모델이 채워 온 칸들을 계획으로 바꿀 때의 규칙.
 *
 * <p>이 타입만이 <b>비어 있는 칸</b>을 안다. 여기를 지나면 계획은 완전하거나 존재하지 않는다.
 * 그 경계가 ADR 005 의 "파싱 실패 시 추측해서 채우지 않는다" 를 코드로 만든 것이다.
 */
class DraftedFieldsTest {

    private static DraftedEntry entry(String price, String allocation) {
        return new DraftedEntry(Price.of(price), Percentage.of(allocation));
    }

    private static List<DraftedEntry> halfAndHalf() {
        return List.of(entry("62000", "50"), entry("60000", "50"));
    }

    private static DraftedFields complete() {
        return new DraftedFields(Direction.LONG, halfAndHalf(),
                Price.of("58000"), Price.of("68000"), 10);
    }

    @Test
    void 모든_칸이_채워져_있으면_계획이_된다() {
        PositionPlan plan = complete().complete();

        assertThat(plan.direction()).isEqualTo(Direction.LONG);
        assertThat(plan.entries().size()).isEqualTo(2);
        assertThat(plan.stopLoss()).isEqualTo(Price.of("58000"));
        assertThat(plan.takeProfit()).isEqualTo(Price.of("68000"));
        assertThat(plan.leverage()).isEqualTo(10);
    }

    @Test
    void 손절가가_빠지면_무엇이_없는지_알려_준다() {
        DraftedFields fields = new DraftedFields(Direction.LONG, halfAndHalf(),
                null, Price.of("68000"), 10);

        assertThat(fields.missing()).containsExactly(PlanField.STOP_LOSS);
        assertThatThrownBy(fields::complete)
                .isInstanceOf(IncompletePlanException.class)
                .hasMessageContaining("손절가");
    }

    /**
     * 예외가 <b>목록을 들고 다닌다.</b> 메시지에서 다시 파싱하지 않아도 무엇이 빠졌는지 알 수
     * 있어야 부르는 쪽이 되묻는 화면을 만들 수 있다.
     */
    @Test
    void 예외가_빠진_항목_목록을_들고_다닌다() {
        DraftedFields fields = new DraftedFields(null, halfAndHalf(),
                Price.of("58000"), null, null);

        assertThatThrownBy(fields::complete)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories
                        .type(IncompletePlanException.class))
                .extracting(IncompletePlanException::missing)
                .isEqualTo(List.of(
                        PlanField.DIRECTION, PlanField.TAKE_PROFIT, PlanField.LEVERAGE));
    }

    /**
     * 하나씩 되묻게 만들면 사용자가 같은 문장을 다섯 번 고쳐 쓰게 된다. 빠진 것은 <b>한 번에
     * 전부</b> 돌려준다.
     */
    @Test
    void 여러_항목이_빠지면_한_번에_모아_알려_준다() {
        DraftedFields fields = new DraftedFields(null, null, null, null, null);

        assertThat(fields.missing()).containsExactly(
                PlanField.DIRECTION, PlanField.ENTRIES,
                PlanField.STOP_LOSS, PlanField.TAKE_PROFIT, PlanField.LEVERAGE);
    }

    @Test
    void 진입_계획이_비어_있으면_빠진_것이다() {
        DraftedFields fields = new DraftedFields(Direction.LONG, List.of(),
                Price.of("58000"), Price.of("68000"), 10);

        assertThat(fields.missing()).containsExactly(PlanField.ENTRIES);
    }

    /**
     * "지금가 근처에서 절반" 같은 문장이 여기로 온다. 비중은 읽히는데 가격이 없다.
     *
     * <p>가격 없는 회차를 <b>빼고</b> 나머지로 계획을 만들면 비중 합이 100 이 아니게 되거나,
     * 더 나쁘게는 우연히 100 이 되어 사용자가 의도하지 않은 계획이 조용히 성립한다.
     */
    @Test
    void 한_회차라도_가격이_비어_있으면_진입_계획_전체가_빠진_것이다() {
        List<DraftedEntry> half = Arrays.asList(
                entry("62000", "50"), new DraftedEntry(null, Percentage.of("50")));
        DraftedFields fields = new DraftedFields(Direction.LONG, half,
                Price.of("58000"), Price.of("68000"), 10);

        assertThat(fields.missing()).containsExactly(PlanField.ENTRIES);
    }

    @Test
    void 회차_자체가_null_이어도_진입_계획이_빠진_것으로_본다() {
        DraftedFields fields = new DraftedFields(Direction.LONG, Arrays.asList((DraftedEntry) null),
                Price.of("58000"), Price.of("68000"), 10);

        assertThat(fields.missing()).containsExactly(PlanField.ENTRIES);
    }

    /**
     * 칸이 다 찼다고 계획이 성립하는 것은 아니다. 정합성 규칙은 Phase 1 이 이미 갖고 있고
     * 여기서 다시 쓰지 않는다 — 규칙을 두 벌 두면 언젠가 한쪽만 바뀐다.
     */
    @Test
    void 비중_합이_100이_아니면_분할_진입_규칙이_잡는다() {
        DraftedFields fields = new DraftedFields(Direction.LONG,
                List.of(entry("62000", "50"), entry("60000", "30")),
                Price.of("58000"), Price.of("68000"), 10);

        assertThat(fields.missing()).isEmpty();
        assertThatThrownBy(fields::complete)
                .isInstanceOf(InvalidPositionPlanException.class)
                .hasMessageContaining("100");
    }

    @Test
    void 롱인데_손절가가_진입가보다_높으면_계획_규칙이_잡는다() {
        DraftedFields fields = new DraftedFields(Direction.LONG, halfAndHalf(),
                Price.of("63000"), Price.of("68000"), 10);

        assertThat(fields.missing()).isEmpty();
        assertThatThrownBy(fields::complete).isInstanceOf(InvalidPositionPlanException.class);
    }

    @Test
    void 완전한_칸이면_빠진_것이_없다() {
        assertThat(complete().missing()).isEmpty();
    }

    @Test
    void 목록을_밖에서_바꿔도_담긴_계획은_바뀌지_않는다() {
        List<DraftedEntry> mutable = new java.util.ArrayList<>(halfAndHalf());
        DraftedFields fields = new DraftedFields(Direction.LONG, mutable,
                Price.of("58000"), Price.of("68000"), 10);
        mutable.clear();

        assertThat(fields.missing()).isEmpty();
    }
}
