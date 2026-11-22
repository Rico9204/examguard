package examguard.display;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.awt.image.WritableRaster;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.LongFunction;

/**
 * 촬영 기기 시뮬레이션 (실험 도구, 최소 버전).
 * 시험 화면이 초소형 카메라·AI 안경에 "어떻게 찍히는지" 근사한다.
 *
 * 처리 순서:
 *  1) 노출: 노출 시간 동안 화면에 나온 프레임들을 평균 (시간차 마스킹의 성패가 여기서 갈린다)
 *  2) 해상도: 촬영 거리·화각·센서 화소로 "화면 1px이 센서 몇 px에 맺히는지" 계산해 축소
 *  3) 렌즈 흐림: 가우시안
 *  4) 센서 노이즈: 가우시안
 *  5) JPEG 압축
 *
 * 하지 않는 것 (보고서에 한계로 적을 것):
 *  - 모아레: 서브픽셀 배열·센서 색 필터·디모자이크에 좌우되어 이 근사로는 재현 불가. 실제 기기로만 확인.
 *  - 원근 왜곡, 롤링 셔터, 자동 노출, 제조사 영상 처리(선명화·노이즈 제거·다중 프레임 HDR 합성).
 *  - 색: 흑백으로 처리한다.
 *
 * 프리셋 값은 공개 제원을 참고한 "가정값"이다. 보고서에 쓰기 전에 출처를 확인하고,
 * 휴대폰으로 실제 화면을 찍은 사진과 비교해 맞춰야 한다.
 */
public final class CameraModel {
    /** ponytail: 모니터 픽셀 간격 고정값 (24인치 FHD≈0.277mm, 15.6인치 FHD 노트북≈0.18mm). 실제 모니터에 맞춰 바꿀 값 */
    public static final double SCREEN_MM_PER_PX = 0.25;
    /** ExamPanel 애니메이션 주기(약 60fps) = 화면 한 프레임 */
    public static final double DISPLAY_FRAME_MS = 1000.0 / 60;

    /*
     * 제원 출처 (2026-11 확인, 소매·리뷰 사이트 기준이라 제조사 공식 사양서로 다시 확인할 것):
     *  - AI 안경: Ray-Ban Meta 12MP 초광각, 화각 100°, 사진 3024×4032(세로), 영상 1440×1920(세로) 30fps,
     *    Gen 2는 3K 영상도 지원 (ray-ban.com, Best Buy, TechRadar). 100°는 대각 화각으로 가정.
     *    안경은 세로 영상이라 "가로"가 짧은 변(1440px)이다.
     *  - 초소형 카메라: 단추형 1080p 30fps, 화각 75° (B&H Mini Gadgets 버튼 카메라). 대각 화각으로 가정.
     *  - 휴대폰: 일반 12MP 주 카메라(4032×3024, 대각 약 79° = 26mm 환산).
     * 렌즈 흐림은 축소 과정에서 이미 평균이 되므로 작게(0.4~0.5px) 둔다.
     * 노출 33ms는 30fps 영상의 최대값이다. 밝은 모니터를 찍으면 자동 노출이 더 짧게 잡을 수 있다.
     */
    public static final List<CameraModel> PRESETS = Arrays.asList(
            new CameraModel("초소형 카메라 (단추형, 1080p 30fps)", 1920, 1080, 75, 500, 33, 0.5, 4, 0.75f),
            new CameraModel("AI 안경 · 영상 (1440×1920 세로, 30fps)", 1440, 1920, 100, 550, 33, 0.5, 3, 0.8f),
            new CameraModel("AI 안경 · 사진 (3024×4032 세로, 짧은 노출)", 3024, 4032, 100, 550, 4, 0.5, 3, 0.85f),
            new CameraModel("휴대폰 (비교 기준, 12MP)", 4032, 3024, 79, 500, 8, 0.4, 2, 0.9f));

    private final String name;
    private final int sensorWidthPx;
    private final double hfovDeg;     // 대각 화각에서 계산한 가로 화각
    private final double distanceMm;
    private final double exposureMs;
    private final double blurSigma;   // 센서 px 단위
    private final double noiseSigma;  // 밝기 0~255 단위
    private final float jpegQuality;

    /** 제원표 그대로 받는다: 해상도 가로×세로, 대각 화각 */
    public CameraModel(String name, int widthPx, int heightPx, double diagonalFovDeg, double distanceMm,
                       double exposureMs, double blurSigma, double noiseSigma, float jpegQuality) {
        this.name = name;
        this.sensorWidthPx = widthPx;
        double halfDiag = Math.tan(Math.toRadians(diagonalFovDeg / 2));
        this.hfovDeg = Math.toDegrees(2 * Math.atan(halfDiag * widthPx / Math.hypot(widthPx, heightPx)));
        this.distanceMm = distanceMm;
        this.exposureMs = exposureMs;
        this.blurSigma = blurSigma;
        this.noiseSigma = noiseSigma;
        this.jpegQuality = jpegQuality;
    }

    public String getName() { return name; }

    @Override
    public String toString() { return name; }

