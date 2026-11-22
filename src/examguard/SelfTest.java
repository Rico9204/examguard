package examguard;

import examguard.analysis.RuleResult;
import examguard.analysis.Sample;
import examguard.analysis.SuspicionReport;
import examguard.analysis.SuspicionScorer;
import examguard.analysis.TrajectoryRecorder;
import examguard.analysis.TrajectorySimulator;
import examguard.display.ExamPanel;
import examguard.display.AntiOcrWatermarkLayer;
import examguard.display.CameraModel;
import examguard.display.MotionNoiseLayer;
import examguard.display.TemporalMaskLayer;
import examguard.display.PlainLayer;
import examguard.display.ProtectionLayer;
import examguard.display.SpotlightLayer;
import examguard.fingerprint.ExamGenerator;
import examguard.fingerprint.LeakTracer;
import examguard.fingerprint.TraceReport;
import examguard.model.ExamPaper;
import examguard.model.QuestionBank;
import examguard.model.QuestionVariant;
import examguard.model.Student;

import javax.imageio.ImageIO;
import java.awt.Point;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 화면 없이 핵심 기능을 점검하는 자체 테스트.
 *   java -cp out examguard.SelfTest              유출 추적 정확도, 궤적 판정 점수
 *   java -cp out examguard.SelfTest --screens 폴더   보호 방식별 화면 캡처 저장 (보고서용)
 */
