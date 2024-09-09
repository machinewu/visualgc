package com.intellij.openapi.project;

public class DefaultProjectFactory {
    private static DefaultProjectFactory singleton = null;
    private static final Project dummyProject = new Project();

    private DefaultProjectFactory() {}

    public static synchronized DefaultProjectFactory getInstance() {
        if (singleton == null) {
            singleton = new DefaultProjectFactory();
        }
        return singleton;
    }

    public Project getDefaultProject() {
        return dummyProject;
    }
}
