package examguard.fingerprint;

import examguard.model.Student;

/** 학생 한 명에 대한 유출본 대조 결과 */
public class TraceResult {
    private final Student student;
    private final int matchedPoints;
    private final int observedPoints;
    private final int matchedNumbers;
    private final int observedNumbers;

    public TraceResult(Student student, int matchedPoints, int observedPoints, int matchedNumbers, int observedNumbers) {
        this.student = student;
        this.matchedPoints = matchedPoints;
        this.observedPoints = observedPoints;
        this.matchedNumbers = matchedNumbers;
        this.observedNumbers = observedNumbers;
    }

    public Student getStudent() { return student; }
    public int getMatchedPoints() { return matchedPoints; }
    public int getObservedPoints() { return observedPoints; }
    public int getMatchedNumbers() { return matchedNumbers; }
    public int getObservedNumbers() { return observedNumbers; }

    /** 0.0 ~ 1.0 */
    public double getScore() {
        int total = observedPoints + observedNumbers;
        return total == 0 ? 0 : (matchedPoints + matchedNumbers) / (double) total;
    }
}
