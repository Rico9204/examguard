package examguard.fingerprint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** 유출 추적 전체 결과 (순위 + 판독 정보 + 신뢰도) */
public class TraceReport {
    private final List<TraceResult> results;
    private final int observedPoints;
    private final Set<Integer> detectedQuestions;

    public TraceReport(List<TraceResult> results, int observedPoints, Set<Integer> detectedQuestions) {
        this.results = Collections.unmodifiableList(new ArrayList<>(results));
        this.observedPoints = observedPoints;
        this.detectedQuestions = Collections.unmodifiableSet(new TreeSet<>(detectedQuestions));
    }

    public List<TraceResult> getResults() { return results; }
    public int getObservedPoints() { return observedPoints; }
    public Set<Integer> getDetectedQuestions() { return detectedQuestions; }

    public int getObservedNumbers() {
        return results.isEmpty() ? 0 : results.get(0).getObservedNumbers();
    }

    public boolean hasEvidence() {
        return observedPoints + getObservedNumbers() > 0;
    }

    /** 1위와 2위의 차이로 신뢰도를 매긴다. 단독 처벌 근거가 아니라 조사 시작 근거로 쓴다. */
    public String getConfidence() {
        if (!hasEvidence() || results.isEmpty()) return "판단 불가 (시험지 식별 표시를 찾지 못함)";
        double top = results.get(0).getScore();
        double second = results.size() > 1 ? results.get(1).getScore() : 0;
        if (top >= 0.99 && top - second >= 0.25) return "높음";
        if (top - second >= 0.15) return "보통";
        return "낮음 (후보가 여러 명입니다. 유출본이 더 필요합니다)";
    }
}
