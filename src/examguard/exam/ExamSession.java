package examguard.exam;

import examguard.model.ExamPaper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 응시 한 번의 진행 상태: 시험지, 답안, 시작 시각, 남은 시간, 문항별 머문 시간.
 * 화면(CbtExamFrame)과 분리해 두어서 화면을 바꿔도 시험 진행 규칙은 그대로다.
 */
public class ExamSession {
    private final ExamPaper paper;
    private final int durationSeconds;
    private final String seat;
    private final boolean keyboardMode;
    private final Map<Integer, String> answers = new HashMap<>();
    private final long[] timeSpentMs;
    private long startedAt;
    private int resumedElapsedSeconds;
    private int currentQuestion = -1;
    private long enteredAt;

    public ExamSession(ExamPaper paper, int durationSeconds, String seat, boolean keyboardMode) {
        this.paper = paper;
        this.durationSeconds = durationSeconds;
        this.seat = seat;
        this.keyboardMode = keyboardMode;
        this.timeSpentMs = new long[paper.getVariants().size()];
    }

    /** 자동 저장된 진행 상황에서 이어서 응시한다 (start() 전에 호출). */
    public void restore(Map<Integer, String> savedAnswers, int elapsedSeconds, long[] savedTimeSpentMs) {
        answers.clear();
        answers.putAll(savedAnswers);
        resumedElapsedSeconds = Math.max(0, Math.min(elapsedSeconds, durationSeconds));
        for (int i = 0; i < timeSpentMs.length && i < savedTimeSpentMs.length; i++) timeSpentMs[i] = savedTimeSpentMs[i];
    }

    public void start() {
        startedAt = System.currentTimeMillis() - resumedElapsedSeconds * 1000L;
    }

    public boolean isStarted() {
        return startedAt > 0;
    }

    /** 시계 기준으로 계산하므로 화면이 잠깐 멈춰도 시간이 정확하다. */
    public int getRemainingSeconds() {
        if (!isStarted()) return durationSeconds - resumedElapsedSeconds;
        long left = durationSeconds * 1000L - (System.currentTimeMillis() - startedAt);
        return (int) Math.max(0, (left + 999) / 1000);
    }

    public int getElapsedSeconds() {
        return durationSeconds - getRemainingSeconds();
    }

    /** 문항을 옮길 때마다 호출: 이전 문항에 머문 시간을 더한다. */
    public void enterQuestion(int index) {
        long now = System.currentTimeMillis();
        if (currentQuestion >= 0) timeSpentMs[currentQuestion] += now - enteredAt;
        currentQuestion = index;
        enteredAt = now;
    }

    /** 제출할 때 호출: 마지막 문항의 시간까지 확정한다. */
    public void stopTiming() {
        enterQuestion(-1);
    }

    public long[] getTimeSpentMs() {
        long[] copy = timeSpentMs.clone();
        if (currentQuestion >= 0) copy[currentQuestion] += System.currentTimeMillis() - enteredAt;
        return copy;
    }

    /** 빈 값이면 답을 지운다. */
    public void setAnswer(int index, String answer) {
        if (answer == null || answer.trim().isEmpty()) answers.remove(index);
        else answers.put(index, answer.trim());
    }

    public String getAnswer(int index) { return answers.get(index); }
    public boolean isAnswered(int index) { return answers.containsKey(index); }
    public int getAnsweredCount() { return answers.size(); }
    public Map<Integer, String> getAnswers() { return new HashMap<>(answers); }

    public List<Integer> getUnanswered() {
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < getQuestionCount(); i++) if (!isAnswered(i)) list.add(i);
        return list;
    }

    public ExamPaper getPaper() { return paper; }
    public int getQuestionCount() { return paper.getVariants().size(); }
    public int getDurationSeconds() { return durationSeconds; }
    public String getSeat() { return seat; }
    /** 편의 제공(방향키 이동 허용) 모드로 응시했는지 */
    public boolean isKeyboardMode() { return keyboardMode; }
    public long getStartedAt() { return startedAt; }
}
