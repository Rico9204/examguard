package examguard.ai;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * AI 안경의 "보고 답하기" 흐름을 흉내 낸다 (실험 도구).
 * 카메라 시뮬레이션 촬영본 + 정해진 프롬프트 → 비전 언어 모델 → 답변.
 *
 * 실제 기기와 맞춘 점 (Ray-Ban Meta 기준, 2026-11 공개 자료):
 *  - 안경은 사진을 폰 앱을 거쳐 Meta AI(클라우드, Llama 계열 멀티모달 모델)로 보낸다.
 *    Meta AI가 실제로 쓰는 모델 버전·크기·전처리는 공개되지 않았다.
 *  - ponytail: 기본값은 gemma3:4b (구글, 8GB GPU에 들어감). 같은 Llama 계열인 llama3.2-vision은
 *    Ollama 0.40부터 실행되지 않아(unknown model architecture 'mllama') 대신 쓴다.
 *    Llama 계열이 꼭 필요하면 예전 Ollama + EXAMGUARD_VISION_MODEL=llama3.2-vision.
 *  - gemma3는 이미지를 896×896으로 줄여 본다. 이 축소도 실제 파이프라인의 일부다 (클라우드 모델도 입력 크기 상한이 있음).
 *  - 안경 답변은 렌즈 화면·음성으로 짧게 나온다 → 출력 길이를 제한한다.
 *  - 2026년 국가자격시험 적발 안경 5건 중 4건이 Rokid(클라우드 Gemini·ChatGPT·DeepSeek·Qwen 중 선택), 1건이 Meta.
 * 다른 점: 네트워크 지연·폰 앱의 재압축·실제 앱의 시스템 프롬프트(비공개, SYSTEM은 가정)는 재현하지 않는다.
 *
 * 환경 변수: OLLAMA_HOST (기본 http://localhost:11434), EXAMGUARD_VISION_MODEL (기본 gemma3:4b)
 * JDK 내장 HttpClient만 쓴다 (외부 라이브러리 없음).
 */
public final class GlassesAssistant {
    /**
     * 실행 폴더의 .env.local (KEY=VALUE 줄). 시스템 환경 변수가 있으면 그쪽이 우선.
     * 아래 설정값들보다 먼저 읽혀야 하므로 맨 위에 둔다 (static 필드는 적힌 순서대로 초기화됨).
     * 키가 들어 있으므로 과제 제출·공유할 때 이 파일은 빼야 한다.
     */
    private static final java.util.Map<String, String> DOTENV = loadDotEnv(java.nio.file.Paths.get(".env.local"));

    /**
     * 수험자 명령: 실제 적발 사례에서 쓰인 문구를 그대로 쓴다. 보호 방식끼리 같은 조건으로 비교하려고 고정한다.
     * 출처: 2026-05-22 정보처리기사 AI 안경 부정행위 (MBC 2026-10-22 보도) — 수험자가 "AI안경과 연동된
     * 휴대전화 어플리케이션에 '문제를 풀어달라'고 미리 명령어를 입력해 둔 것으로 조사됐습니다."
     * 보도된 간접 인용("풀어달라")을 직접 명령형으로 옮겼다. 사진 한 장·영상 모두 같은 명령
     * (실제 기기는 영상이라고 따로 설명하지 않는다).
     * 세무사 초소형 카메라 사례(2026-11 자백 보도)도 "문제를 촬영해 생성형 AI에 전송"했다고만 알려져 문구는 비공개.
     */
    public static final String PROMPT = "문제를 풀어줘";

    /**
     * 기기 쪽 설정 흉내 (수험자 명령이 아니라 안경 앱의 언어·화면 설정).
     * ponytail: 실제 앱의 시스템 프롬프트는 비공개. 한국어로 설정한 앱 + Rokid처럼 렌즈에 짧은 글만
     * 띄우는 화면(긴 답은 폰으로)을 가정한 값. 수험자 명령과 분리해 두어 명령만 바꿔 실험할 수 있다.
     */
    static final String SYSTEM =
            "반드시 한국어로만 답한다. 영어 문장은 절대 쓰지 않는다 "
            + "(프로그래밍 키워드·고유명사처럼 사진에 영어로 적힌 낱말은 그대로 써도 된다). 짧게 답한다.";
    // "너는 스마트 안경 비서다·렌즈 화면에 표시된다"를 넣었더니 작은 모델이 기기 메뉴("알림 설정", "음량 조절")를
    // 답하는 역할극에 빠져 뺐다 (2026-11-23 gemma3:4b 실험).

    private static final String HOST = env("OLLAMA_HOST", "http://localhost:11434");
    /**
     * 모델 공급자: "ollama"(기본, 로컬 — 이미지가 PC 밖으로 나가지 않음) 또는 "openai"(클라우드, OPENAI_API_KEY 필요).
     * 키가 있다는 것만으로 시험 이미지가 외부로 나가지 않도록, openai는 EXAMGUARD_PROVIDER로 명시해야 켜진다.
     */
    public static final boolean OPENAI = "openai".equalsIgnoreCase(env("EXAMGUARD_PROVIDER", "ollama"));
    private static final String OPENAI_URL = env("OPENAI_BASE_URL", "https://api.openai.com/v1") + "/chat/completions";
    /** 실제 안경 앱이 쓸 법한 경량 클라우드 모델이 기본 (AI 안경 ≈ mini급, 초소형 카메라 + 폰 앱 ≈ 최상급 모델) */
    public static final String MODEL = env("EXAMGUARD_VISION_MODEL", OPENAI ? "gpt-5-mini" : "gemma3:4b");

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    /** 결과: 답변 글, 걸린 시간(ms) */
    public static final class Answer {
        public final String text;
        public final long millis;
        Answer(String text, long millis) { this.text = text; this.millis = millis; }
    }

    /**
     * 대화 기억: 실제 라이브 AI처럼 앞서 본 장면과 주고받은 말을 다음 질문에 함께 보낸다.
     * ponytail: 이미지는 최근 MAX_CONTEXT_IMAGES장만 남기고, 더 오래된 차례는 글만 남긴다.
     * 실제 서비스의 기억 범위는 비공개. 20장×256토큰(gemma3) + 글이 num_ctx 8192 안에 들어가게 정한 값.
     */
    static final int MAX_CONTEXT_IMAGES = 20;
    private static final class Turn {
        final String role, content;
        List<String> images; // base64 PNG. 기억 한도를 넘거나 새 문제로 넘어가면 비운다 (글은 남김)
        Turn(String role, String content, List<String> images) { this.role = role; this.content = content; this.images = images; }
    }
    private final List<Turn> history = new ArrayList<>();

    /** 대화 기억을 지운다 (새 실험 조건) */
    public synchronized void reset() { history.clear(); }

    /** 기억 속 이미지만 지우고 대화 글은 남긴다 (새 문제로 넘어갈 때 메모리 확보) */
    public synchronized void forgetImages() {
        for (Turn t : history) t.images = List.of();
    }

    /** 기억 속에 남은 이미지 수 */
    public synchronized int storedImages() {
        int n = 0;
        for (Turn t : history) n += t.images.size();
        return n;
    }

    /** 지금까지 주고받은 차례 수 (질문+답 = 2) */
    public synchronized int turns() { return history.size(); }

    /** 사진 한 장 ("Hey Meta, look and…") */
    public Answer ask(BufferedImage shot) throws IOException, InterruptedException {
        return ask(List.of(shot), PROMPT);
    }

    /**
     * 오래 걸린다 (첫 호출은 모델 적재로 수십 초). Swing 이벤트 스레드에서 부르지 말 것.
     * shots가 비어 있으면 글만 묻는다 (예: "아까 그 문제 답이 뭐였지?").
     */
    public synchronized Answer ask(List<BufferedImage> shots, String prompt) throws IOException, InterruptedException {
        List<String> images = new ArrayList<>();
        for (BufferedImage shot : shots) {
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            ImageIO.write(shot, "png", png); // 촬영본은 이미 JPEG을 거쳤으므로 다시 손실 압축하지 않는다
            images.add(Base64.getEncoder().encodeToString(png.toByteArray()));
        }
        Turn question = new Turn("user", prompt, images);
        List<Turn> convo = new ArrayList<>(history);
        convo.add(question);
        String body;
        HttpRequest.Builder req;
        if (OPENAI) {
            String key = env("OPENAI_API_KEY", "");
            if (key.isEmpty()) throw new IOException("EXAMGUARD_PROVIDER=openai인데 OPENAI_API_KEY 환경 변수가 없음");
            // temperature는 보내지 않는다: gpt-5 계열 추론 모델은 기본값 외에는 400 오류.
            // max_completion_tokens는 추론 토큰까지 포함하므로 넉넉히 (적으면 답이 빈 채로 끝남)
            body = "{\"model\":" + quote(MODEL) + ",\"max_completion_tokens\":4000,"
                    + "\"messages\":" + messagesJson(convo, true) + "}";
            req = HttpRequest.newBuilder(URI.create(OPENAI_URL)).header("Authorization", "Bearer " + key);
        } else {
            // num_ctx: gemma3는 이미지 한 장에 256토큰. 기본 컨텍스트(4096)로는 기억이 금방 넘친다
            body = "{\"model\":" + quote(MODEL) + ",\"stream\":false,"
                    + "\"options\":{\"temperature\":0,\"num_predict\":300,\"num_ctx\":8192},"
                    + "\"messages\":" + messagesJson(convo, false) + "}";
            req = HttpRequest.newBuilder(URI.create(HOST + "/api/chat"));
        }
        HttpRequest request = req.timeout(Duration.ofMinutes(5))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        long t0 = System.nanoTime();
        HttpResponse<String> res = null;
        // 클라우드 요청 한도(429)·일시 오류(5xx)는 잠깐 쉬고 두 번까지 다시 시도 (일괄 평가가 중간에 끊기지 않게)
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                res = http.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (java.net.ConnectException e) {
                throw new IOException((OPENAI ? "OpenAI" : "Ollama") + "에 연결할 수 없음 ("
                        + (OPENAI ? OPENAI_URL : HOST) + ")" + (OPENAI ? "" : ". Ollama를 실행했는지 확인하세요."), e);
            }
            if (res.statusCode() != 429 && res.statusCode() < 500) break;
            if (attempt < 2) Thread.sleep(10_000L * (attempt + 1));
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;
        // 오류 모양: Ollama {"error":"..."}, OpenAI {"error":{"message":"..."}}
        String error = jsonString(res.body(), "error");
        if (res.statusCode() != 200 || error != null) {
            String msg = error != null ? error : jsonString(res.body(), "message");
            throw new IOException("모델 오류 (HTTP " + res.statusCode() + "): " + (msg != null ? msg : res.body())
                    + (res.statusCode() == 404 && !OPENAI ? "\n→ ollama pull " + MODEL : ""));
        }
        String content = jsonString(res.body(), "content");
        if (content == null) throw new IOException("응답 형식을 알 수 없음: " + res.body());
        // 성공했을 때만 기억에 넣는다 (실패한 질문이 다음 대화를 오염시키지 않게)
        history.add(question);
        history.add(new Turn("assistant", content.trim(), List.of()));
        pruneImages(history);
        return new Answer(content.trim(), ms);
    }

    /** 어차피 다시 보내지 않을 옛 이미지(한도 초과분)는 들고 있지 말고 지운다. 계속 쌓이지 않게 */
    static void pruneImages(List<Turn> convo) {
        int budget = MAX_CONTEXT_IMAGES;
        for (int i = convo.size() - 1; i >= 0; i--) {
            Turn t = convo.get(i);
            if (t.images.size() <= budget) budget -= t.images.size();
            else { t.images = List.of(); budget = 0; }
        }
    }

    /**
     * 대화를 messages 배열로. 새 차례부터 거꾸로 세어 이미지가 MAX_CONTEXT_IMAGES를 넘는 옛 차례는 글만 보낸다.
     * Ollama: {"content":"글","images":["b64"]}. OpenAI: {"content":[{"type":"text"},{"type":"image_url"}]}
     */
    static String messagesJson(List<Turn> convo, boolean openai) {
        boolean[] keepImages = new boolean[convo.size()];
        int budget = MAX_CONTEXT_IMAGES;
        for (int i = convo.size() - 1; i >= 0; i--) {
            int n = convo.get(i).images.size();
            if (n == 0) continue;
            if (n > budget) break; // 이보다 오래된 장면은 모두 글만
            keepImages[i] = true;
            budget -= n;
        }
        StringBuilder b = new StringBuilder("[{\"role\":\"system\",\"content\":").append(quote(SYSTEM)).append('}');
        for (int i = 0; i < convo.size(); i++) {
            Turn t = convo.get(i);
            b.append(',');
            b.append("{\"role\":").append(quote(t.role)).append(",\"content\":");
            if (openai && keepImages[i]) {
                b.append("[{\"type\":\"text\",\"text\":").append(quote(t.content)).append('}');
                for (String img : t.images) {
                    b.append(",{\"type\":\"image_url\",\"image_url\":{\"url\":\"data:image/png;base64,").append(img).append("\"}}");
                }
                b.append("]}");
                continue;
            }
            b.append(quote(t.content));
            if (keepImages[i] && !openai) {
                b.append(",\"images\":[");
                for (int k = 0; k < t.images.size(); k++) b.append(k > 0 ? "," : "").append('"').append(t.images.get(k)).append('"');
                b.append(']');
            }
            b.append('}');
        }
        return b.append(']').toString();
    }

    static String quote(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                default:
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c)); else b.append(c);
            }
        }
        return b.append('"').toString();
    }

    /**
     * JSON에서 처음 나오는 "key":"..." 문자열 값을 꺼낸다 (없으면 null).
     * ponytail: 범용 파서가 아님. Ollama /api/chat 응답처럼 키 이름이 겹치지 않는 평평한 응답 전용.
     */
    static String jsonString(String json, String key) {
        int i = json.indexOf("\"" + key + "\"");
        if (i < 0) return null;
        i = json.indexOf(':', i) + 1;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
        if (i >= json.length() || json.charAt(i) != '"') return null;
        StringBuilder b = new StringBuilder();
        for (i++; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"') return b.toString();
            if (c != '\\') { b.append(c); continue; }
            char e = json.charAt(++i);
            switch (e) {
                case 'n': b.append('\n'); break;
                case 't': b.append('\t'); break;
                case 'r': b.append('\r'); break;
                case 'b': b.append('\b'); break;
                case 'f': b.append('\f'); break;
                case 'u': b.append((char) Integer.parseInt(json.substring(i + 1, i + 5), 16)); i += 4; break;
                default: b.append(e); // \" \\ \/
            }
        }
        return null; // 닫히지 않은 문자열
    }

    private static String env(String name, String def) {
        String v = System.getenv(name);
        if (v == null || v.isBlank()) v = DOTENV.get(name);
        return v == null || v.isBlank() ? def : v;
    }

    /** 파일이 없으면 빈 맵. "#" 주석·빈 줄·"export " 접두어·값을 감싼 따옴표를 허용한다 */
    static java.util.Map<String, String> loadDotEnv(java.nio.file.Path file) {
        java.util.Map<String, String> map = new java.util.HashMap<>();
        if (!java.nio.file.Files.isRegularFile(file)) return map;
        try {
            for (String line : java.nio.file.Files.readAllLines(file, java.nio.charset.StandardCharsets.UTF_8)) {
                line = line.strip();
                if (line.startsWith("﻿")) line = line.substring(1); // 메모장이 붙이는 BOM
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (line.startsWith("export ")) line = line.substring(7).strip();
                int eq = line.indexOf('=');
                if (eq <= 0) continue;
                String value = line.substring(eq + 1).strip();
                if (value.length() >= 2 && (value.charAt(0) == '"' || value.charAt(0) == '\'')
                        && value.charAt(value.length() - 1) == value.charAt(0)) {
                    value = value.substring(1, value.length() - 1);
                }
                map.put(line.substring(0, eq).strip(), value);
            }
        } catch (IOException e) {
            System.err.println(".env.local을 읽지 못함: " + e.getMessage()); // 설정 없이 기본값으로 계속
        }
        return map;
    }

    /** 자체 점검: java -cp out examguard.ai.GlassesAssistant */
    public static void main(String[] args) {
        String json = "{\"model\":\"x\",\"message\":{\"role\":\"assistant\",\"content\":\"정답: ③\\n이유는 \\\"A\\\" \\u0041\\\\ 끝\"},\"done\":true}";
        check("정답: ③\n이유는 \"A\" A\\ 끝".equals(jsonString(json, "content")), "content 해독");
        check(jsonString(json, "error") == null, "없는 키");
        check("model not found".equals(jsonString("{\"error\": \"model not found\"}", "error")), "error 키");
        String s = "줄\n\"따옴표\"\\\t";
        check(s.equals(jsonString("{\"k\":" + quote(s) + "}", "k")), "quote 왕복");
        // 기억 한도: 옛 차례(이미지 15장)는 한도(20)를 넘으므로 글만, 최근 차례(10장)와 글 질문은 그대로
        List<Turn> convo = List.of(
                new Turn("user", "옛", java.util.Collections.nCopies(15, "OLD")),
                new Turn("assistant", "답1", List.of()),
                new Turn("user", "새", java.util.Collections.nCopies(10, "NEW")),
                new Turn("assistant", "답2", List.of()),
                new Turn("user", "아까 그 문제 답?", List.of()));
        try {
            java.nio.file.Path f = java.nio.file.Files.createTempFile("dotenv", ".txt");
            java.nio.file.Files.writeString(f, "﻿# 주석\n\nexport A=1\nB = \"x y\"\nC='z'\nbad line\nD=a=b\n");
            java.util.Map<String, String> d = loadDotEnv(f);
            java.nio.file.Files.delete(f);
            check("1".equals(d.get("A")) && "x y".equals(d.get("B")) && "z".equals(d.get("C")) && "a=b".equals(d.get("D"))
                    && d.size() == 4, ".env.local 해석");
            check(loadDotEnv(java.nio.file.Paths.get("없는파일.env")).isEmpty(), "파일 없으면 빈 설정");
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        String m = messagesJson(convo, false);
        String o = messagesJson(convo, true);
        check(!o.contains("OLD") && o.split("data:image/png;base64,NEW", -1).length - 1 == 10, "OpenAI 형식: 최근 이미지만");
        check(o.contains("{\"role\":\"user\",\"content\":[{\"type\":\"text\",\"text\":\"새\"}"), "OpenAI 형식: 글+이미지 배열");
        check(o.contains("{\"role\":\"user\",\"content\":\"옛\"}"), "OpenAI 형식: 이미지 없는 차례는 글만");
        check(!m.contains("OLD") && m.contains("\"옛\""), "옛 장면은 글만 남김");
        check(m.split("NEW", -1).length - 1 == 10, "최근 장면 이미지 유지");
        List<Turn> stored = new ArrayList<>(convo);
        pruneImages(stored);
        check(stored.get(0).images.isEmpty() && stored.get(2).images.size() == 10, "한도 넘은 옛 이미지는 실제로 지움");
        check(m.startsWith("[{\"role\":\"system\""), "기기 설정(한국어)이 맨 앞");
        check(m.endsWith("{\"role\":\"user\",\"content\":\"아까 그 문제 답?\"}]"), "글만 묻는 질문");
        System.out.println("GlassesAssistant 자체 점검 통과");
    }

    private static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }
}
