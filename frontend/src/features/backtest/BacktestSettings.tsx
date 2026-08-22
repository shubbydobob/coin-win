import { Field } from "../../shared/Field";
import type { BacktestForm } from "./backtestForm";

interface Props {
  readonly form: BacktestForm;
  readonly onChange: (form: BacktestForm) => void;
}

/** 백테스트 설정. 화면에 보이는 수가 곧 요청에 실리는 수다. */
export function BacktestSettings({ form, onChange }: Props) {
  const field = <K extends keyof BacktestForm>(key: K, value: BacktestForm[K]) =>
    onChange({ ...form, [key]: value });

  return (
    <div className="space-y-3">
      <div className="grid grid-cols-4 gap-2">
        <Field label="종목" type="text" value={form.symbol} onChange={(v) => field("symbol", v)} />
        <Field label="주기" type="text" value={form.interval} onChange={(v) => field("interval", v)} />
        <label className="block text-xs text-slate-500">
          구간 시작
          <input
            type="date"
            value={form.from}
            onChange={(event) => field("from", event.target.value)}
            className="mt-1 block w-full rounded border border-slate-300 px-2 py-1 text-sm"
          />
        </label>
        <label className="block text-xs text-slate-500">
          구간 끝
          <input
            type="date"
            value={form.to}
            onChange={(event) => field("to", event.target.value)}
            className="mt-1 block w-full rounded border border-slate-300 px-2 py-1 text-sm"
          />
        </label>
      </div>

      <div className="grid grid-cols-4 gap-2">
        <Field label="피벗 좌우 봉수" value={form.pivotLookback} onChange={(v) => field("pivotLookback", v)} />
        <Field label="군집 ATR 배수" value={form.clusterMultiple} onChange={(v) => field("clusterMultiple", v)} />
        <Field label="최소 터치 수" value={form.minTouches} onChange={(v) => field("minTouches", v)} />
        <Field label="ATR 구간" value={form.atrPeriod} onChange={(v) => field("atrPeriod", v)} />
      </div>

      <div className="grid grid-cols-4 gap-2">
        <Field label="손절 버퍼 ATR 배수" value={form.stopBufferMultiple} onChange={(v) => field("stopBufferMultiple", v)} />
        <Field label="최소 손익비" value={form.minRiskReward} onChange={(v) => field("minRiskReward", v)} />
        <Field label="초기 자본" value={form.initialCapital} onChange={(v) => field("initialCapital", v)} />
        <Field label="거래당 리스크" value={form.riskPercent} onChange={(v) => field("riskPercent", v)} />
      </div>

      <div className="grid grid-cols-4 items-end gap-2">
        <Field label="백테스트 레버리지" value={form.leverage} onChange={(v) => field("leverage", v)} />
        <label className="block text-xs text-slate-500">
          자본 방식
          <select
            value={form.capitalMode}
            onChange={(event) => field("capitalMode", event.target.value as BacktestForm["capitalMode"])}
            className="mt-1 block w-full rounded border border-slate-300 px-2 py-1 text-sm"
          >
            <option value="FIXED">고정</option>
            <option value="COMPOUND">복리</option>
          </select>
        </label>
        <label className="flex items-center gap-2 text-xs text-slate-500">
          <input
            type="checkbox"
            checked={form.indicatorFilter}
            onChange={(event) => field("indicatorFilter", event.target.checked)}
          />
          지표 필터를 진입 게이트로
        </label>
      </div>
    </div>
  );
}
