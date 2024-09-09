package com.intellij.openapi.application;

public class ApplicationManager {
    protected static Application singleton = new Application();

    public static Application getApplication() {
        return singleton;
    }
}
