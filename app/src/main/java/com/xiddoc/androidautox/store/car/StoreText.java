package com.xiddoc.androidautox.store.car;

import android.content.Context;

import com.xiddoc.androidautox.R;
import com.xiddoc.androidautox.store.StoreApp;
import com.xiddoc.androidautox.store.StoreController;
import com.xiddoc.androidautox.store.StoreInstaller;
import com.xiddoc.androidautox.store.UpdateCheck;

/** Turns store state into the sentences shown in the car. */
public final class StoreText {

    private StoreText() {}

    public static String outcome(Context c, StoreInstaller.Result r) {
        String detail = r.detail;
        switch (r.outcome) {
            case OK: return c.getString(R.string.store_result_ok);
            case NO_ROOT: return c.getString(R.string.store_result_no_root);
            case INVALID: return c.getString(R.string.store_result_invalid);
            case PROTECTED: return c.getString(R.string.store_result_protected);
            case NOT_RESOLVED: return c.getString(R.string.store_result_not_resolved);
            case NO_DOWNLOAD: return c.getString(R.string.store_result_no_download, detail);
            case DOWNLOAD_FAILED: return c.getString(R.string.store_result_download_failed, detail);
            case TOO_LARGE: return c.getString(R.string.store_result_too_large);
            case HASH_MISMATCH: return c.getString(R.string.store_result_hash_mismatch);
            case WRONG_PACKAGE: return c.getString(R.string.store_result_wrong_package);
            case NO_SIGNER_PIN: return c.getString(R.string.store_result_no_signer_pin);
            case UNSIGNED: return c.getString(R.string.store_result_unsigned);
            case SIGNER_MISMATCH: return c.getString(R.string.store_result_signer_mismatch);
            case DOWNGRADE: return c.getString(R.string.store_result_downgrade);
            case UNREADABLE_APK: return c.getString(R.string.store_result_unreadable);
            case INSTALL_FAILED: return c.getString(R.string.store_result_install_failed, detail);
            case UNINSTALL_FAILED: return c.getString(R.string.store_result_uninstall_failed, detail);
            case NOT_INSTALLED_BY_STORE: return c.getString(R.string.store_result_not_ours);
            default: return c.getString(R.string.store_result_cancelled);
        }
    }

    /** What the running operation is doing, for the progress message. */
    public static String progress(Context c, StoreController controller) {
        String name = controller.busyName();
        StoreInstaller.Stage stage = controller.stage();
        switch (controller.operation()) {
            case REFRESHING:
                return c.getString(R.string.store_refreshing);
            case REMOVING:
                return c.getString(R.string.store_progress_removing, name);
            default:
                break;
        }
        if (stage == null) return c.getString(R.string.store_progress_working);
        switch (stage) {
            case RESOLVING: return c.getString(R.string.store_progress_resolving, name);
            case DOWNLOADING:
                return controller.percent() >= 0
                        ? c.getString(R.string.store_progress_downloading, name, controller.percent())
                        : c.getString(R.string.store_progress_downloading_unknown, name);
            case VERIFYING: return c.getString(R.string.store_progress_verifying, name);
            case INSTALLING: return c.getString(R.string.store_progress_installing, name);
            default: return c.getString(R.string.store_progress_removing, name);
        }
    }

    /** Second line of an app row: summary and install state. */
    static String appLine(Context c, StoreController controller, StoreApp app) {
        StringBuilder sb = new StringBuilder(app.summary);
        long installed = controller.installedVersion(app.packageName);
        UpdateCheck.Status status = UpdateCheck.status(app.versionCode, installed);
        String mark = null;
        if (status == UpdateCheck.Status.UPDATE_AVAILABLE) mark = c.getString(R.string.store_row_update);
        else if (status != UpdateCheck.Status.NOT_INSTALLED) mark = c.getString(R.string.store_row_installed);
        if (mark != null) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(mark);
        }
        return sb.toString();
    }
}
