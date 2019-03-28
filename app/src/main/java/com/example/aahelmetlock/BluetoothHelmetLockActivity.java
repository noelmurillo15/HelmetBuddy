package com.example.aahelmetlock;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.RelativeLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


public class BluetoothHelmetLockActivity extends Activity {

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    /** Popup UI    */
    View popupView;
    PopupWindow popupWindow;
    LinearLayout popup_settings_window;

    TextView mTextViewDeviceName;
    TextView mPopupConnectionState;
    TextView mTextViewDeviceAddress;
    TextView mDataField;

    Button unlock_lock;
    Button popup_close;
    Button popup_settings;

    RelativeLayout progressCircle;

    /** Lock Status */
    boolean locked = false;
    boolean connected = false;
    boolean toggleSettings;

    /** Bluetooth Device vars*/
    String mDeviceName;
    String mDeviceAddress;
    BluetoothLeGattService mBluetoothLeService;
    ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics = new ArrayList<>();
    boolean mConnected = false;

    BluetoothGattCharacteristic mNotifyWrite;
    BluetoothGattCharacteristic mHelmetLockWrite;
    BluetoothGattCharacteristic mNotifyRead;
    BluetoothGattCharacteristic mHelmetLockRead;

    final String LIST_UUID = "UUID";



    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        System.out.println("*****   DeviceControlActivity::onCreate CALLED!   *****");

