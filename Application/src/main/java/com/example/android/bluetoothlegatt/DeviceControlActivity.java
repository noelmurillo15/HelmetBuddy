package com.example.android.bluetoothlegatt;

import android.app.Activity;
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
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ExpandableListView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/** For a given BLE device, this Activity provides the user interface to connect, display data,
 * and display GATT services and characteristics supported by the device.  The Activity
 * communicates with {@code BluetoothLeService}, which in turn interacts with the
 * Bluetooth LE API */
public class DeviceControlActivity extends Activity {
    final static String TAG = DeviceControlActivity.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    /** Popup UI    */
    TextView mPopupConnectionState;
    //TextView mPopupDataField;
    //TextView mPopupDeviceName;

    /** Lock Status */
    boolean locked = false;
    boolean connected = false;
    boolean toggleSettings;

    /** Popup Window Buttons    */
    Button unlocklock;
    Button popupclose;
    Button popup_settings;

    //TextView mConnectionState;
    //TextView mDataField;

    TextView mTextViewDeviceName;
    TextView mTextViewDeviceAddress;

    String mDeviceName;
    String mDeviceAddress;
    ExpandableListView mGattServicesList;
    BluetoothLeService mBluetoothLeService;
    ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics = new ArrayList<>();
    boolean mConnected = false;
    BluetoothGattCharacteristic mNotifyCharacteristic;

    PopupWindow popupWindow;
    View popupView;

    final String LIST_NAME = "NAME";
    final String LIST_UUID = "UUID";


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        System.out.println("*****   DeviceControlActivity::onCreate CALLED!   *****");

        setContentView(R.layout.gatt_services_characteristics);
        getActionBar().hide();
        getWindow().getDecorView().setBackground(getResources().getDrawable(R.drawable.spinbg));

        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        mGattServicesList = findViewById(R.id.gatt_services_list);
        mGattServicesList.setOnChildClickListener(servicesListClickListner);

        toggleSettings = false;

        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        System.out.println("*****   DeviceControlActivity::onResume CALLED!   *****");
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
    }

    /** Code to manage Service lifecycle */
    final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            System.out.println("*****   DeviceControlActivity::onServiceConnected CALLED!   *****");
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            /** Automatically connects to the device upon successful start-up initialization */
            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            System.out.println("*****   DeviceControlActivity::onServiceDisconnected CALLED!   *****");
            mBluetoothLeService = null;
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
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                System.out.println("*****   Creating the popup window");
                if (popupWindow == null) {
                    showPopUp(DeviceControlActivity.this);
                }
                mConnected = true;
                updateConnectionState(R.string.connected);
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                if (popupWindow != null) {
                    popupWindow.dismiss();
                    popupWindow = null;
                    System.out.println("*****   Dismissing the popup window");
                }
                mConnected = false;
                updateConnectionState(R.string.disconnected);
                invalidateOptionsMenu();
                clearUI();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                /** Show all the supported services and characteristics on the user interface   */
                displayGattServices(mBluetoothLeService.getSupportedGattServices());
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                displayData(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
            }
        }
    };

    /** If a given GATT characteristic is selected, check for supported features.  This sample
    demonstrates 'Read' and 'Notify' features.  */
    final ExpandableListView.OnChildClickListener servicesListClickListner =
            new ExpandableListView.OnChildClickListener() {
                @Override
                public boolean onChildClick(ExpandableListView parent, View v, int groupPosition,
                                            int childPosition, long id) {
                    System.out.println("*****   DeviceControlActivity::onChildClick CALLED!   *****");
                    if (mGattCharacteristics != null) {
                        final BluetoothGattCharacteristic characteristic =
                                mGattCharacteristics.get(groupPosition).get(childPosition);
                        final int charaProp = characteristic.getProperties();
                        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
                            /** If there is an active notification on a characteristic, clear
                                it first so it doesn't update the data field on the user interface   */
                            if (mNotifyCharacteristic != null) {
                                mBluetoothLeService.setCharacteristicNotification(
                                        mNotifyCharacteristic, false);
                                mNotifyCharacteristic = null;
                            }
                            mBluetoothLeService.readCharacteristic(characteristic);
                        }
                        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                            mNotifyCharacteristic = characteristic;
                            mBluetoothLeService.setCharacteristicNotification(
                                    characteristic, true);
                        }
                        return true;
                    }
                    return false;
                }
            };

    void clearUI() {
        System.out.println("*****   DeviceControlActivity::clearUI CALLED!   *****");
        mGattServicesList.setAdapter((SimpleExpandableListAdapter) null);
//        mDataField.setText(R.string.no_data);

//        if (mPopupServicesList != null)
//            mPopupServicesList.setAdapter((SimpleExpandableListAdapter) null);

//        if(mPopupDataField != null)
//            mPopupDataField.setText("0");
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
                        SetLock(true);
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
//            mDataField.setText(data);
//            if(mPopupDataField != null)
//                mPopupDataField.setText(data);
        }
    }

    /** Demonstrates how to iterate through the supported GATT Services/Characteristics.
    In this sample, we populate the data structure that is bound to the ExpandableListView
    on the UI    */
    void displayGattServices(List<BluetoothGattService> gattServices) {
        System.out.println("*****   DeviceControlActivity::displayGattServices CALLED!   *****");
        if (gattServices == null) return;
        String uuid;
        String unknownServiceString = getResources().getString(R.string.unknown_service);
        String unknownCharaString = getResources().getString(R.string.unknown_characteristic);
        ArrayList<HashMap<String, String>> gattServiceData = new ArrayList<>();
        ArrayList<ArrayList<HashMap<String, String>>> gattCharacteristicData
                = new ArrayList<>();
        mGattCharacteristics = new ArrayList<>();

        /** Loops through available GATT Services    */
        for (BluetoothGattService gattService : gattServices) {
            HashMap<String, String> currentServiceData = new HashMap<>();
            uuid = gattService.getUuid().toString();
            currentServiceData.put(
                    LIST_NAME, SampleGattAttributes.lookup(uuid, unknownServiceString));
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
                charas.add(gattCharacteristic);
                HashMap<String, String> currentCharaData = new HashMap<>();
                uuid = gattCharacteristic.getUuid().toString();
                currentCharaData.put(
                        LIST_NAME, SampleGattAttributes.lookup(uuid, unknownCharaString));
                currentCharaData.put(LIST_UUID, uuid);
                gattCharacteristicGroupData.add(currentCharaData);
            }
            mGattCharacteristics.add(charas);
            gattCharacteristicData.add(gattCharacteristicGroupData);
        }

        SimpleExpandableListAdapter gattServiceAdapter = new SimpleExpandableListAdapter(
                this,
                gattServiceData,
                android.R.layout.simple_expandable_list_item_2,
                new String[]{LIST_NAME, LIST_UUID},
                new int[]{android.R.id.text1, android.R.id.text2},
                gattCharacteristicData,
                android.R.layout.simple_expandable_list_item_2,
                new String[]{LIST_NAME, LIST_UUID},
                new int[]{android.R.id.text1, android.R.id.text2}
        );
        mGattServicesList.setAdapter(gattServiceAdapter);
