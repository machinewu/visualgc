package com.intellij.openapi.components;

public interface PersistentStateComponent<T> {
    T getState();

    void loadState(T state);

    default void noStateLoaded() { }

    default void initializeComponent() { }
}
