package com.example.aahelmetlock;

import androidx.appcompat.app.AppCompatActivity;
import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;


public class MainActivity extends AppCompatActivity {

    /**
     * Bluetooth Adapter & Ble Device
     */
    BluetoothDevice mBluetoothDevice;
    BluetoothAdapter mBluetoothAdapter;
    BluetoothLeGattService mBluetoothLeService;

    /**
     * Bluetooth Device Variables
     */
    String mDeviceName;
    String mDeviceAddress;
    final String LIST_UUID = "UUID";

    /**
     * BLE Service Characteristic references
     */
    BluetoothGattCharacteristic mNotifyWrite;
    BluetoothGattCharacteristic mHelmetLockWrite;

    BluetoothGattCharacteristic mNotifyRead;
    BluetoothGattCharacteristic mBatteryRead;

    ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics = new ArrayList<>();

    /**
     * Booleans for BLE_Scan
     */
    boolean mScanning;
    boolean mConnected;
    boolean mPermissions;
    boolean mGpsScreenActive;
    boolean mRequestBTActive;
    boolean mRequestLocationActive;

    /**
     * Handler for postdelay BLE_ScanCallback
     */
    Handler mHandler;
    Runnable mRunnable;

    /**
     * Permission Requests
     */
    final int REQUEST_ENABLE_BT = 99;
    final int REQUEST_ENABLE_LOCATION = 98;
    final int REQUEST_ENABLE_GPS = 1001;

    /**
     * BLE Scan period : 1 seconds
     */
    static final long SCAN_PERIOD = 1000;

    /**
     * Buttons for MainActivity UI
     */
    Button mHelmetButton;

    /**
     * Popup Window Buttons
     */
    Button mUnlockLockButton;
    Button mPopupCloseButton;
    Button popup_settings;
    Button mBattery_indicator;

    /**
     * Popup Settings UI
     */
    LinearLayout mPopupWindow;
    LinearLayout popup_settings_window;
    TextView mTextViewDeviceName;
    TextView mPopupConnectionState;
    TextView mTextViewDeviceAddress;
    TextView mDataField;
    int mBatteryLevel;

    /**
     * Lock Status
     */
    boolean locked = false;
    boolean connected = false;
    boolean toggleSettings;

    /**
     * Progress Circle
     */
    RelativeLayout mProgressCircle;


    /*****	START : ACTIVITY OVERRIDES	*****/
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        System.out.println("*****   MainActivity::onCreate  *****");
        super.onCreate(savedInstanceState);

        /**    Sets the background photo & hides action bar   */
        setContentView(R.layout.activity_main);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        getWindow().getDecorView().setBackground(getResources().getDrawable(R.drawable.spinbg));

        /**    Finds the Helmet Button and disables it until Bluetooth Device is found	*/
        mHelmetButton = findViewById(R.id.helmet_button);
        if (mHelmetButton != null)
            mHelmetButton.setVisibility(View.GONE);

        /**    Reset activity booleans	*/
        mRequestLocationActive = false;
        mRequestBTActive = false;
        mGpsScreenActive = false;
        mPermissions = false;
        mHandler = new Handler();

