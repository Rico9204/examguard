package examguard.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 의심 점수 결과 */
public class SuspicionReport {
    private final int total;
    private final String level;
    private final List<RuleResult> results;
    private final int sampleCount;
    private final String inputSummary;
    private final boolean sufficient;

    public SuspicionReport(int total, String level, List<RuleResult> results, int sampleCount,
                           String inputSummary, boolean sufficient) {
        this.total = total;
        this.level = level;
        this.results = Collections.unmodifiableList(new ArrayList<>(results));
        this.sampleCount = sampleCount;
        this.inputSummary = inputSummary;
        this.sufficient = sufficient;
    }

    public int getTotal() { return total; }
    public String getLevel() { return level; }
    public List<RuleResult> getResults() { return results; }
    public int getSampleCount() { return sampleCount; }
    /** "마우스 120 · 방향키 30 · 스크롤 4" */
    public String getInputSummary() { return inputSummary; }
    public boolean isSufficient() { return sufficient; }
}