public class SelfTest {
    public static void main(String[] args) throws Exception {
        List<Student> roster = Student.createRoster(40);
        ExamGenerator gen = ExamGenerator.createDefault(QuestionBank.createSample(), "class-2026-final-secret");

        System.out.println("== 1. 문제 은행 검사 ==");
        List<String> warnings = gen.validate();
        System.out.println(warnings.isEmpty() ? "경고 없음" : String.join("\n", warnings));

        System.out.println("\n== 2. 같은 문제, 다른 시험지 (학생01 vs 학생02, 문제 2) ==");
        System.out.println(gen.generate(roster.get(0)).getVariants().get(1).getText());
        System.out.println(gen.generate(roster.get(1)).getVariants().get(1).getText());

        System.out.println("\n== 3. 유출 추적 정확도 (무작위 학생, 띄어쓰기 바꿔 옮겨 적기, 각 300회) ==");
        LeakTracer tracer = new LeakTracer(gen, roster);
        Random rnd = new Random(7);
        for (int k = 1; k <= 5; k++) {
            int hit = 0, uniqueTop = 0;
            for (int trial = 0; trial < 300; trial++) {
                ExamPaper p = tracer.getPapers().get(rnd.nextInt(roster.size()));
                String leak = respace(leakOf(p, pick(rnd, p.getVariants().size(), k)), rnd);
                TraceReport r = tracer.trace(leak);
                if (r.getResults().get(0).getStudent() == p.getStudent()) hit++;
                if (r.getResults().size() < 2 || r.getResults().get(0).getScore() > r.getResults().get(1).getScore()) uniqueTop++;
            }
            System.out.printf("문제 %d개 유출: 1위 적중 %.1f%%, 단독 1위 %.1f%%%n", k, hit / 3.0, uniqueTop / 3.0);
        }

        System.out.println("\n== 4. 궤적 판정 (읽기 vs 훑기) ==");
        SuspicionScorer scorer = SuspicionScorer.createDefault();
        int w = 760, h = 360;
        for (int seed = 1; seed <= 4; seed++) {
            List<Sample> read = TrajectorySimulator.reading(w, h, 90, 0, 0, seed);
            print("읽기 " + seed, scorer.evaluate(read, w, h));
        }
        for (int radius : new int[]{60, 90, 140}) {
            List<Sample> sweep = TrajectorySimulator.sweep(w, h, radius, 0, 0);
            print("훑기 r=" + radius, scorer.evaluate(sweep, w, h));
        }
        List<Sample> mixed = new ArrayList<>(TrajectorySimulator.reading(w, h, 90, 0, 0, 9));
        mixed.addAll(TrajectorySimulator.sweep(w, h, 90, mixed.get(mixed.size() - 1).getTime() + 500, 0));
        print("읽다가 훑기", scorer.evaluate(mixed, w, h));
        for (int seed = 1; seed <= 2; seed++) {
            print("방향키 읽기" + seed, scorer.evaluate(TrajectorySimulator.keyReading(w, h, 90, 0, 0, seed), w, h));
        }
        print("방향키 훑기", scorer.evaluate(TrajectorySimulator.keySweep(w, h, 90, 0, 0), w, h));

        System.out.println("\n== 5. 다른 학생 정답 검사 (40명 전원) ==");
        int slipFlagged = 0, copyFlagged = 0, oneCopyFlagged = 0;
        List<ExamPaper> papers = tracer.getPapers();
        for (int s = 0; s < papers.size(); s++) {
            ExamPaper own = papers.get(s);
            ExamPaper neighbor = papers.get((s + 1) % papers.size());
            // (a) 문제 5에서 "-1"을 빼먹은 흔한 실수, 나머지는 정답
            java.util.Map<Integer, String> slip = correctAnswers(own);
            slip.put(4, String.valueOf(Integer.parseInt(own.getVariants().get(4).getCorrectAnswer()) + 1));
            if (!examguard.fingerprint.AnswerCheck.findForeignAnswers(own, slip, papers).isEmpty()) slipFlagged++;
            // (b) 옆 사람 시험지의 숫자 문항 답을 모두 베낌
            java.util.Map<Integer, String> copy = correctAnswers(own);
            for (int q : new int[]{0, 1, 4}) copy.put(q, neighbor.getVariants().get(q).getCorrectAnswer());
            if (!examguard.fingerprint.AnswerCheck.findForeignAnswers(own, copy, papers).isEmpty()) copyFlagged++;
            // (c) 한 문항만 베낌 (우연과 구분 안 되므로 보고하지 않아야 함)
            java.util.Map<Integer, String> one = correctAnswers(own);
            one.put(1, neighbor.getVariants().get(1).getCorrectAnswer());
            if (!examguard.fingerprint.AnswerCheck.findForeignAnswers(own, one, papers).isEmpty()) oneCopyFlagged++;
        }
        System.out.printf("흔한 실수(-1 빼먹기)를 부정행위로 지목: %d / 40%n", slipFlagged);
        System.out.printf("숫자 문항 3개를 옆 사람 것으로 베낌 → 지목: %d / 40%n", copyFlagged);
        System.out.printf("한 문항만 베낌 → 지목: %d / 40 (보고 안 하는 것이 정상)%n", oneCopyFlagged);

        System.out.println("\n== 6. 개인화 난이도 조건 · 식별 코드 ==");
        int zeroRemainder = 0, oddDiscount = 0;
        for (ExamPaper p : papers) {
            if (p.getVariants().get(0).getCorrectAnswer().equals("0")) zeroRemainder++;
            for (examguard.fingerprint.NumberMark m : p.getNumberMarks()) {
                if (m.getName().equals("d") && m.getValue() % 5 != 0) oddDiscount++;
            }
        }
        System.out.printf("문제 1 정답 0(나머지 없음): %d명, 문제 2 할인율이 5의 배수가 아님: %d명%n", zeroRemainder, oddDiscount);
        List<Student> big = Student.createRoster(gen.getMaxStudentId());
        java.util.Set<String> codes = new java.util.HashSet<>();
        for (Student s : big) codes.add(java.util.Arrays.toString(gen.codeFor(s)));
        System.out.printf("구분 가능 인원 %d명, 서로 다른 식별 코드 %d개%n", big.size(), codes.size());

        System.out.println("\n== 7. 시간차 픽셀 마스킹 (사진 한 장 vs 서브프레임 2장 평균) ==");
        temporalCheck(gen.generate(roster.get(0)).getVariants().get(2));

        System.out.println("\n== 8. 카메라 시뮬레이션: 원 안이 '보호 없음 촬영본'과 얼마나 같은가 (상관계수, 1=그대로 읽힘 0=정보 없음) ==");
        cameraCheck(gen.generate(roster.get(0)).getVariants().get(2));

        System.out.println("\n== 9. 시간차 마스킹 서브프레임 장수 (3·5장): 한 주기 평균 = 목표 밝기, 카메라 한 장 상관계수 ==");
        subframeCheck(gen.generate(roster.get(0)).getVariants().get(2));

        if (args.length >= 2 && args[0].equals("--screens")) {
            File dir = new File(args[1]);
            dir.mkdirs();
            ExamPaper paper = gen.generate(roster.get(0));
            QuestionVariant v = paper.getVariants().get(2);
            AntiOcrWatermarkLayer marked = new AntiOcrWatermarkLayer();
            marked.setWatermark("ID " + paper.getCodeString());
            ProtectionLayer[] layers = {new PlainLayer(), new SpotlightLayer(), new MotionNoiseLayer(),
                    new TemporalMaskLayer(), marked};
            String[] names = {"plain", "spotlight", "noise", "temporal", "watermark"};
            for (int i = 0; i < layers.length; i++) {
                ExamPanel panel = new ExamPanel(new TrajectoryRecorder());
                panel.setLayer(layers[i]);
                panel.showQuestion(v, 2);
                panel.setRadius(i == 2 ? 170 : 90);
                ImageIO.write(panel.snapshot(new Point(i == 2 ? 300 : 260, i == 2 ? 170 : 110), 0), "png",
                        new File(dir, names[i] + ".png"));
                if (i == 2) {
                    // 한계 실험: 연속된 두 프레임의 차이를 구하면(=동영상 공격) 글자가 드러난다
                    java.awt.image.BufferedImage f0 = panel.snapshot(new Point(300, 170), 0);
                    java.awt.image.BufferedImage f1 = panel.snapshot(new Point(300, 170), 1);
                    ImageIO.write(f1, "png", new File(dir, "noise_frame1.png"));
                    java.awt.image.BufferedImage diff = new java.awt.image.BufferedImage(
                            f0.getWidth(), f0.getHeight(), java.awt.image.BufferedImage.TYPE_INT_RGB);
                    for (int y = 0; y < f0.getHeight(); y++) {
                        for (int x = 0; x < f0.getWidth(); x++) {
                            diff.setRGB(x, y, f0.getRGB(x, y) == f1.getRGB(x, y) ? 0xFFFFFF : 0x000000);
                        }
                    }
                    ImageIO.write(diff, "png", new File(dir, "noise_video_attack.png"));
                }
                panel.clear();
            }
            System.out.println("\n화면 캡처 저장: " + dir.getAbsolutePath());
        }
    }

