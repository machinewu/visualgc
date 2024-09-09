package com.intellij.openapi.components;

public @interface Storage {
    @Deprecated(forRemoval = true)
    String file() default "";

    String value() default "";

    boolean deprecated() default false;

    boolean exclusive() default false;

    boolean exportable() default false;

    boolean usePathMacroManager() default true;
}
