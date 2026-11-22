package examguard.model;

import examguard.fingerprint.ChoicePoint;
import examguard.fingerprint.NumberMark;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** 한 학생에게 배부되는 개인화 시험지. 추적에 필요한 표시 정보도 함께 가진다. */
public class ExamPaper {
    public static final String[] CIRCLED = {"①", "②", "③", "④", "⑤", "⑥"};

    private final Student student;
    private final List<QuestionVariant> variants;
    private final int[] code;
    private final List<ChoicePoint> choicePoints;
    private final List<NumberMark> numberMarks;

    public ExamPaper(Student student, List<QuestionVariant> variants, int[] code,
                     List<ChoicePoint> choicePoints, List<NumberMark> numberMarks) {
        this.student = student;
        this.variants = Collections.unmodifiableList(new ArrayList<>(variants));
        this.code = code.clone();
        this.choicePoints = Collections.unmodifiableList(new ArrayList<>(choicePoints));
        this.numberMarks = Collections.unmodifiableList(new ArrayList<>(numberMarks));
    }

    public Student getStudent() { return student; }
    public List<QuestionVariant> getVariants() { return variants; }
    public int[] getCode() { return code.clone(); }
    public List<ChoicePoint> getChoicePoints() { return choicePoints; }
    public List<NumberMark> getNumberMarks() { return numberMarks; }

    /** answers: 문항 번호(0부터) → 학생이 적은 답 */
    public int grade(Map<Integer, String> answers) {
        int score = 0;
        for (int i = 0; i < variants.size(); i++) {
            QuestionVariant v = variants.get(i);
            if (v.isCorrect(answers.get(i))) {
                score += v.getSource().getPoints();
            }
        }
        return score;
    }

    public int getMaxScore() {
        int sum = 0;
        for (QuestionVariant v : variants) sum += v.getSource().getPoints();
        return sum;
    }

    public String getCodeString() {
        StringBuilder sb = new StringBuilder();
        for (int bit : code) sb.append(bit);
        return sb.toString();
    }

    public String answerLabel(int index) {
        QuestionVariant v = variants.get(index);
        if (!v.isMultipleChoice()) return v.getCorrectAnswer();
        int k = Integer.parseInt(v.getCorrectAnswer()) - 1;
        return CIRCLED[k] + " " + v.getChoices().get(k);
    }

    /** 미리보기 화면용 텍스트 */
    public String toPreviewText() {
        StringBuilder sb = new StringBuilder();
        sb.append("응시자: ").append(student).append('\n');
        sb.append("식별 코드: ").append(getCodeString()).append("  (선택 지점마다 0=앞 표현, 1=뒤 표현)\n\n");
        for (int i = 0; i < variants.size(); i++) {
            QuestionVariant v = variants.get(i);
            sb.append("문제 ").append(i + 1).append(" [").append(v.getSource().getTypeName())
              .append(", ").append(v.getSource().getPoints()).append("점");
            if (!v.getSource().getCategory().isEmpty()) sb.append(", ").append(v.getSource().getCategory());
            sb.append("]\n");
            sb.append(v.getText()).append('\n');
            for (int j = 0; j < v.getChoices().size(); j++) {
                sb.append("   ").append(CIRCLED[j]).append(' ').append(v.getChoices().get(j)).append('\n');
            }
            sb.append("   → 정답: ").append(answerLabel(i)).append("\n\n");
        }
        return sb.toString();
    }
}
