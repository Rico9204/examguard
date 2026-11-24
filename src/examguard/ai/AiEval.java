package examguard.ai;

import examguard.analysis.Sample;
import examguard.analysis.TrajectoryRecorder;
import examguard.analysis.TrajectorySimulator;
import examguard.display.CameraModel;
import examguard.display.ExamPanel;
import examguard.display.LineRulerLayer;
import examguard.display.PlainLayer;
import examguard.display.ProtectionLayer;
import examguard.display.SpotlightLayer;
import examguard.display.TemporalMaskLayer;
import examguard.display.ZeroInfoSpotlightLayer;
import examguard.fingerprint.ExamGenerator;
import examguard.model.ExamPaper;
import examguard.model.QuestionBank;
import examguard.model.QuestionVariant;
import examguard.model.Student;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 일괄 평가: 보호 방식 × 촬영 방식마다 AI(실제 사례 명령 "문제를 풀어줘")가 촬영본으로 문제를 맞히는 비율을 잰다.
 * 실제 명령은 "안 보이면 말해"가 없어 못 읽어도 보기를 찍으므로, 정답률을 우연 수준(4지선다 25%, 주관식 0%)과 비교한다.
 *
 *   java -cp out examguard.ai.AiEval [문항 수=5] [CSV 경로=ai-eval.csv]      호출 수 = 문항 수 × 20
 *   java -cp out examguard.ai.AiEval --selfcheck      채점 규칙 자체 점검 (모델 불필요)
 *
 * 문항은 학생마다 숫자·표현·보기 순서가 다른 시험지에서 차례로 뽑는다 (6문항이 넘으면 다음 학생의 시험지). 문항 수가 칸마다 표본 수다.
 * Ollama가 실행 중이어야 한다. 촬영본은 파일로 저장하지 않는다 (CSV에는 답변 글만).
 */
public final class AiEval {
    /** ponytail: 라이브 AI 프레임 간격 가정값 (CameraPreviewDialog와 같은 2초) */
    private static final long FRAME_MS = 2000;
    private static final int RADIUS = 90;

    private AiEval() {}

    private static final class Cell {
        final String name;
        final CameraModel cam;
        final boolean video;
        int n, correct;
        double chanceSum;
        Cell(String name, CameraModel cam, boolean video) { this.name = name; this.cam = cam; this.video = video; }
    }

    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(System.out, true, "UTF-8"); // Windows 콘솔에서도 한글이 깨지지 않게
        if (args.length > 0 && args[0].equals("--selfcheck")) {
            selfCheck();
            out.println("AiEval 채점 자체 점검 통과");
            return;
        }
        int count = args.length > 0 ? Integer.parseInt(args[0]) : 5;
        String csvPath = args.length > 1 ? args[1] : "ai-eval.csv";

        ExamGenerator gen = ExamGenerator.createDefault(QuestionBank.createSample(), "class-2026-final-secret");
        List<QuestionVariant> items = new ArrayList<>();
        for (Student s : Student.createRoster((count + 5) / 6)) items.addAll(gen.generate(s).getVariants());
        items = items.subList(0, count);

        String[] layerNames = {"보호 없음", "스포트라이트(흐림)", "정보 없는 스포트라이트", "줄 단위 표시", "시간차 마스킹"};
        List<Supplier<ProtectionLayer>> layers = List.of(PlainLayer::new, SpotlightLayer::new,
                ZeroInfoSpotlightLayer::new, LineRulerLayer::new, TemporalMaskLayer::new);

        int total = layerNames.length * 4 * items.size();
        out.printf("모델 %s · 명령 \"%s\" · 문항 %d개 × 보호 %d × 촬영 4 = %d회 호출%n",
                GlassesAssistant.MODEL, GlassesAssistant.PROMPT, items.size(), layerNames.length, total);

