package com.xiddoc.androidautox.store.car;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.model.Action;
import androidx.car.app.model.ActionStrip;
import androidx.car.app.model.ListTemplate;
import androidx.car.app.model.OnClickListener;
import androidx.car.app.model.Template;

import com.xiddoc.androidautox.R;
import com.xiddoc.androidautox.store.Page;
import com.xiddoc.androidautox.store.StoreController;

import java.util.ArrayList;
import java.util.List;

/**
 * A list of rows split into pages that fit the car's row limit. The rows come from a source
 * that is asked again on every redraw, so counts and install marks stay current.
 */
final class StoreListScreen extends StoreScreen {

    interface RowSource {
        List<StoreRow> rows();
    }

    private final String title;
    private final boolean root;
    private final RowSource source;
    private int page;

    StoreListScreen(CarContext carContext, StoreController controller, String title, boolean root,
                    RowSource source) {
        super(carContext, controller);
        this.title = title;
        this.root = root;
        this.source = source;
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        CarContext c = getCarContext();
        List<StoreRow> all = new ArrayList<StoreRow>();
        if (controller.operation() == StoreController.Operation.REFRESHING) {
            all.add(StoreRow.info(c.getString(R.string.store_refreshing)));
        }
        all.addAll(source.rows());

        int limit = listLimit();
        int perPage = all.size() <= limit ? limit : Math.max(1, limit - 2);
        Page<StoreRow> current = Page.of(all, page, perPage);
        page = current.index;
        List<StoreRow> shown = new ArrayList<StoreRow>(current.items);
        if (current.hasPrevious()) {
            shown.add(StoreRow.action(c.getString(R.string.store_page_prev), new Runnable() {
                @Override
                public void run() {
                    page--;
                    invalidate();
                }
            }, c.getString(R.string.store_page_info, current.index + 1, current.pageCount)));
        }
        if (current.hasNext()) {
            shown.add(StoreRow.action(c.getString(R.string.store_page_next), new Runnable() {
                @Override
                public void run() {
                    page++;
                    invalidate();
                }
            }, c.getString(R.string.store_page_info, current.index + 1, current.pageCount)));
        }

        String empty = controller.fdroidEnabled()
                ? c.getString(R.string.store_empty)
                : c.getString(R.string.store_empty) + " " + c.getString(R.string.store_empty_hint);
        ListTemplate.Builder template = new ListTemplate.Builder()
                .setSingleList(items(shown, empty))
                .setTitle(title)
                .setHeaderAction(root ? Action.APP_ICON : Action.BACK);
        if (root && !controller.busy()) {
            template.setActionStrip(new ActionStrip.Builder()
                    .addAction(new Action.Builder()
                            .setTitle(c.getString(R.string.store_refresh))
                            .setOnClickListener(new OnClickListener() {
                                @Override
                                public void onClick() {
                                    controller.refresh(false);
                                }
                            })
                            .build())
                    .build());
        }
        return template.build();
    }
}
