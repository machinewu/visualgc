# Java Perf Tools and IDEA Plugin DevKit Helper

This repo contains a graphical tool for monitoring the HotSpot Garbage Collector, Compiler, and class loader. It can monitor both local and remote JVMs.

## Visual Garbage Collection Monitoring Tool: visualgc_idea
  You can build this project into a standalone runnable VisualGC JAR file OR an IDEA plugin with VisualGC embed.
  Currently, some fake class files have been added to enable building a standalone runnable JAR.
  If you want to build it as an IDEA plugin, simply delete all the code from the [commit 24ad36f](../../commit/24ad36fe74fa5a0ffe3b6f25d6589287ea9ff347) and then introduce the IDEA Plugin SDK to build it.

## Runtime environment
  The runtime environment requires JDK 17 or higher.
  You can use the following commands to run it:
```
"%JAVA_HOME%\bin\java" --add-exports jdk.internal.jvmstat/sun.jvmstat.monitor=ALL-UNNAMED --add-exports jdk.internal.jvmstat/sun.jvmstat.monitor.event=ALL-UNNAMED --add-exports jdk.jstatd/sun.jvmstat.perfdata.monitor.protocol.rmi=ALL-UNNAMED -cp "%JAVA_HOME%\lib\tools.jar";.\visualgc_idea.jar com.sun.jvmstat.tools.visualgc.VisualGCPane
```

## Tips
  If you find that a certain Java process does not appear in the interface for selection, it may be due to insufficient permissions for VisualGC, which can prevent it from recognizing information about higher-privileged Java processes.
