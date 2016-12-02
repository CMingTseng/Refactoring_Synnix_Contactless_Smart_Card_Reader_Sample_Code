package com.example.smartcardapp.manager;

import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.util.Log;

import com.example.smartcardapp.cardreader.CL2100Reader;
import com.example.smartcardapp.utils.CardReaderDataSetObservable;
import com.example.smartcardapp.utils.CardReaderDataSetObserver;

import java.util.ArrayList;
import java.util.UUID;


public class CardReaderManager {
    private static final String TAG = CardReaderManager.class.getSimpleName();
    private static final CardReaderManager sCardReaderManager = new CardReaderManager();
    private final CardReaderDataSetObservable mCardReaderObservable = new CardReaderDataSetObservable();
    private final ContentObserver mCardReaderContentObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
        @Override
        public boolean deliverSelfNotifications() {
            return true;
        }

        @Override
        public void onChange(boolean selfChange) {
            onChange(selfChange, null);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            Log.d(TAG, "@ CardReaderManager ContentObserver onChange Uri : " + uri);
        }
    };

    private final ArrayList<CL2100Reader> mCardReaders = new ArrayList<>();

    public static CardReaderManager getCardReaderManager() {
        return sCardReaderManager;
    }

    public synchronized void registerObserver(CardReaderDataSetObserver cardreaderobserver) {
        try {
            mCardReaderObservable.registerObserver(cardreaderobserver);
        } catch (IllegalStateException ignored) {
        }
    }

    public synchronized void unregisterObserver(CardReaderDataSetObserver cardreaderobserver) {
        mCardReaderObservable.unregisterObserver(cardreaderobserver);
    }

    public synchronized void addCardReader(@NonNull CL2100Reader reader) {
        synchronized (mCardReaders) {
            mCardReaders.add(reader);
            mCardReaderObservable.notifyItemAdded(reader);
        }
    }

    public void removeCardReader(@NonNull CL2100Reader reader) {
        synchronized (mCardReaders) {
            mCardReaders.remove(reader);
        }
        mCardReaderObservable.notifyItemRemoved(reader);
    }

    public int getNFCCardReaderCount() {
        synchronized (mCardReaders) {
            return mCardReaders.size();
        }
    }

    public CL2100Reader getNFCCardReaderByIndex(int index) {
        synchronized (mCardReaders) {
            return mCardReaders.get(index);
        }
    }

    public CL2100Reader getCardReaderByMac(String mac) {
        synchronized (mCardReaders) {
            for (CL2100Reader reader : mCardReaders) {
                final String readermac = reader.getMACAddress().toUpperCase();
                if (mac != null && readermac.equalsIgnoreCase(mac.toUpperCase()))
                    return reader;
            }
            return null;
        }
    }

    public CL2100Reader getCardReaderByUUID(UUID uuid) {
        synchronized (mCardReaders) {
            for (CL2100Reader reader : mCardReaders) {
                final UUID u = reader.getUUID();
                if (u != null && uuid != null && u.equals(uuid)) {
                    return reader;
                }
            }
            return null;
        }
    }

    public int indexOfCardReader(CL2100Reader reader) {
        synchronized (mCardReaders) {
            return mCardReaders.indexOf(reader);
        }
    }
}
