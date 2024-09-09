package com.intellij.openapi.actionSystem;

public class AnActionEvent {
    private static final AnActionEvent dummyAnActionEvent = new AnActionEvent();

    public AnActionEvent() {}

    public static AnActionEvent createFromDataContext(String place,
                                               Presentation presentation,
                                               DataContext dataContext) {
        return dummyAnActionEvent;
    }
}
