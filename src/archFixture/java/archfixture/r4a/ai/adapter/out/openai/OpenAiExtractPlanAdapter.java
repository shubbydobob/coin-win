package archfixture.r4a.ai.adapter.out.openai;

/** 규칙 4 픽스처의 대상. ai.application 이 이 어댑터를 직접 참조하면 헥사고날이 무너진다. */
public class OpenAiExtractPlanAdapter {
    public String extract() {
        return "plan";
    }
}
