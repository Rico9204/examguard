package examguard.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 특정 학생에게 나가는 문항 하나 (문장, 보기 순서, 정답이 학생마다 다르다). */
public class QuestionVariant {
    private final Question source;
    private final String text;
    private final List<String> choices;
    private final String correctAnswer;

    public QuestionVariant(Question source, String text, List<String> choices, String correctAnswer) {
        this.source = source;
        this.text = text;
        this.choices = Collections.unmodifiableList(new ArrayList<>(choices));
        this.correctAnswer = correctAnswer;
    }

    public Question getSource() { return source; }
    public String getText() { return text; }
    public List<String> getChoices() { return choices; }
    public String getCorrectAnswer() { return correctAnswer; }

    public boolean isMultipleChoice() {
        return !choices.isEmpty();
    }

    public boolean isCorrect(String answer) {
        return answer != null && normalize(answer).equals(correctAnswer);
    }

    /** "23,400 " 같은 입력을 "23400"으로 맞춘다. */
    public static String normalize(String s) {
        return s.trim().replace(",", "").replace(" ", "");
    }
}
