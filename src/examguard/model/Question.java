package examguard.model;

import examguard.fingerprint.VariantContext;

import java.util.Map;
import java.util.function.Predicate;

/**
 * 문제 은행에 들어가는 원본 문항.
 * 템플릿 문법:
 *   [[A|B]]        학생마다 A 또는 B 중 하나가 들어가는 "선택 지점" (유출 추적용 표시)
 *   {{x:12-48}}    학생마다 다른 숫자가 들어가는 변수 (정답 계산에도 사용)
 */
public abstract class Question {
    private final String id;
    private final String template;
    private final int points;
    private String category = "";
    private int expectedSeconds = 60;
    private Predicate<Map<String, Integer>> constraint = v -> true;

    protected Question(String id, String template, int points) {
        this.id = id;
        this.template = template;
        this.points = points;
    }

    public String getId() { return id; }
    public String getTemplate() { return template; }
    public int getPoints() { return points; }

    /** 과목·출제 유형 표시 (예: "정보처리산업기사 유형 · 운영체제"). 없으면 빈 문자열 */
    public String getCategory() { return category; }

    /** 문제 은행에 넣을 때 이어서 쓸 수 있도록 자기 자신을 돌려준다. */
    public Question withCategory(String category) {
        this.category = category == null ? "" : category;
        return this;
    }

    /** 보통 수험자가 푸는 데 걸리는 시간(초). "너무 빨리 맞힌 문제"를 찾는 기준이 된다. */
    public int getExpectedSeconds() { return expectedSeconds; }

    public Question withExpectedSeconds(int seconds) {
        this.expectedSeconds = seconds;
        return this;
    }

    /**
     * 학생마다 숫자를 바꿀 때 난이도가 달라지지 않도록 거르는 조건.
     * 예: 나머지 문제에서 나머지가 0이면 더 쉬우므로 v -> v.get("a") % v.get("b") != 0
     * 조건을 만족할 때까지 숫자를 다시 뽑는다.
     */
    public Question withConstraint(Predicate<Map<String, Integer>> constraint) {
        this.constraint = constraint;
        return this;
    }

    public boolean accepts(Map<String, Integer> variables) {
        return constraint.test(variables);
    }

    /**
     * 변형 표시가 적용된 문장으로 학생별 변형본을 만든다.
     * 보기 섞기, 정답 계산 방식은 문항 유형마다 다르므로 하위 클래스가 구현한다.
     */
    public abstract QuestionVariant createVariant(String renderedText, VariantContext ctx);

    public abstract String getTypeName();
}
