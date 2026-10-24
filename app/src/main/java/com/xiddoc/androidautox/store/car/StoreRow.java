package com.xiddoc.androidautox.store.car;

/** One line of a store list: title, up to two lines of text, and what tapping it does. */
final class StoreRow {

    final String title;
    final String[] lines;
    /** Null for a line that only shows information. */
    final Runnable onClick;
    final boolean browsable;

    private StoreRow(String title, String[] lines, Runnable onClick, boolean browsable) {
        this.title = title;
        this.lines = lines;
        this.onClick = onClick;
        this.browsable = browsable;
    }

    static StoreRow info(String title, String... lines) {
        return new StoreRow(title, lines, null, false);
    }

    static StoreRow action(String title, Runnable onClick, String... lines) {
        return new StoreRow(title, lines, onClick, false);
    }

    /** A row that opens another screen. */
    static StoreRow link(String title, Runnable onClick, String... lines) {
        return new StoreRow(title, lines, onClick, true);
    }
}
