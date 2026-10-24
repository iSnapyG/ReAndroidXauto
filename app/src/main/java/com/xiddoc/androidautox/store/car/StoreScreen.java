package com.xiddoc.androidautox.store.car;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.constraints.ConstraintManager;
import androidx.car.app.model.ItemList;
import androidx.car.app.model.Row;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import com.xiddoc.androidautox.store.StoreController;

import java.util.List;

/** Shared plumbing of the store's car screens: redraw when the store changes, build rows. */
abstract class StoreScreen extends Screen implements StoreController.Observer {

    final StoreController controller;
    private final Handler main = new Handler(Looper.getMainLooper());

    StoreScreen(CarContext carContext, StoreController controller) {
        super(carContext);
        this.controller = controller;
        getLifecycle().addObserver(new DefaultLifecycleObserver() {
            @Override
            public void onStart(@NonNull LifecycleOwner owner) {
                StoreScreen.this.controller.addObserver(StoreScreen.this);
            }

            @Override
            public void onStop(@NonNull LifecycleOwner owner) {
                StoreScreen.this.controller.removeObserver(StoreScreen.this);
            }
        });
    }

    /** Called from the store's worker thread; the redraw happens on the main thread. */
    @Override
    public void onChanged() {
        main.post(new Runnable() {
            @Override
            public void run() {
                invalidate();
            }
        });
    }

    /** How many rows one list may hold right now (the host limits this while driving). */
    int listLimit() {
        ConstraintManager constraints = getCarContext().getCarService(ConstraintManager.class);
        return Math.max(1, constraints.getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST));
    }

    void push(Screen screen) {
        getScreenManager().push(screen);
    }

    static ItemList items(List<StoreRow> rows, CharSequence emptyMessage) {
        ItemList.Builder list = new ItemList.Builder();
        for (final StoreRow r : rows) {
            Row.Builder row = new Row.Builder().setTitle(r.title);
            int added = 0;
            for (String line : r.lines) {
                if (line != null && !line.isEmpty() && added < 2) {
                    row.addText(line);
                    added++;
                }
            }
            if (r.onClick != null) {
                row.setOnClickListener(new androidx.car.app.model.OnClickListener() {
                    @Override
                    public void onClick() {
                        r.onClick.run();
                    }
                });
                if (r.browsable) row.setBrowsable(true);
            }
            list.addItem(row.build());
        }
        if (rows.isEmpty()) list.setNoItemsMessage(emptyMessage);
        return list.build();
    }
}
