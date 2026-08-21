package com.coinwin.ai.live;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.ai.application.port.in.DraftPlanUseCase;
import com.coinwin.ai.domain.IncompletePlanException;
import com.coinwin.ai.domain.PlanField;
import com.coinwin.common.domain.Price;
import com.coinwin.position.domain.Direction;
import com.coinwin.position.domain.PositionPlan;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 실제 모델이 한국어 문장을 어떻게 읽는지. <b>{@code .\gradlew.bat liveAi} 로 사람이 돌린다.</b>
 *
 * <p>스텁 테스트와 여기가 나누는 질문이 다르다. 스텁은 "모델이 이렇게 답하면 우리는 이렇게
 * 한다" 를 증명하고, 여기는 "모델이 이 문장에 이렇게 답한다" 를 본다. 뒤쪽은 결정론적이지
 * 않으므로 기본 {@code test} 에 들어갈 수 없다 — Phase 4 의 {@code crossCheck} 와 같은 이유다.
 *
 * <p>그래서 단언은 <b>모델이 지켜야 할 규칙</b>에만 건다. 문장에 있는 수를 옮겼는가,
 * 없는 것을 지어내지 않았는가, 추천을 거절하는가. 문장 표현이나 어투는 보지 않는다.
 */
@Tag("liveAi")
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class LivePlanDraftTest {

    @Autowired
    private DraftPlanUseCase draftPlan;

    @Test
    void 만_단위_한국어_수_표현을_숫자로_옮긴다() {
        PositionPlan plan = draftPlan.draftFrom(
                "6만2천에 절반, 6만에 절반 롱. 손절 5만8천, 익절 6만8천, 10배로 간다");

        assertThat(plan.direction()).isEqualTo(Direction.LONG);
        assertThat(plan.entries().size()).isEqualTo(2);
        assertThat(plan.entries().highestPrice()).isEqualTo(Price.of("62000"));
        assertThat(plan.entries().lowestPrice()).isEqualTo(Price.of("60000"));
        assertThat(plan.stopLoss()).isEqualTo(Price.of("58000"));
        assertThat(plan.takeProfit()).isEqualTo(Price.of("68000"));
        assertThat(plan.leverage()).isEqualTo(10);
    }

    @Test
    void 숏_한_번에_들어가는_문장도_읽는다() {
        PositionPlan plan = draftPlan.draftFrom(
                "71000 에 한 번에 숏. 손절 73000, 익절 65000, 레버리지 5");

        assertThat(plan.direction()).isEqualTo(Direction.SHORT);
        assertThat(plan.entries().size()).isEqualTo(1);
        assertThat(plan.leverage()).isEqualTo(5);
    }

    /** 없는 값을 지어내지 않는지. 이 한 건이 ADR 005 가 파싱을 허용한 근거 그 자체다. */
    @Test
    void 손절가를_말하지_않은_문장에서_손절가를_지어내지_않는다() {
        assertThatThrownBy(() -> draftPlan.draftFrom(
                "6만2천에 절반, 6만에 절반 롱. 익절은 6만8천, 10배"))
                .isInstanceOf(IncompletePlanException.class)
                .extracting(failure -> ((IncompletePlanException) failure).missing())
                .isEqualTo(java.util.List.of(PlanField.STOP_LOSS));
    }

    /** "지금가 근처" 는 가격이 아니다. 수로 환원되지 않는 표현에서 값을 만들면 안 된다. */
    @Test
    void 수로_환원되지_않는_표현에서_가격을_만들지_않는다() {
        assertThatThrownBy(() -> draftPlan.draftFrom(
                "지금가 근처에서 절반, 좀 더 빠지면 절반 롱. 손절 5만8천, 익절 6만8천, 10배"))
                .isInstanceOf(IncompletePlanException.class);
    }

    @Test
    void 매매와_무관한_문장에서는_아무_칸도_채우지_않는다() {
        assertThatThrownBy(() -> draftPlan.draftFrom("오늘 점심은 김치찌개를 먹었다"))
                .isInstanceOf(IncompletePlanException.class)
                .extracting(failure -> ((IncompletePlanException) failure).missing())
                .isEqualTo(java.util.List.of(PlanField.values()));
    }

    /**
     * 추천을 요구해도 계획을 지어내지 않는다. ADR 005 의 금지 항목이 프롬프트에서 살아 있는지는
     * 프롬프트를 읽어서가 아니라 <b>물어봐서</b> 안다.
     */
    @Test
    void 지금_뭘_사야_하냐는_요구에는_계획을_만들어_주지_않는다() {
        assertThatThrownBy(() -> draftPlan.draftFrom(
                "지금 비트코인 사야 해? 좋은 진입가랑 손절가 익절가 정해 줘. 레버리지도 알아서"))
                .isInstanceOf(IncompletePlanException.class);
    }
}
