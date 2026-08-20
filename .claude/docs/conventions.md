# 코딩 컨벤션

## 복잡도 한계 (Checkstyle 강제)

| 항목 | 한계 | 근거 |
|---|---|---|
| 메서드 길이 | 20줄 | 넘으면 책임이 둘 이상 |
| 클래스 길이 | 200줄 | 넘으면 응집도 붕괴 |
| 파라미터 | 4개 | 넘으면 파라미터 객체로 추출 |
| 순환 복잡도 | 8 | 넘으면 조건 분해 또는 다형성 |
| 중첩 깊이 | 3 | 넘으면 early return 또는 추출 |
| 순환 참조 | 0 | ArchUnit |

이 수치는 CI에서 빌드를 실패시킨다. 예외 없음.

**테스트 소스만 예외**: `MethodLength`(40)·`FileLength`(400)만 완화한다. 한글 given/when/then 이름과 경계값 나열이 20줄을 넘기기 쉽고, 위 수치는 프로덕션 코드의 책임 분리를 겨냥한 것이기 때문이다. 순환 복잡도·파라미터 개수·중첩 깊이는 테스트에도 동일하게 적용한다. 설정: `config/checkstyle/checkstyle-test.xml`

## 값 객체

모든 금액·가격 계산은 값 객체를 통한다. `BigDecimal`을 도메인 밖으로 노출하지 않는다.

```java
public record Price(BigDecimal value)       // 스케일 2, HALF_UP, 음수 금지
public record Quantity(BigDecimal value)    // 스케일 8 (BTC)
public record Money(BigDecimal value)       // USDT, 스케일 2
public record Percentage(BigDecimal value)  // 스케일 4
```

이유: 스케일과 반올림 정책이 여러 곳에 흩어지면 계산 결과가 갈라진다. 정책은 값 객체 안에만 존재해야 한다.

## 도메인 우선

계산 로직을 `Service`에 두지 않는다.

```java
// 금지
positionService.calculateAveragePrice(entries);

// 지향
positionPlan.averageEntryPrice();
```

`Service`는 조율만 한다. 조회 → 도메인 호출 → 저장.
`Service` 메서드에 `if`가 3개 이상 생기면 도메인으로 내려야 할 규칙이 샌 것이다.

## 중복

같은 로직이 **두 번째** 나타나면 즉시 추출한다. 세 번째까지 기다리지 않는다.
계산 도메인에서 중복 계산식이 흩어지면 값이 갈라지는 버그가 난다.

## YAGNI

지금 실패하는 테스트를 통과시키는 것 외의 코드는 작성하지 않는다.
"나중에 필요할 것 같은" 필드, 메서드, 추상화는 전부 제거 대상이다.

## Java 21 활용

- 도메인 모델은 `record`
- 상태 표현은 `sealed interface` + 패턴 매칭
- 분기는 `switch` 패턴 매칭

자바 8처럼 쓴 자바 21이 되지 않게 한다.

## 예외

- 도메인 예외는 `domain` 패키지에 정의, 프레임워크 의존 없음
- HTTP 매핑은 `@RestControllerAdvice` 한 곳에서만
- 응답 형식은 `ProblemDetail` (RFC 7807)

## API 문서화

- 모든 공개 DTO에 `@Schema(description = ...)`
- 필드명 반복이 아니라 도메인 의미를 쓴다
  - 나쁨: `"stopLoss"`
  - 좋음: `"손절가. 롱은 최저 진입가보다 낮아야 한다."`
- 모든 엔드포인트에 예제 요청/응답

## 커밋

- 작업 단위마다 커밋. 여러 Phase를 한 커밋에 묶지 않는다.
- 브랜치: `feat/{phase}-{요약}` (예: `feat/phase1-position-sizing`)