        List<Cell[]> table = new ArrayList<>();
        int done = 0;
        try (PrintWriter csv = new PrintWriter(Files.newBufferedWriter(Paths.get(csvPath), StandardCharsets.UTF_8))) {
            csv.print('﻿'); // 엑셀에서 한글이 깨지지 않게 BOM
            csv.println("보호,촬영,문항,정답,AI가 고른 답,맞음,이미지 수,초,AI 답변");
            for (int l = 0; l < layerNames.length; l++) {
                Cell[] cells = {
                        new Cell("초소형 카메라 · 정지 1장", CameraModel.PRESETS.get(0), false),
                        new Cell("초소형 카메라 · 영상", CameraModel.PRESETS.get(0), true),
                        new Cell("AI 안경 · 영상(라이브 AI)", CameraModel.PRESETS.get(1), true),
                        new Cell("AI 안경 · 사진", CameraModel.PRESETS.get(2), false)};
                table.add(cells);
                for (Cell c : cells) {
                    for (int i = 0; i < items.size(); i++) {
                        QuestionVariant v = items.get(i);
                        int q = i % 6;
                        ExamPanel panel = new ExamPanel(new TrajectoryRecorder());
                        panel.setLayer(layers.get(l).get());
                        panel.showQuestion(v, q);
                        panel.setRadius(RADIUS);
                        List<BufferedImage> shots = shoot(panel, c, q, i);
                        panel.clear(); // 애니메이션을 멈춘다 (안 멈추면 평가가 끝나도 프로그램이 종료되지 않음)
                        // 차례마다 새 대화 (앞 문항 기억이 다음 채점에 섞이지 않게)
                        GlassesAssistant.Answer a = new GlassesAssistant().ask(shots, GlassesAssistant.PROMPT);
                        String picked = pick(v, a.text);
                        boolean ok = v.getCorrectAnswer().equals(picked);
                        c.n++;
                        if (ok) c.correct++;
                        c.chanceSum += v.isMultipleChoice() ? 1.0 / v.getChoices().size() : 0;
                        csv.printf("%s,%s,%s,%s,%s,%s,%d,%.1f,%s%n", layerNames[l], c.name, v.getSource().getId(),
                                v.getCorrectAnswer(), picked == null ? "" : picked, ok ? "O" : "X",
                                shots.size(), a.millis / 1000.0, csvField(a.text));
                        csv.flush();
                        out.printf("\r진행 %d/%d", ++done, total);
                    }
                }
            }
        }

