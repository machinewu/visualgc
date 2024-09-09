package com.intellij.ui;

import java.awt.*;
import java.util.function.Supplier;

public class JBColor extends Color {
    private final String name;
    private final Color darkColor;
    private final Color defaultColor;
    private final Supplier<? extends Color> func;

    public JBColor(int rgb, int darkRGB) {
        this(new Color(rgb, (rgb & 0xff000000) != 0), new Color(darkRGB, (rgb & 0xff000000) != 0));
    }

    public JBColor(Color regular, Color dark) {
        super(regular.getRGB(), regular.getAlpha() != 255);
        name = null;
        defaultColor = null;
        darkColor = dark;
        func = null;
    }


}
