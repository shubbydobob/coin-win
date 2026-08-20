# ADR 004 — BigDecimal 대신 값 객체

- 상태: 채택
- 날짜: 2026-08-21

## 맥락

이 프로젝트의 계산은 전부 돈이다. 평단가, 청산가, 수량, 최대 손실, 손익비.
`BigDecimal` 을 그대로 쓰면 스케일과 반올림 정책을 호출부마다 정해야 한다.

```java
// 이렇게 되면 어디선가 반드시 갈라진다
price.multiply(qty).setScale(2, RoundingMode.HALF_UP);
price.multiply(qty).setScale(8, RoundingMode.HALF_EVEN);
```

## 결정

**`Price` / `Quantity` / `Money` / `Percentage` 네 개의 record 를 두고, 도메인 밖으로 `BigDecimal` 을 노출하지 않는다.**

| 타입 | 스케일 | 음수 | 의미 |
|---|---|---|---|
| `Price` | 2 | 금지 | USDT 기준 가격 |
| `Quantity` | 8 | 금지 | BTC 수량 (사토시 단위) |
| `Money` | 2 | **허용** | USDT 금액 |
| `Percentage` | 4 | 금지 | 비율 (100 = 100%) |

반올림은 전부 `HALF_UP`. 정규화는 생성자에서 한 번만 일어난다.

## 근거

- **정책이 한 곳에만 존재한다.** 스케일과 반올림은 값 객체 생성자 안에서 끝난다.
  호출부는 정책을 알 필요도, 알 방법도 없다.
- **동등성이 값 기준으로 동작한다.** record 의 `equals` 는 `BigDecimal.equals` 를 쓰는데
  이것은 스케일에 민감해서 `1.50 != 1.5` 다. 생성 시점에 스케일을 고정했기 때문에
  `Price.of("1.5").equals(Price.of("1.50"))` 가 참이 된다. 이 성질 자체를 테스트로 못박았다.
- **타입이 단위를 강제한다.** `Money.dividedBy(Money) → Quantity` 는 "금액을 단가로 나누면
  수량"이라는 도메인 규칙을 시그니처로 표현한다. `BigDecimal.divide(BigDecimal)` 은 아무것도 말하지 않는다.

### `Money` 만 음수를 허용하는 이유

실현손익(`TradeRecord.realizedPnl`)이 음수여야 한다. 가격·수량·비율에는 음수가 존재하지 않는다.
부호 검사는 **반올림 전에** 한다. 그렇지 않으면 `-0.001` 이 스케일 2에서 `-0.00` 이 되어
음수 검사를 빠져나간다.

## 결과

- 값 객체마다 연산을 하나씩 추가해야 한다. YAGNI 를 지켜 **테스트가 요구하는 것만** 만든다.
  Phase 0 에는 `Money.dividedBy` 와 `Percentage.applyTo` 둘뿐이다.
- 영속성 계층에서 `BigDecimal` 로 변환해야 한다. 이는 adapter 의 책임이며,
  ArchUnit 규칙 1 이 도메인에 JPA 가 들어오는 것을 막는다.
- `domain` 패키지 커버리지 90% 게이트(JaCoCo)가 값 객체의 경계값 테스트를 강제한다.

## 관련

- `conventions.md` 값 객체 절
- [007 — Spring Boot 4 기준선](007-spring-boot-4-baseline.md) (Jackson 3 직렬화 시 고려사항)
