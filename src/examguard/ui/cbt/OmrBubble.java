package examguard.ui.cbt;

import javax.swing.JComponent;
import java.awt.BasicStroke;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/** 답안 표기란의 동그라미 하나. 고르면 검게 칠해진다 (OMR 카드 모양). */
class OmrBubble extends JComponent {
    private static final int SIZE = 28;
    private final String label;
    private boolean selected;
    private boolean hover;

    OmrBubble(String label, Runnable onClick) {
        this.label = label;
        setPreferredSize(new Dimension(SIZE + 4, SIZE + 4));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setToolTipText(label + "번");
        addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { if (isEnabled()) onClick.run(); }
            @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
            @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
        });
    }

    void setSelected(boolean selected) {
        this.selected = selected;
        repaint();
    }

    boolean isSelected() {
        return selected;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int x = (getWidth() - SIZE) / 2, y = (getHeight() - SIZE) / 2;
        if (selected) {
            g2.setColor(CbtStyle.INK);
            g2.fillOval(x, y, SIZE, SIZE);
        } else if (hover) {
            g2.setColor(CbtStyle.CURRENT_ROW);
            g2.fillOval(x, y, SIZE, SIZE);
        }
        g2.setColor(selected ? CbtStyle.INK : CbtStyle.MUTED);
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawOval(x, y, SIZE, SIZE);

        g2.setFont(CbtStyle.font(java.awt.Font.BOLD, 14));
        FontMetrics fm = g2.getFontMetrics();
        g2.setColor(selected ? java.awt.Color.WHITE : CbtStyle.INK);
        g2.drawString(label, x + (SIZE - fm.stringWidth(label)) / 2, y + (SIZE + fm.getAscent() - fm.getDescent()) / 2);
        g2.dispose();
    }
}
