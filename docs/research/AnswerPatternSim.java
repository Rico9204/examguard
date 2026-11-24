import java.util.Arrays;
import java.util.Random;

/**
 * 연구용 시뮬레이션: 부정행위자 답안 패턴 신호가 "문항 수가 적은 시험"에서도 통하는가?
 * 프로그램 본체와 무관한 독립 파일 (JDK 8+, 외부 라이브러리 없음).
 *
 *   javac -encoding UTF-8 -d out/research docs/research/AnswerPatternSim.java
 *   java -cp out/research AnswerPatternSim
 *
 * 1. 개인 적합도 lz (Drasgow 등 1985): Rasch 모형, 성실 응시자 기준 하위 5%·1%를 문턱으로 두고 탐지율을 잰다.
 *    - 부정 A "어려운 문항만 AI 도움": 실력 낮음, 어려운 절반은 90% 정답, 나머지는 자기 실력
 *    - 부정 B "전 문항 AI 도움": 모든 문항 90% 정답 (실력과 무관)
 * 2. 너무 빠른 정답 규칙 (예상 시간 × 0.25 미만): 성실 응시자 풀이 시간이 로그정규(van der Linden 2006)일 때
 *    시간 퍼짐 σ에 따라 오탐률이 얼마나 변하는가.
 * ponytail: 문항 모수를 "참값으로 안다"고 가정 (실제로는 추정 오차가 더해져 결과가 더 나빠짐).
 */
public class AnswerPatternSim {
    static final Random RNG = new Random(20261123);
    static final int HONEST = 20000, CHEATERS = 5000;

    public static void main(String[] args) {
        selfCheck();
        System.out.println("== 1. lz 탐지율 (성실 응시자 오탐 5% / 1% 문턱) ==");
        System.out.println("문항수 | A:어려운 문항만 AI (5%/1%) | B:전 문항 AI (5%/1%)");
        for (int n : new int[]{6, 20, 40, 80}) {
            double[] b = difficulties(n);
            double[] honest = new double[HONEST];
            for (int i = 0; i < HONEST; i++) honest[i] = lz(b, simulate(b, RNG.nextGaussian(), false, false));
            Arrays.sort(honest);
            double c5 = honest[(int) (HONEST * 0.05)], c1 = honest[(int) (HONEST * 0.01)];
            int a5 = 0, a1 = 0, b5 = 0, b1 = 0;
            for (int i = 0; i < CHEATERS; i++) {
                double la = lz(b, simulate(b, -1 + 0.5 * RNG.nextGaussian(), true, false));
                double lb = lz(b, simulate(b, -1 + 0.5 * RNG.nextGaussian(), false, true));
                if (la < c5) a5++;
                if (la < c1) a1++;
                if (lb < c5) b5++;
                if (lb < c1) b1++;
            }
            System.out.printf("%5d  | %5.1f%% / %5.1f%%            | %5.1f%% / %5.1f%%%n",
                    n, pct(a5), pct(a1), pct(b5), pct(b1));
        }

        System.out.println();
        System.out.println("== 2. '너무 빠른 정답'(예상 시간 25% 미만) 성실 응시자 오탐률 ==");
        System.out.println("시간 퍼짐 σ | 문항당 | 숫자 3문항 중 1개 이상");
        for (double sigma : new double[]{0.3, 0.5, 0.8, 1.0}) {
            int hit = 0, trials = 200000;
            for (int i = 0; i < trials; i++) if (Math.exp(sigma * RNG.nextGaussian()) < 0.25) hit++;
            double p = (double) hit / trials;
            System.out.printf("   %.1f      | %5.2f%% | %5.2f%%%n", sigma, 100 * p, 100 * (1 - Math.pow(1 - p, 3)));
        }
    }

    static double pct(int k) { return 100.0 * k / CHEATERS; }

    static double[] difficulties(int n) {
        double[] b = new double[n];
        for (int i = 0; i < n; i++) b[i] = -2 + 4.0 * i / (n - 1);
        return b;
    }

    static double p(double theta, double b) { return 1 / (1 + Math.exp(-(theta - b))); }

    static int[] simulate(double[] b, double theta, boolean aiOnHard, boolean aiOnAll) {
        int[] u = new int[b.length];
        for (int i = 0; i < b.length; i++) {
            boolean ai = aiOnAll || (aiOnHard && b[i] > 0);
            u[i] = RNG.nextDouble() < (ai ? 0.9 : p(theta, b[i])) ? 1 : 0;
        }
        return u;
    }

    /** 표준화 로그우도 lz. θ는 최대우도 추정 (만점·영점은 ±4로 고정). 낮을수록 부적합. */
    static double lz(double[] b, int[] u) {
        double theta = mle(b, u), l0 = 0, e = 0, v = 0;
        for (int i = 0; i < b.length; i++) {
            double pi = p(theta, b[i]), lp = Math.log(pi), lq = Math.log(1 - pi);
            l0 += u[i] == 1 ? lp : lq;
            e += pi * lp + (1 - pi) * lq;
            v += pi * (1 - pi) * (lp - lq) * (lp - lq);
        }
        return (l0 - e) / Math.sqrt(v);
    }

    static double mle(double[] b, int[] u) {
        int sum = 0;
        for (int x : u) sum += x;
        if (sum == 0) return -4;
        if (sum == u.length) return 4;
        double t = 0;
        for (int it = 0; it < 50; it++) {
            double g = 0, h = 0;
            for (int i = 0; i < b.length; i++) {
                double pi = p(t, b[i]);
                g += u[i] - pi;
                h += pi * (1 - pi);
            }
            t = Math.max(-4, Math.min(4, t + g / h));
        }
        return t;
    }

    /** 논리가 깨지면 실패하는 최소 점검: 쉬운 것부터 맞힌 패턴은 적합, 뒤집힌 패턴은 강한 부적합 */
    static void selfCheck() {
        double[] b = difficulties(20);
        int[] guttman = new int[20], reversed = new int[20];
        for (int i = 0; i < 10; i++) { guttman[i] = 1; reversed[19 - i] = 1; }
        if (!(lz(b, guttman) > 0 && lz(b, reversed) < -3)) throw new IllegalStateException("lz 계산 오류");
        if (Math.abs(mle(b, guttman)) > 0.5) throw new IllegalStateException("MLE 오류");
    }
}
