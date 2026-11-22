package examguard.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 여러 판정 규칙의 점수를 가중 평균해 0~100점 의심 점수를 만든다. */
public class SuspicionScorer {
    public static final int MIN_SAMPLES = 40;

    private final List<ScanRule> rules = new ArrayList<>();
    private final List<Double> weights = new ArrayList<>();

    public SuspicionScorer addRule(ScanRule rule, double weight) {
        rules.add(rule);
        weights.add(weight);
        return this;
    }

    /** 화면 노출 규칙이 가장 직접적인 근거라서 가중치 2 */
    public static SuspicionScorer createDefault() {
        return new SuspicionScorer()
                .addRule(new SteadySpeedRule(), 1)
                .addRule(new MonotonicSweepRule(), 1)
                .addRule(new FastCoverageRule(), 2);
    }

    public List<ScanRule> getRules() {
        return Collections.unmodifiableList(rules);
    }

    public SuspicionReport evaluate(List<Sample> all, int width, int height) {
        List<Sample> mouse = new ArrayList<>();
        int keys = 0, scrolls = 0;
        for (Sample s : all) {
            if (s.getSource() == Sample.Source.MOUSE) mouse.add(s);
            else if (s.getSource() == Sample.Source.KEY) keys++;
            else scrolls++;
        }
        String inputs = String.format("마우스 %d · 방향키 %d · 스크롤 %d", mouse.size(), keys, scrolls);
        if (width <= 0 || height <= 0) {
            width = 760;
            height = 360;
        }

        // 방향키만 써도 "데이터 부족"으로 빠져나가지 못하도록 전체 입력으로 최소 개수를 센다.
        List<RuleResult> results = new ArrayList<>();
        if (all.size() < MIN_SAMPLES) {
            for (ScanRule r : rules) results.add(new RuleResult(r.getName(), 0, "데이터 부족"));
            return new SuspicionReport(0, "데이터 부족", results, all.size(), inputs, false);
        }

        double sum = 0, weightSum = 0;
        for (int i = 0; i < rules.size(); i++) {
            ScanRule rule = rules.get(i);
            RuleResult r = rule.evaluate(rule.usesAllInputs() ? all : mouse, width, height);
            results.add(r);
            sum += r.getScore() * weights.get(i);
            weightSum += weights.get(i);
        }
        int total = (int) Math.round(sum / weightSum * 100);
        String level = total >= 70 ? "의심" : total >= 40 ? "주의" : "정상";
        return new SuspicionReport(total, level, results, all.size(), inputs, true);
    }
}
