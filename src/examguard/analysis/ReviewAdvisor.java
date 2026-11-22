package examguard.analysis;

import examguard.model.ExamPaper;
import examguard.model.QuestionVariant;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 감독관 보조: 여러 신호를 모아 "확인 권장" 항목을 만든다.
 * 이 결과는 판정이 아니라 감독관이 누구를, 무엇을 먼저 볼지 정하는 우선순위다.
 * 최종 판단과 불이익 처분은 반드시 사람이 확인한 뒤에 한다.
 */
public final class ReviewAdvisor {
    /** 예상 풀이 시간의 이 비율보다 빨리 맞히면 "너무 빠른 정답"으로 본다 */
    private static final double TOO_FAST_RATIO = 0.25;

    private ReviewAdvisor() {}

    public static List<String> advise(ExamPaper paper, Map<Integer, String> answers, long[] timeSpentMs,
                                      SuspicionReport report, List<String> foreignAnswers, boolean keyboardMode) {
        List<String> flags = new ArrayList<>();

        if (report.isSufficient() && report.getTotal() >= 70) {
            flags.add("화면을 빠르게 훑는 행동 (의심 점수 " + report.getTotal() + ") — 녹화 시도 가능성, 궤적 재생으로 확인");
        }
        for (String f : foreignAnswers) {
            flags.add("다른 학생 정답과 일치: " + f);
        }
        // 숫자 문항만 본다: 객관식은 찍어서 빨리 맞힐 수도 있어 신호가 약하다
        for (int i = 0; i < paper.getVariants().size() && i < timeSpentMs.length; i++) {
            QuestionVariant v = paper.getVariants().get(i);
            if (v.isMultipleChoice() || !v.isCorrect(answers.get(i))) continue;
            double seconds = timeSpentMs[i] / 1000.0;
            int expected = v.getSource().getExpectedSeconds();
            if (seconds < expected * TOO_FAST_RATIO) {
                flags.add(String.format("문제 %d: 보통 %d초 걸리는 계산 문제를 %.0f초 만에 정답 — 풀이 과정 확인",
                        i + 1, expected, seconds));
            }
        }
        if (keyboardMode) {
            flags.add("편의 제공(방향키 이동) 모드 응시 — 별도 감독 기록과 함께 검토");
        }
        return flags;
    }
}
