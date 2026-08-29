/**
 * 표시 형식. **여기서만 자릿수를 다룬다.**
 *
 * JSON 숫자는 스케일을 싣지 못한다 — 서버가 `60000.00` 을 보내도 자바스크립트에 도착하면
 * `60000` 이다. 값 객체가 정한 자릿수(`conventions.md`: Price 2 · Quantity 8 · Money 2 ·
 * Percentage 4)를 표시할 때 되살리는 것이 이 모듈의 전부다.
 *
 * **이것은 정책의 두 번째 사본이 맞다.** 그럼에도 안전한 이유는 이 사본이 *표시 자릿수*만 갖고
 * *반올림 정책*은 갖지 않기 때문이다. 계산이 없으므로 갈라질 값이 없다. 그 전제를 지키는
 * 규칙이 하나 있다 — **스케일을 줄여서 표시하지 않는다.** 줄이는 순간 브라우저가 반올림을
 * 하게 되고, 위 문장이 거짓이 된다.
 *
 * 근거: `docs/spec/phase8-frontend.md` § 8
 */

/** 값 객체의 스케일. 출처는 `conventions.md` 의 표다. */
const PRICE_SCALE = 2;

const QUANTITY_SCALE = 8;

const MONEY_SCALE = 2;

const PERCENT_SCALE = 4;

/**
 * 증거금 대비 위험의 스케일. **넷이 아니라 둘인 것이 요점이다** — 이 값은 도메인이 이미 소수
 * 둘로 깎아서 내려보낸다(`ExchangePosition.MARGIN_RISK_SCALE`). 넷으로 적으면 서버가 없앤
 * 자리를 화면이 `00` 으로 되살려, 재지 않은 정밀도가 있는 것처럼 보이게 한다.
 *
 * **이것은 위 § "스케일을 줄여서 표시하지 않는다" 를 어기는 것이 아니다.** 줄이는 일은 서버가
 * 했고 여기는 그 자릿수를 그대로 되살릴 뿐이다 — `WON_SCALE` 이 0 인 것과 같은 이유다.
 */
const MARGIN_RISK_SCALE = 2;

/**
 * 원화의 스케일. **0 인 것이 요점이다** — 원에는 그 아래 단위가 없고, `Won` 값 객체가 이미
 * 소수점 없이 내려보낸다. 여기서 자릿수를 늘리면 서버가 반올림해 없앤 자리를 화면이
 * `.00` 으로 되살려, 원화에 소수점이 있는 것처럼 보이게 한다.
 */
const WON_SCALE = 0;

/**
 * 손익비처럼 단위가 없는 배수. 값 객체가 아니라 도메인이 직접 정한 자릿수다
 * (`PositionPlan.RATIO_SCALE`, `JournalSummary` 의 손익비도 같다).
 */
const RATIO_SCALE = 2;

/**
 * 로케일을 환경에 맡기지 않는다. 맡기면 같은 값이 사람마다 다르게 보이고, 테스트가 통과하는
 * 컴퓨터와 아닌 컴퓨터가 갈린다. 숫자 표기는 언어 설정이 아니라 이 파일이 정한다.
 */
const LOCALE = "en-US";

function formatter(scale: number, useGrouping: boolean): Intl.NumberFormat {
  return new Intl.NumberFormat(LOCALE, {
    minimumFractionDigits: scale,
    maximumFractionDigits: scale,
    useGrouping,
  });
}

/**
 * 천단위 구분은 **자릿수를 눈으로 세게 되는 수**에 붙인다.
 *
 * 가격과 금액은 같은 표 안에 나란히 놓이고 다섯 자리를 넘는다 — 구분이 없으면 자릿수를 눈으로
 * 세게 된다. 비율은 0~100 이라 붙일 자리가 없다.
 *
 * **수량에도 붙인다. 처음에는 붙이지 않았고 그 근거가 틀렸다** — 원래 주석은 "수량(BTC)은 이
 * 프로젝트의 증거금 규모에서 언제나 1 미만" 이라고 적고 있었다. 내 포지션은 그렇지만
 * **미결제약정은 10만 BTC 대**다. 감시 화면에 `107554.86600000` 이 구분 기호 하나 없이
 * 떴고, 그것을 읽으려면 자릿수를 정확히 눈으로 세야 한다. 전제가 참인 자리(0.115)에서는
 * 구분이 붙을 곳이 없으므로 아무것도 달라지지 않는다.
 *
 * **이것은 스케일을 건드리지 않는다.** 구분 기호는 자릿수를 바꾸지 않고 반올림도 하지
 * 않으므로 위 § "스케일을 줄여서 표시하지 않는다" 에 걸리지 않는다. 소수점 아래 여덟 자리는
 * 그대로 남아 있고, 그 자릿수가 미결제약정에 맞는가는 **도메인이 답할 질문**이다.
 */
