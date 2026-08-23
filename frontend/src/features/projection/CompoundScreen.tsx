import { useMutation } from "@tanstack/react-query";
import { useState } from "react";

import { post } from "../../api/client";
import { ApiFailure } from "../../api/problem";
import { Field } from "../../shared/Field";
import { CompoundResult } from "./CompoundResult";
import { STARTING_FORM, toRequest } from "./compoundForm";
import type { CompoundForm } from "./compoundForm";
import type { components } from "../../api/schema";

type Result = components["schemas"]["CompoundTargetResponse"];

/**
 * 목표 복리. **월 목표를 정해 놓고 거꾸로 푼다.**
 *
 * 승률도 손익비도 묻지 않는다. 묻는 것은 "얼마를 원하는가" 와 "무엇을 내고 하는가" 뿐이고,
 * 답은 "그러려면 거래 한 건마다 가격이 얼마나 움직여야 하는가" 다.
 *
 * **비용 세 칸(수수료·슬리피지·레버리지)이 이 화면의 이유다.** 목표만 넣고 복리를 굴리면
 * 어떤 목표든 그럴듯한 곡선이 나온다. 레버리지가 그 목표에 필요한 가격 변동을 줄여 주는
 * 대신 수수료를 그만큼 키운다는 사실은, 셋을 함께 넣어야만 수로 나온다.
 */
export function CompoundScreen() {
  const [form, setForm] = useState<CompoundForm>(STARTING_FORM);
  const field = <K extends keyof CompoundForm>(key: K, value: CompoundForm[K]) =>
    setForm({ ...form, [key]: value });

  const target = useMutation<Result, Error>({
    mutationFn: () => post("/api/projections/compound-target", toRequest(form)),
  });

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-4 gap-2">
        <Field
          label="시작 자산 (USDT)"
          value={form.startingCapital}
          onChange={(v) => field("startingCapital", v)}
        />
        <Field
          label="월 목표 수익률 (%, 내 돈 기준)"
          value={form.monthlyTarget}
          onChange={(v) => field("monthlyTarget", v)}
        />
        <Field label="기간 (개월)" value={form.months} onChange={(v) => field("months", v)} />
        <Field
          label="레버리지 (배)"
          value={form.leverage}
          onChange={(v) => field("leverage", v)}
        />
        <Field
          label="거래당 투입 비율 (%)"
          value={form.marginUsage}
          onChange={(v) => field("marginUsage", v)}
        />
        <Field
          label="수수료 (%, 한 쪽)"
          value={form.feeRate}
          onChange={(v) => field("feeRate", v)}
        />
        <Field
          label="슬리피지 (%, 한 쪽)"
          value={form.slippage}
          onChange={(v) => field("slippage", v)}
        />
        <Field
          label="월 거래 수 (진입→청산 1건)"
          value={form.tradesPerMonth}
          onChange={(v) => field("tradesPerMonth", v)}
        />
      </div>

      {/*
        기본값의 출처를 화면에 적는다. 어디서 온 수인지 모르면 고칠 때가 됐는지도 모른다.

        **눈금이 둘이라는 것도 적는다.** 목표는 내 돈 기준이고 수수료는 명목 기준이다 —
        같은 화면의 두 수가 다른 것에 대한 비율인데 라벨만으로는 그게 안 보인다.
      */}
      <p className="text-xs leading-relaxed text-ink-3">
        목표 수익률은 명목이 아니라 <b>내 돈(증거금)</b> 기준이다 — 월 10% 면 800 이 880 이
        된다. 레버리지는 그 목표를 바꾸지 않고, 그러려면 가격이 얼마나 움직여야 하는지를
        바꾼다. 반대로 <b>수수료와 슬리피지는 명목</b>에 붙고 진입·청산 두 번 난다
        (그래서 월 거래 수 20 은 주문 40번이다).
        {" "}
        <b>명목 = 자산 × 투입 비율 × 레버리지</b> 이므로, 매 거래에 전액을 넣지 않으면 그만큼
        비용이 준다. 수수료 기본값은 바이낸스 USDⓈ-M 무기한의 일반 사용자 테이커(0.05%)이며
        지정가로만 들어가면 메이커 0.02% 다. <b>투입 비율 20% 와 슬리피지 0.02% 는 근거 있는
        수가 아니라 자리표시자다</b> — 자기 매매에서 재서 고쳐 넣는다.
      </p>

      <button
        type="button"
        onClick={() => target.mutate()}
        disabled={target.isPending}
        className="rounded-md bg-accent px-3 py-1.5 text-sm font-medium text-accent-ink disabled:opacity-50"
      >
        {target.isPending ? "계산하는 중" : "계산"}
      </button>

      {target.error && (
        <p role="alert" className="text-sm text-down">
          {target.error instanceof ApiFailure ? target.error.problem.detail : target.error.message}
        </p>
      )}

      {target.data && <CompoundResult result={target.data} />}
    </div>
  );
}