    /**
     * 원 안에서 "글자 픽셀 평균 - 배경 픽셀 평균"(신호)과 배경 픽셀의 표준편차(노이즈)를 잰다.
     * 사진 한 장은 신호 대비 노이즈가 커야 하고, 두 장 평균은 노이즈가 정확히 0이어야 한다(= 사람 눈에 보이는 화면).
     */
    static void temporalCheck(QuestionVariant v) {
        Point at = new Point(300, 120);
        ExamPanel ref = new ExamPanel(new TrajectoryRecorder());
        ref.setLayer(new PlainLayer() {
            @Override public boolean prefersLargeText() { return true; } // 같은 글자 크기로 렌더링
        });
        ref.showQuestion(v, 2);
        ExamPanel panel = new ExamPanel(new TrajectoryRecorder());
        panel.setLayer(new TemporalMaskLayer());
        panel.showQuestion(v, 2);
        panel.setRadius(90);
        java.awt.image.BufferedImage plain = ref.snapshot(at, 0);
        java.awt.image.BufferedImage f0 = panel.snapshot(at, 0), f1 = panel.snapshot(at, 1);
        panel.clear(); // 애니메이션 타이머를 멈춰야 JVM이 끝난다

        double[] one = new double[4], avg = new double[4]; // 글자합, 글자수, 배경합, 배경제곱합
        int bgCount = 0;
        double maxErr = 0; // 픽셀마다 |두 장 평균 - 목표 밝기| (안티앨리어싱 회색 픽셀도 정확히 맞아야 함)
        for (int y = at.y - 60; y < at.y + 60; y++) {
            for (int x = at.x - 60; x < at.x + 60; x++) {
                int p = plain.getRGB(x, y);
                int lum = (((p >> 16) & 0xFF) * 3 + ((p >> 8) & 0xFF) * 6 + (p & 0xFF)) / 10;
                boolean text = lum < 128;
                double a = f0.getRGB(x, y) & 0xFF, m = (a + (f1.getRGB(x, y) & 0xFF)) / 2;
                maxErr = Math.max(maxErr, Math.abs(m - Math.round(128 + (lum - 128) * 0.16)));
                if (text) { one[0] += a; one[1]++; avg[0] += m; }
                else { one[2] += a; one[3] += a * a; avg[2] += m; avg[3] += m * m; bgCount++; }
            }
        }
        double sig1 = one[2] / bgCount - one[0] / one[1];
        double sd1 = Math.sqrt(one[3] / bgCount - Math.pow(one[2] / bgCount, 2));
        double sigA = avg[2] / bgCount - avg[0] / one[1];
        double sdA = Math.sqrt(Math.max(0, avg[3] / bgCount - Math.pow(avg[2] / bgCount, 2)));
        System.out.printf("사진 한 장 : 글자-배경 밝기 차 %.1f, 노이즈 표준편차 %.1f (신호/노이즈 %.2f)%n", sig1, sd1, sig1 / sd1);
        System.out.printf("2장 평균   : 글자-배경 밝기 차 %.1f, 배경 표준편차 %.1f (안티앨리어싱), 목표 대비 최대 오차 %.1f%n",
                sigA, sdA, maxErr);
        if (maxErr > 0.5) throw new AssertionError("서브프레임 쌍의 평균이 목표 밝기와 다름 (최대 오차 " + maxErr + ")");
        if (sig1 / sd1 > 1.0) throw new AssertionError("한 장만으로 글자가 노이즈보다 강함 (SNR=" + sig1 / sd1 + ")");
    }