    /** 화면 1px이 센서 몇 px에 맺히는가 (1 이상이면 화면보다 세밀하므로 1로 자름) */
    public double scale() {
        double fovMm = 2 * distanceMm * Math.tan(Math.toRadians(hfovDeg / 2));
        return Math.min(1.0, sensorWidthPx * SCREEN_MM_PER_PX / fovMm);
    }

    /** 노출 시간 동안 화면에 나온 프레임 수 */
    public int framesPerExposure() {
        return Math.max(1, (int) Math.round(exposureMs / DISPLAY_FRAME_MS));
    }

    /** 화면 설명 한 줄 (미리보기 창에 표시) */
    public String describe(int screenWidthPx) {
        return String.format("가로 화각 %.0f° · 화면 %dpx → 촬영 %dpx (%.2f배) · 노출 %.0fms = 화면 %d프레임 평균 · JPEG %.0f%%",
                hfovDeg, screenWidthPx, (int) Math.round(screenWidthPx * scale()), scale(), exposureMs,
                framesPerExposure(), jpegQuality * 100);
    }

    /**
     * frameAt: 화면 프레임 번호 → 그 순간의 화면 이미지. startFrame부터 노출 시간만큼 평균한다.
     * 결과는 센서 해상도의 흑백 이미지 (화면보다 작다).
     */
    public BufferedImage capture(LongFunction<BufferedImage> frameAt, long startFrame, Random rnd) {
        // 1) 노출: 프레임 평균
        // ponytail: 노출이 프레임 경계에 딱 맞는다고 가정. 실제로는 위상이 어긋나 일부 프레임만 섞인다.
        int n = framesPerExposure();
        BufferedImage first = frameAt.apply(startFrame);
        int w = first.getWidth(), h = first.getHeight();
        double[] acc = new double[w * h];
        for (int k = 0; k < n; k++) {
            BufferedImage f = k == 0 ? first : frameAt.apply(startFrame + k);
            int[] rgb = f.getRGB(0, 0, w, h, null, 0, w);
            for (int i = 0; i < rgb.length; i++) {
                int p = rgb[i];
                acc[i] += (((p >> 16) & 0xFF) * 3 + ((p >> 8) & 0xFF) * 6 + (p & 0xFF)) / 10.0;
            }
        }
        BufferedImage gray = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        WritableRaster gr = gray.getRaster();
        for (int i = 0; i < acc.length; i++) gr.setSample(i % w, i / w, 0, (int) Math.round(acc[i] / n));

        // 2) 해상도
        int tw = Math.max(1, (int) Math.round(w * scale())), th = Math.max(1, (int) Math.round(h * scale()));
        BufferedImage img = downscale(gray, tw, th);

        // 3) 렌즈 흐림
        if (blurSigma > 0) img = gaussian(img, blurSigma);

        // 4) 센서 노이즈
        WritableRaster r = img.getRaster();
        for (int y = 0; y < th; y++) {
            for (int x = 0; x < tw; x++) {
                int v = (int) Math.round(r.getSample(x, y, 0) + rnd.nextGaussian() * noiseSigma);
                r.setSample(x, y, 0, Math.max(0, Math.min(255, v)));
            }
        }

        // 5) JPEG
        return jpeg(img, jpegQuality);
    }

    /** 반씩 줄여 가며 축소 (한 번에 크게 줄이면 가는 획이 통째로 빠지는 앨리어싱이 생긴다) */
    private static BufferedImage downscale(BufferedImage src, int tw, int th) {
        BufferedImage cur = src;
        int w = src.getWidth(), h = src.getHeight();
        while (w / 2 >= tw && h / 2 >= th) {
            w /= 2;
            h /= 2;
            cur = resize(cur, w, h);
        }
        return (w == tw && h == th) ? cur : resize(cur, tw, th);
    }

    private static BufferedImage resize(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    private static BufferedImage gaussian(BufferedImage src, double sigma) {
        int radius = (int) Math.ceil(sigma * 3);
        int size = radius * 2 + 1;
        float[] k = new float[size * size];
        float sum = 0;
        for (int y = -radius; y <= radius; y++) {
            for (int x = -radius; x <= radius; x++) {
                float v = (float) Math.exp(-(x * x + y * y) / (2 * sigma * sigma));
                k[(y + radius) * size + x + radius] = v;
                sum += v;
            }
        }
        for (int i = 0; i < k.length; i++) k[i] /= sum;
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        return new ConvolveOp(new Kernel(size, size, k), ConvolveOp.EDGE_NO_OP, null).filter(src, out);
    }

    private static BufferedImage jpeg(BufferedImage img, float quality) {
        try {
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (MemoryCacheImageOutputStream out = new MemoryCacheImageOutputStream(bytes)) {
                writer.setOutput(out);
                ImageWriteParam p = writer.getDefaultWriteParam();
                p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                p.setCompressionQuality(quality);
                writer.write(null, new IIOImage(img, null, null), p);
            } finally {
                writer.dispose();
            }
            return ImageIO.read(new ByteArrayInputStream(bytes.toByteArray()));
        } catch (IOException e) {
            throw new UncheckedIOException(e); // 메모리 안에서만 쓰므로 실제로는 일어나지 않음
        }
    }
}
