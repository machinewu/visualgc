package com.intellij.openapi.ui;

import com.intellij.openapi.project.Project;

import javax.swing.*;

public class Messages {
    public final static int YES = 0;
    public final static int NO = 1;

    public Messages() {

    }

    public static String showInputDialog(Project project,
                                       String message,
                                       String title,
                                       int iconType,
                                       String initialValue,
                                       InputValidator validator) {
        if (initialValue != null && initialValue.length() > 0) {
            message += "\n默认值: " + initialValue;
        }
        return JOptionPane.showInputDialog(null, message, title, iconType);
    }

    public static int showYesNoDialog(String message,
                                      String title,
                                      String yesText,
                                      String noText,
                                      int iconType) {
        Object[] options = {yesText, noText};
        return JOptionPane.showOptionDialog(null, message, title, JOptionPane.YES_NO_OPTION, iconType, null, options, options[0]);
    }

    public static int getErrorIcon() {
        return JOptionPane.ERROR_MESSAGE;
    }

    public static int getQuestionIcon() {
        return JOptionPane.QUESTION_MESSAGE;
    }

    public static void showTextAreaDialog(final JTextField textField,
                                          final String title,
                                          final String dimensionServiceKey) {
        //pass
    }
}
