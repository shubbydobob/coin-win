# ADR 013 — `Candle` 을 `market/domain` 에 두고 모듈 간 의존 규칙을 넓힌다

- 상태: 채택
- 날짜: 2026-08-21

## 맥락

`Candle` 은 `market` 이 생산하고 `indicator`(Phase 4)와 `backtest`(Phase 6)가 소비한다.
`LoadCandlesPort` 의 반환 타입이므로 포트를 쓰는 쪽은 어차피 이 타입을 본다.

그런데 `architecture.md` 의 모듈 간 규칙은 이렇게 적혀 있었다.

```
common   ← 모든 모듈 (역방향 금지)
backtest → indicator, position, market.application.port.out
journal  → position
그 외 모듈 간 직접 참조 금지
```

`Candle` 을 `market/domain` 에 두면 `indicator → market.domain` 과
`backtest → market.domain` 이 필요해지는데 둘 다 "그 외" 에 걸린다.
(`.claude/skills/domain-model/SKILL.md` 는 `Candle` 을 `indicator/domain` 에 적어 두었지만,
그러면 `market → indicator` 라는 더 나쁜 방향이 생긴다.)

## 결정

**`Candle` 은 `market/domain` 에 둔다.** 그리고 모듈 간 규칙에 두 줄을 명시적으로 추가한다.

```
backtest  → market.domain
indicator → market.domain
```

`common/domain` 으로 올리는 선택지는 채택하지 않았다.

## 근거

**`common` 의 성격을 지킨다.** 지금 `common/domain` 에 있는 것은 `Money`·`Price`·
`Quantity`·`Percentage` 넷이고, 이들의 공통점은 **반올림과 스케일 정책을 소유한 값**이라는
것이다(ADR 004). `Candle` 은 그런 값이 아니라 시장 데이터 모델이다. 여기에 넣기 시작하면
"여러 모듈이 쓰니까" 라는 이유로 `common` 이 공용 모델 전반으로 넓어진다. 그 순간
`common ← 모든 모듈` 이라는 강한 규칙이 아무 제약도 하지 않게 된다.

**규칙을 어기는 것이 아니라 고치는 것이다.** "그 외 모듈 간 직접 참조 금지" 가 막으려던 것은
`position ↔ indicator` 같은 **임의의 상호 참조**다. `market → indicator → market` 처럼 순환이
생기거나, 어느 모듈이 어느 모듈을 아는지 아무도 말할 수 없게 되는 상태를 막는 규칙이다.

`indicator → market.domain` 은 단방향이고, 순환을 만들지 않으며(ArchUnit 규칙 3 이 감시한다),
무엇보다 **이미 암묵적으로 존재하던 의존**이다. `backtest → market.application.port.out` 이
허용돼 있는데 그 포트가 `CandleSeries` 를 돌려주므로, `backtest` 는 규칙 표에 적히지 않은 채로
이미 `market.domain` 을 보고 있었다. 적히지 않은 의존을 적는 편이 낫다.

**대안을 버린 이유.** `Candle` 을 각 모듈이 자기 타입으로 다시 정의하고 경계에서 변환하는
방법도 있다. 순수하지만, 캔들 수천 개를 지표 계산에 넘길 때마다 변환 비용을 내고, 무엇보다
같은 개념에 정의가 셋 생긴다. 값이 갈라지는 버그를 막자는 것이 이 프로젝트의 값 객체 방침인데
정반대로 간다.

## 한계

- 이 결정은 **`market.domain` 에 한한다.** `indicator` 나 `backtest` 가 `market.application`
  이나 `market.adapter` 를 참조하는 것은 여전히 금지다(규칙 5 가 어댑터 쪽을 강제한다).
- `market.domain` 에 시장 데이터가 아닌 것을 넣으면 이 논리가 무너진다. 이 패키지는
  캔들·지표 입력·거래소 메타데이터에 한정한다.

## 관련

- [002 — 부분 헥사고날](002-partial-hexagonal.md)
- [004 — BigDecimal 대신 값 객체](004-value-objects-over-bigdecimal.md) — `common` 의 성격
- `.claude/docs/architecture.md` — 모듈 간 의존 규칙에 두 줄을 추가했다
- `.claude/skills/domain-model/SKILL.md` — `Candle` 위치 표기를 이 결정에 맞춰 고쳤다
