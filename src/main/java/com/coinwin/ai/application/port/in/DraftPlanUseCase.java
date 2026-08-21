package com.coinwin.ai.application.port.in;

import com.coinwin.position.domain.PositionPlan;

/**
 * 자연어 한 문단을 매매 계획 초안으로 바꾼다.
 *
 * <p><b>초안일 뿐이다.</b> 저장하지도, 포지션을 계산하지도 않는다. 사용자가 확인한 뒤 기존
 * API 에 직접 넣는다 — AI 응답이 아무것도 자동으로 실행하지 않는다는 ADR 005 의 경계다.
 */
public interface DraftPlanUseCase {

    PositionPlan draftFrom(String sentence);
}
