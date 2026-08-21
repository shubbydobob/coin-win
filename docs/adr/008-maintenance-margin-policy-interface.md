# ADR 008 — `MaintenanceMarginPolicy`: 구현체가 하나인데도 인터페이스를 두는 이유

- 상태: 채택
- 날짜: 2026-08-21

## 맥락

Phase 1 의 `position/domain` 은 청산가를 계산한다. 공식은 유지증거금률(MMR)에 의존한다.

```
LONG:  liq = entry × (1 - 1/leverage + MMR)
SHORT: liq = entry × (1 + 1/leverage - MMR)
```

Phase 1 시점에 MMR 을 구할 방법은 **0.4% 고정 근사치**뿐이다. 바이낸스의 실제 MMR 은
포지션 명목가에 따라 구간별로 달라지고(`leverageBracket`), 그 값을 가져오려면
`market` 모듈과 HTTP 어댑터가 필요한데 둘 다 Phase 3 에 있다.

여기서 [ADR 002](002-partial-hexagonal.md) 및 `architecture.md` 의 규칙과 충돌한다.

> **구현체가 하나뿐인 인터페이스는 만들지 않는다.**

Phase 1 에서 `MaintenanceMarginPolicy` 를 인터페이스로 두면 구현체는
`FixedMaintenanceMarginPolicy` 하나다. 규칙을 문자 그대로 읽으면 위반이다.

## 결정

**`MaintenanceMarginPolicy` 를 Phase 1 부터 인터페이스로 정의한다.** 이는 ADR 002 규칙의
위반이 아니라 규칙에 이미 적혀 있는 조건을 만족하는 사례다. ADR 002 의 원문은
"구현체가 둘 이상 **존재하거나 존재할 예정인** 모듈에만 포트를 정의한다" 이고,
`MaintenanceMarginPolicy` 는 "존재할 예정" 에 해당한다.

- Phase 1: `FixedMaintenanceMarginPolicy` (MMR 0.4% 고정)
- Phase 3: `BracketMaintenanceMarginPolicy` (`leverageBracket` 기반) 추가

두 번째 구현체는 **희망이 아니라 로드맵에 날짜가 잡힌 항목**이다.
`roadmap.md` Phase 3 의 작업 목록에 명시돼 있고, Phase 3 완료 조건이
"Phase 1 청산가가 거래소 실제값과 오차 범위 내 일치" 이므로 고정 근사치로는 통과할 수 없다.

## 근거

ADR 002 의 규칙이 막으려는 것은 **투기적 추상화** — "나중에 갈아끼울 일이 있을지도 모른다"는
막연한 기대로 인터페이스를 세우고, 구현체가 영원히 하나로 남는 경우다.
`PositionCalculatorPort` 같은 것이 그 예다.

이 건은 성격이 다르다. 두 번째 구현체의 존재 여부가 아니라 **도착 시점만** 미정이었고,
그 시점조차 Phase 3 으로 고정돼 있다. 인터페이스를 미루면 대신 치르는 비용은:

- Phase 1 의 청산가 테스트가 MMR 0.4% 라는 **근사치에 직접 결합**된다.
  Phase 3 에서 구간별 MMR 로 바뀌는 순간 이 테스트들이 한꺼번에 깨지고,
  깨진 이유가 "공식이 틀렸다" 인지 "MMR 입력이 달라졌다" 인지 구분되지 않는다.
- MMR 조회가 `market` 모듈에서 오는데, `position/domain` 이 구체 클래스를 직접 들고 있으면
  Phase 3 에서 `position → market` 방향 의존이 생긴다.
  `architecture.md` 의 모듈 간 의존 규칙("그 외 모듈 간 직접 참조 금지")과 정면으로 부딪힌다.
  인터페이스를 `position/domain` 에 두면 의존 방향이 뒤집혀 `market` 쪽이 이를 구현하거나,
  조립이 `application` 층에서 끝난다.

즉 인터페이스의 값은 "구현체 교체" 가 아니라 **의존 방향 보존**에서 나온다.

## 한계

이 판단은 다음 조건이 유지되는 동안에만 유효하다.

- Phase 3 에서 `BracketMaintenanceMarginPolicy` 가 실제로 추가된다.
- **Phase 3 이 끝났는데도 구현체가 하나뿐이면 이 ADR 을 폐기하고 인터페이스를 인라인한다.**
  그때는 ADR 002 의 규칙이 그대로 적용되는 상태이기 때문이다.

이 문장이 이 문서의 핵심이다. "예정" 을 근거로 든 추상화는 예정이 어긋났을 때
철회 조건이 같이 적혀 있지 않으면 그냥 투기적 추상화와 구별되지 않는다.

## 결과

- `MaintenanceMarginPolicy` 는 `position/domain` 에 둔다. 프레임워크 의존 없음(ArchUnit 규칙 1).
- 기본 구현 `FixedMaintenanceMarginPolicy` 의 0.4% 는 근사치임을 이름과 문서에 드러낸다.
  상수를 `0.004` 로 흘려두지 않는다.
- Phase 1 의 청산가 테스트는 MMR 을 **테스트가 주입**한다. 고정값을 하드코딩하지 않는다.
  같은 테스트가 Phase 3 의 구간별 구현에서도 그대로 돌아가야 한다.
- Phase 3 종료 시 이 ADR 의 "한계" 절을 재확인한다.

## 관련

- [002 — 부분 헥사고날](002-partial-hexagonal.md) — 이 문서가 예외를 주장하는 대상
- `roadmap.md` Phase 3 — `MaintenanceMarginPolicy` 교체 항목
- `.claude/skills/domain-model/SKILL.md` — 청산가 공식
