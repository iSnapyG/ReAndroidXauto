package com.xiddoc.androidautox.store.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.xiddoc.androidautox.R;
import com.xiddoc.androidautox.store.BatchSummary;
import com.xiddoc.androidautox.store.InstallChoice;
import com.xiddoc.androidautox.store.InstallSelection;
import com.xiddoc.androidautox.store.InstalledApps;
import com.xiddoc.androidautox.store.StoreApp;
import com.xiddoc.androidautox.store.StoreController;
import com.xiddoc.androidautox.store.StoreFilter;
import com.xiddoc.androidautox.store.StoreInstaller;
import com.xiddoc.androidautox.store.car.StoreText;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The "Install" tab: a searchable checklist of the store's apps. Tick several, tap Install,
 * and they are installed one after the other. Built in code (no layout file) and excluded from
 * coverage; the decisions it makes live in {@link InstallChoice}, {@link InstallSelection},
 * {@link StoreFilter} and {@link BatchSummary}, which are unit tested.
 */
public final class StoreInstallPage {

    private final Activity activity;
    private final StoreController controller;
    private final InstalledApps installed;
    private final InstallSelection selection = new InstallSelection();
    private final StoreController.Observer observer;
    private final RowAdapter adapter = new RowAdapter();

    private List<StoreApp> shown = Collections.emptyList();
    private List<StoreApp> lastBatch = Collections.emptyList();
    private boolean batchRunning;
    private StoreApp.Kind kind; // null: every kind

    private EditText search;
    private TextView status;
    private TextView empty;
    private Button installButton;
    private Button cancelButton;

