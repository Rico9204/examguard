package examguard;

import examguard.analysis.RuleResult;
import examguard.analysis.Sample;
import examguard.analysis.SuspicionReport;
import examguard.analysis.SuspicionScorer;
import examguard.analysis.TrajectoryRecorder;
import examguard.analysis.TrajectorySimulator;
import examguard.display.ExamPanel;
import examguard.display.Gamma;
import examguard.display.AntiOcrWatermarkLayer;
import examguard.display.CameraModel;
import examguard.display.Cellophane;
import examguard.display.ColorKeyLayer;
import examguard.display.LineRulerLayer;
import examguard.display.ZeroInfoSpotlightLayer;
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

        // 보정 도구: 시간이 모두 같으면 σ=0이라 기준 = 그 시간, 퍼질수록 기준이 내려가야 함
        double same = examguard.analysis.TimeCalibration.threshold(new double[]{60, 60, 60});
        double spread = examguard.analysis.TimeCalibration.threshold(new double[]{30, 60, 120});
        if (Math.abs(same - 60) > 1e-9 || !(spread < 30)) {
            throw new AssertionError("TimeCalibration 기준 계산 오류 (" + same + ", " + spread + ")");
        }

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

        System.out.println("\n== 10. 색 열쇠 고정 노이즈: 보호 없음 화면과의 상관 (1=그대로 읽힘, 0=정보 없음) ==");
        colorKeyCheck(gen.generate(roster.get(0)).getVariants().get(2));

        System.out.println("\n== 11. 부분 표시 비교: 원(줄) 밖에 남은 글자 세부 정보 (보호 없음 화면과의 고주파 상관, 0=정보 없음) ==");
        outsideInfoCheck(gen.generate(roster.get(0)).getVariants().get(2));

        if (args.length >= 2 && args[0].equals("--screens")) {
            File dir = new File(args[1]);
            dir.mkdirs();
            ExamPaper paper = gen.generate(roster.get(0));
            QuestionVariant v = paper.getVariants().get(2);
            AntiOcrWatermarkLayer marked = new AntiOcrWatermarkLayer();
            marked.setWatermark("ID " + paper.getCodeString());
            ProtectionLayer[] layers = {new PlainLayer(), new SpotlightLayer(), new MotionNoiseLayer(),
                    new TemporalMaskLayer(), marked, new ZeroInfoSpotlightLayer(), new LineRulerLayer()};
            String[] names = {"plain", "spotlight", "noise", "temporal", "watermark", "zeroinfo", "linemode"};
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
        panel.setLayer(temporalPhysics(2)); // N=2 쌍 상쇄의 물리량 검사 (기본값은 N=3)
        panel.showQuestion(v, 2);
        panel.setRadius(90);
        java.awt.image.BufferedImage plain = ref.snapshot(at, 0);
        java.awt.image.BufferedImage f0 = panel.snapshot(at, 0), f1 = panel.snapshot(at, 1);
        panel.clear(); // 애니메이션 타이머를 멈춰야 JVM이 끝난다

        double[] one = new double[4], avg = new double[4]; // 글자합, 글자수, 배경합, 배경제곱합
        int bgCount = 0;
        double maxErr = 0; // 픽셀마다 |사람 눈이 보는 두 장 평균 - 목표 밝기| (빛 기준 평균, 감마 2.2)
        for (int y = at.y - 60; y < at.y + 60; y++) {
            for (int x = at.x - 60; x < at.x + 60; x++) {
                int p = plain.getRGB(x, y);
                int lum = (((p >> 16) & 0xFF) * 3 + ((p >> 8) & 0xFF) * 6 + (p & 0xFF)) / 10;
                boolean text = lum < 128;
                double a = f0.getRGB(x, y) & 0xFF;
                // 사람 눈은 빛을 평균한다 (예전 검사는 픽셀 값을 평균해 사람 눈에 남는 노이즈를 놓쳤다)
                double m = perceived((Gamma.toLinear((int) a) + Gamma.toLinear(f1.getRGB(x, y) & 0xFF)) / 2);
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
        ProtectionLayer[] layers = {new SpotlightLayer(), new MotionNoiseLayer(), temporalPhysics(2),
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
            TemporalMaskLayer layer = temporalPhysics(n);
            ExamPanel panel = panelFor(layer, v);
            java.awt.image.BufferedImage[] frames = new java.awt.image.BufferedImage[n];
            for (int f = 0; f < n; f++) frames[f] = panel.snapshot(at, f);
            double maxErr = 0;
            for (int y = at.y - 60; y < at.y + 60; y++) {
                for (int x = at.x - 60; x < at.x + 60; x++) {
                    int p = plain.getRGB(x, y);
                    int lum = (((p >> 16) & 0xFF) * 3 + ((p >> 8) & 0xFF) * 6 + (p & 0xFF)) / 10;
                    double sum = 0;
                    for (java.awt.image.BufferedImage f : frames) sum += Gamma.toLinear(f.getRGB(x, y) & 0xFF);
                    maxErr = Math.max(maxErr, Math.abs(perceived(sum / n) - Math.round(128 + (lum - 128) * 0.16)));
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
        System.out.println("  (녹화 프레임 여러 장을 평균하면 3·5장도 복원됨: 5장 평균 약 0.83·0.84 — README 참고)");
    }

    /**
     * 색 열쇠: 열쇠 색마다 ① 맨눈(밝기) ② 맞는 셀로판 ③ 틀린 셀로판 ④ 흑백 카메라 ⑤ 컬러 카메라 + 열쇠 채널 분리(공격).
     * 맨눈은 0에 가깝고 맞는 셀로판은 1에 가까워야 하며, ⑤가 높게 나오는 것이 이 방식의 한계다.
     */
    static void colorKeyCheck(QuestionVariant v) {
        Point at = new Point(300, 120);
        ExamPanel ref = panelFor(new PlainLayer() {
            @Override public boolean prefersLargeText() { return true; }
        }, v);
        java.awt.image.BufferedImage plain = ref.snapshot(at, 0);
        CameraModel video = CameraModel.PRESETS.get(1); // AI 안경 영상
        java.awt.image.BufferedImage plainShot = video.capture(f -> plain, 0, new Random(1));
        // 열쇠 색이 모두 나오도록 시드를 고른다 (시드 % 열쇠 수)
        for (long seed = 0; seed < ColorKeyLayer.KEYS.length; seed++) {
            ColorKeyLayer layer = new ColorKeyLayer(seed);
            ExamPanel panel = panelFor(layer, v);
            java.awt.image.BufferedImage img = panel.snapshot(at, 0);
            Cellophane key = layer.getKey();
            // 맨눈: 밝기만 보면 0이 나와도 색조 차이로 읽힐 수 있다 → 밝기·빨강-초록·파랑-노랑 중 가장 큰 상관
            double nakedLum = pixelCorr(plain, img, at, p -> (((p >> 16) & 0xFF) * 3 + ((p >> 8) & 0xFF) * 6 + (p & 0xFF)) / 10.0);
            double nakedRG = pixelCorr(plain, img, at, p -> ((p >> 16) & 0xFF) - ((p >> 8) & 0xFF));
            double nakedBY = pixelCorr(plain, img, at, p -> (p & 0xFF) - (((p >> 16) & 0xFF) + ((p >> 8) & 0xFF)) / 2.0);
            double naked = Math.max(Math.abs(nakedLum), Math.max(Math.abs(nakedRG), Math.abs(nakedBY)));
            double right = pixelCorr(plain, img, at, p -> key.channel(p));
            StringBuilder wrong = new StringBuilder();
            for (Cellophane c : Cellophane.values()) {
                if (c != key) wrong.append(String.format(" %s %.2f", c.getLabel(), pixelCorr(plain, img, at, p -> c.channel(p))));
            }
            double gray = correlation(plainShot, video.capture(f -> img, 0, new Random(1)), at, 60, video.scale());
            java.awt.image.BufferedImage keyOnly = channelImage(img, key); // 컬러 카메라로 찍고 열쇠 채널만 꺼낸 것
            double split = correlation(plainShot, video.capture(f -> keyOnly, 0, new Random(1)), at, 60, video.scale());
            System.out.printf("열쇠 %s: 맨눈(밝기 %.2f, 색 %.2f/%.2f) | 맞는 셀로판 %.2f | 틀린 셀로판%s | 흑백 카메라 %.2f | 컬러 카메라+채널 분리 %.2f%n",
                    key.getLabel(), nakedLum, nakedRG, nakedBY, right, wrong, gray, split);
            panel.clear();
            if (naked > 0.35) throw new AssertionError(key + " 맨눈에 글자가 비침 (" + naked + ")");
            if (right < 0.95) throw new AssertionError(key + " 셀로판으로 봐도 글자가 깨끗하지 않음 (" + right + ")");
            for (Cellophane c : Cellophane.values()) {
                double leak = pixelCorr(plain, img, at, p -> c.channel(p));
                if (c != key && Math.abs(leak) > 0.5) throw new AssertionError(c + "(틀린 색)으로도 글자가 보임 (" + leak + ")");
            }
        }
        ref.clear();
        System.out.println("  (컬러 카메라 + 채널 분리가 높게 나오는 것이 이 방식의 한계: 열쇠 색이 2가지뿐이라 다 시도하면 됨)");
    }

    /**
     * 원(줄)을 맨 아래에 두고, 위쪽 글줄들(원 밖)에 글자 정보가 얼마나 남는지 잰다.
     * 측정 영역 = 각 글줄의 안쪽(가장자리에서 4px 이상 떨어진 곳). 가장자리를 빼야 "줄 위치(막대 테두리)"가 아니라
     * 글자 모양 정보만 본다. 밝기 그대로의 상관과 세부(픽셀 - 7×7 평균) 상관을 함께 본다.
     * 흐린 원은 흐림 커널을 알고 있으므로(상자 흐림 2회) Richardson-Lucy 디블러로 되돌려 본다 (공격자 시나리오).
     */
    static void outsideInfoCheck(QuestionVariant v) {
        ExamPanel ref = panelFor(new PlainLayer(), v);
        java.awt.image.BufferedImage plain = ref.snapshot(null, 0);
        int w = plain.getWidth(), h = plain.getHeight();
        Point at = new Point(w / 2, h - 20); // 원·줄을 맨 아래에 두어 위쪽 글줄이 모두 원 밖
        boolean[] mask = new boolean[w * h];
        for (int[] l : ZeroInfoSpotlightLayer.detectLines(plain)) {
            if (l[1] > h - 60) continue; // 원(줄)이 걸리는 아래쪽 줄은 제외
            for (int y = l[0] + 4; y <= l[1] - 4; y++) for (int x = l[2] + 4; x <= l[3] - 4; x++) mask[y * w + x] = true;
        }
        double[] p0 = gray(plain), d0 = detail(p0, w, h);

        ProtectionLayer[] layers = {new SpotlightLayer(), new ZeroInfoSpotlightLayer(), new LineRulerLayer()};
        String[] names = {"흐린 원", "정보 없는 원", "줄 단위"};
        for (int i = 0; i < layers.length; i++) {
            ExamPanel p = panelFor(layers[i], v);
            double[] g = gray(p.snapshot(at, 0));
            double raw = corr(g, p0, mask), det = corr(detail(g, w, h), d0, mask);
            System.out.printf("%-8s 원 밖 글자 정보: 밝기 %.2f · 세부 %.2f%n", names[i], raw, det);
            if (i == 0) {
                int r = Math.max(8, 20 / 2); // ExamPanel 기본: 글자 크기 20 → 흐림 반경 10
                double[] restored = richardsonLucy(g, w, h, r, 10);
                System.out.printf("%-8s 원 밖 글자 정보: 밝기 %.2f · 세부 %.2f  ← 흐림 커널을 알고 디블러(10회)로 되돌린 공격%n",
                        "  └ 디블러", corr(restored, p0, mask), corr(detail(restored, w, h), d0, mask));
            } else if (Math.abs(raw) > 0.05 || Math.abs(det) > 0.05) {
                throw new AssertionError(names[i] + "인데 원 밖에 글자 정보가 남음 (" + raw + ", " + det + ")");
            }
            p.clear();
        }
        ref.clear();
        System.out.println("  (흐린 원: 눈으로는 못 읽고 단순 디블러로도 복원 안 되지만 통계적으로 정보가 남음 → '완벽'은 증명 불가."
                + " 정보 없는 원·줄 단위: 원 밖 정보 0 → 사진 한 장엔 원(줄) 안만 담김을 증명 가능)");
    }

    private static double[] gray(java.awt.image.BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        int[] rgb = img.getRGB(0, 0, w, h, null, 0, w);
        double[] g = new double[rgb.length];
        for (int i = 0; i < rgb.length; i++) {
            int p = rgb[i];
            g[i] = (((p >> 16) & 0xFF) * 3 + ((p >> 8) & 0xFF) * 6 + (p & 0xFF)) / 10.0;
        }
        return g;
    }

    /** 고주파 성분 (픽셀 - 7×7 평균). 가장자리 3px는 0 */
    private static double[] detail(double[] g, int w, int h) {
        double[] out = new double[g.length];
        for (int y = 3; y < h - 3; y++) {
            for (int x = 3; x < w - 3; x++) {
                double s = 0;
                for (int dy = -3; dy <= 3; dy++) for (int dx = -3; dx <= 3; dx++) s += g[(y + dy) * w + x + dx];
                out[y * w + x] = g[y * w + x] - s / 49;
            }
        }
        return out;
    }

    /** mask가 true인 픽셀만으로 피어슨 상관 (한쪽이 균일하면 0) */
    private static double corr(double[] a, double[] b, boolean[] mask) {
        double sa = 0, sb = 0, saa = 0, sbb = 0, sab = 0;
        int n = 0;
        for (int i = 0; i < a.length; i++) {
            if (!mask[i]) continue;
            sa += a[i]; sb += b[i]; saa += a[i] * a[i]; sbb += b[i] * b[i]; sab += a[i] * b[i];
            n++;
        }
        double va = saa / n - Math.pow(sa / n, 2), vb = sbb / n - Math.pow(sb / n, 2);
        return va < 1e-9 || vb < 1e-9 ? 0 : (sab / n - (sa / n) * (sb / n)) / Math.sqrt(va * vb);
    }

    /** QuestionRenderer.blur와 같은 흐림 (상자 흐림을 가로·세로 2회). 가장자리는 흰색(255)으로 채움 */
    private static double[] boxBlurTwice(double[] src, int w, int h, int r) {
        double[] a = src;
        for (int pass = 0; pass < 2; pass++) a = boxBlur(boxBlur(a, w, h, r, true), w, h, r, false);
        return a;
    }

    private static double[] boxBlur(double[] src, int w, int h, int r, boolean horizontal) {
        double[] out = new double[src.length];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double s = 0;
                for (int k = -r; k <= r; k++) {
                    int xx = horizontal ? x + k : x, yy = horizontal ? y : y + k;
                    s += (xx < 0 || yy < 0 || xx >= w || yy >= h) ? 255 : src[yy * w + xx];
                }
                out[y * w + x] = s / (2 * r + 1);
            }
        }
        return out;
    }

    /** Richardson-Lucy 디콘볼루션: 흐림 커널(대칭)을 알 때 흐림을 되돌리는 표준 방법 */
    private static double[] richardsonLucy(double[] observed, int w, int h, int r, int iterations) {
        double[] est = observed.clone();
        for (int it = 0; it < iterations; it++) {
            double[] blurred = boxBlurTwice(est, w, h, r);
            double[] ratio = new double[est.length];
            for (int i = 0; i < ratio.length; i++) ratio[i] = observed[i] / Math.max(1e-3, blurred[i]);
            double[] corr = boxBlurTwice(ratio, w, h, r);
            for (int i = 0; i < est.length; i++) est[i] *= corr[i];
        }
        return est;
    }

    private static double pixelCorr(java.awt.image.BufferedImage plain, java.awt.image.BufferedImage img, Point at,
                                    java.util.function.IntToDoubleFunction f) {
        double sa = 0, sb = 0, saa = 0, sbb = 0, sab = 0;
        int n = 0;
        for (int y = at.y - 60; y < at.y + 60; y++) {
            for (int x = at.x - 60; x < at.x + 60; x++) {
                int p = plain.getRGB(x, y);
                double a = (((p >> 16) & 0xFF) * 3 + ((p >> 8) & 0xFF) * 6 + (p & 0xFF)) / 10.0, b = f.applyAsDouble(img.getRGB(x, y));
                sa += a; sb += b; saa += a * a; sbb += b * b; sab += a * b;
                n++;
            }
        }
        double cov = sab / n - (sa / n) * (sb / n);
        return cov / Math.sqrt((saa / n - Math.pow(sa / n, 2)) * (sbb / n - Math.pow(sb / n, 2)));
    }

    private static java.awt.image.BufferedImage channelImage(java.awt.image.BufferedImage img, Cellophane c) {
        java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(img.getWidth(), img.getHeight(),
                java.awt.image.BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int v = c.channel(img.getRGB(x, y));
                out.setRGB(x, y, (v << 16) | (v << 8) | v);
            }
        }
        return out;
    }

    private static ExamPanel panelFor(ProtectionLayer layer, QuestionVariant v) {
        ExamPanel p = new ExamPanel(new TrajectoryRecorder());
        p.setLayer(layer);
        p.setRadius(90);
        p.showQuestion(v, 2);
        return p;
    }

    /** 노이즈 물리량 검사용 시간차 마스킹 (장수 지정. 기본값은 N=3) */
    static TemporalMaskLayer temporalPhysics(int n) {
        TemporalMaskLayer layer = new TemporalMaskLayer();
        layer.setSubframes(n);
        return layer;
    }

    /** 빛의 세기 평균 → 사람 눈이 보는 밝기 (픽셀 값 단위, 반올림 없이) */
    static double perceived(double meanLinear) {
        return 255 * Math.pow(meanLinear, 1 / Gamma.GAMMA);
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
