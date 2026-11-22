package examguard.model;

import examguard.fingerprint.VariantContext;

import java.util.Collections;
import java.util.Map;
import java.util.function.ToIntFunction;

/** 숫자 단답형 문항: 학생마다 다른 숫자로 정답을 다시 계산한다. */
public class NumericQuestion extends Question {
    private final ToIntFunction<Map<String, Integer>> answerFormula;

    public NumericQuestion(String id, String template, int points, ToIntFunction<Map<String, Integer>> answerFormula) {
        super(id, template, points);
        this.answerFormula = answerFormula;
    }

    @Override
    public QuestionVariant createVariant(String renderedText, VariantContext ctx) {
        int answer = answerFormula.applyAsInt(ctx.getVariables());
        return new QuestionVariant(this, renderedText, Collections.<String>emptyList(), String.valueOf(answer));
    }

    @Override
    public String getTypeName() {
        return "단답형(숫자)";
    }
}
