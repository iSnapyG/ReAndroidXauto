package com.xiddoc.androidautox.store.car;

import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.car.app.Screen;
import androidx.car.app.Session;

import com.xiddoc.androidautox.store.StoreController;
import com.xiddoc.androidautox.store.android.StoreEnvironment;

/** One connection of the Android Auto host to the store. */
final class StoreSession extends Session {

    @NonNull
    @Override
    public Screen onCreateScreen(@NonNull Intent intent) {
        StoreController controller = StoreEnvironment.controller(getCarContext());
        controller.reloadInBackground(); // show what is on disk as soon as it is read
        controller.refresh(false); // then fetch whatever is due in the background
        return StoreScreens.home(getCarContext(), controller);
    }
}
