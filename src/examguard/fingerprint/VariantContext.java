package examguard.fingerprint;

import examguard.model.Student;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** 시험지 한 부를 만드는 동안 각 표시 층(FingerprintLayer)이 공유하는 상태. */
public class VariantContext {
    private final Student student;
    private final Random random;
    private final int[] code;
    private int bitCursor;
    private int questionIndex;
    private final Map<String, Integer> variables = new HashMap<>();
    private final List<ChoicePoint> choicePoints = new ArrayList<>();
    private final List<NumberMark> numberMarks = new ArrayList<>();

    public VariantContext(Student student, Random random, int[] code) {
        this.student = student;
        this.random = random;
        this.code = code.clone();
    }

    public void beginQuestion(int index) {
        questionIndex = index;
        variables.clear();
    }

    /** 현재 상태를 기억한다 (문항 조건에 맞지 않아 숫자를 다시 뽑을 때 되돌리기 위해) */
    public int[] mark() {
        return new int[]{choicePoints.size(), numberMarks.size(), bitCursor};
    }

    public void rollback(int[] mark) {
        while (choicePoints.size() > mark[0]) choicePoints.remove(choicePoints.size() - 1);
        while (numberMarks.size() > mark[1]) numberMarks.remove(numberMarks.size() - 1);
        bitCursor = mark[2];
        variables.clear();
    }

    /** 학생 식별 코드의 다음 비트 (코드보다 선택 지점이 많으면 처음부터 반복) */
    public int nextBit() {
        if (code.length == 0) return 0;
        return code[bitCursor++ % code.length];
    }

    public void addChoicePoint(String optionA, String optionB, int bit) {
        choicePoints.add(new ChoicePoint(questionIndex, choicePoints.size(), optionA, optionB, bit));
    }

    public void putVariable(String name, int value, boolean traceable) {
        variables.put(name, value);
        if (traceable) {
            numberMarks.add(new NumberMark(questionIndex, name, value));
        }
    }

    public Student getStudent() { return student; }
    public Random getRandom() { return random; }
    public int getQuestionIndex() { return questionIndex; }
    public Map<String, Integer> getVariables() { return Collections.unmodifiableMap(variables); }
    public List<ChoicePoint> getChoicePoints() { return new ArrayList<>(choicePoints); }
    public List<NumberMark> getNumberMarks() { return new ArrayList<>(numberMarks); }
}
