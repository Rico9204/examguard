package examguard.fingerprint;

/** 학생마다 다르게 들어간 숫자 하나 (유출본에서 이 숫자를 찾아 대조한다). */
public class NumberMark {
    private final int questionIndex;
    private final String name;
    private final int value;

    public NumberMark(int questionIndex, String name, int value) {
        this.questionIndex = questionIndex;
        this.name = name;
        this.value = value;
    }

    public int getQuestionIndex() { return questionIndex; }
    public String getName() { return name; }
    public int getValue() { return value; }
}
