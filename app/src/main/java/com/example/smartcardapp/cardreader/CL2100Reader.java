package com.example.smartcardapp.cardreader;

import android.content.Context;
import android.database.ContentObserver;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import com.example.smartcardapp.SampleApplication;
import com.example.smartcardapp.utils.ContentObservableCompat;

import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import amlib.ccid.Error;
import amlib.ccid.Reader;
import amlib.hw.HWType;
import amlib.hw.HardwareInterface;

/**
 * Created by Neo on 2016/12/2 002.
 */

public class CL2100Reader {
    private static final String TAG = CL2100Reader.class.getSimpleName();
    private HardwareInterface mRealReaderHW;
    private Reader mRealReader;
    private UsbDevice mUsbDev;
    private String mDeviceName;
    private String mModelName;
    private String mMACAddress;
    private String mFirmwareVersion;
    private UUID mUUID;
    private ExecutorService mWorkExecutor = Executors.newSingleThreadExecutor();
    private ContentObservableCompat mObservable = new ContentObservableCompat();
    private ContentObserver mObserver = new ContentObserver(null) {
        @Override
        public void onChange(boolean selfChange) {
            onChange(selfChange, null);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
                if (uri == null) {
                    uri = Uri.parse("content://synnix.com/cardreader?uuid=" + getUUID());
                }
                mObservable.dispatchChangeCompat(selfChange, uri);
            } else {
                mObservable.dispatchChange(selfChange);
            }
        }
    };
    private static final String APDUCOMMAND_CARD_PRESENNCE_CHECK = "FFE0000400";
    private static final String APDUCOMMAND_MIFARE_READ_UID = "FFCA000000";
    private static final String APDUCOMMAND_SYNNIX_CARDREADER_LED_BUZZER = "FFE102010CA0FCFCA0FD04A0FE04A0FF04";
    // "OK" status word sent in response to SELECT AID command (0x9000)
    private static final byte[] SELECT_OK_SW = {(byte) 0x90, (byte) 0x00};


    private void dispatchChange(boolean selfChange, String property) {
        mObservable.dispatchChangeCompat(selfChange, Uri.parse(property));
    }

    public final void registerContentObserver(ContentObserver observer) {
        try {
            mObservable.registerObserver(observer);
        } catch (IllegalStateException e) {
            Log.v(TAG, "registerContentObserver() failed : " + e);
        }
    }

    public final void unregisterContentObserver(ContentObserver observer) {
        try {
            mObservable.unregisterObserver(observer);
        } catch (IllegalStateException e) {
            Log.v(TAG, "unregisterContentObserver() failed : " + e);
        }
    }

    public synchronized String getDeviceName() {
        return mDeviceName;
    }

    public synchronized void setDeviceName(String name) {
        if (mDeviceName != name) {
            mDeviceName = name;
            dispatchChange(false, "devicename");
        }
    }

    public synchronized String getModelName() {
        return mModelName;
    }

    public synchronized void setModelName(String modelname) {
        if (mModelName != modelname) {
            mModelName = modelname;
            dispatchChange(false, "modelname");
        }
    }

    public synchronized void setFirmwareVersion(String firmwareversion) {
        if (mFirmwareVersion != firmwareversion) {
            mFirmwareVersion = firmwareversion;
            dispatchChange(false, "firmwareversion");
        }
    }

    public synchronized String getMACAddress() {
        return mMACAddress;
    }

    public synchronized void setMACAddress(String mac) {
        if (mMACAddress != mac.toUpperCase()) {
            mMACAddress = mac.toUpperCase();
            dispatchChange(false, "mac");
        }
    }

    public UsbDevice getUSBDevice() {
        return mUsbDev;
    }

    private void setUSBDevice(UsbDevice usbdevice) {
        mUsbDev = usbdevice;
    }

    public UUID getUUID() {
        return mUUID;
    }

    public synchronized void setUUID(UUID uuid) {
        mUUID = uuid;
        dispatchChange(false, "uuid");
    }

    public Bundle getArguments() {
        final Bundle arguments = new Bundle();
        arguments.putString("CL2100Reader_UUID", String.valueOf(getUUID()));
        return arguments;
    }

    public static CL2100Reader fromUsbDeviceInfo(UsbDevice udev) {
        CL2100Reader reader = new CL2100Reader();
        if (udev.getVendorId() == 0x1206 && (udev.getProductId() == 0x2107 || udev.getProductId() == 0x2105)) {
            reader.updateFromInfo(udev);
            return reader;
        }
        return null;
    }

    private synchronized void updateFromInfo(UsbDevice udev) {
        try {
            mRealReaderHW = new HardwareInterface(HWType.eUSB);
            mRealReaderHW.setLog(SampleApplication.getContext(), true, 0xff);
        } catch (Exception e) {
            Log.e(TAG, "Get Exception : " + e.getMessage());
            return;
        }
        setMACAddress(String.valueOf(udev.getVendorId()) + String.valueOf(udev.getProductId()));
        setUSBDevice(udev);
        setDeviceName(udev.getDeviceName());
        mObservable.dispatchChange(false);
    }

    public static abstract class OnCardReaderResultListener {
        private Handler mHandler;

        public OnCardReaderResultListener(Handler handler) {
            mHandler = handler;
        }

        public final void dispatchReaderResult(final CL2100Reader cardreader, final byte[] result, final Exception exreason) {
            if (mHandler == null) {
                if (exreason == null)
                    onReadSuccess(cardreader, result);
                else
                    onReadFailed(cardreader, result, exreason);
                return;
            }

            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (exreason == null)
                        onReadSuccess(cardreader, result);
                    else
                        onReadFailed(cardreader, result, exreason);
                }
            });
        }

        public abstract void onReadSuccess(final CL2100Reader cardreader, final byte[] result);

        public abstract void onReadFailed(final CL2100Reader cardreader, final byte[] result, final Exception ex);
    }


    public Future<?> initReader(final OnCardReaderResultListener listener, final boolean quiet) {
        return executeNetworkTaskWithGuard(
                new Runnable() {
                    @Override
                    public void run() {
                        initReaderInfoBlocking(listener, quiet);
                    }
                });
    }

    public Future<?> powerOn(final OnCardReaderResultListener listener, final boolean quiet) {//FIXME  Connect RF Card ??
        return executeNetworkTaskWithGuard(
                new Runnable() {
                    @Override
                    public void run() {
                        powerOnBlocking(listener, quiet);
                    }
                });
    }

    public Future<?> powerOff(final OnCardReaderResultListener listener, final boolean quiet) { //FIXME Disconnect RF Card ? need check getSlotStatus() == Error.READER_NO_CARD ??
        return executeNetworkTaskWithGuard(
                new Runnable() {
                    @Override
                    public void run() {
                        powerOffBlocking(listener, quiet);
                    }
                });
    }

    public Future<?> selfTest(final OnCardReaderResultListener listener, final boolean quiet) {
        return executeNetworkTaskWithGuard(
                new Runnable() {
                    @Override
                    public void run() {
                        selfTestBlocking(listener, quiet);
                    }
                });
    }

    public Future<?> getSlotStatus(final OnCardReaderResultListener listener, final boolean quiet) {//FIXME /*detect card hotplug events*/ ?
        return executeNetworkTaskWithGuard(
                new Runnable() {
                    @Override
                    public void run() {
                        getSlotStatusBlocking(listener, quiet);
                    }
                });
    }

    public Future<?> scanCard(final OnCardReaderResultListener listener, final boolean quiet) {
        return executeNetworkTaskWithGuard(
                new Runnable() {
                    @Override
                    public void run() {
                        scanCardBlocking(listener, quiet);
                    }
                });
    }

    public Future<?> powerOnWithScan(final OnCardReaderResultListener listener, final boolean quiet) {
        return executeNetworkTaskWithGuard(
                new Runnable() {
                    @Override
                    public void run() {
                        powerOnWithScanBlocking(listener, quiet);
                    }
                });
    }

    private void initReaderInfoBlocking(final OnCardReaderResultListener listener, final boolean quiet) {
        try {
            if (mRealReaderHW.Init((UsbManager) SampleApplication.getContext().getSystemService(Context.USB_SERVICE), mUsbDev)) {
                Log.e(TAG, "Reader init ok");
                mRealReader = new Reader(mRealReaderHW);
                mRealReader.setSlot((byte) 0);//FIXME  SlotNum !!! getSlot from USBDevice ?
                listener.dispatchReaderResult(CL2100Reader.this, new byte[0], null);//FIXME
            } else {
                Log.e(TAG, "Reader init fail");
                listener.dispatchReaderResult(CL2100Reader.this, new byte[0], new Exception("Error :  Reader init fail"));
            }
        } catch (Exception e) {
            Log.e(TAG, "Get Exception : " + e.getMessage());
        }
    }

    private void powerOnBlocking(final OnCardReaderResultListener listener, final boolean quiet) {
        try {
            final int ret = mRealReader.setPower(Reader.CCID_POWERON);
            if (ret == Error.READER_SUCCESSFUL) {
                String atr = mRealReader.getAtrString();
                listener.dispatchReaderResult(CL2100Reader.this, mRealReader.getAtr(), null); //FIXME !!??
            } else if (ret == Error.READER_NO_CARD) {
                listener.dispatchReaderResult(CL2100Reader.this, "Card Absent".getBytes(), new Exception("powerOn Card Absent")); //FIXME !!??
                Log.d(TAG, "powerOn Card Absent");
            } else {
                Log.d(TAG, "powerOn fail");
                listener.dispatchReaderResult(CL2100Reader.this, ("powerOn fail :" + String.valueOf(ret)).getBytes(), new Exception("powerOn fail :" + String.valueOf(ret))); //FIXME !!??
            }
        } catch (Exception e) {
            Log.e(TAG, "PowerON Get Exception : " + e.getMessage());
            listener.dispatchReaderResult(CL2100Reader.this, new byte[0], new Exception("PowerON Get Exception : " + e.getMessage())); //FIXME !!??
        }
    }

    private void powerOffBlocking(final OnCardReaderResultListener listener, final boolean quiet) {
        try {
            final int ret = mRealReader.setPower(Reader.CCID_POWEROFF);
            if (ret == Error.READER_SUCCESSFUL) {
                listener.dispatchReaderResult(CL2100Reader.this, "power off successfully".getBytes(), null); //FIXME !!??
            } else if (ret == Error.READER_NO_CARD) {
                listener.dispatchReaderResult(CL2100Reader.this, "Card Absent".getBytes(), new Exception("powerOff Card Absent")); //FIXME !!??
                Log.d(TAG, "powerOff Card Absent");
            } else {
                Log.d(TAG, "powerOff fail");
                listener.dispatchReaderResult(CL2100Reader.this, ("powerOff fail :" + String.valueOf(ret)).getBytes(), new Exception("powerOff fail :" + String.valueOf(ret))); //FIXME !!??
            }
        } catch (Exception e) {
            Log.e(TAG, "PowerOFF Get Exception : " + e.getMessage());
            listener.dispatchReaderResult(CL2100Reader.this, new byte[0], new Exception("PowerOFF Get Exception : " + e.getMessage())); //FIXME !!??
        }
    }

    private void selfTestBlocking(final OnCardReaderResultListener listener, final boolean quiet) {
        final byte[] sendAPDUcommand = toByteArray(APDUCOMMAND_SYNNIX_CARDREADER_LED_BUZZER);
        try {
            if (mRealReader.transmit(sendAPDUcommand, sendAPDUcommand.length, new byte[300], new int[1]) == Error.READER_SUCCESSFUL) {
                listener.dispatchReaderResult(CL2100Reader.this, new byte[0], null); //FIXME !!??
            } else {
                Log.e(TAG, "Fail to Send APDU: " + "(" + Integer.toHexString(mRealReader.getCmdFailCode()) + ")");
                listener.dispatchReaderResult(CL2100Reader.this, new byte[0], new Exception("Fail to Send APDU: " + "(" + Integer.toHexString(mRealReader.getCmdFailCode()) + ")")); //FIXME !!??
            }
        } catch (Exception e) {
            Log.e(TAG, "Get Exception : " + e.getMessage());
            listener.dispatchReaderResult(CL2100Reader.this, new byte[0], e); //FIXME !!??
        }
    }

    private void getSlotStatusBlocking(final OnCardReaderResultListener listener, final boolean quiet) {
        final byte[] pCardStatus = new byte[1];
        try {
            if (mRealReader.getCardStatus(pCardStatus) == Error.READER_SUCCESSFUL) {
                if (pCardStatus[0] == Reader.SLOT_STATUS_CARD_ABSENT) {
                    // Error.READER_NO_CARD;
                    listener.dispatchReaderResult(CL2100Reader.this, new byte[0], null); //FIXME !!??
                } else {
                    // Error.READER_SUCCESSFUL;
                    listener.dispatchReaderResult(CL2100Reader.this, new byte[0], null);//FIXME !!??
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Get Exception : " + e.getMessage());
            listener.dispatchReaderResult(CL2100Reader.this, new byte[0], e);
        }
    }

    private void powerOnWithScanBlocking(final OnCardReaderResultListener listener, final boolean quiet) {
        Thread receiveThread = new Thread() {
            @Override
            public void run() {
                while (true) {
                    try {
                        final int ret = mRealReader.setPower(Reader.CCID_POWERON);
                        if (ret == Error.READER_SUCCESSFUL) {
                            final String atr = mRealReader.getAtrString();//FIXME MIFARE/IsoDep/.....
                            Log.d(TAG, "Get ATR　String : " + atr);
                            getCardPresennceStatusBlocking(atr, listener, quiet);
                        } else {
                            Log.d(TAG, "powerOn fail");
                        }

                    } catch (Exception e) {
                        Log.e(TAG, "PowerON Get Exception : " + e.getMessage());
                        listener.dispatchReaderResult(CL2100Reader.this, new byte[0], new Exception("PowerON Get Exception : " + e.getMessage())); //FIXME !!??
                    }
                }
            }
        };
        receiveThread.start();
    }

    private void getCardPresennceStatusBlocking(final String cardtype, final OnCardReaderResultListener listener, final boolean quiet) {
        final byte[] sendAPDUcommand = toByteArray(APDUCOMMAND_CARD_PRESENNCE_CHECK);
        byte[] recvRes = new byte[300];
        try {
            if (mRealReader.transmit(sendAPDUcommand, sendAPDUcommand.length, recvRes, new int[1]) == Error.READER_SUCCESSFUL) {
                if (respondCardPresennceStatus(recvRes)) {
                    scanAfterPowerOnBlocking("Mifare", listener, quiet);//FIXME CardType  //FAKE MIFARE
                }
            } else {
                Log.e(TAG, "Fail to Send APDU: " + "(" + Integer.toHexString(mRealReader.getCmdFailCode()) + ")");
                listener.dispatchReaderResult(CL2100Reader.this, new byte[0], new Exception("Fail to Send APDU: " + "(" + Integer.toHexString(mRealReader.getCmdFailCode()) + ")")); //FIXME !!??
            }
        } catch (Exception e) {
            Log.e(TAG, "Get Exception : " + e.getMessage());
            listener.dispatchReaderResult(CL2100Reader.this, new byte[0], e); //FIXME !!??
        }
    }

    private void scanAfterPowerOnBlocking(final String cardtype, final OnCardReaderResultListener listener, final boolean quiet) {
        final byte[] sendAPDUcommand = toByteArray(APDUCOMMAND_MIFARE_READ_UID);
        byte[] recvRes = new byte[300];

        try {
            if (mRealReader.transmit(sendAPDUcommand, sendAPDUcommand.length, recvRes, new int[1]) == Error.READER_SUCCESSFUL) {
                listener.dispatchReaderResult(CL2100Reader.this, recvRes, null);
            } else {
                Log.e(TAG, "Fail to Send APDU: " + "(" + Integer.toHexString(mRealReader.getCmdFailCode()) + ")");
                listener.dispatchReaderResult(CL2100Reader.this, new byte[0], new Exception("Fail to Send APDU: " + "(" + Integer.toHexString(mRealReader.getCmdFailCode()) + ")")); //FIXME !!??
            }
        } catch (Exception e) {
            Log.e(TAG, "Get Exception : " + e.getMessage());
            listener.dispatchReaderResult(CL2100Reader.this, new byte[0], e);
        }
    }

    private void scanCardBlocking(final OnCardReaderResultListener listener, final boolean quiet) {
        scanAfterPowerOnBlocking("unknow", listener, quiet);
    }

    private boolean respondCardPresennceStatus(byte[] apdurespond) {
        byte[] statusWord = {apdurespond[0], apdurespond[1]};
        if (Arrays.equals(SELECT_OK_SW, statusWord)) {
            return true;
        }
        return false;
    }

    private boolean respondStatus(byte[] apdurespond) {
        byte[] statusWord = {apdurespond[4], apdurespond[5]};
        return respondCardPresennceStatus(statusWord);
    }

    public Future<?> executeNetworkTaskWithGuard(final Runnable task) {
        return mWorkExecutor.submit(task);
    }

    private byte[] toByteArray(String hexString) {

        int hexStringLength = hexString.length();
        byte[] byteArray = null;
        int count = 0;
        char c;
        int i;

        // Count number of hex characters
        for (i = 0; i < hexStringLength; i++) {

            c = hexString.charAt(i);
            if (c >= '0' && c <= '9' || c >= 'A' && c <= 'F' || c >= 'a'
                    && c <= 'f') {
                count++;
            }
        }

        byteArray = new byte[(count + 1) / 2];
        boolean first = true;
        int len = 0;
        int value;
        for (i = 0; i < hexStringLength; i++) {

            c = hexString.charAt(i);
            if (c >= '0' && c <= '9') {
                value = c - '0';
            } else if (c >= 'A' && c <= 'F') {
                value = c - 'A' + 10;
            } else if (c >= 'a' && c <= 'f') {
                value = c - 'a' + 10;
            } else {
                value = -1;
            }

            if (value >= 0) {

                if (first) {

                    byteArray[len] = (byte) (value << 4);

                } else {

                    byteArray[len] |= value;
                    len++;
                }

                first = !first;
            }
        }

        return byteArray;
    }
}
