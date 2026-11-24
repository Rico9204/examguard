package examguard.analysis;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * "너무 빠른 정답" 기준을 실측 데이터로 정하는 보정 도구 (docs/research/answer-pattern-research.md 5-1).
 *
 *   java -cp out examguard.analysis.TimeCalibration [data 폴더, 기본 data]
 *
 * data/*_answers.csv의 정답 문항 머문 시간을 문항별로 모아, 풀이 시간이 로그정규분포를 따른다고 보고
 * (van der Linden 2006) 하위 0.5% 지점 exp(μ − 2.58σ)를 출력한다.
 * 성실 응시자 기록만 넣어야 한다 (일부러 훑기·방치 실험 기록은 data 밖으로 빼 둘 것).
 * 출력값은 아직 ReviewAdvisor에 연결하지 않는다: 문항당 20건 이상 모인 뒤 사람이 보고 반영한다.
 */
public final class TimeCalibration {
    /** 표준정규분포 하위 0.5% 지점 */
    static final double Z_LOW = 2.576;
    static final int MIN_SAMPLES = 20;

    private TimeCalibration() {}

    public static void main(String[] args) throws IOException {
        Path dir = Paths.get(args.length > 0 ? args[0] : "data");
        Map<Integer, List<Double>> byQuestion = load(dir);
        if (byQuestion.isEmpty()) {
            System.out.println(dir.toAbsolutePath() + " 에 정답 기록이 없습니다.");
            return;
        }
        System.out.println("문항 | 정답 기록 수 | 중앙값(초) | σ(log) | 하위 0.5% 기준(초) | 비고");
        for (Map.Entry<Integer, List<Double>> e : byQuestion.entrySet()) {
            double[] s = e.getValue().stream().mapToDouble(Double::doubleValue).toArray();
            double[] ms = logMeanSd(s);
            System.out.printf("%4d | %11d | %10.1f | %6.2f | %18.1f | %s%n", e.getKey(), s.length,
                    Math.exp(ms[0]), ms[1], threshold(s),
                    s.length < MIN_SAMPLES ? "표본 부족 (" + MIN_SAMPLES + "건 미만) — 기준으로 쓰지 말 것" : "");
        }
    }

    /** 하위 0.5% 기준 시간 exp(μ − 2.58σ) */
    public static double threshold(double[] seconds) {
        double[] ms = logMeanSd(seconds);
        return Math.exp(ms[0] - Z_LOW * ms[1]);
    }

    /** log(시간)의 평균과 표본 표준편차 */
    static double[] logMeanSd(double[] seconds) {
        double sum = 0, sq = 0;
        for (double t : seconds) sum += Math.log(t);
        double mean = sum / seconds.length;
        for (double t : seconds) sq += (Math.log(t) - mean) * (Math.log(t) - mean);
        double sd = seconds.length > 1 ? Math.sqrt(sq / (seconds.length - 1)) : 0;
        return new double[]{mean, sd};
    }

    /** 문항 번호 → 정답 문항 머문 시간(초). 0초(기록 단위 미만)는 log를 못 하므로 뺀다. */
    static Map<Integer, List<Double>> load(Path dir) throws IOException {
        Map<Integer, List<Double>> out = new TreeMap<>();
        if (!Files.isDirectory(dir)) return out;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*_answers.csv")) {
            for (Path f : files) {
                List<String> lines = Files.readAllLines(f, StandardCharsets.UTF_8);
                for (int i = 1; i < lines.size(); i++) { // 0번 줄은 머리글
                    // 적은 답에만 쉼표가 들어갈 수 있으므로 정오·시간은 뒤에서부터 읽는다
                    String[] c = lines.get(i).split(",");
                    if (c.length < 5) continue;
                    try {
                        int q = Integer.parseInt(c[0].trim());
                        double t = Double.parseDouble(c[c.length - 1].trim());
                        if ("O".equals(c[c.length - 2].trim()) && t > 0) {
                            out.computeIfAbsent(q, k -> new ArrayList<>()).add(t);
                        }
                    } catch (NumberFormatException ignored) {
                        // 형식이 다른 줄은 건너뜀
                    }
                }
            }
        }
        return out;
    }
}
