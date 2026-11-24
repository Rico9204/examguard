package examguard.fingerprint;

import examguard.model.ExamPaper;
import examguard.model.Question;
import examguard.model.QuestionBank;
import examguard.model.QuestionVariant;
import examguard.model.Student;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;

/**
 * 문제 은행 + 비밀키 + 학생 → 학생별 개인화 시험지.
 * 같은 학생이면 언제 만들어도 같은 시험지가 나온다(시드가 학번과 비밀키로 정해짐).
 * 그래서 시험지를 따로 저장하지 않아도 나중에 다시 만들어 유출본과 대조할 수 있다.
 */
public class ExamGenerator {
    /** 학생 번호에 쓰는 비트 수의 최소·최대 (6비트 = 63명, 10비트 = 1023명) */
    private static final int MIN_ID_BITS = 6;
    private static final int MAX_ID_BITS = 10;
    private static final int MAX_RETRIES = 200;

    private final QuestionBank bank;
    private final String secretKey;
    private final List<FingerprintLayer> layers;

    public ExamGenerator(QuestionBank bank, String secretKey, List<FingerprintLayer> layers) {
        this.bank = bank;
        this.secretKey = secretKey;
        this.layers = Collections.unmodifiableList(new ArrayList<>(layers));
    }

    /** 숫자 변형을 먼저 적용한 뒤 문장 변형을 적용한다. */
    public static ExamGenerator createDefault(QuestionBank bank, String secretKey) {
        return new ExamGenerator(bank, secretKey,
                Arrays.<FingerprintLayer>asList(new NumericLayer(), new TextVariantLayer()));
    }

    public ExamPaper generate(Student student) {
        int[] code = codeFor(student);
        VariantContext ctx = new VariantContext(student, new Random(seedFor(student)), code);
        List<QuestionVariant> variants = new ArrayList<>();
        List<Question> questions = bank.getQuestions();
        for (int i = 0; i < questions.size(); i++) {
            Question q = questions.get(i);
            ctx.beginQuestion(i);
            String text = q.getTemplate();
            // 난이도 조건(withConstraint)에 맞는 숫자가 나올 때까지 다시 뽑는다.
            for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
                int[] mark = ctx.mark();
                text = q.getTemplate();
                for (FingerprintLayer layer : layers) {
                    text = layer.apply(text, ctx);
                }
                if (q.accepts(ctx.getVariables())) break;
                if (attempt == MAX_RETRIES - 1) {
                    throw new IllegalStateException(q.getId() + ": 조건을 만족하는 숫자를 찾지 못했습니다");
                }
                ctx.rollback(mark);
            }
            variants.add(q.createVariant(text, ctx));
        }
        return new ExamPaper(student, variants, code, ctx.getChoicePoints(), ctx.getNumberMarks());
    }

    /** 이 문제 은행으로 구분할 수 있는 최대 학생 번호 */
    public int getMaxStudentId() {
        return (1 << idBits()) - 1;
    }

    private int idBits() {
        return Math.max(MIN_ID_BITS, Math.min(MAX_ID_BITS, (countChoicePoints() - 1) / 2));
    }

    /**
     * 선택 지점 수만큼의 식별 코드.
     *   [학생 번호 n비트][같은 n비트 반복 — 일부만 유출돼도 읽도록][패리티 1비트][나머지: 학번·비밀키 해시]
     * 선택 지점이 많을수록 n이 커진다 (선택 지점 15곳이면 7비트 = 127명).
     * 마지막으로 시험마다 비밀키로 정해지는 마스크와 XOR한다. 마스크는 모든 학생에게 같아서
     * 학생끼리 코드가 겹치지 않는 성질은 그대로이고, 비밀키를 모르면 코드에서 학생 번호를 알아낼 수 없다.
     */
    public int[] codeFor(Student student) {
        int n = idBits();
        int id = student.getId();
        if (id > getMaxStudentId()) {
            throw new IllegalArgumentException("학생 번호 " + id + "는 선택 지점 수로 구분할 수 있는 범위("
                    + getMaxStudentId() + "명)를 넘습니다. 문제에 선택 지점을 더 넣으세요.");
        }
        int[] c = new int[Math.max(2 * n + 1, countChoicePoints())];
        int ones = 0;
        for (int i = 0; i < n; i++) {
            c[i] = (id >> (n - 1 - i)) & 1;
            c[n + i] = c[i];
            ones += c[i];
        }
        c[2 * n] = ones % 2; // 패리티
        long extra = hash("code", student.getStudentNo());
        for (int i = 2 * n + 1; i < c.length; i++) {
            c[i] = (int) ((extra >>> ((i - 2 * n - 1) % 64)) & 1);
        }
        long mask = hash("mask", "exam");
        for (int i = 0; i < c.length; i++) {
            c[i] ^= (int) ((mask >>> (i % 64)) & 1);
        }
        return c;
    }

    private int countChoicePoints() {
        int count = 0;
        for (Question q : bank.getQuestions()) {
            Matcher m = TextVariantLayer.PATTERN.matcher(q.getTemplate());
            while (m.find()) count++;
        }
        return count;
    }

    /** 시험 단위 시드 (학생과 무관). 같은 비밀키면 같은 값 → 색 열쇠처럼 시험마다 바뀌어야 하는 설정에 쓴다 */
    public long examSeed(String purpose) {
        return hash(purpose, "exam");
    }

    private long seedFor(Student student) {
        return hash("variant", student.getStudentNo());
    }

    private long hash(String purpose, String subject) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest((purpose + ":" + secretKey + ":" + subject)
                    .getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.wrap(h).getLong();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 선택 지점 표현들이 서로 겹치거나, 고정 문장 안에 들어 있으면 추적이 헷갈린다.
     * 문제 은행을 바꿀 때마다 이 검사를 돌려 경고를 확인한다.
     */
    public List<String> validate() {
        List<String> warnings = new ArrayList<>();
        List<String> alts = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        List<String> literals = new ArrayList<>();

        for (Question q : bank.getQuestions()) {
            Matcher m = TextVariantLayer.PATTERN.matcher(q.getTemplate());
            while (m.find()) {
                alts.add(normalize(m.group(1)));
                labels.add(q.getId() + " '" + m.group(1) + "'");
                alts.add(normalize(m.group(2)));
                labels.add(q.getId() + " '" + m.group(2) + "'");
            }
            String literal = q.getTemplate()
                    .replaceAll(TextVariantLayer.PATTERN.pattern(), "|")
                    .replaceAll(NumericLayer.PATTERN.pattern(), "|");
            literals.add(normalize(literal));
        }
        for (int i = 0; i < alts.size(); i++) {
            for (int j = i + 1; j < alts.size(); j++) {
                if (alts.get(i).contains(alts.get(j)) || alts.get(j).contains(alts.get(i))) {
                    warnings.add("선택 표현이 겹칩니다: " + labels.get(i) + " / " + labels.get(j));
                }
            }
            for (int k = 0; k < literals.size(); k++) {
                if (literals.get(k).contains(alts.get(i))) {
                    warnings.add("선택 표현이 고정 문장에도 있습니다: " + labels.get(i)
                            + " (문제 " + (k + 1) + ")");
                }
            }
        }
        return warnings;
    }

    public QuestionBank getBank() { return bank; }
    public List<FingerprintLayer> getLayers() { return layers; }

    /** 띄어쓰기를 바꿔 옮겨 적어도 대조할 수 있도록 공백을 모두 지운다. */
    public static String normalize(String s) {
        return s.replaceAll("\\s+", "");
    }
}
