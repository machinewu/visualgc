package com.intellij.ide;

import javax.swing.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class HelpTooltip {
    private String title;
    private String description;
    private String link;

    public HelpTooltip setTitle(String title) {
        this.title = title;
        return this;
    }

    public HelpTooltip setDescription(String description) {
        this.description = description;
        return this;
    }

    public HelpTooltip setLink(String linkText, Runnable linkAction) {
        this.link = linkText;
        //ignore linkAction
        return this;
    }

    public void installOn(JComponent component) {
        component.setToolTipText("<html><b>" + this.title + "</b><br/>" + this.description + "</html>");
    }
}
