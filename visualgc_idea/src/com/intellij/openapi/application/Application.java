package com.intellij.openapi.application;

import com.github.beansoft.jvm.ApplicationSettingsService;

import java.util.concurrent.ConcurrentHashMap;

public class Application {
    private static final ConcurrentHashMap<Class<?>, Object> instances = new ConcurrentHashMap<>();

    public static <T> T getService(Class<?> clazz) {
        return (T) instances.computeIfAbsent(clazz, Application::createInstance);
    }

    private static Object createInstance(Class<?> clazz) {
        try {
            Object object = clazz.getDeclaredConstructor().newInstance();
            if (object instanceof ApplicationSettingsService) {
                ((ApplicationSettingsService)object).getState().setRemoteJvmURL("");
            }
            return object;
        } catch (Exception e) {
            throw new RuntimeException("Unable to create instance of: " + clazz.getName(), e);
        }
    }
}