        out.printf("%n%n| 보호 방식 | 촬영 방식 | 정답률 | 우연 수준 | 판정 |%n| --- | --- | --- | --- | --- |%n");
        for (int l = 0; l < table.size(); l++) {
            for (Cell c : table.get(l)) {
                double chance = c.chanceSum / c.n;
                out.printf("| %s | %s | %d/%d (%.0f%%) | %.0f%% | %s |%n", layerNames[l], c.name, c.correct, c.n,
                        100.0 * c.correct / c.n, 100 * chance, verdict(c.correct, c.n, chance));
            }
        }
        out.println("\n판정: 95% 신뢰구간 하한이 우연 수준보다 높으면 \"읽고 맞힘\". 표본이 적으면 \"판단 보류\"가 많아진다 (문항 수를 늘릴 것).");
        out.println("문항별 답변: " + Paths.get(csvPath).toAbsolutePath());
    }

    /** 사진: 읽는 궤적 중 한 순간 1장. 영상: 궤적을 따라 FRAME_MS마다 1장 (기억 한도까지 고르게 솎음) */
    static List<BufferedImage> shoot(ExamPanel panel, Cell c, int q, long seed) {
        Dimension d = panel.getPreferredSize();
        List<Sample> path = TrajectorySimulator.reading(d.width, d.height, RADIUS, 0, q, seed);
        List<Sample> picks = new ArrayList<>();
        if (!c.video) {
            picks.add(path.get(new Random(seed).nextInt(path.size())));
        } else {
            long next = path.get(0).getTime();
            for (Sample s : path) {
                if (s.getTime() >= next) { picks.add(s); next = s.getTime() + FRAME_MS; }
            }
            if (picks.size() > GlassesAssistant.MAX_CONTEXT_IMAGES) {
                // ponytail: 긴 문제는 고르게 솎는다. 실제 라이브 AI는 오래된 장면을 잊는 쪽일 수 있음
                List<Sample> thin = new ArrayList<>();
                for (int k = 0; k < GlassesAssistant.MAX_CONTEXT_IMAGES; k++) {
                    thin.add(picks.get(k * picks.size() / GlassesAssistant.MAX_CONTEXT_IMAGES));
                }
                picks = thin;
            }
        }
        List<BufferedImage> shots = new ArrayList<>();
        Random rnd = new Random(seed);
        for (Sample s : picks) {
            java.awt.Point at = new java.awt.Point(s.getX(), s.getY());
            long frame = (long) (s.getTime() / CameraModel.DISPLAY_FRAME_MS);
            shots.add(c.cam.capture(f -> panel.snapshot(at, f), frame, rnd));
        }
        return shots;
    }

    private static final Pattern CHOICE_NUM = Pattern.compile("\\(([1-9])\\)|(?<![0-9])([1-9])\\s*번|^\\s*([1-9])(?![0-9])");
    private static final Pattern ANSWER_NUM = Pattern.compile("답[^0-9\\-]{0,10}(-?[0-9][0-9,]*)");
    private static final Pattern ANY_NUM = Pattern.compile("-?[0-9][0-9,]*");
    /** "못 읽음" 답변. 안내 목록 번호("1. 텍스트로 보내주세요")를 답으로 집지 않게 한다 */
    private static final Pattern CANNOT_READ = Pattern.compile(
            "읽을 수 없|읽기 어렵|알아볼 수 없|판독(이)? (불가|어렵)|흐려서|흐릿해|선명한 사진|다시 (찍|촬영)|텍스트로 보내");
    private static final Pattern STATED_ANSWER = Pattern.compile("정답|답은|답:|답 *[0-9①-⑨]");

    /**
     * AI 답변에서 고른 답을 꺼낸다 (못 찾으면 null = 오답 처리).
     * 객관식: ① 보기 글이 딱 하나만 나오면 그것 ② 원 숫자 ③ "(3)", "3번", 맨 앞 숫자. "문제 3"은 답으로 보지 않는다.
     * 주관식: "답" 뒤의 첫 숫자, 없으면 첫 숫자 (쉼표 제거).
     * 못 읽었다고 하면서 답을 밝히지 않으면 null.
     * ponytail: 규칙 기반 채점. 애매한 답변은 CSV의 원문으로 사람이 다시 볼 것.
     */
    static String pick(QuestionVariant v, String answer) {
        if (CANNOT_READ.matcher(answer).find() && !STATED_ANSWER.matcher(answer).find()) return null;
        if (v.isMultipleChoice()) {
            List<String> choices = v.getChoices();
            String low = answer.toLowerCase();
            int found = -1, count = 0;
            for (int i = 0; i < choices.size(); i++) {
                if (low.contains(choices.get(i).toLowerCase())) { found = i; count++; }
            }
            if (count == 1) return String.valueOf(found + 1);
            int best = -1, bestPos = Integer.MAX_VALUE;
            for (int i = 0; i < choices.size(); i++) {
                int pos = answer.indexOf(ExamPaper.CIRCLED[i]);
                if (pos >= 0 && pos < bestPos) { best = i; bestPos = pos; }
            }
            if (best >= 0) return String.valueOf(best + 1);
            Matcher m = CHOICE_NUM.matcher(answer);
            if (m.find()) {
                for (int g = 1; g <= 3; g++) {
                    if (m.group(g) != null && Integer.parseInt(m.group(g)) <= choices.size()) return m.group(g);
                }
            }
            return null;
        }
        Matcher m = ANSWER_NUM.matcher(answer);
        if (m.find()) return QuestionVariant.normalize(m.group(1));
        m = ANY_NUM.matcher(answer);
        return m.find() ? QuestionVariant.normalize(m.group()) : null;
    }

    /** 95% 윌슨 신뢰구간 하한이 우연 수준보다 높은가 */
    static String verdict(int k, int n, double chance) {
        double z = 1.96, p = (double) k / n;
        double low = (p + z * z / (2 * n) - z * Math.sqrt(p * (1 - p) / n + z * z / (4.0 * n * n))) / (1 + z * z / n);
        if (low > chance) return "**읽고 맞힘** (우연 이상)";
        return p <= chance ? "우연 수준 이하 (못 읽음)" : "판단 보류 (표본 부족)";
    }

    private static String csvField(String s) {
        return "\"" + s.replace("\r", "").replace("\n", " / ").replace("\"", "\"\"") + "\"";
    }

    private static void selfCheck() {
        QuestionVariant mc = new QuestionVariant(null, "", List.of("static", "final", "interface", "transient"), "3");
        check("3".equals(pick(mc, "(3) interface")), "보기 번호+글");
        check("3".equals(pick(mc, "③")), "원 숫자");
        check("1".equals(pick(mc, "static")), "보기 글만");
        check(pick(mc, "문제 3") == null, "문제 번호는 답이 아님");
        check("3".equals(pick(mc, "interface와 static을 비교하면 정답은 ③")), "글 두 개면 원 숫자로");
        check("2".equals(pick(mc, "2번입니다")), "N번");
        QuestionVariant num = new QuestionVariant(null, "", List.of(), "23400");
        check("23400".equals(pick(num, "23,400원")), "쉼표 숫자");
        check("23400".equals(pick(num, "문제 2: 답 23,400원")), "답 뒤 숫자 우선");
        QuestionVariant rem = new QuestionVariant(null, "", List.of(), "1");
        check("1".equals(pick(rem, "정답은 1명 (21 ÷ 4 = 5 나머지 1)")), "정답 뒤 숫자");
        check(pick(rem, "이미지의 글자가 흐려서 문제 내용을 읽을 수 없습니다.\n1. 문제를 텍스트로 보내주거나") == null,
                "못 읽음 안내의 목록 번호는 답이 아님");
        check(pick(mc, "글자가 흐릿해 알아볼 수 없습니다. 1. 다시 찍어 주세요") == null, "객관식 못 읽음");
        check("1".equals(pick(rem, "일부가 흐릿해 확실하진 않지만 정답은 1명")), "흐려도 답을 밝히면 채점");
        check(verdict(10, 12, 0.25).startsWith("**읽고"), "10/12는 우연 이상");
        check(verdict(3, 12, 0.25).startsWith("우연"), "3/12는 우연 수준");
        check(verdict(5, 12, 0.25).startsWith("판단 보류"), "5/12는 보류");
    }

    private static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }
}