//        if (mPopupServicesList != null)
//            mPopupServicesList.setAdapter(gattServiceAdapter);
    }

    static IntentFilter makeGattUpdateIntentFilter() {
        System.out.println("*****   DeviceControlActivity::makeGattUpdateIntentFilter CALLED!   *****");
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
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
            popupView = inflater.inflate(R.layout.popupwindow, viewGroup);

            /** Window Size    */
            int width = LinearLayout.LayoutParams.MATCH_PARENT;
            int height = LinearLayout.LayoutParams.WRAP_CONTENT;

            /** Create & Show Popup window  */
            popupWindow = new PopupWindow(popupView, width, height, false);
            setContentView(R.layout.popupwindow);

            popup_settings = findViewById(R.id.popup_settings_button);
            mTextViewDeviceName = findViewById(R.id.popup_device_name);
            mTextViewDeviceAddress = findViewById(R.id.popup_device_address);

            popup_settings.setVisibility(View.VISIBLE);
            mTextViewDeviceName.setVisibility(View.INVISIBLE);
            mTextViewDeviceAddress.setVisibility(View.INVISIBLE);

            /** Set Settings button onClick Function   */
            popup_settings.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    System.out.println("*****   POPUP SETTINGS BUTTON HAS BEEN CLICKED");
                    toggleSettings = !toggleSettings;
                    if (toggleSettings) {
                        mTextViewDeviceName.setVisibility(View.VISIBLE);
                        mTextViewDeviceAddress.setVisibility(View.VISIBLE);
                        mTextViewDeviceName.setText("Device Name : " + mDeviceName);
                        mTextViewDeviceAddress.setText("Device Name : " + mDeviceAddress);
                    } else {
                        mTextViewDeviceName.setVisibility(View.INVISIBLE);
                        mTextViewDeviceAddress.setVisibility(View.INVISIBLE);
                    }
                }
            });
        }

        /** Popup UI references */
        mPopupConnectionState = findViewById(R.id.popup_connection_state);
        unlocklock = findViewById(R.id.UnlockLock);
        popupclose = findViewById(R.id.close_popup_window);

        locked = false;
        ToggleLock();

        /** Update UI   */
        try {   /** Unlock Button   */
            ((TextView) findViewById(R.id.UnlockLock)).setText(getResources().getString(R.string.unlock_this_helmet));
        } catch (Exception e) {
            System.out.println("*****   Failed to find Unlock button by ID");
        }

        /** Setup Button onClick Listeners  */
        unlocklock.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ToggleLock();
            }
        });
        popupclose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                popupWindow.dismiss();
                onBackPressed();
            }
        });

//        mPopupServicesList = findViewById(R.id.popup_gatt_services_list);
//        if (mPopupServicesList != null)
//            mPopupServicesList.setOnChildClickListener(servicesListClickListner);
//        else
//            System.out.println("*****   Popup Window Gatt Services List ExpandableListView is NULL");

//        mPopupDeviceName = findViewById(R.id.popup_device_address);
//        mPopupDataField = findViewById(R.id.popup_data_value);
    }

    /** Changes the Lock/Unlock Button text*/
    void onLockChange(){
        if(locked){
            ((TextView) findViewById(R.id.UnlockLock)).setText(getResources().getString(R.string.unlock_this_helmet));
        }
        else{
            ((TextView) findViewById(R.id.UnlockLock)).setText(getResources().getString(R.string.lock_this_helmet));
        }
    }

    /** Sets the lock bool  */
    void SetLock(boolean _b){
        locked = _b;
        onLockChange();
    }

    /** Toggles the lock bool   */
    void ToggleLock(){
        locked = !locked;
        if(!connected)
            locked = true;

        onLockChange();
    }
}