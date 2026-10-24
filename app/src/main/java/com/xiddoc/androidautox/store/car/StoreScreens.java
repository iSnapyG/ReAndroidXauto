package com.xiddoc.androidautox.store.car;

import androidx.car.app.CarContext;
import androidx.car.app.Screen;

import com.xiddoc.androidautox.R;
import com.xiddoc.androidautox.store.StoreApp;
import com.xiddoc.androidautox.store.StoreController;
import com.xiddoc.androidautox.store.StoreFilter;

import java.util.ArrayList;
import java.util.List;

/** Builds the store's screens and the rows that link them together. */
final class StoreScreens {

    private StoreScreens() {}

    /** Supplies a (re-evaluated) list of apps. */
    interface AppSource {
        List<StoreApp> apps();
    }

    static Screen home(final CarContext c, final StoreController controller) {
        return new StoreListScreen(c, controller, c.getString(R.string.store_title), true,
                new StoreListScreen.RowSource() {
                    @Override
                    public List<StoreRow> rows() {
                        return homeRows(c, controller);
                    }
                });
    }

    private static List<StoreRow> homeRows(final CarContext c, final StoreController controller) {
        final List<StoreApp> apps = controller.apps();
        List<StoreRow> rows = new ArrayList<StoreRow>();
        rows.add(openList(c, controller, c.getString(R.string.store_home_car),
                StoreFilter.byKind(apps, StoreApp.Kind.CAR).size(),
                new AppSource() {
                    @Override
                    public List<StoreApp> apps() {
                        return StoreFilter.byKind(controller.apps(), StoreApp.Kind.CAR);
                    }
                }));
        rows.add(openList(c, controller, c.getString(R.string.store_home_autox),
                StoreFilter.byKind(apps, StoreApp.Kind.AUTOX).size(),
                new AppSource() {
                    @Override
                    public List<StoreApp> apps() {
                        return StoreFilter.byKind(controller.apps(), StoreApp.Kind.AUTOX);
                    }
                }));
        rows.add(StoreRow.link(c.getString(R.string.store_home_browse), new Runnable() {
            @Override
            public void run() {
                getScreenManager(c).push(categories(c, controller));
            }
        }));
        rows.add(StoreRow.link(c.getString(R.string.store_home_search), new Runnable() {
            @Override
            public void run() {
                getScreenManager(c).push(new StoreSearchScreen(c, controller));
            }
        }));
        int updates = StoreFilter.updates(apps, controller.trackedVersions()).size();
        rows.add(openUpdates(c, controller, updates));
        rows.add(openList(c, controller,
                c.getString(R.string.store_home_installed, controller.trackedVersions().size()),
                -1, new AppSource() {
                    @Override
                    public List<StoreApp> apps() {
                        return StoreFilter.installed(controller.apps(), controller.trackedVersions());
                    }
                }));
        return rows;
    }

    private static androidx.car.app.ScreenManager getScreenManager(CarContext c) {
        return c.getCarService(androidx.car.app.ScreenManager.class);
    }

    /** A row that opens a list of apps; {@code count < 0} leaves the count out of the row. */
    private static StoreRow openList(final CarContext c, final StoreController controller,
                                     final String title, int count, final AppSource source) {
        return StoreRow.link(title, new Runnable() {
            @Override
            public void run() {
                getScreenManager(c).push(appList(c, controller, title, source));
            }
        }, count < 0 ? "" : c.getString(R.string.store_home_count, count));
    }

    private static StoreRow openUpdates(final CarContext c, final StoreController controller, int count) {
        final String title = c.getString(R.string.store_home_updates, count);
        return StoreRow.link(title, new Runnable() {
            @Override
            public void run() {
                getScreenManager(c).push(new StoreListScreen(c, controller, title, false,
                        new StoreListScreen.RowSource() {
                            @Override
                            public List<StoreRow> rows() {
                                List<StoreApp> due = StoreFilter.updates(controller.apps(),
                                        controller.trackedVersions());
                                List<StoreRow> rows = new ArrayList<StoreRow>();
                                if (!due.isEmpty()) {
                                    rows.add(StoreRow.action(
                                            c.getString(R.string.store_update_all, due.size()),
                                            new Runnable() {
                                                @Override
                                                public void run() {
                                                    controller.updateAll();
                                                }
                                            }));
                                }
                                for (StoreApp app : due) rows.add(appRow(c, controller, app));
                                return rows;
                            }
                        }));
            }
        });
    }

    private static Screen categories(final CarContext c, final StoreController controller) {
        return new StoreListScreen(c, controller, c.getString(R.string.store_home_browse), false,
                new StoreListScreen.RowSource() {
                    @Override
                    public List<StoreRow> rows() {
                        List<StoreRow> rows = new ArrayList<StoreRow>();
                        for (final String category : StoreFilter.BROWSE_CATEGORIES) {
                            int n = StoreFilter.byCategory(controller.apps(), category).size();
                            if (n == 0) continue;
                            rows.add(openList(c, controller, category, n, new AppSource() {
                                @Override
                                public List<StoreApp> apps() {
                                    return StoreFilter.byCategory(controller.apps(), category);
                                }
                            }));
                        }
                        return rows;
                    }
                });
    }

    private static Screen appList(final CarContext c, final StoreController controller, String title,
                                  final AppSource source) {
        return new StoreListScreen(c, controller, title, false, new StoreListScreen.RowSource() {
            @Override
            public List<StoreRow> rows() {
                List<StoreRow> rows = new ArrayList<StoreRow>();
                for (StoreApp app : source.apps()) rows.add(appRow(c, controller, app));
                return rows;
            }
        });
    }

    /** A row for one app that opens its detail screen. */
    static StoreRow appRow(final CarContext c, final StoreController controller,
                           final StoreApp app) {
        return StoreRow.link(app.name, new Runnable() {
            @Override
            public void run() {
                getScreenManager(c).push(new StoreDetailScreen(c, controller, app));
            }
        }, StoreText.appLine(c, controller, app));
    }
}
