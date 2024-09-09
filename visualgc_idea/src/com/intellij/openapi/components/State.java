package com.intellij.openapi.components;

import java.util.function.Supplier;

public @interface State {
    String name();

    Storage [] storages() default {};

    boolean reloadable() default true;

    boolean defaultStateAsResource() default false;

    String additionalExportDirectory() default "";

    @Deprecated(forRemoval = true)
    String additionalExportFile() default "";

    Class<? extends NameGetter> presentableName() default NameGetter.class;

    boolean externalStorageOnly() default false;

    boolean reportStatistic() default true;

    boolean allowLoadInTests() default false;

    boolean useLoadedStateAsExisting() default true;

    boolean getStateRequiresEdt() default false;

    boolean perClient() default false;

    abstract class NameGetter implements Supplier<String> {
        @Override
        public abstract String get();
    }

    boolean exportable() default false;
}
