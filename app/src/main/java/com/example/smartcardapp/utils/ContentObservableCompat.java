package com.example.smartcardapp.utils;

import android.database.ContentObservable;
import android.net.Uri;
import android.os.Build;

/**
 * Created by Neo on 2016/12/2 002.
 */

public class ContentObservableCompat extends ContentObservable {
    private static final String TAG = ContentObservableCompat.class.getSimpleName();

    public void dispatchChangeCompat(boolean selfChange, Uri uri) {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
            super.dispatchChange(selfChange, uri);
        else
            super.dispatchChange(selfChange);
    }

    @Deprecated
    @Override
    public final void dispatchChange(boolean selfChange) {
        dispatchChangeCompat(selfChange, null);
    }
}