    /**
     * 보호 방식 × 카메라 표. 같은 카메라·같은 노이즈 시드로 "보호 없음"과 각 보호 방식을 찍어,
     * 스포트라이트 원 안 영역의 픽셀 상관계수를 비교한다. (원 밖은 모든 방식이 흐림이라 제외)
     */
    static void cameraCheck(QuestionVariant v) {
        Point at = new Point(300, 120);
        ProtectionLayer[] layers = {new SpotlightLayer(), new MotionNoiseLayer(), new TemporalMaskLayer(),
                new AntiOcrWatermarkLayer()};
        String[] names = {"스포트라이트", "움직임 노이즈", "시간차 마스킹", "셰이딩·워터마크"};
        ((AntiOcrWatermarkLayer) layers[3]).setWatermark("ID 010101110010101");
        StringBuilder head = new StringBuilder(String.format("%-14s", ""));
        for (int ci = 1; ci <= CameraModel.PRESETS.size(); ci++) head.append(String.format(" | 카메라%d", ci));
        System.out.println(head);
        double[][] corr = new double[layers.length][CameraModel.PRESETS.size()];
        double[] misaligned = new double[CameraModel.PRESETS.size()]; // 시간차 마스킹, 노출이 서브프레임 쌍에 어긋날 때
        for (int li = 0; li < layers.length; li++) {
            ProtectionLayer layer = layers[li];
            ExamPanel ref = panelFor(new PlainLayer() {
                @Override public boolean prefersLargeText() { return layer.prefersLargeText(); }
            }, v);
            ExamPanel panel = panelFor(layer, v);
            StringBuilder row = new StringBuilder(String.format("%-14s", names[li]));
            for (int ci = 0; ci < CameraModel.PRESETS.size(); ci++) {
                CameraModel cam = CameraModel.PRESETS.get(ci);
                java.awt.image.BufferedImage a = cam.capture(f -> ref.snapshot(at, f), 0, new Random(1));
                java.awt.image.BufferedImage b = cam.capture(f -> panel.snapshot(at, f), 0, new Random(1));
                corr[li][ci] = correlation(a, b, at, 60, cam.scale());
                row.append(String.format(" | %6.2f", corr[li][ci]));
                if (layer instanceof TemporalMaskLayer) {
                    // 홀수 프레임에서 노출 시작 = 한 쌍의 뒷장 + 다음 쌍의 앞장 (서로 다른 노이즈라 지워지지 않음)
                    java.awt.image.BufferedImage a1 = cam.capture(f -> ref.snapshot(at, f), 1, new Random(1));
                    java.awt.image.BufferedImage b1 = cam.capture(f -> panel.snapshot(at, f), 1, new Random(1));
                    misaligned[ci] = correlation(a1, b1, at, 60, cam.scale());
                }
            }
            System.out.println(row);
            if (layer instanceof TemporalMaskLayer) {
                StringBuilder mis = new StringBuilder(String.format("%-14s", "  └ 쌍 어긋남"));
                for (double m : misaligned) mis.append(String.format(" | %6.2f", m));
                System.out.println(mis);
            }
            panel.clear();
            ref.clear();
        }
        System.out.println("  (위 표는 노출이 서브프레임 쌍에 딱 맞을 때(0번 프레임 시작). 실제 영상은 노출 시작이 무작위라 "
                + "2프레임 노출의 약 절반이 '쌍 어긋남'이 된다)");
        for (int ci = 0; ci < CameraModel.PRESETS.size(); ci++) {
            CameraModel cam = CameraModel.PRESETS.get(ci);
            System.out.printf("  카메라%d %s: %s%n", ci + 1, cam.getName(), cam.describe(760));
        }
        // 시간차 마스킹: 짧은 노출 사진(1프레임)은 막고, 30fps 영상(2프레임 평균)은 뚫려야 모델이 맞다
        double photo = corr[2][2], video = corr[2][1];
        if (photo > 0.5) throw new AssertionError("짧은 노출인데 시간차 마스킹이 뚫림 (" + photo + ")");
        if (video < 0.7) throw new AssertionError("2프레임 평균인데 글자가 복원되지 않음 (" + video + ")");
        if (misaligned[1] > video - 0.3) throw new AssertionError("쌍이 어긋났는데도 쌍이 맞을 때만큼 읽힘 (" + misaligned[1] + ")");
    }

