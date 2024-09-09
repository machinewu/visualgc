package com.intellij.openapi.ui;

import javax.swing.*;

public class ComboBox<E> extends JComboBox<E> {
    public ComboBox(E [] items) {
        super(items);
    }
}
