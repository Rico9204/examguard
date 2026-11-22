package examguard.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 문제 은행. 실제 과제에서는 관리자 화면에서 등록·수정하고 파일로 저장하도록 확장한다. */
public class QuestionBank {
    private final List<Question> questions = new ArrayList<>();

    public void add(Question q) {
        questions.add(q);
    }

    public List<Question> getQuestions() {
        return Collections.unmodifiableList(questions);
    }

    public int size() {
        return questions.size();
    }

    /**
     * 시연용 문제 6개 (선택 지점 15곳, 합계 100점).
     * 주의: 선택 지점의 표현들은 시험지 전체에서 서로 겹치지 않아야 추적이 정확하다.
     *       ExamGenerator.validate()가 이를 검사한다.
     */
    public static QuestionBank createSample() {
        QuestionBank bank = new QuestionBank();

        bank.add(new NumericQuestion("Q1",
                "한 반 학생 {{a:21-39}}명이 {{b:4-9}}명씩 [[조를 이룰 때|모둠을 만들 때]], "
                + "[[조에 들어가지 못하는|어느 모둠에도 속하지 못하는]] 학생 수를 [[구하시오|계산하시오]].",
                15, v -> v.get("a") % v.get("b"))
                .withConstraint(v -> v.get("a") % v.get("b") != 0) // 나머지 0은 더 쉬우므로 제외
                .withExpectedSeconds(60));

        bank.add(new NumericQuestion("Q2",
                "정가 {{p:12-48}},000원인 운동화를 {{d:15-35}}% [[할인받아|할인된 값에]] 샀다. "
                + "실제로 [[지불한|결제한]] 금액은 몇 원인지 [[답하시오|적으시오]].",
                20, v -> v.get("p") * 10 * (100 - v.get("d")))
                .withConstraint(v -> v.get("d") % 5 == 0) // 17%처럼 암산이 어려운 할인율 제외
                .withExpectedSeconds(90));

        bank.add(new MultipleChoiceQuestion("Q3",
                "[[다음 중|아래 보기 가운데]] 자바에서 한 클래스가 [[여러 타입의 역할을|여러 개의 규약을]] "
                + "동시에 갖도록 할 때 쓰는 것을 [[고르시오|선택하시오]].",
                15, "interface", "static", "final", "transient")
                .withExpectedSeconds(30));

        bank.add(new MultipleChoiceQuestion("Q4",
                "[[부모 클래스의 메서드를|상위 클래스에 정의된 메서드를]] 자식 클래스에서 "
                + "[[다시 정의하는|새로 구현하는]] 것을 무엇이라 하는가?",
                15, "오버라이딩", "오버로딩", "캡슐화", "직렬화")
                .withExpectedSeconds(30));

        bank.add(new NumericQuestion("Q5",
                "int[] arr = new int[{{n:12-30}}]; 일 때, "
                + "[[마지막 원소의 인덱스는|맨 끝 원소를 가리키는 인덱스는]] [[얼마인가|무엇인가]]?",
                15, v -> v.get("n") - 1)
                .withExpectedSeconds(40));

        // 정보처리산업기사 필기 운영체제 단원에서 자주 나오는 개념(HRN 스케줄링)을
        // 같은 형식으로 새로 쓴 문제. 실제 기출 문장을 옮긴 것이 아니다.
        bank.add(new MultipleChoiceQuestion("Q6",
                "[[HRN 스케줄링 방식에서|HRN(Highest Response-ratio Next) 기법에서]] "
                + "작업의 우선순위를 [[구하는 식으로|계산하는 공식으로]] 옳은 것은?",
                20,
                "(대기 시간 + 서비스 시간) / 서비스 시간",
                "(대기 시간 + 서비스 시간) / 대기 시간",
                "서비스 시간 / (대기 시간 + 서비스 시간)",
                "(대기 시간 - 서비스 시간) / 서비스 시간")
                .withCategory("정보처리산업기사 유형 · 운영체제")
                .withExpectedSeconds(40));

        return bank;
    }
}