        setContentView(R.layout.gatt_services_characteristics);
        getWindow().getDecorView().setBackground(getResources().getDrawable(R.drawable.spinbg));

        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);
        toggleSettings = false;

        Intent gattServiceIntent = new Intent(this, BluetoothLeGattService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        System.out.println("*****   DeviceControlActivity::onResume CALLED!   *****");
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            System.out.println("*   Connect request result=" + result);
        }

        if(progressCircle == null)
            progressCircle = findViewById(R.id.loadingPanel);
        if (progressCircle != null){
            System.out.println("*   Progress Bar is Not NULL");
            progressCircle.setVisibility(View.VISIBLE);
        }
    }

    /** Code to manage Service lifecycle */
    final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            System.out.println("*****   DeviceControlActivity::onServiceConnected CALLED!   *****");
            mBluetoothLeService = ((BluetoothLeGattService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                System.out.println("*****   SYSTEM FINISH::onServiceConnected - Unable to initialize Bluetooth *****");
                finish();
            }
            /** Automatically connects to the device upon successful start-up initialization */
            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            System.out.println("*****   SYSTEM EXIT::onServiceDisconnected *****");
            mBluetoothLeService = null;
            System.exit(0);
        }
    };


    /** Handles various events fired by the Service
     ACTION_GATT_CONNECTED: connected to a GATT server
     ACTION_GATT_DISCONNECTED: disconnected from a GATT server
     ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services
     ACTION_DATA_AVAILABLE: received data from the device
     This can be a result of read or notification operations    */
    final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            System.out.println("*****   DeviceControlActivity::onReceive CALLED!   *****");
            final String action = intent.getAction();
            if (BluetoothLeGattService.ACTION_GATT_CONNECTED.equals(action)) {
                if (popupWindow == null) {
                    showPopUp(BluetoothHelmetLockActivity.this);
                }
                mConnected = true;
                updateConnectionState(R.string.connected);
                invalidateOptionsMenu();
            } else if (BluetoothLeGattService.ACTION_GATT_DISCONNECTED.equals(action)) {
                if (popupWindow != null) {
                    popupWindow.dismiss();
                    popupWindow = null;
                }
                mConnected = false;
                updateConnectionState(R.string.disconnected);
                invalidateOptionsMenu();
                clearUI();
                System.out.println("*****   SYSTEM EXIT::onBluetoothActionDisconnected *****");
                System.exit(0);
            } else if (BluetoothLeGattService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                /** Show all the supported services and characteristics on the user interface   */
                displayGattServices(mBluetoothLeService.getSupportedGattServices());
            } else if (BluetoothLeGattService.ACTION_DATA_AVAILABLE.equals(action)) {
                displayData(intent.getStringExtra(BluetoothLeGattService.EXTRA_DATA));
            } else if(BluetoothLeGattService.EXTRA_DATA.equals(action)){
                System.out.println("*   EXTRA DATA AVAILABLE    ");
                displayData(intent.getStringExtra(BluetoothLeGattService.EXTRA_DATA));
            }

            if(BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())){
                if(intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1) == BluetoothAdapter.STATE_OFF){
                    System.out.println("*   SYSTEM EXIT::onBluetoothDisabled    ");
                    System.exit(0);
                }
            }
        }
    };

    void clearUI() {
        System.out.println("*****   DeviceControlActivity::clearUI CALLED!   *****");
        if(mDataField != null)
            mDataField.setText(R.string.no_data);
    }

    @Override
    protected void onPause() {
        super.onPause();
        System.out.println("*****   DeviceControlActivity::onPause CALLED!   *****");
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        System.out.println("*****   DeviceControlActivity::onDestroy CALLED!   *****");
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    void updateConnectionState(final int resourceId) {
        System.out.println("*****   DeviceControlActivity::updateConnectionState CALLED!   *****");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mPopupConnectionState != null){
                    mPopupConnectionState.setText(resourceId);
                    if(mPopupConnectionState.getText() == getResources().getString(R.string.disconnected)){
                        setLock(true);
                        connected = false;
                    }
                    else{
                        connected = true;
                    }
                }
            }
        });
    }

    /** Displays the data from Gatt Characteristic  */
    void displayData(String data) {
        System.out.println("*****   DeviceControlActivity::displayData CALLED!   *****");
        if (data != null) {
            mDataField.setText(data);
        }
    }

    /** Demonstrates how to iterate through the supported GATT Services/Characteristics.
     In this sample, we populate the data structure that is bound to the ExpandableListView
     on the UI    */
    void displayGattServices(List<BluetoothGattService> gattServices) {
        System.out.println("*****   DeviceControlActivity::displayGattServices CALLED!   *****");
        if (gattServices == null) return;
        if(progressCircle == null)
            progressCircle = findViewById(R.id.loadingPanel);
        if (progressCircle != null){
            System.out.println("*   Progress Bar is Not NULL");
            progressCircle.setVisibility(View.GONE);
        }
        String uuid;
        ArrayList<HashMap<String, String>> gattServiceData = new ArrayList<>();
        ArrayList<ArrayList<HashMap<String, String>>> gattCharacteristicData
                = new ArrayList<>();
        mGattCharacteristics = new ArrayList<>();

        /** Loops through available GATT Services    */
        for (BluetoothGattService gattService : gattServices) {
            HashMap<String, String> currentServiceData = new HashMap<>();
            uuid = gattService.getUuid().toString();
            currentServiceData.put(LIST_UUID, uuid);
            gattServiceData.add(currentServiceData);

            ArrayList<HashMap<String, String>> gattCharacteristicGroupData =
                    new ArrayList<>();
            List<BluetoothGattCharacteristic> gattCharacteristics =
                    gattService.getCharacteristics();
            ArrayList<BluetoothGattCharacteristic> charas =
                    new ArrayList<>();

            /** Loops through available Characteristics  */
            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                if(mBluetoothLeService.UUID_HELMET_LOCK_WRITE.equals(gattCharacteristic.getUuid())) {
                    mHelmetLockWrite = gattCharacteristic;
                    if (mNotifyWrite != null) {
                        mBluetoothLeService.setCharacteristicNotification(mNotifyWrite, false);
                        mNotifyWrite = null;
                    }
                    mNotifyWrite = mHelmetLockWrite;
                    mBluetoothLeService.setCharacteristicNotification(mNotifyWrite, true);
                }

                charas.add(gattCharacteristic);
                HashMap<String, String> currentCharaData = new HashMap<>();
                uuid = gattCharacteristic.getUuid().toString();
                currentCharaData.put(LIST_UUID, uuid);
                gattCharacteristicGroupData.add(currentCharaData);
            }
            mGattCharacteristics.add(charas);
            gattCharacteristicData.add(gattCharacteristicGroupData);
        }
    }

    static IntentFilter makeGattUpdateIntentFilter() {
        System.out.println("*****   DeviceControlActivity::makeGattUpdateIntentFilter CALLED!   *****");
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeGattService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeGattService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeGattService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeGattService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    /** Initializes the popup window if it has never been created
     & finds the references for the UI   */
    public void showPopUp(Context context) {
        System.out.println("*****   DeviceControlActivity::showPopUp CALLED!   *****");
        if (popupWindow == null) {
            /** Popup View    */
            LinearLayout viewGroup = findViewById(R.id.popup);
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(LAYOUT_INFLATER_SERVICE);
            popupView = inflater.inflate(R.layout.popup_window, viewGroup);

            /** Window Size    */
            int width = LinearLayout.LayoutParams.MATCH_PARENT;
            int height = LinearLayout.LayoutParams.WRAP_CONTENT;

            /** Create & Show Popup window  */
            popupWindow = new PopupWindow(popupView, width, height, false);
            setContentView(R.layout.popup_window);

            popup_settings = findViewById(R.id.popup_settings_button);
            popup_settings_window = findViewById(R.id.info_popup_window);
            mTextViewDeviceName = findViewById(R.id.device_name);
            mTextViewDeviceAddress = findViewById(R.id.device_address);
            mDataField = findViewById(R.id.device_data);
            mDataField.setText("0");

            popup_settings_window.setVisibility(View.GONE);

            /** Set Settings button onClick Function   */
            popup_settings.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    System.out.println("*****   POPUP SETTINGS BUTTON HAS BEEN CLICKED");
                    toggleSettings = !toggleSettings;
                    if (toggleSettings) {
                        popup_settings_window.setVisibility(View.VISIBLE);
                        mTextViewDeviceName.setText(" " + mDeviceName);
                        mTextViewDeviceAddress.setText(" " + mDeviceAddress);
                    } else {
                        popup_settings_window.setVisibility(View.GONE);
                    }
                }
            });
        }

        /** Popup UI references */
        mPopupConnectionState = findViewById(R.id.DisconnectedText);
        unlock_lock = findViewById(R.id.UnlockLock);
        popup_close = findViewById(R.id.close_popup_window);
        locked = false;
        toggleLock();

        /** Setup Button onClick Listeners  */
        unlock_lock.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleLock();
            }
        });
        popup_close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                popupWindow.dismiss();
                onBackPressed();
            }
        });

        /** Update UI   */
        try {   /** Unlock Button   */
            ((TextView) findViewById(R.id.UnlockLock)).setText(getResources().getString(R.string.unlock_this_helmet));
        } catch (Exception e) {
//            System.out.println("*****   Failed to find Unlock button by ID");
        }
    }

    /** Changes the Lock/Unlock Button text*/
    void onLockChange(){
//        System.out.println("*****   DeviceControlActivity::onLockChange");
        if(locked){
            ((TextView) findViewById(R.id.UnlockLock)).setText(getResources().getString(R.string.unlock_this_helmet));
            if(mNotifyWrite != null) {
                mBluetoothLeService.writeCharacteristic(mNotifyWrite, "0");
                mDataField.setText("Locked");
            }
        }
        else{
            ((TextView) findViewById(R.id.UnlockLock)).setText(getResources().getString(R.string.lock_this_helmet));
            if(mNotifyWrite != null) {
                mBluetoothLeService.writeCharacteristic(mNotifyWrite, "1");
                mDataField.setText("Unlocked");
            }
        }
    }

    /** Sets the lock bool  */
    void setLock(boolean _b){
//        System.out.println("*****   DeviceControlActivity::setLock");
        locked = _b;
        onLockChange();
    }

    /** Toggles the lock bool   */
    void toggleLock(){
//        System.out.println("*****   DeviceControlActivity::toggleLock");
        locked = !locked;
        if(!connected)
            locked = true;

        onLockChange();
    }
}
