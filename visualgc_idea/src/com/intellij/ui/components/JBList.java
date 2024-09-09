package com.intellij.ui.components;

import com.intellij.openapi.ui.StatusText;

import javax.swing.*;
import java.awt.*;

public class JBList<E> extends JList<E> {
    private final StatusText statusText = new StatusText("");

    public JBList(ListModel<E> dataModel) {
        super(dataModel);
    }

    public StatusText getEmptyText() {
        return statusText;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        // 如果模型为空，则绘制提示消息
        if (this.getModel().getSize() == 0) {
            FontMetrics fm = g.getFontMetrics();
            int x = (getWidth() - fm.stringWidth(statusText.getText())) / 2;
            int y = (getHeight() + fm.getAscent()) / 2;
            g.drawString(statusText.getText(), x, y);
        }
    }
}