    /**
     * N=3·5: ① N장 평균이 목표 밝기와 같은지(반올림 오차 0.5 이내) ② 30fps 노출(화면 2프레임)이 한 주기를 다 덮지 못해
     * 어느 타이밍에 찍어도 글자가 잘 안 읽히는지 (N=2의 "깨끗한 프레임"이 사라졌는지).
     */
    static void subframeCheck(QuestionVariant v) {
        Point at = new Point(300, 120);
        ExamPanel ref = panelFor(new PlainLayer() {
            @Override public boolean prefersLargeText() { return true; }
        }, v);
        java.awt.image.BufferedImage plain = ref.snapshot(at, 0);
        for (int n : new int[]{3, 5}) {
            TemporalMaskLayer layer = new TemporalMaskLayer();
            layer.setSubframes(n);
            ExamPanel panel = panelFor(layer, v);
            java.awt.image.BufferedImage[] frames = new java.awt.image.BufferedImage[n];
            for (int f = 0; f < n; f++) frames[f] = panel.snapshot(at, f);
            double maxErr = 0;
            for (int y = at.y - 60; y < at.y + 60; y++) {
                for (int x = at.x - 60; x < at.x + 60; x++) {
                    int p = plain.getRGB(x, y);
                    int lum = (((p >> 16) & 0xFF) * 3 + ((p >> 8) & 0xFF) * 6 + (p & 0xFF)) / 10;
                    double sum = 0;
                    for (java.awt.image.BufferedImage f : frames) sum += f.getRGB(x, y) & 0xFF;
                    maxErr = Math.max(maxErr, Math.abs(sum / n - Math.round(128 + (lum - 128) * 0.16)));
                }
            }
            StringBuilder row = new StringBuilder(String.format("%d장 (60Hz에서 %dHz) 평균 오차 최대 %.2f · 카메라 한 장(시작 0/1프레임):",
                    n, 60 / n, maxErr));
            double worstVideo = 0;
            for (int ci = 0; ci < CameraModel.PRESETS.size(); ci++) {
                CameraModel cam = CameraModel.PRESETS.get(ci);
                double[] c = new double[2];
                for (int phase = 0; phase < 2; phase++) {
                    final long start = phase;
                    java.awt.image.BufferedImage a = cam.capture(f -> ref.snapshot(at, f), start, new Random(1));
                    java.awt.image.BufferedImage b = cam.capture(f -> panel.snapshot(at, f), start, new Random(1));
                    c[phase] = correlation(a, b, at, 60, cam.scale());
                }
                if (ci == 1) worstVideo = Math.max(c[0], c[1]);
                row.append(String.format(" | 카메라%d %.2f/%.2f", ci + 1, c[0], c[1]));
            }
            System.out.println(row);
            panel.clear();
            if (maxErr > 0.5 + 1e-9) throw new AssertionError(n + "장 평균이 목표 밝기와 다름 (최대 오차 " + maxErr + ")");
            if (worstVideo > 0.6) throw new AssertionError(n + "장인데 30fps 한 장으로 읽힘 (" + worstVideo + ")");
        }
        ref.clear();
        System.out.println("  (녹화 프레임 여러 장을 평균하면 3·5장도 복원됨: 5장 평균 약 0.87·0.95 — README 참고)");
    }

