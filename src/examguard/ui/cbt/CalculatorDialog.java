package examguard.ui.cbt;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.math.BigDecimal;
import java.math.MathContext;

/** CBT 화면의 [계산기]: 사칙연산만 되는 간단한 계산기 (창을 띄운 채 문제를 볼 수 있음) */
class CalculatorDialog extends JDialog {
    private final JLabel display = new JLabel("0", JLabel.RIGHT);
    private BigDecimal stored;
    private String pendingOp;
    private boolean startNew = true;

    CalculatorDialog(JFrame owner) {
        super(owner, "계산기", false);
        display.setFont(CbtStyle.font(Font.BOLD, 26));
        display.setOpaque(true);
        display.setBackground(java.awt.Color.WHITE);
        display.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(CbtStyle.LINE), BorderFactory.createEmptyBorder(8, 10, 8, 10)));

        String[] keys = {"C", "←", "%", "÷", "7", "8", "9", "×", "4", "5", "6", "−", "1", "2", "3", "+", "±", "0", ".", "="};
        JPanel grid = new JPanel(new GridLayout(5, 4, 4, 4));
        for (String k : keys) {
            JButton b = new JButton(k);
            b.setFont(CbtStyle.font(Font.BOLD, 18));
            b.setFocusPainted(false);
            b.addActionListener(e -> press(k));
            grid.add(b);
        }
        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        root.add(display, BorderLayout.NORTH);
        root.add(grid, BorderLayout.CENTER);
        setContentPane(root);
        setSize(280, 360);
        setResizable(false);
        // 문제를 가리지 않도록 창 왼쪽 아래에 띄운다
        setLocation(owner.getX() + 20, owner.getY() + Math.max(0, owner.getHeight() - getHeight() - 80));
    }

    private void press(String k) {
        try {
            switch (k) {
                case "C": stored = null; pendingOp = null; show(BigDecimal.ZERO); startNew = true; break;
                case "←":
                    String t = display.getText();
                    display.setText(t.length() <= 1 || startNew ? "0" : t.substring(0, t.length() - 1));
                    break;
                case "±": show(current().negate()); break;
                case "%": show(current().divide(BigDecimal.valueOf(100), MathContext.DECIMAL64)); break;
                case "+": case "−": case "×": case "÷": operator(k); break;
                case "=": operator(null); break;
                case ".":
                    if (startNew) { display.setText("0."); startNew = false; }
                    else if (!display.getText().contains(".")) display.setText(display.getText() + ".");
                    break;
                default: // 숫자
                    display.setText(startNew || display.getText().equals("0") ? k : display.getText() + k);
                    startNew = false;
            }
        } catch (ArithmeticException ex) {
            display.setText("오류");
            stored = null;
            pendingOp = null;
            startNew = true;
        }
    }

    private void operator(String op) {
        BigDecimal value = current();
        if (stored != null && pendingOp != null && !startNew) value = apply(stored, value, pendingOp);
        show(value);
        stored = value;
        pendingOp = op;
        startNew = true;
    }

    private static BigDecimal apply(BigDecimal a, BigDecimal b, String op) {
        switch (op) {
            case "+": return a.add(b);
            case "−": return a.subtract(b);
            case "×": return a.multiply(b);
            default: return a.divide(b, MathContext.DECIMAL64);
        }
    }

    private BigDecimal current() {
        try {
            return new BigDecimal(display.getText());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private void show(BigDecimal v) {
        BigDecimal s = v.stripTrailingZeros();
        display.setText(s.scale() < 0 ? s.setScale(0).toPlainString() : s.toPlainString());
    }
}
