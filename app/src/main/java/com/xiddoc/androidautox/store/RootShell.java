package com.xiddoc.androidautox.store;

import java.util.List;

/** A root shell (the app's libsu shell on a device). */
public interface RootShell {

    boolean isRoot();

    /** Runs one command and returns its output, standard output and error together. */
    List<String> run(String command);
}
