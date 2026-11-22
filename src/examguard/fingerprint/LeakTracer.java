package examguard.fingerprint;

import examguard.model.ExamPaper;
import examguard.model.Student;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * 유출된 시험지 텍스트(전체 또는 일부)를 받아 누구의 시험지인지 추정한다.
 * 1) 선택 지점마다 A/B 중 어느 표현이 들어 있는지 읽어 비트를 얻고
 * 2) 그 문항에 들어간 숫자가 유출본에 있는지 확인한 뒤
 * 3) 학생별 시험지와 비교해 일치율로 순위를 매긴다.
 */
public class LeakTracer {
    private final ExamGenerator generator;
    private final List<Student> roster;
    private List<ExamPaper> papers;

    public LeakTracer(ExamGenerator generator, List<Student> roster) {
        this.generator = generator;
        this.roster = roster;
    }

    /** 명단 전체의 시험지 (같은 시드로 다시 만들 수 있으므로 저장 없이 재생성) */
    public synchronized List<ExamPaper> getPapers() {
        if (papers == null) {
            List<ExamPaper> list = new ArrayList<>();
            for (Student s : roster) list.add(generator.generate(s));
            papers = Collections.unmodifiableList(list);
        }
        return papers;
    }

    public TraceReport trace(String leakText) {
        String leak = ExamGenerator.normalize(leakText == null ? "" : leakText);
        List<ExamPaper> all = getPapers();
        if (all.isEmpty() || leak.isEmpty()) {
            return new TraceReport(Collections.<TraceResult>emptyList(), 0, new TreeSet<Integer>());
        }

        // 1) 선택 지점 판독: 둘 중 정확히 하나만 보일 때만 판독한 것으로 친다.
        Map<Integer, Integer> observed = new LinkedHashMap<>();
        Set<Integer> detectedQuestions = new TreeSet<>();
        for (ChoicePoint cp : all.get(0).getChoicePoints()) {
            boolean hasA = leak.contains(ExamGenerator.normalize(cp.getOptionA()));
            boolean hasB = leak.contains(ExamGenerator.normalize(cp.getOptionB()));
            if (hasA != hasB) {
                observed.put(cp.getPointIndex(), hasA ? 0 : 1);
                detectedQuestions.add(cp.getQuestionIndex());
            }
        }

        // 2) 학생별 대조
        List<TraceResult> results = new ArrayList<>();
        for (ExamPaper paper : all) {
            int matchedPoints = 0;
            for (Map.Entry<Integer, Integer> e : observed.entrySet()) {
                if (paper.getChoicePoints().get(e.getKey()).getBit() == e.getValue()) matchedPoints++;
            }
            int matchedNumbers = 0;
            int observedNumbers = 0;
            for (NumberMark mark : paper.getNumberMarks()) {
                if (!detectedQuestions.contains(mark.getQuestionIndex())) continue;
                observedNumbers++;
                if (containsNumber(leak, mark.getValue())) matchedNumbers++;
            }
            results.add(new TraceResult(paper.getStudent(), matchedPoints, observed.size(),
                    matchedNumbers, observedNumbers));
        }
        results.sort((a, b) -> {
            int c = Double.compare(b.getScore(), a.getScore());
            return c != 0 ? c : Integer.compare(b.getMatchedPoints(), a.getMatchedPoints());
        });
        return new TraceReport(results, observed.size(), detectedQuestions);
    }

    /** 앞뒤가 숫자가 아닌 곳에서 정확히 그 숫자가 나오는지 (23이 230에 걸리지 않도록) */
    static boolean containsNumber(String text, int value) {
        return Pattern.compile("(?<!\\d)" + value + "(?!\\d)").matcher(text).find();
    }

    public ExamGenerator getGenerator() { return generator; }
}
