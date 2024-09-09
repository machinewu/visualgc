package com.intellij.openapi.actionSystem;

public final class Presentation {
    private String text;
    public Presentation() {
    }

    public Presentation(String text) {
        this.text = text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getText() {
        return this.text;
    }
}
