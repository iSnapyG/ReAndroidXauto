package com.xiddoc.androidautox.store.car;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.model.Action;
import androidx.car.app.model.SearchTemplate;
import androidx.car.app.model.Template;

import com.xiddoc.androidautox.R;
import com.xiddoc.androidautox.store.StoreApp;
import com.xiddoc.androidautox.store.StoreController;
import com.xiddoc.androidautox.store.StoreFilter;

import java.util.ArrayList;
import java.util.List;

/** Type or speak a name; matching apps appear below as you go. */
final class StoreSearchScreen extends StoreScreen {

    private String query = "";

    StoreSearchScreen(CarContext carContext, StoreController controller) {
        super(carContext, controller);
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        CarContext c = getCarContext();
        List<StoreApp> found = StoreFilter.search(controller.apps(), query);
        List<StoreRow> rows = new ArrayList<StoreRow>();
        int limit = listLimit();
        for (final StoreApp app : found) {
            if (rows.size() >= limit) break;
            rows.add(StoreScreens.appRow(c, controller, app));
        }
        SearchTemplate.SearchCallback callback = new SearchTemplate.SearchCallback() {
            @Override
            public void onSearchTextChanged(@NonNull String searchText) {
                query = searchText;
                invalidate();
            }

            @Override
            public void onSearchSubmitted(@NonNull String searchText) {
                query = searchText;
                invalidate();
            }
        };
        return new SearchTemplate.Builder(callback)
                .setHeaderAction(Action.BACK)
                .setSearchHint(c.getString(R.string.store_search_hint))
                .setShowKeyboardByDefault(true)
                .setItemList(items(rows, c.getString(R.string.store_empty)))
                .build();
    }
}
