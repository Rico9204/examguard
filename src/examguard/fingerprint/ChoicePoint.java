package examguard.fingerprint;

/** 시험지 안의 선택 지점 하나: 학생의 식별 비트에 따라 A(0) 또는 B(1) 표현이 들어간다. */
public class ChoicePoint {
    private final int questionIndex;
    private final int pointIndex;
    private final String optionA;
    private final String optionB;
    private final int bit;

    public ChoicePoint(int questionIndex, int pointIndex, String optionA, String optionB, int bit) {
        this.questionIndex = questionIndex;
        this.pointIndex = pointIndex;
        this.optionA = optionA;
        this.optionB = optionB;
        this.bit = bit;
    }

    public int getQuestionIndex() { return questionIndex; }
    public int getPointIndex() { return pointIndex; }
    public String getOptionA() { return optionA; }
    public String getOptionB() { return optionB; }
    public int getBit() { return bit; }

    public String getChosen() {
        return bit == 0 ? optionA : optionB;
    }
}
