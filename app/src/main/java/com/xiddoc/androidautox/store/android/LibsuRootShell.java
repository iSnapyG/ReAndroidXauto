package com.xiddoc.androidautox.store.android;

import com.topjohnwu.superuser.Shell;
import com.xiddoc.androidautox.store.RootShell;

import java.util.ArrayList;
import java.util.List;

/** The app's libsu root shell. Blocking: call from a background thread only. */
public final class LibsuRootShell implements RootShell {

    @Override
    public boolean isRoot() {
        return Shell.getShell().isRoot();
    }

    @Override
    public List<String> run(String command) {
        Shell.Result result = Shell.cmd(command).exec();
        List<String> lines = new ArrayList<String>(result.getOut());
        lines.addAll(result.getErr());
        return lines;
    }
}
