package com.intellij.openapi.ui;

public interface InputValidator {
    boolean checkInput(String inputString);
    boolean canClose(String inputString);
}
