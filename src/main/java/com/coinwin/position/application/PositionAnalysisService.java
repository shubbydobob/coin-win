package com.coinwin.position.application;

import com.coinwin.position.domain.MaintenanceMarginPolicy;
import com.coinwin.position.domain.PositionAnalysis;
import com.coinwin.position.domain.PositionPlan;
import com.coinwin.position.domain.RiskBudget;
import org.springframework.stereotype.Service;

/**
 * 계획과 리스크 예산에 유지증거금률 정책을 붙여 산출을 조율한다.
 *
 * <p>계산은 한 줄도 하지 않는다. 이 클래스의 유일한 책임은 <b>조립</b>이다 — 도메인이 알아서는
 * 안 되는 "지금 어떤 MMR 구현을 쓰는가" 를 여기서 주입한다. Phase 3 에서 구간별 구현으로
 * 바뀔 때 바뀌는 곳도 여기 하나다.
 */
@Service
public class PositionAnalysisService {

    private final MaintenanceMarginPolicy maintenanceMarginPolicy;

    public PositionAnalysisService(MaintenanceMarginPolicy maintenanceMarginPolicy) {
        this.maintenanceMarginPolicy = maintenanceMarginPolicy;
    }

    public PositionAnalysis analyze(PositionPlan plan, RiskBudget budget) {
        return plan.analyze(budget, maintenanceMarginPolicy);
    }
}
