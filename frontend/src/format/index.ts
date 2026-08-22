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
 * 천단위 구분은 **금액 크기의 수**에만 붙인다.
 *
 * 가격과 금액은 같은 표 안에 나란히 놓이고 다섯 자리를 넘는다 — 구분이 없으면 자릿수를 눈으로
 * 세게 된다. 수량(BTC)은 이 프로젝트의 증거금 규모에서 언제나 1 미만이고, 비율은 0~100 이라
 * 붙일 자리가 없다.
 */
const PRICE = formatter(PRICE_SCALE, true);

const QUANTITY = formatter(QUANTITY_SCALE, false);

const MONEY = formatter(MONEY_SCALE, true);

const PERCENT = formatter(PERCENT_SCALE, false);

const RATIO = formatter(RATIO_SCALE, false);

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

export function ratio(value: number): string {
  return RATIO.format(signed(value));
}

/** 값이 없다는 표시. **0 과 다른 사실이다** — "손익비가 0" 과 "손익비를 말할 수 없다"는 다르다. */
export const NOTHING = "—";

export function orNothing<T>(value: T | null | undefined, show: (present: T) => string): string {
  return value === null || value === undefined ? NOTHING : show(value);
}

/**
 * 시각. **UTC 로 표시하고 `UTC` 를 붙인다.**
 *
 * 로컬 타임존으로 바꾸면 캔들 시각·체결 시각과 어긋나 보인다. 문자열을 자르기만 하고 `Date` 로
 * 파싱하지 않는 것이 요점이다 — 파싱하는 순간 브라우저의 타임존이 끼어든다.
 */
export function instant(iso: string): string {
  return `${iso.slice(0, 10)} ${iso.slice(11, 16)} UTC`;
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
