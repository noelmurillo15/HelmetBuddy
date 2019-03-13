/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ExpandableListView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * For a given BLE device, this Activity provides the user interface to connect, display data,
 * and display GATT services and characteristics supported by the device.  The Activity
 * communicates with {@code BluetoothLeService}, which in turn interacts with the
 * Bluetooth LE API.
 */
public class DeviceControlActivity extends Activity {
    final static String TAG = DeviceControlActivity.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    //  Popup UI
    TextView mPopupConnectionState;
    //TextView mPopupDataField;
    //TextView mPopupDeviceName;
    ExpandableListView mPopupServicesList;

    //    TextView mConnectionState;
//    TextView mDataField;
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

    // Code to manage Service lifecycle.
    final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            System.out.println("*****   DeviceControlActivity::onServiceConnected CALLED!   *****");
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            System.out.println("*****   DeviceControlActivity::onServiceDisconnected CALLED!   *****");
            mBluetoothLeService = null;
        }
    };

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
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
                // Show all the supported services and characteristics on the user interface.
                displayGattServices(mBluetoothLeService.getSupportedGattServices());
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                displayData(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
            }
        }
    };

    // If a given GATT characteristic is selected, check for supported features.  This sample
    // demonstrates 'Read' and 'Notify' features.  See
    // http://d.android.com/reference/android/bluetooth/BluetoothGatt.html for the complete
    // list of supported characteristic features.
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
                            // If there is an active notification on a characteristic, clear
                            // it first so it doesn't update the data field on the user interface.
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

        if (mPopupServicesList != null)
            mPopupServicesList.setAdapter((SimpleExpandableListAdapter) null);

//        if(mPopupDataField != null)
//            mPopupDataField.setText("0");
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        System.out.println("*****   DeviceControlActivity::onCreate CALLED!   *****");

        setContentView(R.layout.gatt_services_characteristics);
        getWindow().getDecorView().setBackground(getResources().getDrawable(R.drawable.spinbg));

        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        mGattServicesList = findViewById(R.id.gatt_services_list);
        mGattServicesList.setOnChildClickListener(servicesListClickListner);

        getActionBar().setTitle(mDeviceName);
        getActionBar().setDisplayHomeAsUpEnabled(true);
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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        System.out.println("*****   DeviceControlActivity::onCreateOptionsMenu CALLED!   *****");
        getMenuInflater().inflate(R.menu.gatt_services, menu);
        if (mConnected) {
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(true);
        } else {
            menu.findItem(R.id.menu_connect).setVisible(true);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        System.out.println("*****   DeviceControlActivity::onOptionsItemSelected CALLED!   *****");
        switch (item.getItemId()) {
            case R.id.menu_connect:
                mBluetoothLeService.connect(mDeviceAddress);
                return true;
            case R.id.menu_disconnect:
                mBluetoothLeService.disconnect();
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    void updateConnectionState(final int resourceId) {
        System.out.println("*****   DeviceControlActivity::updateConnectionState CALLED!   *****");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
//                mConnectionState.setText(resourceId);
                if (mPopupConnectionState != null)
                    mPopupConnectionState.setText(resourceId);
            }
        });
    }

    void displayData(String data) {
        System.out.println("*****   DeviceControlActivity::displayData CALLED!   *****");
        if (data != null) {
//            mDataField.setText(data);
//            if(mPopupDataField != null)
//                mPopupDataField.setText(data);
        }
    }

    // Demonstrates how to iterate through the supported GATT Services/Characteristics.
    // In this sample, we populate the data structure that is bound to the ExpandableListView
    // on the UI.
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

        // Loops through available GATT Services.
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

            // Loops through available Characteristics.
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
        if (mPopupServicesList != null)
            mPopupServicesList.setAdapter(gattServiceAdapter);
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

    public void showPopUp(Context context) {
        System.out.println("*****   DeviceControlActivity::showPopUp CALLED!   *****");
        if (popupWindow == null) {
            //  View
            LinearLayout viewGroup = findViewById(R.id.popup);
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(LAYOUT_INFLATER_SERVICE);
            popupView = inflater.inflate(R.layout.popupwindow, viewGroup);

            //  Size
            int width = LinearLayout.LayoutParams.MATCH_PARENT;
            int height = LinearLayout.LayoutParams.WRAP_CONTENT;

            //  Create & Show Popup window
            popupWindow = new PopupWindow(popupView, width, height, false);
            setContentView(R.layout.popupwindow);
        }

        //  Popup UI references
        mPopupServicesList = findViewById(R.id.popup_gatt_services_list);
        if (mPopupServicesList != null)
            mPopupServicesList.setOnChildClickListener(servicesListClickListner);
        else
            System.out.println("*****   Popup Window Gatt Services List ExpandableListView is NULL");

        mPopupConnectionState = findViewById(R.id.popup_connection_state);
//        mPopupDeviceName = findViewById(R.id.popup_device_address);
//        mPopupDataField = findViewById(R.id.popup_data_value);

        //  Update UI
        try {   //  Unlock Button
            ((TextView) findViewById(R.id.UnlockLock)).setText("Unlock Helmet");
        } catch (Exception e) {
            System.out.println("*****   Failed to change Unlock button text");
        }
//        try { //  Settings Button
//            ((TextView) findViewById(R.id.Settings)).setText("My Settings");
//        } catch (Exception e){
//            System.out.println("*****   Failed to change Settings button text");
//        }

        if (mPopupConnectionState != null)
            mPopupConnectionState.setText("Connected");
        else
            System.out.println("*****   Popup Window Connection State TextView is NULL");

//        if(mPopupDeviceName != null)
//            mPopupDeviceName.setText(mDeviceAddress);
//        else
//            System.out.println("*****   Popup Window Device Name TextView is NULL");

//        if(mPopupDataField != null)
//            mPopupDataField.setText("0");
//        else
//            System.out.println("*****   Popup Window Data Field TextView is NULL");

        //  Touch sense
        popupView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                popupWindow.dismiss();
                onBackPressed();
                return true;
            }
        });
    }
}