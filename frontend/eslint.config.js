import js from "@eslint/js";
import reactHooks from "eslint-plugin-react-hooks";
import globals from "globals";
import tseslint from "typescript-eslint";

/**
 * 규칙은 기억이 아니라 빌드가 지킨다 — `.claude/docs/roadmap.md` Phase 0 의 태도를 프론트에
 * 옮긴 것이다. 아래 세 규칙이 명세 § 3.1 의 표다.
 *
 * 규칙이 실제로 발동하는지는 `eslint-fixture/` 의 일부러 어기는 파일과
 * `test/eslint-rules.test.ts` 가 증명한다. 통과하는 것처럼 보이면서 아무것도 매칭하지 않는
 * 린트 규칙을 잡기 위해서다.
 */
const ROUNDING = "반올림은 src/format/ 안에서만 한다. 서버가 정한 스케일을 화면이 다시 굴리지 않는다 (명세 § 3.1)";

const PARSING = "응답 값을 수로 되돌리지 않는다. 입력 파싱은 src/form/ 이다 (명세 § 3.1)";

const BOUNDARY = "feature 끼리 직접 부르지 않는다. 공유가 필요하면 src/shared/ 로 올린다 (명세 § 3.1)";

/**
 * 반올림하는 도구 전부.
 *
 * `Intl.NumberFormat` 이 목록에 있는 것이 요점이다 — 명세 초안은 `toFixed` 만 적었는데
 * `format/` 이 실제로 쓰는 것은 `Intl` 이다. 그것을 빼 두면 규칙이 **가장 흔한 반올림 경로를
 * 그냥 통과시킨다.**
 */
const ROUNDS = [
  {
    selector: "NewExpression[callee.object.name='Intl'][callee.property.name='NumberFormat']",
    message: ROUNDING,
  },
];

const ROUNDS_PROPERTIES = [
  { property: "toFixed", message: ROUNDING },
  { object: "Math", property: "round", message: ROUNDING },
  { object: "Math", property: "floor", message: ROUNDING },
  { object: "Math", property: "ceil", message: ROUNDING },
];

/** 문자열을 수로 바꾸는 도구 전부. `Number.isNaN` 은 호출이 아니라 멤버라서 걸리지 않는다. */
const PARSES = [
  { selector: "CallExpression[callee.name='Number']", message: PARSING },
  { selector: "CallExpression[callee.name='parseFloat']", message: PARSING },
  { selector: "CallExpression[callee.name='parseInt']", message: PARSING },
];

/**
 * `architecture.md` 의 "그 외 모듈 간 직접 참조 금지" 를 프론트에 옮긴 것. 이름을 손으로
 * 열거하는 것은 ArchUnit 규칙 4 와 같은 형태다 — 그래서 픽스처도 그와 같이 하나 둔다.
 */
const FEATURES = ["overview", "plan", "journal", "backtest", "projection"];

const featureBoundaries = FEATURES.map((feature) => ({
  files: [`**/features/${feature}/**`],
  rules: {
    "no-restricted-imports": [
      "error",
      {
        patterns: [
          {
            group: FEATURES.filter((other) => other !== feature).flatMap((other) => [
              `../${other}/*`,
              `**/features/${other}/*`,
            ]),
            message: BOUNDARY,
          },
        ],
      },
    ],
  },
}));

export default tseslint.config(
  { ignores: ["dist/**", "node_modules/**", "src/api/schema.d.ts"] },
  js.configs.recommended,
  tseslint.configs.recommended,
  reactHooks.configs.flat["recommended-latest"],
  {
    files: ["**/*.{ts,tsx}"],
    languageOptions: {
      ecmaVersion: 2022,
      globals: globals.browser,
    },
    rules: {
      "no-restricted-properties": ["error", ...ROUNDS_PROPERTIES],
      "no-restricted-syntax": ["error", ...ROUNDS, ...PARSES],
    },
  },
  // 예외는 방향마다 하나씩이고, 서로의 금지는 그대로 남는다 — 형식 모듈이 응답을 파싱하거나
  // 입력 모듈이 반올림하기 시작하면 그것도 규칙 위반이다.
  {
    files: ["src/format/**"],
    rules: {
      "no-restricted-properties": "off",
      "no-restricted-syntax": ["error", ...PARSES],
    },
  },
  {
    files: ["src/form/**"],
    rules: {
      "no-restricted-syntax": ["error", ...ROUNDS],
    },
  },
  ...featureBoundaries,
);
