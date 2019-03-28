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

	/**	Used to retrieve bluetooth device name and address from BLE Scan activity	*/
    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    /** Popup Window    */
    View mPopupView;
    PopupWindow mPopupWindow;

	/** Popup Window Buttons    */
    Button mUnlockLockButton;
    Button mPopupCloseButton;
    Button popup_settings;

	/** Popup Settings UI    */
    LinearLayout popup_settings_window;
    TextView mTextViewDeviceName;
    TextView mPopupConnectionState;
    TextView mTextViewDeviceAddress;
    TextView mDataField;

	/** Progress Circle	*/
    RelativeLayout mProgressCircle;

    /** Lock Status */
    boolean locked = false;
    boolean connected = false;
    boolean toggleSettings;

    /** Bluetooth Device Variables	*/
    String mDeviceName;
    String mDeviceAddress;
    boolean mConnected = false;
    BluetoothLeGattService mBluetoothLeService;
    ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics = new ArrayList<>();

	/**	BLE Service Characteristic references	*/
    BluetoothGattCharacteristic mNotifyWrite;
    BluetoothGattCharacteristic mHelmetLockWrite;
    BluetoothGattCharacteristic mNotifyRead;
    BluetoothGattCharacteristic mHelmetLockRead;

    final String LIST_UUID = "UUID";



    @Override
    public void onCreate(Bundle savedInstanceState) {
//        System.out.println("*****   DeviceControlActivity::onCreate CALLED!   *****");
        super.onCreate(savedInstanceState);

		/**	Set view to progress circle while app attempts to connectto passed in bluetooth device	*/
        setContentView(R.layout.gatt_services_characteristics);

		/**	Set view's background photo	*/
        getWindow().getDecorView().setBackground(getResources().getDrawable(R.drawable.spinbg));

		/**	Retreieve passed in Bluetooth name and address	*/
        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);
        toggleSettings = false;

		/**	Attempt to bind to Gatt service	*/
        Intent gattServiceIntent = new Intent(this, BluetoothLeGattService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onResume() {
//        System.out.println("*****   DeviceControlActivity::onResume CALLED!   *****");
        super.onResume();

		/**	Register receiver that listens for specific bluetooth Actions	*/
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());

		/**	Attempt to Connect to BLE Service	*/
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            System.out.println("*   Connect request result=" + result);
        }

		/**	Set progress circle visible	*/
        if(mProgressCircle == null)
            mProgressCircle = findViewById(R.id.loadingPanel);
        if (mProgressCircle != null){
            mProgressCircle.setVisibility(View.VISIBLE);
        }
    }

    /** Code to manage Service lifecycle */
    final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