        /** Check to determine whether BLE is supported on the device	*/
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            System.out.println("*****   SYSTEM EXIT::DeviceScanActivity::onCreate	*****");
            finish();
            System.exit(0);
        }

        /** Initializes a Bluetooth adapter	*/
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        /** Checks if Bluetooth is supported on the device.  */
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show();
            System.out.println("*****   SYSTEM EXIT::DeviceScanActivity::onCreate	*****");
            finish();
            System.exit(0);
        }
    }

    @Override
    protected void onResume() {
        System.out.println("*****   MainActivity::ON_RESUME    *****");
        super.onResume();

        /**  Check All permissions needed for BLE Scan   */
        checkPermissions();

        /** Can now safely register GattUpdate Intent   */
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());

        /**  Find Progress Circle   */
        if (mProgressCircle == null)
            mProgressCircle = findViewById(R.id.loadingPanel);

        /**  Find Helmet Button */
        if (mHelmetButton == null)
            mHelmetButton = findViewById(R.id.helmet_button);

        /**  Find Popup Window */
        if (mPopupWindow == null)
            mPopupWindow = findViewById(R.id.popup);

        /**  Find Popup Buttons */
        if (mUnlockLockButton == null)
            mUnlockLockButton = findViewById(R.id.UnlockLock);
        if (mPopupCloseButton == null)
            mPopupCloseButton = findViewById(R.id.close_popup_window);

        /**  Find Popup Info Window UI  */
        if (popup_settings == null)
            popup_settings = findViewById(R.id.popup_settings_button);
        if (popup_settings_window == null)
            popup_settings_window = findViewById(R.id.info_popup_window);
        if (mPopupConnectionState == null)
            mPopupConnectionState = findViewById(R.id.DisconnectedText);
        if (mTextViewDeviceName == null)
            mTextViewDeviceName = findViewById(R.id.device_name);
        if (mTextViewDeviceAddress == null)
            mTextViewDeviceAddress = findViewById(R.id.device_address);
        if (mDataField == null)
            mDataField = findViewById(R.id.device_data);
        if (mBattery_indicator == null)
            mBattery_indicator = findViewById(R.id.battery_indicator);

        /**  Set UI default values */
        mBattery_indicator.setBackgroundResource(R.drawable.battery_very_low_icon);
        mPopupCloseButton.setVisibility(View.GONE);
        popup_settings_window.setVisibility(View.GONE);
        popup_settings.setVisibility(View.GONE);
        mUnlockLockButton.setVisibility(View.GONE);
        mProgressCircle.setVisibility(View.GONE);
        mPopupWindow.setVisibility(View.GONE);

        /**  Start Initial Scan  */
        if (hasPermissions())
            scanDevices(true);
    }

    @Override
    protected void onPause() {
        System.out.println("*****   MainActivity::ON_PAUSE    *****");
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
        mRequestLocationActive = false;
        mGpsScreenActive = false;
        mRequestBTActive = false;
        mPermissions = false;
        mScanning = false;
    }

    @Override
    protected void onDestroy() {
        System.out.println("*****   MainActivity::onDestroy   *****");
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        /**    Listens for Bluetooth enable/disable	*/
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == Activity.RESULT_CANCELED) {
                System.out.println("*****   SYSTEM EXIT::onBluetoothEnableCancel *****");
                finish(); System.exit(0);
            } else {
                mRequestBTActive = true;
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResult) {
        /**    Checks if user denied Permission request*/
        if (requestCode == REQUEST_ENABLE_LOCATION) {
            if (grantResult[0] == getPackageManager().PERMISSION_DENIED) {
                System.out.println("*****   SYSTEM EXIT::onLocationEnableDeny *****");
                finish(); System.exit(0);
            }
        }
    }
    /*****	END : ACTIVITY OVERRIDES	*****/


    /*****	START : BLUETOOTH FUNCTIONS	*****/

    /**
     * Bluetooth Scan for BLE devices
     */
    public void scanDevices(final boolean enable) {
        System.out.println("*****   MainActivity::SCAN_DEVICES : " + enable + "    *****");
        if (enable) {
            mScanning = true;

            /**    Make sure mProgressCircle is active during scanning	*/
            if (mProgressCircle != null)
                mProgressCircle.setVisibility(View.VISIBLE);

            /** Runnable setup for stopping ble scan after a pre-defined period of time  */
            if (mRunnable == null) {
                mRunnable = new Runnable() {
                    public void run() {
                        mScanning = false;
                        mBluetoothAdapter.stopLeScan(mScanCallback);
                        invalidateOptionsMenu();

                        /**    Scan has ended, disable mProgressCircle	*/
                        if (mProgressCircle != null)
                            mProgressCircle.setVisibility(View.GONE);

                        InitializeHelmetButton();
                    }
                };
            }

            /** Start the scan  */
            mBluetoothAdapter.startLeScan(mScanCallback);

            /** Start the post delay to stop scan  */
            mHandler.postDelayed(mRunnable, SCAN_PERIOD);
        } else {
            mScanning = false;
            mBluetoothAdapter.stopLeScan(mScanCallback);
        }
        invalidateOptionsMenu();
    }

    /**
     * Bluetooth Scan Callback Override
     */
    BluetoothAdapter.LeScanCallback mScanCallback =
            new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (device.getName() != null) {
                                System.out.println("*   Found : " + device.getName());
                                if (device.getName().contains("HelmetLock")) {
                                    if (mBluetoothDevice == null && device != null) {
                                        System.out.println("*   Adding : " + device.getName());
                                        mBluetoothDevice = device;
                                    }
                                }
                            }
                        }
                    });
                }
            };


    /**
     * Code to manage Bluetooth Service lifecycle
     */
    final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            System.out.println("*****   MainActivity::mServiceConnection::onServiceConnected   *****");

            /**    Attempts to Initialize Bluetooth Service	*/
            mBluetoothLeService = ((BluetoothLeGattService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                System.out.println("*****   SYSTEM EXIT::onServiceConnected - Unable to initialize Bluetooth *****");
                finish(); System.exit(0);
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

    /**
     * Handles various events fired by the Bluetooth Service
     */
    final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            System.out.println("*****   MainActivity::mGattUpdateReceiver::onReceive    *****");

            /**    Retrieve Action Type */
            final String action = intent.getAction();

            /**    On successful Gatt Connection    */
            if (BluetoothLeGattService.ACTION_GATT_CONNECTED.equals(action)) {
                showPopUp();
                mConnected = true;
                updateConnectionState(R.string.connected);
                invalidateOptionsMenu();
            }
            /**    On unsuccessful Gatt Connection	*/
            else if (BluetoothLeGattService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                updateConnectionState(R.string.disconnected);
                invalidateOptionsMenu();
                clearUI();
            }
            /**    On successful retrieval of Gatt services	*/
            else if (BluetoothLeGattService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                displayGattServices(mBluetoothLeService.getSupportedGattServices());
            }
            /**    On successful data transfer	*/
            else if (BluetoothLeGattService.ACTION_DATA_AVAILABLE.equals(action)) {
                displayData(intent.getStringExtra(BluetoothLeGattService.EXTRA_DATA));
            }
            /**    On successful extra data transfer	*/
            else if (BluetoothLeGattService.EXTRA_DATA.equals(action)) {
                displayData(intent.getStringExtra(BluetoothLeGattService.EXTRA_DATA));
            }

            /**    If Bluetooth is turned off, exit application	*/
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) {
                if (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1) == BluetoothAdapter.STATE_OFF) {
                    System.out.println("*   SYSTEM EXIT::onBluetoothDisabled    ");
                    finish(); System.exit(0);
                }
            }
        }
    };
    /*****	END : BLUETOOTH FUNCTIONS	*****/


    /**
     *
     */
    static IntentFilter makeGattUpdateIntentFilter() {
        System.out.println("*****   MainActivity::makeGattUpdateIntentFilter   *****");
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeGattService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeGattService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeGattService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeGattService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(BluetoothLeGattService.EXTRA_DATA);
        return intentFilter;
    }


    /*****	START : PRIVATE FUNCTIONS	*****/

    /**
     * Check for All Permissions
     */
    void checkPermissions() {
        System.out.println("*****   MainActivity::CheckPermissions    *****");
        if (!hasPermissions()) {
            if (!mRequestLocationActive && !mRequestBTActive && !hasGPSActive()) {
                ShowSettingsAlert();
                return;
            }
            mGpsScreenActive = false;
            if (!mGpsScreenActive && !mRequestBTActive && !hasLocationPermission()) {
                requestLocationPermission();
                return;
            }
            mRequestLocationActive = false;
            if (!mGpsScreenActive && !mRequestLocationActive && !hasBluetoothPermission()) {
                requestBluetoothEnable();
                return;
            }
            mRequestBTActive = false;
            return;
        }
        mPermissions = true;
    }

    /**
     * This function makes sure that the required permissions for BLE Scan are enabled
     */
    boolean hasPermissions() {
        return (hasBluetoothPermission() && hasLocationPermission() && hasGPSActive());
    }

    /**
     * Checks if bluetooth is enabled
     */
    boolean hasBluetoothPermission() {
        if (mBluetoothAdapter == null)
            mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        return (mBluetoothAdapter != null && mBluetoothAdapter.isEnabled());
    }

    /**
     * Attempts to turn on Bluetooth
     */
    void requestBluetoothEnable() {
        if (!mRequestBTActive && !hasBluetoothPermission()) {
            Intent enableBTIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBTIntent, REQUEST_ENABLE_BT);
        }
    }

    /**
     * Checks if Location permissions have been granted
     */
    boolean hasLocationPermission() {
        boolean fineLocation = (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == getPackageManager().PERMISSION_GRANTED);
        boolean courseLocation = (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == getPackageManager().PERMISSION_GRANTED);
        return (fineLocation && courseLocation);
    }

    /**
     * Attempts to turn on Bluetooth
     */
    void requestLocationPermission() {
        if (!mRequestLocationActive && !hasLocationPermission()) {
            mRequestLocationActive = true;
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_ENABLE_LOCATION);
        }
    }

    /**
     * Checks if GPS has been turned ON
     */
    boolean hasGPSActive() {
        final LocationManager manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        return manager.isProviderEnabled(manager.GPS_PROVIDER);
    }

    /**
     * Asks user to turn on GPS
     */
    void requestGPS() {
        if (mGpsScreenActive && !hasGPSActive()) {
            startActivityForResult(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS), REQUEST_ENABLE_GPS);
        }
    }

    /**
     * Display Alert Window to prompt GPS
     */
    void ShowSettingsAlert() {
        if (!mGpsScreenActive) {
            mGpsScreenActive = true;
            AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
            alertDialog.setTitle("Error - GPS is needed for this App");
            alertDialog.setMessage("Please press Ok to enable GPS.");
            alertDialog.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    requestGPS();
                }
            });
            alertDialog.setNegativeButton("No", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    System.out.println("*****   SYSTEM EXIT::onGPSEnable = No *****");
                    finish();
                    System.exit(0);
                }
            });
            alertDialog.show();
        }
    }

    /**
     * Tries to find the helmet button which is created when a device has been found
     * If the helmet button is not found, will do another BLE scan until found
     */
    void InitializeHelmetButton() {
        System.out.println("*****   MainActivity::InitializeHelmetButton    *****");

        /**    Safety check before displaying helmet button for Bluetooth Gatt Server Connection	*/
        if (mHelmetButton != null) {
            System.out.println("*   HelmetButton is not NULL");
            if (mBluetoothDevice == null) {
                System.out.println("*   mBluetoothDevice is NULL");
                mHelmetButton.setVisibility(View.GONE);
                mHelmetButton = null;
                scanDevices(true);
                return;
            }

            /**    Display Helmet button	*/
            System.out.println("*   Setting HelmetButton Visible");
            mHelmetButton.setVisibility(View.VISIBLE);
            if (mProgressCircle != null)
                mProgressCircle.setVisibility(View.GONE);

            /** Set Helmet button onClick Function */
            System.out.println("*   Creating HelmetButton setOnClickListener");
            if (!mHelmetButton.hasOnClickListeners()) {
                mHelmetButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        System.out.println("*   helmet button has been pressed");
                        establishConnection();
                    }
                });
            }
        } else {
            /**    If helmet button was not found, try to find and re-scan*/
            System.out.println("*   HelmetButton is NULL");
            mHelmetButton = findViewById(R.id.helmet_button);
            scanDevices(true);
        }
    }

    /**
     *
     */
    void establishConnection() {
        if (mProgressCircle != null)
            mProgressCircle.setVisibility(View.VISIBLE);

        System.out.println("*****   MainActivity::establishConnection   *****");
        /**    Retrieve passed in Bluetooth name and address	*/
        mDeviceName = mBluetoothDevice.getName();
        mDeviceAddress = mBluetoothDevice.getAddress();
        toggleSettings = false;
        mBluetoothDevice = null;
        mHelmetButton.setVisibility(View.GONE);

        /**    Attempt to bind to Gatt service	*/
        Intent gattServiceIntent = new Intent(this, BluetoothLeGattService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        /**    Attempt to Connect to BLE Service	*/
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            System.out.println("*   Connect request result=" + result);
        }
    }

    /**
     *
     */
    void clearUI() {
        System.out.println("*****   MainActivity::clearUI   *****");
        /**    Clear all text fields	*/
        if (mDataField != null)
            mDataField.setText(R.string.no_data);
        if (mTextViewDeviceName != null)
            mTextViewDeviceName.setText(R.string.no_data);
        if (mPopupConnectionState != null)
            mPopupConnectionState.setText(R.string.no_data);
        if (mTextViewDeviceAddress != null)
            mTextViewDeviceAddress.setText(R.string.no_data);
    }

    /**
     *
     */
    void updateConnectionState(final int resourceId) {
        System.out.println("*****   MainActivity::updateConnectionState   *****");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mPopupConnectionState != null) {
                    mPopupConnectionState.setText(resourceId);
                    if (mPopupConnectionState.getText() == getResources().getString(R.string.disconnected)) {
                        setLock(true);
                        connected = false;
                    } else {
                        connected = true;
                    }
                }
            }
        });
    }

    /**
     * Displays the data from Gatt Characteristic
     */
    void displayData(String data) {
        System.out.println("*****   MainActivity::displayData   *****");
        if (data != null) {
            System.out.println("*   DATA HAS BEEN RECEIVED");
            mBatteryLevel = Integer.parseInt(data);
            if (mBattery_indicator != null) {
                if (mBatteryLevel >= 4) {
                    mBattery_indicator.setBackgroundResource(R.drawable.battery_full_icon);
                } else if (mBatteryLevel >= 3 && mBatteryLevel < 4) {
                    mBattery_indicator.setBackgroundResource(R.drawable.battery_almost_full_icon);
                } else if (mBatteryLevel >= 2 && mBatteryLevel < 3) {
                    mBattery_indicator.setBackgroundResource(R.drawable.battery_low_icon);
                } else {
                    mBattery_indicator.setBackgroundResource(R.drawable.battery_very_low_icon);
                }
            }
        }
    }

    /**
     * Demonstrates how to iterate through the supported GATT Services/Characteristics.
     * In this sample, we populate the data structure that is bound to the ExpandableListView
     * on the UI
     */
    void displayGattServices(List<BluetoothGattService> gattServices) {
        System.out.println("*****   MainActivity::displayGattServices   *****");

        /**    Safety Check	*/
        if (gattServices == null) return;

        /**    Disable Progress Circle	*/
        if (mProgressCircle != null)
            mProgressCircle.setVisibility(View.GONE);

        /**    START : searching through connected device's GATT services and characteristics	*/
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
                if (mBluetoothLeService.UUID_HELMET_LOCK_WRITE.equals(gattCharacteristic.getUuid())) {
                    mHelmetLockWrite = gattCharacteristic;
                }

                if (mBluetoothLeService.UUID_BATTERY_LEVEL_READ.equals(gattCharacteristic.getUuid())) {
                    mBatteryRead = gattCharacteristic;
                    if (mNotifyRead != null) {
                        mBluetoothLeService.setCharacteristicNotification(mNotifyRead, false);
                        mNotifyRead = null;
                    }

                    mUnlockLockButton.setEnabled(false);

                    Timer checkBatteryTimer = new Timer();
                    checkBatteryTimer.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    System.out.println("*   Done Checking for Battery Level, switching bluetooth gatt service to Lock/Unlock");
                                    if (mNotifyWrite != null) {
                                        mBluetoothLeService.setCharacteristicNotification(mNotifyWrite, false);
                                        mNotifyWrite = null;
                                    }
                                    mNotifyWrite = mHelmetLockWrite;
                                    mBluetoothLeService.setCharacteristicNotification(mNotifyWrite, true);

                                    mUnlockLockButton.setEnabled(true);
                                }
                            });
                        }
                    }, 2000);

                    mNotifyRead = mBatteryRead;
                    mBluetoothLeService.readCharacteristic(mNotifyRead);
                    mBluetoothLeService.setCharacteristicNotification(mNotifyRead, true);
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
        /**    END : searching through connected device's GATT services and characteristics	*/
    }

    /**
     * Initializes the popup window if it has never been created
     * & finds the references for the UI
     */
    void showPopUp() {
        System.out.println("*****   MainActivity::showPopUp   *****");

        mHelmetButton.setVisibility(View.GONE);
        mPopupWindow.setVisibility(View.VISIBLE);
        mPopupCloseButton.setVisibility(View.VISIBLE);
        popup_settings_window.setVisibility(View.GONE);
        popup_settings.setVisibility(View.VISIBLE);
        mUnlockLockButton.setVisibility(View.VISIBLE);

        /** Set Settings button onClick Function   */
        if (!popup_settings.hasOnClickListeners()) {
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

        locked = false;
        toggleLock();

        /** Setup Button onClick Listeners  */
        if (!mUnlockLockButton.hasOnClickListeners()) {
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
                    }, 1000);
                }
            });
        }

        if (!mPopupCloseButton.hasOnClickListeners()) {
            mPopupCloseButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mDeviceName = "";
                    mDeviceAddress = "";
                    mBluetoothLeService.disconnect();
                    mBluetoothLeService.close();
                    mGattCharacteristics.clear();
                    mNotifyWrite = null;
                    mHelmetLockWrite = null;

                    mPopupCloseButton.setVisibility(View.GONE);
                    popup_settings_window.setVisibility(View.GONE);
                    popup_settings.setVisibility(View.GONE);
                    mUnlockLockButton.setVisibility(View.GONE);
                    mProgressCircle.setVisibility(View.GONE);
                    mPopupWindow.setVisibility(View.GONE);

                    /**    Make sure user didn't remove any permissions */
                    checkPermissions();

                    /**  Start Re-Scan  */
                    if (hasPermissions())
                        scanDevices(true);
                }
            });
        }

        /** Update UI   */
        try {   /** Unlock Button   */
            ((TextView) findViewById(R.id.UnlockLock)).setText(getResources().getString(R.string.unlock_this_helmet));
        } catch (Exception e) {
            System.out.println("*****   Failed to find Unlock button by ID");
        }
    }

    /**
     * Changes the Lock/Unlock Button text
     */
    void onLockChange() {
        if (locked) {
            ((TextView) findViewById(R.id.UnlockLock)).setText(getResources().getString(R.string.unlock_this_helmet));
            if (mNotifyWrite != null) {
                mBluetoothLeService.writeCharacteristic(mNotifyWrite, "0");
                mDataField.setText("Locked");
            }
        } else {
            ((TextView) findViewById(R.id.UnlockLock)).setText(getResources().getString(R.string.lock_this_helmet));
            if (mNotifyWrite != null) {
                mBluetoothLeService.writeCharacteristic(mNotifyWrite, "1");
                mDataField.setText("Unlocked");
            }
        }
    }

    /**
     * Sets the lock bool
     */
    void setLock(boolean _b) {
        locked = _b;
        onLockChange();
    }

    /**
     * Toggles the lock bool
     */
    void toggleLock() {
        locked = !locked;
        if (!connected)
            locked = true;

        onLockChange();
    }
    /*****	END : PRIVATE FUNCTIONS	*****/
}