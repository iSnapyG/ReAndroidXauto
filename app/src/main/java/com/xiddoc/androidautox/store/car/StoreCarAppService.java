package com.xiddoc.androidautox.store.car;

import androidx.annotation.NonNull;
import androidx.car.app.CarAppService;
import androidx.car.app.Session;
import androidx.car.app.validation.HostValidator;

import com.xiddoc.androidautox.autox.HostAllowlist;

/**
 * The AutoX Store as a car app: a second entry point (next to AutoX itself) that Android Auto
 * shows in its launcher. Only the official Android Auto host may connect, using the same
 * allowlist as {@code AutoXCarAppService}.
 *
 * <p>Framework entry point; excluded from the coverage gate.
 */
public final class StoreCarAppService extends CarAppService {

    @NonNull
    @Override
    public HostValidator createHostValidator() {
        HostAllowlist allowlist = HostAllowlist.createDefault();
        HostValidator.Builder builder = new HostValidator.Builder(getApplicationContext());
        for (HostAllowlist.HostEntry entry : allowlist.entries()) {
            for (String digest : entry.sha256Digests) {
                String canonical = HostAllowlist.canonicalDigestForCarAppSdk(digest);
                if (canonical != null) {
                    builder.addAllowedHost(entry.packageName, canonical);
                }
            }
        }
        return builder.build();
    }

    @NonNull
    @Override
    public Session onCreateSession() {
        return new StoreSession();
    }
}
