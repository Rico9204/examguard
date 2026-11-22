package examguard.fingerprint;

/**
 * 시험지에 학생별 표시를 넣는 층.
 * 새 추적 방식(배치 변형, 시각 워터마크 등)은 이 인터페이스를 구현해 추가하면 되고,
 * ExamGenerator 코드는 고칠 필요가 없다.
 */
public interface FingerprintLayer {
    String getName();

    /** 템플릿 문장에 이 층의 표시를 적용한 결과를 돌려준다. */
    String apply(String text, VariantContext ctx);
}
