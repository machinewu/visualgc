package com.intellij.ui.components;

import javax.swing.*;
import java.awt.*;

public class JBScrollPane extends JScrollPane {
    public JBScrollPane(Component view) {
        super(view);
        init();
    }

    private void init() {
        init(true);
    }

    private void init(boolean setupCorners) {
        setLayout(createLayout());
    }

    protected Layout createLayout() {
        return new Layout();
    }

    public static class Layout extends ScrollPaneLayout {

    }
}
