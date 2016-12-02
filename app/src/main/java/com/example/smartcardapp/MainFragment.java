package com.example.smartcardapp;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;

import amlib.ccid.Reader;
import amlib.hw.HWType;
import amlib.hw.HardwareInterface;

public class MainFragment extends Fragment {
    private Reader mReader;
    private HardwareInterface mMyDev;
    private UsbDevice mUsbDev;
    private UsbManager mManager;
    Builder mSlotDialog;
    Builder mPowerDialog;
    private byte mSlotNum;
    private PendingIntent mPermissionIntent;
    private Button mListButton;
    private Button mOpenButton;
    private Button mCloseButton;
    private Button mConnButton;
    private Button mDisconnButton;
    private Button mTestButton;
    private Button mUIDButton;
    private Button mSendAPDUButton;
    private Button mSwitchButton;
    private ArrayAdapter<String> mReaderAdapter;
    private TextView mTextViewReader;

    private TextView mTextViewRAPDU;

    private TextView mTextViewResult;
    private EditText mEditTextApdu;
    private ProgressDialog mCloseProgress;
    //private EditText mEditTextMode;

    private Spinner mModeSpinner;
    private Spinner mReaderSpinner;
    private ArrayAdapter<String> mModeList;
    //	private ArrayAdapter<String> mModeAdapter;
    private String mStrMessage;
    private final String mode2 = "I2c Mode";
    private final String mode3 = "SLE4428 Mode";
    private final byte DEFAULT_SN_LEN = 32;
    private Context mContext;


    private static final String TAG = "Synnix-Test";
    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";


    private Timer myTimer;
    private EnumeTimerTask enumeTimerTask;

    public MainFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View rootview = inflater.inflate(R.layout.fragment_main, container, false);
        setupViews(rootview);
        mSlotNum = (byte) 0;
        mContext = container.getContext();

        Log.d(TAG, "onCreateView");
        try {
            mMyDev = new HardwareInterface(HWType.eUSB);
            mMyDev.setLog(mContext, true, 0xff);

        } catch (Exception e) {
            mStrMessage = "Get Exception : " + e.getMessage();
            Log.e(TAG, mStrMessage);
        }
        // Get USB manager
        Log.d(TAG, " mManager");
        mManager = (UsbManager) mContext.getSystemService(Context.USB_SERVICE);
        toRegisterReceiver();

