package com.example.smartcardapp.utils;

import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.example.smartcardapp.cardreader.CL2100Reader;

/**
 * Created by Neo on 2016/12/2 002.
 */

public interface CardReaderDataSetObserver {
    void onItemAdded(@NonNull CL2100Reader item);

    void onItemRemoved(@NonNull CL2100Reader item);

    void onItemsChanged();

    void onItemContentChanged(@NonNull CL2100Reader item, @Nullable Uri uri);
}
