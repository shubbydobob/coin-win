package com.coinwin.journal.adapter.out.persistence;

import com.coinwin.journal.domain.ExitReason;
import com.coinwin.journal.domain.TradeQuery;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.dsl.BooleanExpression;
import java.util.Arrays;
import java.util.List;

/**
 * {@link TradeQuery} 를 QueryDSL 조건으로 옮긴다.
 *
 * <p>조건이 비어 있으면 {@code where} 절에 아무것도 붙이지 않는다 — 그것이 "동적 조회" 이고
 * QueryDSL 을 쓰는 이유다. 문자열 SQL 로는 조건 개수만큼 분기가 생긴다.
 *
 * <p><b>계획 준수 조건은 컬럼이 아니라 {@link ExitReason} 에서 파생시킨다.</b> 지키는
 * 이유 목록을 여기에 손으로 적으면 새 이유를 추가할 때 이 파일이 조용히 뒤처지고, 인메모리
 * 어댑터와 답이 갈린다. enum 을 훑어서 만들면 그 일이 일어날 수 없다.
 */
final class ClosedTradePredicates {

    private static final QTradeEntity TRADE = QTradeEntity.tradeEntity;

    private ClosedTradePredicates() {
    }

    static BooleanBuilder of(TradeQuery query) {
        BooleanBuilder where = new BooleanBuilder(TRADE.state.eq(TradeState.CLOSED));
        query.closedFrom().ifPresent(from -> where.and(TRADE.exitAt.goe(from)));
        query.closedTo().ifPresent(to -> where.and(TRADE.exitAt.lt(to)));
        query.direction().ifPresent(direction -> where.and(TRADE.direction.eq(direction)));
        query.exitReason().ifPresent(reason -> where.and(TRADE.exitReason.eq(reason)));
        query.followedPlan().ifPresent(followed -> where.and(byAdherence(followed)));
        return where;
    }

    private static BooleanExpression byAdherence(boolean followed) {
        List<ExitReason> honoring = Arrays.stream(ExitReason.values())
                .filter(ExitReason::honorsPlan)
                .toList();
        return followed ? TRADE.exitReason.in(honoring) : TRADE.exitReason.notIn(honoring);
    }
}
