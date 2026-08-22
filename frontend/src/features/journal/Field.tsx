interface Props {
  readonly label: string;
  readonly value: string;
  readonly onChange: (value: string) => void;
  readonly type?: "number" | "datetime-local" | "text";
}

/** 이 화면 안에서만 쓰는 입력 한 칸. 두 번째 화면이 쓰기 시작하면 그때 `shared/` 로 올린다. */
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
