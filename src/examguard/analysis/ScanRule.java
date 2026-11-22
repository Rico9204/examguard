package examguard.analysis;

import java.util.List;

/**
 * "녹화하려고 화면을 훑는 행동"을 찾는 판정 규칙 하나.
 * 규칙을 추가하려면 이 인터페이스를 구현해 SuspicionScorer에 등록한다.
 */
public interface ScanRule {
    String getName();

    String getDescription();

    /**
     * samples: usesAllInputs()가 true면 마우스·방향키·스크롤 모두, false면 마우스 이동만.
     * width/height: 문제 화면 크기
     */
    RuleResult evaluate(List<Sample> samples, int width, int height);

    /**
     * 이 규칙이 방향키·스크롤 이동까지 볼지.
     * "얼마나 드러냈나"처럼 입력 방식과 상관없는 규칙은 true,
     * "속도가 일정한가"처럼 마우스 손동작에만 의미 있는 규칙은 false
     * (방향키를 누르고 있으면 자동 반복 때문에 속도가 원래 일정하다).
     */
    default boolean usesAllInputs() {
        return false;
    }
}
