package examguard.display;

import examguard.model.ExamPaper;
import examguard.model.QuestionVariant;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.util.ArrayList;
import java.util.List;

/** 문항을 이미지로 그리고, 흐린 사본을 만든다. */
public final class QuestionRenderer {
    public static final int MIN_HEIGHT = 320;
    private static final int MARGIN = 32;

    private QuestionRenderer() {}

    public static BufferedImage render(QuestionVariant v, int number, int width, int fontSize, boolean bold) {
        Font body = new Font(Font.SANS_SERIF, bold ? Font.BOLD : Font.PLAIN, fontSize);
        Font head = new Font(Font.SANS_SERIF, Font.BOLD, Math.round(fontSize * 0.8f));

        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        Graphics2D pg = probe.createGraphics();
        FontMetrics fm = pg.getFontMetrics(body);
        FontMetrics hm = pg.getFontMetrics(head);
        pg.dispose();

        int textWidth = width - MARGIN * 2;
        int lineHeight = fm.getHeight() + fontSize / 3;
        List<String> lines = wrap(v.getText(), fm, textWidth);
        List<String> choiceLines = new ArrayList<>();
        for (int j = 0; j < v.getChoices().size(); j++) {
            choiceLines.addAll(wrap(ExamPaper.CIRCLED[j] + "  " + v.getChoices().get(j), fm, textWidth - 24));
        }

        int height = MARGIN + hm.getHeight() + 14 + lines.size() * lineHeight
                + (choiceLines.isEmpty() ? 0 : 18 + choiceLines.size() * lineHeight) + MARGIN;
        height = Math.max(height, MIN_HEIGHT);

        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);

        int y = MARGIN;
        g.setFont(head);
        g.setColor(new Color(90, 90, 90));
        g.drawString("문제 " + number + "   (" + v.getSource().getPoints() + "점)", MARGIN, y + hm.getAscent());
        y += hm.getHeight() + 14;

        g.setFont(body);
        g.setColor(new Color(20, 20, 20));
        for (String line : lines) {
            g.drawString(line, MARGIN, y + fm.getAscent());
            y += lineHeight;
        }
        if (!choiceLines.isEmpty()) {
            y += 18;
            for (String line : choiceLines) {
                g.drawString(line, MARGIN + 24, y + fm.getAscent());
                y += lineHeight;
            }
        }
        g.dispose();
        return img;
    }

    /** 단어 단위 줄바꿈 (한 단어가 너무 길면 글자 단위로 자른다) */
    static List<String> wrap(String text, FontMetrics fm, int maxWidth) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (fm.stringWidth(candidate) <= maxWidth) {
                line.setLength(0);
                line.append(candidate);
                continue;
            }
            if (line.length() > 0) {
                out.add(line.toString());
                line.setLength(0);
            }
            StringBuilder piece = new StringBuilder();
            for (char ch : word.toCharArray()) {
                if (piece.length() > 0 && fm.stringWidth(piece.toString() + ch) > maxWidth) {
                    out.add(piece.toString());
                    piece.setLength(0);
                }
                piece.append(ch);
            }
            line.append(piece);
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }

    /** 가로·세로 상자 흐림을 두 번씩 적용 (가우시안 흐림에 가까움) */
    public static BufferedImage blur(BufferedImage src, int radius) {
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage padded = new BufferedImage(w + 2 * radius, h + 2 * radius, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = padded.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, padded.getWidth(), padded.getHeight());
        g.drawImage(src, radius, radius, null);
        g.dispose();

        int size = 2 * radius + 1;
        float[] k = new float[size];
        for (int i = 0; i < size; i++) k[i] = 1f / size;
        ConvolveOp horizontal = new ConvolveOp(new Kernel(size, 1, k), ConvolveOp.EDGE_NO_OP, null);
        ConvolveOp vertical = new ConvolveOp(new Kernel(1, size, k), ConvolveOp.EDGE_NO_OP, null);

        BufferedImage a = vertical.filter(horizontal.filter(padded, null), null);
        BufferedImage b = vertical.filter(horizontal.filter(a, null), null);

        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D og = out.createGraphics();
        og.drawImage(b.getSubimage(radius, radius, w, h), 0, 0, null);
        og.dispose();
        return out;
    }
}
