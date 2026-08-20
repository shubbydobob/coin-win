# ADR 003 — 도메인형 패키지 구조

- 상태: 채택
- 날짜: 2026-08-21

## 맥락

패키지를 나누는 두 가지 방식이 있다.

- **기술형**: `controller` / `service` / `repository` / `dto`
- **도메인형**: `market` / `position` / `journal` / `indicator` ...

## 결정

**최상위는 도메인으로 나눈다.** 기술 계층은 각 도메인 안에서 나눈다.

```
com.coinwin.position.domain
com.coinwin.position.application
com.coinwin.market.adapter.out.binance
```

## 근거

- 하나의 변경은 보통 하나의 도메인에 모인다. "분할 진입 계산을 고친다"는 `position` 안에서 끝난다.
  기술형이면 같은 변경이 `controller` / `service` / `domain` 세 폴더에 흩어진다.
- 모듈 간 의존 규칙(architecture.md)을 **패키지 이름으로 표현할 수 있다.**
  ArchUnit 규칙 4·5 가 `com.coinwin.market.application..` 같은 패턴으로 작성되는 것은
  이 구조 덕분이다. 기술형 구조에서는 "market 의 application" 을 지목할 방법이 없다.
- ADR 001 이 단일 모듈을 택했으므로, 도메인 경계를 표현할 수단이 패키지밖에 없다.

## 결과

- ArchUnit 규칙이 패키지 이름에 의존한다. 패키지를 옮기면 규칙도 같이 고쳐야 한다.
  이는 의도된 결합이다 — 규칙이 구조를 따라가지 않으면 조용히 무의미해진다.
- 도메인 간 공통 요소는 `common` 에 둔다. `common` 은 모든 모듈이 참조할 수 있고
  역방향(= `common` 이 다른 모듈을 참조)은 금지된다.

## 관련

- [001 — 단일 모듈](001-single-module.md)
- [002 — 부분 헥사고날](002-partial-hexagonal.md)
