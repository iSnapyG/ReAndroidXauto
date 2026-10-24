package com.xiddoc.androidautox.store;

import java.io.IOException;

/** A catalog, index or release document that is not in the shape the store expects. */
public final class StoreFormatException extends IOException {
    private static final long serialVersionUID = 1L;

    public StoreFormatException(String message) {
        super(message);
    }
}