//            System.out.println("*****   DeviceControlActivity::onServiceConnected CALLED!   *****");

			/**	Attempts to Initialize Bluetootj Service	*/
            mBluetoothLeService = ((BluetoothLeGattService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                System.out.println("*****   SYSTEM FINISH::onServiceConnected - Unable to initialize Bluetooth *****");
                finish();
            }

            /** Attempts to connect to the device upon successful start-up initialization */
            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            System.out.println("*****   SYSTEM EXIT::onServiceDisconnected *****");
            mBluetoothLeService = null;
            finish(); System.exit(0);
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
//            System.out.println("*****   DeviceControlActivity::onReceive CALLED!   *****");

			/**	Retreive Action Type*/
            final String action = intent.getAction();

			/**	On successful Gatt Connection	*/
            if (BluetoothLeGattService.ACTION_GATT_CONNECTED.equals(action)) {
                if (mPopupWindow == null) {
                    showPopUp(BluetoothHelmetLockActivity.this);
                }
                mConnected = true;
                updateConnectionState(R.string.connected);
                invalidateOptionsMenu();
            } 
			/**	On unsuccessful Gatt Connection	*/
			else if (BluetoothLeGattService.ACTION_GATT_DISCONNECTED.equals(action)) {
                if (mPopupWindow != null) {
                    mPopupWindow.dismiss();
                    mPopupWindow = null;
                }
                mConnected = false;
                updateConnectionState(R.string.disconnected);
                invalidateOptionsMenu();
                clearUI();
                System.out.println("*****   SYSTEM EXIT::onBluetoothActionDisconnected *****");
                System.exit(0);
            } 
			/**	On successful retrieval of Gatt services	*/
			else if (BluetoothLeGattService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                displayGattServices(mBluetoothLeService.getSupportedGattServices());
            } 
			/**	On successful data transfer	*/
			else if (BluetoothLeGattService.ACTION_DATA_AVAILABLE.equals(action)) {
                displayData(intent.getStringExtra(BluetoothLeGattService.EXTRA_DATA));
            } 
			/**	On successful extra data transfer	*/
			else if(BluetoothLeGattService.EXTRA_DATA.equals(action)){
                displayData(intent.getStringExtra(BluetoothLeGattService.EXTRA_DATA));
            }

			/**	If Bluetooth is turned off, exit application	*/
            if(BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())){
                if(intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1) == BluetoothAdapter.STATE_OFF){
                    System.out.println("*   SYSTEM EXIT::onBluetoothDisabled    ");
                    finish(); System.exit(0);
                }
            }
        }
    };

    void clearUI() {
		/**	Clear all text fields	*/
        if(mDataField != null)
            mDataField.setText(R.string.no_data);
		if(mTextViewDeviceName != null)
			mTextViewDeviceName.setText(R.string.no_data);
		if(mPopupConnectionState != null)
			mPopupConnectionState.setText(R.string.no_data);
		if(mTextViewDeviceAddress != null)
			mTextViewDeviceAddress.setText(R.string.no_data);
    }

    @Override
    protected void onPause() {
//        System.out.println("*****   DeviceControlActivity::onPause CALLED!   *****");
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
//        System.out.println("*****   DeviceControlActivity::onDestroy CALLED!   *****");
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    void updateConnectionState(final int resourceId) {
//        System.out.println("*****   DeviceControlActivity::updateConnectionState CALLED!   *****");
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
//        System.out.println("*****   DeviceControlActivity::displayData CALLED!   *****");
        if (data != null) {
            mDataField.setText(data);
        }
    }

    /** Demonstrates how to iterate through the supported GATT Services/Characteristics.
     In this sample, we populate the data structure that is bound to the ExpandableListView
     on the UI    */
    void displayGattServices(List<BluetoothGattService> gattServices) {
//        System.out.println("*****   DeviceControlActivity::displayGattServices CALLED!   *****");

		/**	Saefty Check	*/
        if (gattServices == null) return;

		/**	Disable Progress Circle	*/
        if(mProgressCircle == null)
            mProgressCircle = findViewById(R.id.loadingPanel);
        if (mProgressCircle != null){
            mProgressCircle.setVisibility(View.GONE);
        }

		/**	START : searching through connected device's GATT services and characteristics	*/
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
		/**	END : searching through connected device's GATT services and characteristics	*/
    }

    static IntentFilter makeGattUpdateIntentFilter() {
//        System.out.println("*****   DeviceControlActivity::makeGattUpdateIntentFilter CALLED!   *****");
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
//        System.out.println("*****   DeviceControlActivity::showPopUp CALLED!   *****");
        if (mPopupWindow == null) {
            /** Popup View    */
            LinearLayout viewGroup = findViewById(R.id.popup);
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(LAYOUT_INFLATER_SERVICE);
            mPopupView = inflater.inflate(R.layout.popup_window, viewGroup);

            /** Window Size    */
            int width = LinearLayout.LayoutParams.MATCH_PARENT;
            int height = LinearLayout.LayoutParams.WRAP_CONTENT;

            /** Create & Show Popup window  */
            mPopupWindow = new PopupWindow(mPopupView, width, height, false);
            setContentView(R.layout.popup_window);

            popup_settings = findViewById(R.id.popup_settings_button);
            popup_settings_window = findViewById(R.id.info_popup_window);
            mTextViewDeviceName = findViewById(R.id.device_name);
            mTextViewDeviceAddress = findViewById(R.id.device_address);
            mDataField = findViewById(R.id.device_data);
            mDataField.setText("Locked");

            popup_settings_window.setVisibility(View.GONE);

            /** Set Settings button onClick Function   */
            popup_settings.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
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
        mUnlockLockButton = findViewById(R.id.UnlockLock);
        mPopupCloseButton = findViewById(R.id.close_popup_window);
        locked = false;
        toggleLock();

        /** Setup Button onClick Listeners  */
        mUnlockLockButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleLock();

				mUnlockLockButton.setEnabled(false);

				Timer buttonTimer = new Timer();
				buttonTimer.schedule(new TimerTask() {
					@Override
					public void run() {
						runOnUiThread(new Runnable() {
							@Override
							public void run() {
								mUnlockLockButton.setEnabled(true);
							}
						});
					}
				}, 1250);
            }
        });
        mPopupCloseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mPopupWindow.dismiss();
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
        locked = _b;
        onLockChange();
    }

    /** Toggles the lock bool   */
    void toggleLock(){
        locked = !locked;
        if(!connected)
            locked = true;

        onLockChange();
    }
}