package com.example.smartcardapp.adpater;

import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.example.smartcardapp.R;
import com.example.smartcardapp.cardreader.CL2100Reader;
import com.example.smartcardapp.manager.CardReaderManager;
import com.example.smartcardapp.utils.CardReaderDataSetObserver;

import java.util.ArrayList;

/**
 * Created by Neo on 2016/12/2 002.
 */

public class CardReaderAdapter extends BaseAdapter {
    private final String TAG = CardReaderAdapter.class.getSimpleName();

    private ArrayList<CL2100Reader> mCardReaders = new ArrayList<>();

    private final ContentObserver mContentObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
        @Override
        public boolean deliverSelfNotifications() {
            return true;
        }

        @Override
        public void onChange(boolean selfChange) {

        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {

        }
    };
    private final CardReaderDataSetObserver mCardReaderObserver = new CardReaderDataSetObserver() {
        @Override
        public void onItemAdded(@NonNull CL2100Reader item) {
            onItemsChanged();
            item.registerContentObserver(mContentObserver);
        }

        @Override
        public void onItemRemoved(@NonNull CL2100Reader item) {
            item.unregisterContentObserver(mContentObserver);
        }

        @Override
        public void onItemsChanged() {
            try {
                mCardReaders.clear();
                ArrayList<CL2100Reader> cardreaders = new ArrayList<>();
                final CardReaderManager manager = CardReaderManager.getCardReaderManager();
                final int count = manager.getNFCCardReaderCount();
                for (int i = 0; i < count; ++i) {
                    final CL2100Reader device = manager.getNFCCardReaderByIndex(i);
                    cardreaders.add(device);
                }
                mCardReaders = cardreaders;
                mCardReaders.trimToSize();
                notifyDataSetChanged();
            } catch (IllegalStateException ex) {
                Log.w(TAG, ex);
            }
        }

        @Override
        public void onItemContentChanged(@NonNull CL2100Reader item, @Nullable Uri uri) {
            final String property = uri.getQueryParameter("property");
            if (property != null) {
                Log.d(TAG, "Show ItemContentChanged @ " + property);
            }
        }
    };

    public void startMonitor() {
        CardReaderManager.getCardReaderManager().registerObserver(mCardReaderObserver);
    }

    public void stopMonitor() {
        CardReaderManager.getCardReaderManager().unregisterObserver(mCardReaderObserver);
    }

    @Override
    public int getCount() {
        return mCardReaders.size();
    }

    @Override
    public Object getItem(int position) {
        return mCardReaders.get(position);
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).hashCode();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = View.inflate(parent.getContext(), android.R.layout.simple_spinner_item, null);
            final ViewHolder holder = new ViewHolder(convertView);
            convertView.setTag(R.id.tag_view_holder, holder);
        }
        final ViewHolder holder = (ViewHolder) convertView.getTag(R.id.tag_view_holder);
        holder.name.setText(((CL2100Reader) getItem(position)).getDeviceName());
        return convertView;
    }

    private class ViewHolder {
        TextView name;

        ViewHolder(View view) {
            name = (TextView) view.findViewById(android.R.id.text1);
        }
    }
}
