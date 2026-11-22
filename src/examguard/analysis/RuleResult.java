package examguard.analysis;

/** 규칙 하나의 판정 결과. score는 0(정상) ~ 1(의심) */
public class RuleResult {
    private final String ruleName;
    private final double score;
    private final String detail;

    public RuleResult(String ruleName, double score, String detail) {
        this.ruleName = ruleName;
        this.score = clamp01(score);
        this.detail = detail;
    }

    public String getRuleName() { return ruleName; }
    public double getScore() { return score; }
    public String getDetail() { return detail; }

    public static double clamp01(double v) {
        return Math.max(0, Math.min(1, v));
    }
}
