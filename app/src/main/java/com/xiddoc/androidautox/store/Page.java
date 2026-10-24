package com.xiddoc.androidautox.store;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One page of a long list. The car screen can only show a handful of rows at a time, so every
 * list in the store is paged.
 */
public final class Page<T> {

    public final List<T> items;
    /** Zero-based, already clamped into range. */
    public final int index;
    public final int pageCount;

    private Page(List<T> items, int index, int pageCount) {
        this.items = Collections.unmodifiableList(items);
        this.index = index;
        this.pageCount = pageCount;
    }

    public boolean hasPrevious() {
        return index > 0;
    }

    public boolean hasNext() {
        return index + 1 < pageCount;
    }

    /**
     * @param requested zero-based page wanted; out-of-range values are clamped
     * @param size      rows per page (at least 1)
     */
    public static <T> Page<T> of(List<T> all, int requested, int size) {
        int perPage = Math.max(1, size);
        int pages = Math.max(1, (all.size() + perPage - 1) / perPage);
        int index = Math.min(Math.max(0, requested), pages - 1);
        int from = index * perPage;
        int to = Math.min(all.size(), from + perPage);
        return new Page<T>(new ArrayList<T>(all.subList(from, to)), index, pages);
    }
}
