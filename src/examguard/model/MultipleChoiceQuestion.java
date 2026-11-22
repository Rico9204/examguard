package examguard.model;

import examguard.fingerprint.VariantContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** 객관식 문항: 학생마다 보기 순서를 섞는다. */
public class MultipleChoiceQuestion extends Question {
    private final String correct;
    private final List<String> distractors;

    public MultipleChoiceQuestion(String id, String template, int points, String correct, String... distractors) {
        super(id, template, points);
        this.correct = correct;
        this.distractors = Arrays.asList(distractors);
    }

    @Override
    public QuestionVariant createVariant(String renderedText, VariantContext ctx) {
        List<String> choices = new ArrayList<>();
        choices.add(correct);
        choices.addAll(distractors);
        Collections.shuffle(choices, ctx.getRandom());
        String answer = String.valueOf(choices.indexOf(correct) + 1); // "1" ~ "4"
        return new QuestionVariant(this, renderedText, choices, answer);
    }

    @Override
    public String getTypeName() {
        return "객관식";
    }
}
