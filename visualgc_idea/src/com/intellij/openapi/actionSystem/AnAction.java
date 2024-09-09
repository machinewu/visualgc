package com.intellij.openapi.actionSystem;

public class AnAction {
    private static final Presentation dummyPresentation = new Presentation("");

    public AnAction() {}

    public void actionPerformed(AnActionEvent e) {
        // pass
    }

    public final Presentation getTemplatePresentation() {
        return dummyPresentation;
    }

    public boolean isDumbAware() {
        return true;
    }
}