        myTimer = new Timer();
        enumeTimerTask = new EnumeTimerTask();
        myTimer.schedule(enumeTimerTask, 300);
        return rootview;
    }

    public void setupViews(View view) {
        String[] pMode = new String[]{mode2, mode3};

        mListButton = (Button) view.findViewById(R.id.buttonList);
        mOpenButton = (Button) view.findViewById(R.id.buttonOpen);
        mCloseButton = (Button) view.findViewById(R.id.buttonClose);
        mConnButton = (Button) view.findViewById(R.id.buttonConn);
        mDisconnButton = (Button) view.findViewById(R.id.buttonDisconn);
        mTestButton = (Button) view.findViewById(R.id.buttonTest);
        mUIDButton = (Button) view.findViewById(R.id.buttonUID);
        mSendAPDUButton = (Button) view.findViewById(R.id.buttonAPDU);
        mSwitchButton = (Button) view.findViewById(R.id.buttonSwitch);

        onCreateButtonSetup();

        mTextViewReader = (TextView) view.findViewById(R.id.textReader);
        mTextViewResult = (TextView) view.findViewById(R.id.textResult);
        mEditTextApdu = (EditText) view.findViewById(R.id.editTextAPDU);
        mTextViewRAPDU = (TextView) view.findViewById(R.id.textResponse);
        mEditTextApdu.setText("00A4040007A000000004101000");
        mModeSpinner = (Spinner) view.findViewById(R.id.modeSpinner);

        mModeList = new ArrayAdapter<String>(getContext(), android.R.layout.simple_spinner_item, pMode);

        mModeList.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mModeSpinner.setAdapter(mModeList);
        setupReaderSpinner(view);
        setReaderSlotView();
    }

    private void toRegisterReceiver() {
        // Register receiver for USB permission
        mPermissionIntent = PendingIntent.getBroadcast(getContext(), 0, new Intent(ACTION_USB_PERMISSION), 0);
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        getActivity().registerReceiver(mReceiver, filter);
    }

    private void setupReaderSpinner(View view) {
        // Initialize reader spinner
        mReaderAdapter = new ArrayAdapter<String>(getContext(), android.R.layout.simple_spinner_item);

        mReaderSpinner = (Spinner) view.findViewById(R.id.spinnerDevice);
        mReaderSpinner.setAdapter(mReaderAdapter);
        mReaderSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {

            @Override
            public void onItemSelected(AdapterView<?> arg0, View arg1, int position, long arg3) {
            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0) {
                // TODO Auto-generated method stub
            }
        });
    }

    private void setReaderSlotView() {
        final String[] arraySlot = new String[]{"slot:0", "Slot:1"};
        mSlotDialog = new AlertDialog.Builder(getContext());
        DialogInterface.OnClickListener Select = new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                mSlotNum = (byte) which;
            }
        };

        DialogInterface.OnClickListener OkClick = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                requestDevPerm();
            }
        };

        mSlotDialog.setPositiveButton("OK", OkClick);
        mSlotDialog.setTitle("Select Slot Number");
        mSlotDialog.setSingleChoiceItems(arraySlot, 0, Select);

    }

    private void checkSlotNumber(UsbDevice uDev) {
        //if(uDev.getProductId() == 0x9522 || uDev.getProductId() == 0x9525 ||uDev.getProductId() == 0x9526 )
        //	mSlotDialog.show();
        //else{
        mSlotNum = (byte) 0;
        requestDevPerm();
        //}
    }

    private void updateViewReader() {
        int pid;
        int vid;
        try {
            pid = mUsbDev.getProductId();
            vid = mUsbDev.getVendorId();
        } catch (NullPointerException e) {
            mStrMessage = "Get Exception : " + e.getMessage();
            Log.e(TAG, mStrMessage);
            return;
        }
        mTextViewReader.setText("Reader:" + Integer.toHexString(vid) + " " + Integer.toHexString(pid));
    }

    private void updateReaderList(Intent intent) {
        // Update reader list
        mReaderAdapter.clear();
        for (UsbDevice device : mManager.getDeviceList().values()) {
            Log.d(TAG, "Update reader list : " + device.getDeviceName());
            if (isSynReader(device))
                mReaderAdapter.add(device.getDeviceName());
        }

        UsbDevice device = (UsbDevice) intent
                .getParcelableExtra(UsbManager.EXTRA_DEVICE);
    }

    private UsbDevice getSpinnerSelect() {
        String deviceName;
        deviceName = (String) mReaderSpinner.getSelectedItem();
        if (deviceName != null) {
            // For each device
            for (UsbDevice device : mManager.getDeviceList().values()) {
                if (deviceName.equals(device.getDeviceName())) {
                    return device;
                }
            }
        }
        return null;
    }

    private void requestDevPerm() {
        UsbDevice dev = getSpinnerSelect();
        if (dev != null)
            mManager.requestPermission(dev, mPermissionIntent);
        else
            Log.e(TAG, "selected not found");
    }

    public void ListOnClick(View view) {
        Log.d(TAG, "ListOnClick");
        EnumeDev();
    }

    public void OpenOnClick(View view) {
        Log.d(TAG, "OpenOnClick");
        UsbDevice dev = getSpinnerSelect();
        if (dev != null)
            checkSlotNumber(dev);
    }

    public void ConnOnClick(View view) {
        Log.d(TAG, "ConnOnClick");
        TextView textViewResult;
        int ret;
        textViewResult = (TextView) getView().findViewById(R.id.textResult);
        ret = poweron();
        if (ret == Error.READER_SUCCESSFUL) {
            //textViewResult.setText("power on successfully");
            getATROnClick();
        } else if (ret == Error.READER_NO_CARD) {
            textViewResult.setText("Card Absent");
        } else
            textViewResult.setText("power on fail:" + Integer.toString(ret));
    }

    public void DisconnOnClick(View view) {
        Log.d(TAG, "DisconnOnClick");
        TextView textViewResult;
        int ret;
        textViewResult = (TextView) getView().findViewById(R.id.textResult);
        ret = poweroff();
        if (ret == Error.READER_SUCCESSFUL)
            textViewResult.setText("power off successfully");
        else if (ret == Error.READER_NO_CARD) {
            textViewResult.setText("Card Absent");
        } else
            textViewResult.setText("power off fail:" + Integer.toString(ret));
    }

    public void TestOnClick(View view) {
        Log.d(TAG, "TestOnClick");
        sendAPDU("FFE102010CA0FCFCA0FD04A0FE04A0FF04");
    }

    public void UIDOnClick(View view) {
        Log.d(TAG, "UIDOnClick");
        sendAPDU("FFCA000000");
    }

    public void APDUOnClick(View view) {
        Log.d(TAG, "APDUOnClick");
        sendAPDU("");
    }

    public void getATROnClick() {      //View view){
        String atr;
        Log.d(TAG, "getATROnClick");
        try {
            atr = mReader.getAtrString();
            mTextViewResult.setText(" ATR:" + atr);
            //mATRButton.setEnabled(false);
        } catch (Exception e) {
            mStrMessage = "Get Exception : " + e.getMessage();
            Log.e(TAG, mStrMessage);
            mTextViewResult.setText(mStrMessage);
        }
    }

    public void sendAPDU(String command) {
        byte[] pSendAPDU;
        byte[] pRecvRes = new byte[300];
        int[] pRevAPDULen = new int[1];
        String apduStr;
        int sendLen, result;

        Log.d(TAG, "sendAPDUkOnClick");
        pRevAPDULen[0] = 300;

        if (command != "") {
            apduStr = command;
        } else {
            apduStr = mEditTextApdu.getText().toString();
        }
        pSendAPDU = toByteArray(apduStr);
        sendLen = pSendAPDU.length;

        try {
            result = mReader.transmit(pSendAPDU, sendLen, pRecvRes, pRevAPDULen);
            Log.d(TAG, " Reader Result code : " + result);
            if (result == Error.READER_SUCCESSFUL) {
                mTextViewRAPDU.setText(logBuffer(pRecvRes, pRevAPDULen[0]));
            } else {
                mTextViewResult.setText("Fail to Send APDU: " + Integer.toString(result)
                        + "(" + Integer.toHexString(mReader.getCmdFailCode()) + ")");
                Log.e(TAG, "Fail to Send APDU: " + Integer.toString(result)
                        + "(" + Integer.toHexString(mReader.getCmdFailCode()) + ")");
            }
        } catch (Exception e) {
            mStrMessage = "Get Exception : " + e.getMessage();
            Log.e(TAG, mStrMessage);
            mTextViewResult.setText(mStrMessage);
        }
    }

    private int getSlotStatus() {
        int ret = Error.READER_NO_CARD;
        byte[] pCardStatus = new byte[1];

			/*detect card hotplug events*/
        try {
            if (mReader.getCardStatus(pCardStatus) == Error.READER_SUCCESSFUL) {
                //Log.d(TAG,"cmd OK  mSlotStatus = " +mSlotStatus);
                if (pCardStatus[0] == Reader.SLOT_STATUS_CARD_ABSENT) {
                    ret = Error.READER_NO_CARD;
                } else
                    ret = Error.READER_SUCCESSFUL;
            }
        } catch (Exception e) {
            mStrMessage = "Get Exception : " + e.getMessage();
            mTextViewResult.setText(mStrMessage);
        }
        return ret;
    }

    public void CloseOnClick(View view) {
        mTextViewRAPDU.setText("");
        new CloseTask().execute();
    }

    class EnumeTimerTask extends TimerTask {

        @Override
        public void run() {

            getActivity().runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    EnumeDev();
                }
            });
        }

    }

    private class OpenTask extends AsyncTask<UsbDevice, Void, Integer> {

        @Override
        protected Integer doInBackground(UsbDevice... params) {
            int status = 0;
            try {
                status = InitReader();
                if (status != 0) {
                    Log.e(TAG, "fail to initial reader");
                    return status;
                }
                //status = mReader.connect();
            } catch (Exception e) {
                mStrMessage = "Get Exception : " + e.getMessage();
                getActivity().runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        mTextViewResult.setText(mStrMessage);
                    }
                });
            }
            return status;
        }

        @Override
        protected void onPostExecute(Integer result) {
            if (result != 0) {
                mTextViewResult.setText("Open fail: " + Integer.toString(result));
                Log.e(TAG, "Open fail: " + Integer.toString(result));
            } else {
                onOpenButtonSetup();
                mTextViewResult.setText("Open successfully");
                Log.e(TAG, "Open successfully");
            }
        }
    }

    private int closeReaderUp() {
        Log.d(TAG, "Closing reader...");
        int ret = 0;

        if (mReader != null) {
            ret = mReader.close();
        }
        return ret;
    }

    private void closeReaderBottom() {
        onCloseButtonSetup();
        cleanText();
        mMyDev.Close();
        mSlotNum = (byte) 0;
    }

    private void setUpCloseDialog() {
        mCloseProgress = new ProgressDialog(getContext());
        mCloseProgress.setMessage("Closing Reader");
        mCloseProgress.setCancelable(false);
        mCloseProgress.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        mCloseProgress.show();
    }

    private class CloseTask extends AsyncTask<Void, Void, Integer> {

        @Override
        protected void onPreExecute() {
            setUpCloseDialog();

        }

        @Override
        protected Integer doInBackground(Void... params) {
            int status = 0;
            try {
                do {
                    status = closeReaderUp();
                } while (status == Error.READER_CMD_BUSY);

            } catch (Exception e) {
                mStrMessage = "Get Exception : " + e.getMessage();
                getActivity().runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        mTextViewResult.setText(mStrMessage);
                    }
                });
            }

            return status;
        }

        @Override
        protected void onPostExecute(Integer result) {
            if (result != 0) {
                mTextViewResult.setText("Close fail: " + Integer.toString(result));
                Log.e(TAG, "Close fail: " + Integer.toString(result));
            } else {
                mTextViewResult.setText("Close successfully");
                Log.e(TAG, "Close successfully");
            }
            closeReaderBottom();
            mCloseProgress.dismiss();
        }
    }

    private int poweron() {
        int result = Error.READER_SUCCESSFUL;
        Log.d(TAG, "poweron");
        //check slot status first
        if (getSlotStatus() == Error.READER_NO_CARD) {
            mTextViewResult.setText("Card Absent");
            Log.d(TAG, "Card Absent");
            return Error.READER_NO_CARD;
        }
        try {
            result = mReader.setPower(Reader.CCID_POWERON);
        } catch (Exception e) {
            Log.e(TAG, "PowerON Get Exception : " + e.getMessage());
        }
        return result;
    }

    private int poweroff() {
        int result = Error.READER_SUCCESSFUL;
        Log.d(TAG, "poweroff");
        if (getSlotStatus() == Error.READER_NO_CARD) {
            mTextViewResult.setText("Card Absent");
            Log.d(TAG, "Card Absent");
            return Error.READER_NO_CARD;
        }
        //----------poweroff card------------------
        try {
            result = mReader.setPower(Reader.CCID_POWEROFF);
        } catch (Exception e) {
            Log.e(TAG, "PowerOFF Get Exception : " + e.getMessage());
        }
        return result;
    }

    public void cleanText() {
        mTextViewResult.setText("");
        mTextViewReader.setText("");

    }

    private void onCreateButtonSetup() {
        mListButton.setEnabled(true);
        mOpenButton.setEnabled(true);
        mCloseButton.setEnabled(false);
        mConnButton.setEnabled(false);
        mDisconnButton.setEnabled(false);
        mTestButton.setEnabled(false);
        mUIDButton.setEnabled(false);
        mSendAPDUButton.setEnabled(false);
        mSwitchButton.setEnabled(false);
    }

    private void onOpenButtonSetup() {
        mOpenButton.setEnabled(false);
        mCloseButton.setEnabled(true);
        mConnButton.setEnabled(true);
        mDisconnButton.setEnabled(true);
        mTestButton.setEnabled(true);
        mUIDButton.setEnabled(true);
        mSendAPDUButton.setEnabled(true);
    }

    private void onCloseButtonSetup() {
        mOpenButton.setEnabled(true);
        mCloseButton.setEnabled(false);
        mConnButton.setEnabled(false);
        mDisconnButton.setEnabled(false);
        mTestButton.setEnabled(false);
        mUIDButton.setEnabled(false);
        mSwitchButton.setEnabled(false);
        mSendAPDUButton.setEnabled(false);
    }


    private void onDevPermit(UsbDevice dev) {
        mUsbDev = dev;
        try {
            updateViewReader();
            new OpenTask().execute(dev);
        } catch (Exception e) {
            mStrMessage = "Get Exception : " + e.getMessage();
            Log.e(TAG, mStrMessage);
        }
    }


    private void onDetache(Intent intent) {
        UsbDevice udev = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
        if (udev != null) {
            if (udev.equals(mUsbDev)) {
                closeReaderUp();
                closeReaderBottom();
            }
        } else {
            Log.d(TAG, "usb device is null");
        }
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "Broadcast Receiver");

            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            onDevPermit(device);
                        }
                    } else {
                        Log.d(TAG, "Permission denied for device " + device.getDeviceName());
                    }
                }

            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {

                Log.d(TAG, "Device Detached");
                onDetache(intent);

                synchronized (this) {
                    updateReaderList(intent);
                }

            }
        }/*end of onReceive(Context context, Intent intent) {*/
    };

    private boolean isSynReader(UsbDevice udev) {
        if (udev.getVendorId() == 0x1206 &&
                (udev.getProductId() == 0x2107 || udev.getProductId() == 0x2105))
            return true; // sw.added
        return false;
    }

    private int EnumeDev() {
        UsbDevice device = null;
        UsbManager manager = (UsbManager) getActivity().getSystemService(Context.USB_SERVICE);

        HashMap<String, UsbDevice> deviceList = manager.getDeviceList();
        Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
        Log.d(TAG, " EnumeDev");
        mReaderAdapter.clear();
        while (deviceIterator.hasNext()) {
            device = deviceIterator.next();
            Log.d(TAG, " " + Integer.toHexString(device.getVendorId()) + " " + Integer.toHexString(device.getProductId()));
            if (isSynReader(device)) {
                Log.d(TAG, "Found Device");
                mReaderAdapter.add(device.getDeviceName());
            }
        }
        //requestDevPerm();

        return 0;
    }

    private int InitReader() {
        int Status = 0;
        boolean init;//
        Log.d(TAG, "InitReader");
        try {
            init = mMyDev.Init(mManager, mUsbDev);
            if (!init) {
                Log.e(TAG, "Device init fail");
                return -1;
            }
            //	mMyDev.setLog(mContext,true, 0xff);
            mReader = new Reader(mMyDev);
            mReader.setSlot(mSlotNum);

        } catch (Exception e) {

            mStrMessage = "Get Exception : " + e.getMessage();
            Log.e(TAG, mStrMessage);
            return -1;
        }
        return Status;
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

    private String logBuffer(byte[] buffer, int bufferLength) {

        String bufferString = "";
        String dbgString = "";

        for (int i = 0; i < bufferLength; i++) {

            String hexChar = Integer.toHexString(buffer[i] & 0xFF);
            if (hexChar.length() == 1) {
                hexChar = "0" + hexChar;
            }

            if (i % 16 == 0) {
                if (dbgString != "") {
//                    Log.d(LOG_TAG, dbgString);
                    bufferString += dbgString;
                    dbgString = "";
                }
            }

            dbgString += hexChar.toUpperCase() + " ";
        }

        if (dbgString != "") {
//        	Log.d(LOG_TAG, dbgString);
            bufferString += dbgString;
        }

        return bufferString;
    }
}
