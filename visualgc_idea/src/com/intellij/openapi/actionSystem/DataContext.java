package com.intellij.openapi.actionSystem;

public interface DataContext {
    default <T> T getData(DataKey<T> key) {
        return (T)getData(key.getName());
    }

    Object getData(String paramString);
}
