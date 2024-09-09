package com.intellij.openapi.actionSystem;

import java.util.HashMap;

public class ActionManager {
    private static ActionManager singleton = null;
    private static HashMap<String, AnAction> actionList;
    private static final AnAction dummyAnAction = new AnAction();

    private ActionManager() {
        actionList = new HashMap<>();
    }

    public static synchronized ActionManager getInstance() {
        if (singleton == null) {
            singleton = new ActionManager();
        }
        return singleton;
    }

    public synchronized AnAction getAction(String actionId) {
        return actionList.putIfAbsent(actionId, dummyAnAction);
    }
}
