package examguard.fingerprint;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** [[A|B]] 선택 지점에 학생 식별 코드의 비트에 따라 A 또는 B 표현을 넣는다. */
public class TextVariantLayer implements FingerprintLayer {
    public static final Pattern PATTERN = Pattern.compile("\\[\\[([^|\\]]+)\\|([^\\]]+)\\]\\]");

    @Override
    public String getName() {
        return "문장 변형";
    }

    @Override
    public String apply(String text, VariantContext ctx) {
        Matcher m = PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String a = m.group(1);
            String b = m.group(2);
            int bit = ctx.nextBit();
            ctx.addChoicePoint(a, b, bit);
            m.appendReplacement(sb, Matcher.quoteReplacement(bit == 0 ? a : b));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
