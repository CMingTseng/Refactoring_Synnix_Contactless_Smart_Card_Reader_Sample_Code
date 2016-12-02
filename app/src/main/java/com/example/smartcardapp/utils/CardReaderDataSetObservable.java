package com.example.smartcardapp.utils;

import android.net.Uri;

import com.example.smartcardapp.cardreader.CL2100Reader;

import java.util.ArrayList;

/**
 * Created by Neo on 2016/12/2 002.
 */

public class CardReaderDataSetObservable {
    private static final String TAG = CardReaderDataSetObservable.class.getSimpleName();
    private final ArrayList<CardReaderDataSetObserver> mObservers = new ArrayList<>();

    public synchronized void registerObserver(CardReaderDataSetObserver observer) {
        if (mObservers.contains(observer))
            throw new IllegalStateException("observer " + observer + " already registered to " + this + ".");
        mObservers.add(observer);
    }

    public synchronized void unregisterObserver(CardReaderDataSetObserver observer) {
        mObservers.remove(observer);
    }

    public synchronized void unregisterAllObservers() {
        mObservers.clear();
    }

    public synchronized void notifyItemAdded(CL2100Reader item) {
        for (int i = mObservers.size() - 1; i >= 0; --i) {
            final CardReaderDataSetObserver observer = mObservers.get(i);
            observer.onItemAdded(item);
        }
    }

    public synchronized void notifyItemRemoved(CL2100Reader item) {
        for (int i = mObservers.size() - 1; i >= 0; --i) {
            final CardReaderDataSetObserver observer = mObservers.get(i);
            observer.onItemRemoved(item);
        }
    }

    public synchronized void notifyItemContentChanged(CL2100Reader item, Uri uri) {
        for (int i = mObservers.size() - 1; i >= 0; --i) {
            final CardReaderDataSetObserver observer = mObservers.get(i);
            observer.onItemContentChanged(item, uri);
        }
    }
}