    private static ExamPanel panelFor(ProtectionLayer layer, QuestionVariant v) {
        ExamPanel p = new ExamPanel(new TrajectoryRecorder());
        p.setLayer(layer);
        p.setRadius(90);
        p.showQuestion(v, 2);
        return p;
    }

    /** 화면 좌표 at 주변 ±half px 영역(센서 좌표로 환산)의 피어슨 상관계수 */
    private static double correlation(java.awt.image.BufferedImage a, java.awt.image.BufferedImage b,
                                      Point at, int half, double scale) {
        int x0 = (int) ((at.x - half) * scale), x1 = (int) ((at.x + half) * scale);
        int y0 = (int) ((at.y - half) * scale), y1 = (int) ((at.y + half) * scale);
        double sa = 0, sb = 0, saa = 0, sbb = 0, sab = 0;
        int n = 0;
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                double p = a.getRaster().getSample(x, y, 0), q = b.getRaster().getSample(x, y, 0);
                sa += p; sb += q; saa += p * p; sbb += q * q; sab += p * q;
                n++;
            }
        }
        double cov = sab / n - (sa / n) * (sb / n);
        double va = saa / n - Math.pow(sa / n, 2), vb = sbb / n - Math.pow(sb / n, 2);
        return cov / Math.sqrt(va * vb);
    }

    private static void print(String label, SuspicionReport r) {
        StringBuilder sb = new StringBuilder();
        for (RuleResult rr : r.getResults()) {
            sb.append(String.format(" | %s %.0f (%s)", rr.getRuleName(), rr.getScore() * 100, rr.getDetail()));
        }
        System.out.printf("%-10s 총점 %3d %-4s 점 %5d개%s%n", label, r.getTotal(), r.getLevel(), r.getSampleCount(), sb);
    }

    static java.util.Map<Integer, String> correctAnswers(ExamPaper p) {
        java.util.Map<Integer, String> m = new java.util.HashMap<>();
        for (int i = 0; i < p.getVariants().size(); i++) m.put(i, p.getVariants().get(i).getCorrectAnswer());
        return m;
    }

    static List<Integer> pick(Random rnd, int n, int k) {
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < n; i++) idx.add(i);
        Collections.shuffle(idx, rnd);
        List<Integer> out = new ArrayList<>(idx.subList(0, k));
        Collections.sort(out);
        return out;
    }

    static String leakOf(ExamPaper p, List<Integer> questions) {
        StringBuilder sb = new StringBuilder();
        for (int q : questions) {
            QuestionVariant v = p.getVariants().get(q);
            sb.append(v.getText()).append('\n');
            for (int j = 0; j < v.getChoices().size(); j++) {
                sb.append(ExamPaper.CIRCLED[j]).append(' ').append(v.getChoices().get(j)).append('\n');
            }
        }
        return sb.toString();
    }

    static String respace(String s, Random rnd) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c == ' ' && rnd.nextDouble() < 0.3) continue;
            sb.append(c);
            if (c >= '가' && c <= '힣' && rnd.nextDouble() < 0.05) sb.append(' ');
        }
        return sb.toString();
    }
}
