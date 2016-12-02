package com.example.smartcardapp.fragment;

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
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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

import com.example.smartcardapp.R;
import com.example.smartcardapp.adpater.CardReaderAdapter;
import com.example.smartcardapp.cardreader.CL2100Reader;
import com.example.smartcardapp.manager.CardReaderManager;

import java.util.Iterator;

import static android.hardware.usb.UsbManager.ACTION_USB_DEVICE_ATTACHED;
import static com.example.smartcardapp.manager.CardReaderManager.getCardReaderManager;

public class MainFragment extends Fragment {
    private static final String TAG = "Synnix-Test";
    private UsbDevice mUsbDev;
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
    private CardReaderAdapter mReaderAdapter;
    private TextView mTextViewReader;

    private TextView mTextViewRAPDU;

    private TextView mTextViewResult;
    private EditText mEditTextApdu;
    private ProgressDialog mCloseProgress;

    private Spinner mModeSpinner;
    private Spinner mReaderSpinner;
    private ArrayAdapter<String> mModeList;

    private String mStrMessage;
    private final String mode2 = "I2c Mode";
    private final String mode3 = "SLE4428 Mode";
    private final byte DEFAULT_SN_LEN = 32;
    private Context mContext;
    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                final UsbDevice usbD = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                ((UsbManager) context.getSystemService(Context.USB_SERVICE)).requestPermission(usbD, mPermissionIntent);
            }

            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    final UsbDevice usbD = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (usbD != null) {
                            final CL2100Reader newcardreader = CL2100Reader.fromUsbDeviceInfo(usbD);
                            if (newcardreader != null) {
                                getCardReaderManager().addCardReader(newcardreader);
                            }
                        }
                    } else {
                        Log.d(TAG, "Permission denied for device " + usbD.getDeviceName());
                    }
                }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                Log.d(TAG, "Device Detached");
                final UsbDevice udev = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                Log.d(TAG, "Device" + udev.getProductId() + "Detached");
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View rootview = inflater.inflate(R.layout.fragment_main, container, false);
        setupViews(rootview);
        mSlotNum = (byte) 0;
        mContext = container.getContext();
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

        mModeList = new ArrayAdapter<String>(mContext, android.R.layout.simple_spinner_item, pMode);

        mModeList.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mModeSpinner.setAdapter(mModeList);
        setupReaderSpinner(view);
        setReaderSlotView();
    }

    private void toRegisterReceiver() {
        mPermissionIntent = PendingIntent.getBroadcast(getContext(), 0, new Intent(ACTION_USB_PERMISSION), 0);
        final IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        getActivity().registerReceiver(mReceiver, filter);
    }

    private void toCheckInUSB() {
        final UsbManager usbmanager = (UsbManager) getContext().getSystemService(Context.USB_SERVICE);
        final Iterator<UsbDevice> usbdevicelists = usbmanager.getDeviceList().values().iterator();
        while (usbdevicelists.hasNext()) {
            usbmanager.requestPermission(usbdevicelists.next(), mPermissionIntent);
        }
    }

    private void setupReaderSpinner(View view) {
        // Initialize reader spinner
        mReaderAdapter = new CardReaderAdapter();
        mReaderSpinner = (Spinner) view.findViewById(R.id.spinnerDevice);
        mReaderSpinner.setAdapter(mReaderAdapter);
        mReaderSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {

            @Override
            public void onItemSelected(AdapterView<?> arg0, View arg1, int position, long arg3) {
            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0) {
            }
        });
    }

    private void setReaderSlotView() {
        final String[] arraySlot = new String[]{"slot:0", "Slot:1"};
        mSlotDialog = new AlertDialog.Builder(mContext);
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

    private UsbDevice getSpinnerSelect() {
        String deviceName;
        deviceName = (String) mReaderSpinner.getSelectedItem();
        if (deviceName != null) {
            // For each device
            for (UsbDevice device : ((UsbManager) mContext.getSystemService(Context.USB_SERVICE)).getDeviceList().values()) {
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
            ((UsbManager) mContext.getSystemService(Context.USB_SERVICE)).requestPermission(dev, mPermissionIntent);
        else
            Log.e(TAG, "selected not found");
    }

    public void ListOnClick(View view) {
        Log.d(TAG, "ListOnClick");
        final CardReaderManager manager = CardReaderManager.getCardReaderManager();
        final int count = manager.getNFCCardReaderCount();
        for (int i = 0; i < count; i++) {
            final CL2100Reader cardreader = manager.getNFCCardReaderByIndex(i);
            cardreader.initReader(new CL2100Reader.OnCardReaderResultListener(new Handler(Looper.getMainLooper())) {
                @Override
                public void onReadSuccess(CL2100Reader cardreader, byte[] result) {
                    onOpenButtonSetup();
                }

                @Override
                public void onReadFailed(CL2100Reader cardreader, byte[] result, Exception ex) {

                }
            }, false);
        }
    }

    public void OpenOnClick(View view) {
        Log.d(TAG, "OpenOnClick");
//        UsbDevice dev = getSpinnerSelect();
//        if (dev != null)
//            checkSlotNumber(dev);
    }

    public void ConnOnClick(View view) { //FIXME　poweron()
        Log.d(TAG, "ConnOnClick");
        final CardReaderManager manager = CardReaderManager.getCardReaderManager();
        final int count = manager.getNFCCardReaderCount();
        for (int i = 0; i < count; i++) {
            final CL2100Reader cardreader = manager.getNFCCardReaderByIndex(i);
            cardreader.powerOn(new CL2100Reader.OnCardReaderResultListener(new Handler(Looper.getMainLooper())) {
                @Override
                public void onReadSuccess(CL2100Reader cardreader, byte[] result) {
                    mTextViewResult.setText(" ATR:" + new String(result));
                }

                @Override
                public void onReadFailed(CL2100Reader cardreader, byte[] result, Exception ex) {
                    mTextViewResult.setText(new String(result));
                }
            }, false);
        }
    }

    public void DisconnOnClick(View view) { //FIXME poweroff()
        Log.d(TAG, "DisconnOnClick");
        final TextView textViewResult = (TextView) getView().findViewById(R.id.textResult);
        final CardReaderManager manager = CardReaderManager.getCardReaderManager();
        final int count = manager.getNFCCardReaderCount();
        for (int i = 0; i < count; i++) {
            final CL2100Reader cardreader = manager.getNFCCardReaderByIndex(i);
            cardreader.powerOff(new CL2100Reader.OnCardReaderResultListener(new Handler(Looper.getMainLooper())) {
                @Override
                public void onReadSuccess(CL2100Reader cardreader, byte[] result) {
                    textViewResult.setText(new String(result));
                }

                @Override
                public void onReadFailed(CL2100Reader cardreader, byte[] result, Exception ex) {
                    textViewResult.setText(new String(result));
                }
            }, false);
        }
    }

    public void TestOnClick(View view) {
        Log.d(TAG, "TestOnClick");
        final CardReaderManager manager = CardReaderManager.getCardReaderManager();
        final int count = manager.getNFCCardReaderCount();
        for (int i = 0; i < count; i++) {
            final CL2100Reader cardreader = manager.getNFCCardReaderByIndex(i);
            cardreader.selfTest(new CL2100Reader.OnCardReaderResultListener(new Handler(Looper.getMainLooper())) {
                @Override
                public void onReadSuccess(CL2100Reader cardreader, byte[] result) {

                }

                @Override
                public void onReadFailed(CL2100Reader cardreader, byte[] result, Exception ex) {

                }
            }, false);
        }
    }

    public void UIDOnClick(View view) {
        Log.d(TAG, "UIDOnClick");
        final CardReaderManager manager = CardReaderManager.getCardReaderManager();
        final int count = manager.getNFCCardReaderCount();
        for (int i = 0; i < count; i++) {
            final CL2100Reader cardreader = manager.getNFCCardReaderByIndex(i);
            cardreader.powerOnWithScan(new CL2100Reader.OnCardReaderResultListener(new Handler(Looper.getMainLooper())) {
                @Override
                public void onReadSuccess(CL2100Reader cardreader, byte[] result) {
                    resolveCardID("unknow", result);
                }

                @Override
                public void onReadFailed(CL2100Reader cardreader, byte[] result, Exception ex) {

                }
            }, false);
        }
    }

    public void APDUOnClick(View view) {
        Log.d(TAG, "APDUOnClick");
//        sendAPDU("");
    }

    public void CloseOnClick(View view) {
        mTextViewRAPDU.setText("");
//        new CloseTask().execute();
    }

    private void setUpCloseDialog() {
        mCloseProgress = new ProgressDialog(mContext);
        mCloseProgress.setMessage("Closing Reader");
        mCloseProgress.setCancelable(false);
        mCloseProgress.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        mCloseProgress.show();
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

    public void resolveCardID(String cardtype, final byte[] id) {
        mTextViewRAPDU.setText(logBuffer(id, 1));
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
                    bufferString += dbgString;
                    dbgString = "";
                }
            }
            dbgString += hexChar.toUpperCase() + " ";
        }

        if (dbgString != "") {
            bufferString += dbgString;
        }

        return bufferString;
    }

    @Override
    public void onResume() {
        mReaderAdapter.startMonitor();
        super.onResume();
        toRegisterReceiver();
        toCheckInUSB();
    }

    @Override
    public void onPause() {
        mReaderAdapter.stopMonitor();
        getActivity().unregisterReceiver(mReceiver);
        super.onPause();
    }
}
