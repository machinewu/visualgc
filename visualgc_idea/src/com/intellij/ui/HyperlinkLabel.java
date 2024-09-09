package com.intellij.ui;

import com.sun.jvmstat.tools.visualgc.resource.Res;

import javax.swing.*;
import javax.swing.event.HyperlinkListener;

public class HyperlinkLabel extends JLabel {
    public HyperlinkLabel(String text) {
        if (Res.getString("link.label.error.fetch.jvm.list.please.add.following.lines.to.opened.editor").equals(text)) {
            text += "\n" +
                    "--add-exports=jdk.internal.jvmstat/sun.jvmstat.monitor=ALL-UNNAMED\n" +
                    "--add-exports=jdk.internal.jvmstat/sun.jvmstat.monitor.event=ALL-UNNAMED\n" +
                    "--add-exports=jdk.internal.jvmstat/sun.jvmstat.perfdata.monitor=ALL-UNNAMED";
        }
        setText(text);
    }

    public void addHyperlinkListener(HyperlinkListener listener) {

    }
}
