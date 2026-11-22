package examguard.ui.cbt;

import examguard.display.AntiOcrWatermarkLayer;
import examguard.display.AntiOcrWatermarkLayer.GridColor;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

/**
 * 실험 도구: 안티-OCR 셰이딩 격자의 간격·대비·색을 바꿔 가며
 * 실제 카메라(휴대폰)로 모아레가 생기는 조건을 찾는다. 바꾸는 즉시 문제 화면에 반영된다.
 */
class GridTuningDialog extends JDialog {
    private final AntiOcrWatermarkLayer layer;
    private final JComponent target;
    private final JSpinner period = new JSpinner(new SpinnerNumberModel(AntiOcrWatermarkLayer.DEFAULT_PERIOD, 2, 16, 1));
    private final JSlider alpha = new JSlider(0, 255, AntiOcrWatermarkLayer.DEFAULT_ALPHA);
    private final JLabel alphaValue = CbtStyle.label(" ", Font.PLAIN, 13, CbtStyle.INK);
    private final JComboBox<GridColor> color = new JComboBox<>(GridColor.values());

    GridTuningDialog(JFrame owner, AntiOcrWatermarkLayer layer, JComponent target) {
        super(owner, "격자 조절 (실험)", false);
        this.layer = layer;
        this.target = target;
        period.setValue(layer.getGridPeriod());
        alpha.setValue(layer.getGridAlpha());
        color.setSelectedItem(layer.getGridColor());

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(10, 12, 6, 12));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;
        row(form, c, 0, "간격 (모니터 실제 px)", period);
        JPanel alphaRow = new JPanel(new BorderLayout(6, 0));
        alphaRow.add(alpha, BorderLayout.CENTER);
        alphaRow.add(alphaValue, BorderLayout.EAST);
        row(form, c, 1, "대비 (선 진하기)", alphaRow);
        row(form, c, 2, "색", color);

        // 이 화면의 배율: 125%면 화면 1px(논리)이 모니터 1.25px. 격자는 배율을 풀고 모니터 실제 px로 그린다.
        double scale = owner.getGraphicsConfiguration().getDefaultTransform().getScaleX();
        JLabel note = CbtStyle.label(String.format("<html>화면 배율 %.0f%% · 격자는 모니터 실제 픽셀 단위로 그림<br>"
                + "휴대폰으로 화면을 찍으며 거리·간격·대비를 바꿔 모아레가 생기는 조건을 기록하세요.</html>",
                scale * 100), Font.PLAIN, 12, CbtStyle.MUTED);
        c.gridx = 0;
        c.gridy = 3;
        c.gridwidth = 2;
        form.add(note, c);

        JButton reset = new JButton("기본값");
        reset.addActionListener(e -> {
            period.setValue(AntiOcrWatermarkLayer.DEFAULT_PERIOD);
            alpha.setValue(AntiOcrWatermarkLayer.DEFAULT_ALPHA);
            color.setSelectedItem(GridColor.BLACK);
        });
        JPanel buttons = new JPanel();
        buttons.add(reset);

        add(form, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);

        period.addChangeListener(e -> apply());
        alpha.addChangeListener(e -> apply());
        color.addActionListener(e -> apply());
        apply();
        pack();
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(HIDE_ON_CLOSE);
    }

    private static void row(JPanel form, GridBagConstraints c, int y, String label, JComponent field) {
        c.gridx = 0;
        c.gridy = y;
        c.gridwidth = 1;
        c.fill = GridBagConstraints.NONE;
        form.add(CbtStyle.label(label, Font.BOLD, 13, CbtStyle.INK), c);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        form.add(field, c);
    }

    private void apply() {
        int a = alpha.getValue();
        alphaValue.setText(String.format("%3d (약 %d%%)", a, Math.round(a / 2.55)));
        layer.setGrid((Integer) period.getValue(), a, (GridColor) color.getSelectedItem());
        target.repaint();
    }
}
