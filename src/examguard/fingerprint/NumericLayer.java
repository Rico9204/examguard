package examguard.fingerprint;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** {{x:12-48}} 자리에 학생마다 다른 숫자를 넣는다. */
public class NumericLayer implements FingerprintLayer {
    public static final Pattern PATTERN = Pattern.compile("\\{\\{(\\w+):(\\d+)-(\\d+)\\}\\}");

    /** 이 값 이상인 숫자만 추적 표시로 쓴다. 한 자리 수는 다른 숫자와 우연히 겹치기 쉬워서 제외한다. */
    private static final int TRACEABLE_MIN = 10;

    @Override
    public String getName() {
        return "숫자 변형";
    }

    @Override
    public String apply(String text, VariantContext ctx) {
        Matcher m = PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String name = m.group(1);
            int lo = Integer.parseInt(m.group(2));
            int hi = Integer.parseInt(m.group(3));
            int value = lo + ctx.getRandom().nextInt(hi - lo + 1);
            ctx.putVariable(name, value, lo >= TRACEABLE_MIN);
            m.appendReplacement(sb, String.valueOf(value));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
