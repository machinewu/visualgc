package com.intellij.openapi.ui;

public class StatusText {
    private String text;

    public StatusText() {}

    public StatusText(String text) {
        this.text = text;
    }

    public StatusText setText(String text) {
        this.text = text;
        return this;
    }

    public String getText() {
        return this.text;
    }

    public StatusText appendLine(String text) {
        this.text = this.text + "\n" + text;
        return this;
    }
}
