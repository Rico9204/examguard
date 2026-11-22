package examguard.fingerprint;

import examguard.model.ExamPaper;
import examguard.model.QuestionVariant;
import examguard.model.Student;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 채점 후 검사: 자기 시험지로는 틀렸는데, 다른 학생 시험지의 정답과 똑같은 답을 적었는가?
 *
 * 오탐을 줄이는 규칙 (검토에서 지적된 문제 반영):
 *  1. 흔한 실수로 나올 수 있는 답은 제외한다: 본인 정답 ±1(예: n-1에서 -1을 빼먹음), ×10, ÷10(자릿수 실수),
 *     그리고 본인 문제에 적힌 숫자를 그대로 적은 경우.
 *  2. 한 문항만 겹치면 우연일 수 있으므로 보고하지 않는다.
 *     **같은 학생의 정답과 2문항 이상** 겹칠 때만 보고한다 (남의 시험지를 보고 베낀 경우의 모양).
 * 객관식은 보기 순서가 섞여 있어 번호가 의미 없으므로 숫자 문항만 본다.
 */
public final class AnswerCheck {
    private static final int MIN_MATCHED_QUESTIONS = 2;

    private AnswerCheck() {}

    public static List<String> findForeignAnswers(ExamPaper own, Map<Integer, String> answers, List<ExamPaper> all) {
        // 다른 학생 → 그 학생 정답과 겹친 문항 번호들
        Map<Student, List<Integer>> matches = new LinkedHashMap<>();

        for (int i = 0; i < own.getVariants().size(); i++) {
            QuestionVariant v = own.getVariants().get(i);
            String raw = answers.get(i);
            if (v.isMultipleChoice() || raw == null || raw.trim().isEmpty() || v.isCorrect(raw)) continue;

            long answer;
            try {
                answer = Long.parseLong(QuestionVariant.normalize(raw));
            } catch (NumberFormatException e) {
                continue;
            }
            if (isCommonSlip(answer, Long.parseLong(v.getCorrectAnswer())) || appearsInQuestion(own, i, answer)) {
                continue;
            }
            for (ExamPaper p : all) {
                if (p.getStudent().getId() == own.getStudent().getId()) continue;
                if (p.getVariants().get(i).getCorrectAnswer().equals(String.valueOf(answer))) {
                    matches.computeIfAbsent(p.getStudent(), k -> new ArrayList<>()).add(i);
                }
            }
        }

        List<String> found = new ArrayList<>();
        for (Map.Entry<Student, List<Integer>> e : matches.entrySet()) {
            if (e.getValue().size() < MIN_MATCHED_QUESTIONS) continue;
            StringBuilder qs = new StringBuilder();
            for (int q : e.getValue()) qs.append(qs.length() > 0 ? ", " : "").append("문제 ").append(q + 1);
            found.add(String.format("%s 시험지의 정답과 %d문항 일치 (%s) — 본인 시험지로는 모두 오답",
                    e.getKey(), e.getValue().size(), qs));
        }
        return found;
    }

    static boolean isCommonSlip(long answer, long correct) {
        return Math.abs(answer - correct) <= 1 || answer == correct * 10 || answer * 10 == correct;
    }

    private static boolean appearsInQuestion(ExamPaper own, int questionIndex, long answer) {
        for (NumberMark m : own.getNumberMarks()) {
            if (m.getQuestionIndex() == questionIndex && m.getValue() == answer) return true;
        }
        return false;
    }
}