    private StoreInstallPage(Activity activity) {
        this.activity = activity;
        this.controller = StoreEnvironment.controller(activity);
        this.installed = new AndroidInstalledApps(activity.getPackageManager());
        this.observer = new StoreController.Observer() {
            @Override
            public void onChanged() {
                StoreInstallPage.this.activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        onStoreChanged();
                    }
                });
            }
        };
    }

    /** Builds the page inside {@code host}. Call {@link #detach} when the activity goes away. */
    public static StoreInstallPage attach(Activity activity, ViewGroup host) {
        StoreInstallPage page = new StoreInstallPage(activity);
        host.addView(page.build(), new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        page.controller.addObserver(page.observer);
        // On the store's own thread: reading a big cached catalog must not hold up the UI. No
        // network here; downloads happen from the store settings and when the car opens the store.
        if (page.controller.apps().isEmpty()) page.controller.reloadInBackground();
        return page;
    }

    public void detach() {
        controller.removeObserver(observer);
    }

    // ---- building the page ----------------------------------------------------------------

    private int dp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                activity.getResources().getDisplayMetrics());
    }

    private View build() {
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(8), dp(12), 0);

        TextView intro = new TextView(activity);
        intro.setText(R.string.store_install_intro);
        intro.setTextColor(activity.getColor(R.color.text_muted));
        intro.setTextSize(13);
        root.addView(intro);

        search = new EditText(activity);
        search.setHint(R.string.store_home_search);
        search.setSingleLine(true);
        search.setInputType(InputType.TYPE_CLASS_TEXT);
        search.setTextColor(activity.getColor(R.color.text_primary));
        search.setHintTextColor(activity.getColor(R.color.text_muted));
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void afterTextChanged(Editable s) {
                rebuild();
            }
        });
        root.addView(search);

        RadioGroup filter = new RadioGroup(activity);
        filter.setOrientation(RadioGroup.HORIZONTAL);
        final RadioButton all = filterButton(R.string.store_install_filter_all);
        final RadioButton car = filterButton(R.string.store_home_car);
        final RadioButton autox = filterButton(R.string.store_home_autox);
        filter.addView(all);
        filter.addView(car);
        filter.addView(autox);
        all.setChecked(true);
        filter.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                kind = checkedId == car.getId() ? StoreApp.Kind.CAR
                        : checkedId == autox.getId() ? StoreApp.Kind.AUTOX : null;
                rebuild();
            }
        });
        root.addView(filter);

        LinearLayout tools = new LinearLayout(activity);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        tools.addView(toolButton(R.string.store_install_select_all, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selection.pickAll(shown, installed);
                rebuild();
            }
        }));
        tools.addView(toolButton(R.string.store_install_clear, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selection.clear();
                rebuild();
            }
        }));
        tools.addView(toolButton(R.string.store_install_add, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                StoreAddAppDialog.show(activity);
            }
        }));
        root.addView(tools);

        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        installButton = toolButton(R.string.store_install_button, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                installSelected();
            }
        });
        cancelButton = toolButton(R.string.store_action_cancel, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                controller.cancel();
            }
        });
        cancelButton.setVisibility(View.GONE);
        actions.addView(installButton);
        actions.addView(cancelButton);
        root.addView(actions);

        status = new TextView(activity);
        status.setTextColor(activity.getColor(R.color.text_primary));
        status.setPadding(dp(4), dp(4), dp(4), dp(4));
        root.addView(status);

        empty = new TextView(activity);
        empty.setText(R.string.store_install_none);
        empty.setTextColor(activity.getColor(R.color.text_muted));
        empty.setPadding(dp(4), dp(16), dp(4), 0);
        root.addView(empty);

        ListView list = new ListView(activity);
        list.setAdapter(adapter);
        list.setDivider(null);
        list.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(android.widget.AdapterView<?> parent, View view, int position, long id) {
                StoreApp app = shown.get(position);
                boolean selectable = InstallChoice.selectable(stateOf(app));
                selection.toggle(app.packageName, selectable);
                rebuild();
            }
        });
        list.setOnItemLongClickListener(new android.widget.AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(android.widget.AdapterView<?> parent, View view, int position, long id) {
                return offerRemoval(shown.get(position));
            }
        });
        root.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        rebuild();
        return root;
    }

    private RadioButton filterButton(int label) {
        RadioButton b = new RadioButton(activity);
        b.setId(View.generateViewId());
        b.setText(label);
        b.setTextColor(activity.getColor(R.color.text_primary));
        b.setTextSize(12);
        return b;
    }

    private Button toolButton(int label, View.OnClickListener click) {
        Button b = new Button(activity);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(12);
        b.setOnClickListener(click);
        return b;
    }

    // ---- behaviour ------------------------------------------------------------------------

    private InstallChoice.State stateOf(StoreApp app) {
        return InstallChoice.of(app, installed.versionCode(app.packageName));
    }

    private void installSelected() {
        List<StoreApp> chosen = selection.chosen(controller.apps(), installed);
        if (chosen.isEmpty()) return;
        if (controller.installAll(chosen)) {
            lastBatch = chosen;
            batchRunning = true;
        } else {
            Toast.makeText(activity, activity.getString(R.string.store_busy_other,
                    StoreText.progress(activity, controller)), Toast.LENGTH_SHORT).show();
        }
    }

    private void onStoreChanged() {
        if (batchRunning && !controller.busy()) {
            batchRunning = false;
            showBatchResult();
        } else if (controller.busy()) {
            status.setText(StoreText.progress(activity, controller));
        }
        rebuild();
    }

    private void showBatchResult() {
        BatchSummary s = controller.summary(lastBatch);
        StringBuilder text = new StringBuilder(
                activity.getString(R.string.store_install_summary, s.ok, s.failed, s.notRun));
        for (StoreApp app : lastBatch) {
            StoreInstaller.Result r = controller.lastResult(app.packageName);
            if (r != null && !r.ok() && r.outcome != StoreInstaller.Outcome.CANCELLED) {
                text.append('\n').append(app.name).append(": ").append(StoreText.outcome(activity, r));
                break; // the first problem is enough here; the car screens show each one
            }
        }
        status.setText(text);
    }

    private boolean offerRemoval(final StoreApp app) {
        Set<String> mine = new HashSet<String>();
        for (StoreApp a : controller.customApps()) mine.add(a.packageName);
        if (!mine.contains(app.packageName)) return false;
        new AlertDialog.Builder(activity)
                .setMessage(activity.getString(R.string.store_install_remove_own, app.name))
                .setPositiveButton(R.string.store_action_remove, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        try {
                            controller.removeCustomApp(app.packageName);
                            Toast.makeText(activity, R.string.store_install_removed, Toast.LENGTH_SHORT).show();
                        } catch (IOException e) {
                            Toast.makeText(activity, e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    }
                })
                .setNegativeButton(R.string.store_action_cancel, null)
                .show();
        return true;
    }

    /** Recomputes the visible list and the buttons from the controller's current state. */
    private void rebuild() {
        selection.prune(controller.apps(), installed);
        shown = StoreFilter.matching(controller.apps(), kind, search.getText().toString());
        adapter.notifyDataSetChanged();
        empty.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
        boolean busy = controller.busy();
        installButton.setText(activity.getString(R.string.store_install_button, selection.size()));
        installButton.setEnabled(!busy && selection.size() > 0);
        cancelButton.setVisibility(busy ? View.VISIBLE : View.GONE);
    }

    // ---- rows -----------------------------------------------------------------------------

    private final class RowAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return shown.size();
        }

        @Override
        public Object getItem(int position) {
            return shown.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Row row = convertView == null ? new Row(parent.getContext()) : (Row) convertView;
            row.bind(shown.get(position));
            return row;
        }
    }

    private final class Row extends LinearLayout {
        private final CheckBox box;
        private final TextView title;
        private final TextView line;

        Row(Context context) {
            super(context);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(dp(4), dp(8), dp(4), dp(8));
            box = new CheckBox(context);
            box.setClickable(false);
            box.setFocusable(false);
            addView(box);
            LinearLayout text = new LinearLayout(context);
            text.setOrientation(VERTICAL);
            title = new TextView(context);
            title.setTextSize(16);
            title.setTextColor(context.getColor(R.color.text_primary));
            line = new TextView(context);
            line.setTextSize(12);
            line.setTextColor(context.getColor(R.color.text_muted));
            text.addView(title);
            text.addView(line);
            addView(text, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }

        void bind(StoreApp app) {
            InstallChoice.State state = stateOf(app);
            boolean selectable = InstallChoice.selectable(state);
            box.setEnabled(selectable);
            box.setChecked(selectable && selection.contains(app.packageName));
            setAlpha(selectable ? 1f : 0.55f);
            title.setText(app.name);
            line.setText(describe(app, state));
        }

        private String describe(StoreApp app, InstallChoice.State state) {
            List<String> parts = new ArrayList<String>();
            if (app.kind == StoreApp.Kind.CAR) parts.add(activity.getString(R.string.store_home_car));
            if (app.kind == StoreApp.Kind.AUTOX) parts.add(activity.getString(R.string.store_home_autox));
            if (!app.summary.isEmpty()) parts.add(app.summary);
            switch (state) {
                case UPDATE: parts.add(activity.getString(R.string.store_row_update)); break;
                case RECHECK: parts.add(activity.getString(R.string.store_install_state_recheck)); break;
                case INSTALLED: parts.add(activity.getString(R.string.store_row_installed)); break;
                case UNAVAILABLE: parts.add(activity.getString(R.string.store_install_state_unavailable)); break;
                default: break;
            }
            StringBuilder sb = new StringBuilder();
            for (String p : parts) {
                if (sb.length() > 0) sb.append(" · ");
                sb.append(p);
            }
            return sb.toString();
        }
    }
}
