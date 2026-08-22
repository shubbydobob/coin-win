interface Props {
  readonly label: string;
  readonly value: string;
  readonly onChange: (value: string) => void;
  readonly type?: "number" | "datetime-local" | "text";
}

/**
 * 입력 한 칸. `/journal` 과 `/backtest` 가 함께 쓰기 시작한 시점에 `shared/` 로 올렸다 —
 * 두 번째 사용처가 생겼을 때만 만든다는 § 4 의 규칙 그대로다.
 */
export function Field({ label, value, onChange, type = "number" }: Props) {
  return (
    <label className="block text-xs text-slate-500">
      {label}
      <input
        type={type}
        step={type === "number" ? "any" : undefined}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        className="mt-1 block w-full rounded border border-slate-300 px-2 py-1 text-sm"
      />
    </label>
  );
}
