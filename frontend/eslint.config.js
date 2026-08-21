import js from "@eslint/js";
import reactHooks from "eslint-plugin-react-hooks";
import globals from "globals";
import tseslint from "typescript-eslint";

// Phase 8 § 3.1 의 프로젝트 고유 금지 규칙(toFixed · 응답값 파싱 · feature 간 직접 import)은
// 7 단계에서 들어온다. 화면이 하나 있어야 규칙이 현실적인지 알 수 있기 때문이다.
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
  },
);
