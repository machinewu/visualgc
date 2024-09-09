package com.intellij.openapi.actionSystem;

public class DataKey<T> {
    private final String name;

    private DataKey(String name) {
        this.name = name;
    }

    public String getName() {
        return this.name;
    }

    public static <T> DataKey<T> create(String name) {
        return new DataKey<>(name);
    }

    public final boolean is(String dataId) {
        return this.name.equals(dataId);
    }

    public final T getData(DataContext dataContext) {
        return (T)dataContext.getData(this.name);
    }
}