const PRICE = formatter(PRICE_SCALE, true);

const QUANTITY = formatter(QUANTITY_SCALE, true);

const MONEY = formatter(MONEY_SCALE, true);

const PERCENT = formatter(PERCENT_SCALE, false);

const MARGIN_RISK = formatter(MARGIN_RISK_SCALE, false);

const RATIO = formatter(RATIO_SCALE, false);

/** 배수 표시. 자릿수 상한은 손익비와 같고 하한만 0 이다 — 끝의 0 이 지워진다. */
const MULTIPLE = new Intl.NumberFormat("ko-KR", {
  minimumFractionDigits: 0,
  maximumFractionDigits: RATIO_SCALE,
});

/** 원화도 금액 크기의 수다. 백만 단위가 예사라 구분이 없으면 자릿수를 눈으로 세게 된다. */
const WON = formatter(WON_SCALE, true);

export function price(value: number): string {
  return PRICE.format(signed(value));
}

export function quantity(value: number): string {
  return QUANTITY.format(signed(value));
}

export function money(value: number): string {
  return MONEY.format(signed(value));
}

export function percent(value: number): string {
  return `${PERCENT.format(signed(value))}%`;
}

/** 증거금 대비 위험(%). 도메인이 소수 둘로 낸 값을 그 자릿수 그대로 적는다. */
export function riskPercent(value: number): string {
  return `${MARGIN_RISK.format(signed(value))}%`;
}

export function ratio(value: number): string {
  return RATIO.format(signed(value));
}

/**
 * 레버리지 같은 배수. **끝의 0 을 지운다** — `44.00배` 는 소수 두 자리가 의미를 갖는 수처럼
 * 보이는데 이 값은 그렇지 않다. `44배` 로 적고, 실제로 소수가 있으면(`44.35`) 그대로 남긴다.
 *
 * **반올림을 새로 하지 않는다.** 최대 자릿수는 손익비와 같은 둘이고 최소만 0 으로 내린 것이라,
 * 서버가 낸 수에서 사라지는 정보가 없다 — `docs/adr/020` 이 금지한 "프론트가 숫자를 만드는 것"
 * 에 걸리지 않는다.
 */
export function multiple(value: number): string {
  return MULTIPLE.format(signed(value));
}

/** 원화 금액. 단위를 붙여 낸다 — USDT 와 나란히 놓이므로 어느 쪽인지가 수에 붙어 있어야 한다. */
export function won(value: number): string {
  return `${WON.format(signed(value))}원`;
}

/** 값이 없다는 표시. **0 과 다른 사실이다** — "손익비가 0" 과 "손익비를 말할 수 없다"는 다르다. */
export const NOTHING = "—";

export function orNothing<T>(value: T | null | undefined, show: (present: T) => string): string {
  return value === null || value === undefined ? NOTHING : show(value);
}

/**
 * 한국 표준시의 UTC 오프셋. **고정 상수인 것이 요점이다** — 한국에는 서머타임이 없으므로
 * 어느 날짜에든 +9 이고, 그래서 시간대 데이터베이스 없이 정확하다.
 */
const KST_OFFSET_MINUTES = 9 * 60;

const MS_PER_MINUTE = 60_000;

/**
 * 시각. **KST 로 표시하고 꼬리표는 붙이지 않는다.**
 *
 * 서버는 언제나 UTC(`Instant`)로 보낸다. 그것을 그대로 띄우면 사람이 화면의 시각과 자기
 * 거래소 화면·자기 시계를 매번 9시간 암산으로 맞춰야 하고, 그 암산은 언젠가 틀린다.
 *
 * **`UTC` 를 붙이던 자리에 `KST` 를 붙이지 않는 이유는 그 꼬리표의 목적이 달라졌기
 * 때문이다.** UTC 는 사용자의 시계와 다른 눈금이라 표시가 필요했다. KST 는 이 도구를 쓰는
 * 사람의 시계 그 자체다 — 매 줄에 붙는 같은 글자는 읽히지 않고 자리만 차지한다. 눈금이
 * 무엇인지 말해야 하는 자리는 **입력 위젯**뿐이고, 거기 라벨에는 `(KST)` 가 남아 있다.
 *
 * **브라우저의 타임존을 쓰지 않는다.** `toLocaleString` 이나 `getHours` 를 쓰면 같은 값이
 * 컴퓨터마다 다르게 보이고, 테스트가 통과하는 기계와 아닌 기계가 갈린다 — 위 `LOCALE` 이
 * 숫자에 대해 정한 것과 같은 태도다. 고정 오프셋을 더한 뒤 UTC 로 읽으면 환경이 끼어들 자리가
 * 없다.
 *
 * **읽어내지 못한 문자열은 그대로 낸다.** `duration` 과 같은 규칙이다 — 화면이 멈추지도, 없는
 * 시각을 지어내지도 않는다.
 *
 * 입력 위젯도 같은 눈금이어야 한다. 짝은 `form/instantAt` 이다.
 */
