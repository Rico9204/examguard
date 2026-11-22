package examguard.ui.cbt;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;

/** CBT 화면 공통 색과 글꼴 */
final class CbtStyle {
    static final Color NAVY = new Color(26, 52, 92);
    static final Color NAVY_LIGHT = new Color(46, 82, 132);
    static final Color BACKGROUND = new Color(236, 240, 245);
    static final Color PANEL = Color.WHITE;
    static final Color LINE = new Color(196, 204, 214);
    static final Color CURRENT_ROW = new Color(220, 232, 250);
    static final Color INK = new Color(30, 30, 30);
    static final Color MUTED = new Color(100, 110, 120);
    static final Color WARNING = new Color(200, 40, 40);
    static final Color SUBMIT = new Color(190, 50, 50);

    private CbtStyle() {}

    static Font font(int style, float size) {
        return new Font(Font.SANS_SERIF, style, Math.round(size));
    }

    static JLabel label(String text, int style, float size, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(font(style, size));
        l.setForeground(color);
        return l;
    }

    /** 시스템 테마와 상관없이 같은 모양으로 보이는 평평한 버튼 */
    static JButton button(String text, Color background, Color foreground) {
        JButton b = new JButton(text);
        b.setFont(font(Font.BOLD, 14));
        b.setBackground(background);
        b.setForeground(foreground);
        b.setOpaque(true);
        b.setContentAreaFilled(true);
        b.setBorderPainted(true);
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(background.darker()),
                BorderFactory.createEmptyBorder(8, 16, 8, 16)));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }
}