export function instant(iso: string): string {
  const at = new Date(iso);
  if (Number.isNaN(at.getTime())) {
    return iso;
  }
  const seoul = new Date(at.getTime() + KST_OFFSET_MINUTES * MS_PER_MINUTE).toISOString();
  return `${seoul.slice(0, 10)} ${seoul.slice(11, 16)}`;
}

/**
 * 남은 날짜. `PT456H13M` 을 `D-19` 로 옮긴다.
 *
 * **`duration` 과 다른 질문에 답한다.** 그쪽은 "얼마나 걸렸나"(보유 기간·거래 간격)이고
 * 이쪽은 "며칠 남았나" 다. 열아홉 날 남은 것을 `19일 0시간 13분` 으로 읽으면 사람이 앞의
 * 숫자만 떼어 다시 세게 되고, 그 세기는 남은 시간이 24시간 아래로 내려가는 순간 틀린다.
 *
 * **하루 미만은 `D-0` 이 아니라 시분으로 말한다.** 오늘 안에 벌어질 일에 `D-0` 만 띄우면
 * 세 시간 뒤인지 이십 분 뒤인지가 사라지는데, 그 구분이 필요해지는 유일한 날이 바로 그날이다.
 *
 * 지난 것은 `지남` 이다. 음수 날짜(`D+3`)는 카운트다운처럼 읽히므로 쓰지 않는다.
 */
export function dday(iso: string): string {
  const parts = ISO_DURATION.exec(iso);
  if (!parts) {
    return iso;
  }
  const hours = amount(parts[1]);
  const days = Math.floor(hours / HOURS_PER_DAY);
  if (days > 0) {
    return `D-${days}`;
  }
  return duration(iso);
}

const ISO_DURATION = /^PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)(?:\.\d+)?S)?$/;

const HOURS_PER_DAY = 24;

/**
 * 기간. `PT26H30M` 을 `1일 2시간 30분` 으로 옮긴다.
 *
 * **일 단위까지 올린다** (§ 13.2 의 답). 이 수를 보는 목적은 비교가 아니라 감각이다 — "얼마나
 * 오래 들고 있었나". `26시간` 은 하루가 넘는지가 즉시 읽히지 않는다.
 *
 * **0 이 아닌 단위는 하나도 빠뜨리지 않는다.** 표시를 줄이는 순간 "8시간" 이 8시간 55분을
 * 뜻하게 되고, 그것은 § 8 이 자릿수에 대해 금지한 것과 같은 종류의 거짓말이다.
 *
 * 읽어내지 못한 문자열은 **그대로 낸다.** 화면이 멈추지도, 없는 값을 지어내지도 않는다.
 */
export function duration(iso: string): string {
  const parts = ISO_DURATION.exec(iso);
  if (!parts) {
    return iso;
  }
  const hours = amount(parts[1]);
  const units: [number, string][] = [
    [Math.floor(hours / HOURS_PER_DAY), "일"],
    [hours % HOURS_PER_DAY, "시간"],
    [amount(parts[2]), "분"],
    [amount(parts[3]), "초"],
  ];
  const said = units.filter(([amount]) => amount > 0).map(([amount, unit]) => `${amount}${unit}`);
  return said.length === 0 ? "0초" : said.join(" ");
}

function amount(captured: string | undefined): number {
  return captured === undefined ? 0 : Number.parseInt(captured, 10);
}

/**
 * 음수 영을 영으로 되돌린다.
 *
 * 자바스크립트에는 `-0` 이 있고 `Intl` 은 그것을 `-0.00` 으로 낸다. 손익 표에서 그것은
 * "아주 작은 손실" 로 읽히지만 실제로는 0 이다. 값을 바꾸는 것이 아니라 **없는 부호를 지우는**
 * 것이므로 이 모듈이 하지 않기로 한 계산에 해당하지 않는다.
 */
function signed(value: number): number {
  return Object.is(value, -0) ? 0 : value;
}
