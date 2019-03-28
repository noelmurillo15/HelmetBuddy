package com.example.aahelmetlock;

import androidx.appcompat.app.AppCompatActivity;
import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.Toast;


public class MainActivity extends AppCompatActivity {

    /**
     * Bluetooth Adapter & Ble Device List
     */
    BluetoothAdapter mBluetoothAdapter;
    BluetoothDevice mBluetoothDevice;

    /**
     * Booleans for BLE_Scan
     */
    boolean mScanning;
    boolean mPermissions;
    boolean mGpsScreenActive;
    boolean mRequestBTActive;
    boolean mRequestLocationActive;

    /**
     * Buttons for UI
     */
    Button helmetBtn;

    /**
     * Handler for BLE_ScanCallback
     */
    Handler mHandler;
    Runnable mRunnable;
    RelativeLayout progressCircle;

    /**
     * Permission Requests
     */
    final int REQUEST_ENABLE_BT = 99;
    final int REQUEST_ENABLE_LOCATION = 98;
    final int REQUEST_ENABLE_GPS = 1001;

    /**
     * BLE Scan period : 2 seconds
     */
    static final long SCAN_PERIOD = 2000;


    /**
     * START : ACTIVITY OVERRIDES
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        /**  Sets the background photo & hides action bar   */
        getWindow().getDecorView().setBackground(getResources().getDrawable(R.drawable.spinbg));

        helmetBtn = findViewById(R.id.helmet_button);
        if (helmetBtn != null)
            helmetBtn.setVisibility(View.GONE);

        mRequestLocationActive = false;
        mRequestBTActive = false;
        mGpsScreenActive = false;
        mPermissions = false;
        mHandler = new Handler();

        /** Use this check to determine whether BLE is supported on the device.  Then you can
         selectively disable BLE-related features.    */
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            System.out.println("*****   SYSTEM FINISH::DeviceScanActivity::onCreate -  *****");
            finish();
        }

        /** Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
         BluetoothAdapter through BluetoothManager.   */
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        /** Checks if Bluetooth is supported on the device.  */
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show();
            System.out.println("*****   SYSTEM FINISH::DeviceScanActivity::onCreate -  *****");
            finish();
        }
    }


    @Override
    protected void onResume() {
//        System.out.println("*****   DeviceScanActivity::ON_RESUME HAS BEEN CALLED!    *****");
        super.onResume();

        /**  Check All permissions needed for BLE Scan   */
        checkPermissions();

        /**  Start Initial Scan  */
        if (hasPermissions())
            scanDevices(true);
    }

    @Override
    protected void onPause() {
//        System.out.println("*****   DeviceScanActivity::ON_PAUSE HAS BEEN CALLED!    *****");
        super.onPause();
        mGpsScreenActive = false;
        mPermissions = false;
        mRequestBTActive = false;
        mRequestLocationActive = false;
        mScanning = false;
    }

    @Override
    public void onBackPressed() {
//        System.out.println("*****   DeviceScanActivity::ON_BACK_PRESSED HAS BEEN CALLED!    *****");
        super.onBackPressed();
        if (progressCircle == null)
            progressCircle = findViewById(R.id.loadingPanel);
        else
            progressCircle.setVisibility(View.VISIBLE);

        checkPermissions();

        /**  Start Initial Scan  */
        if (hasPermissions())
            scanDevices(true);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == Activity.RESULT_CANCELED) {
                System.out.println("*****   SYSTEM EXIT::onBluetoothEnableCancel *****");
                finish();
                System.exit(0);
            } else {
                mRequestBTActive = true;
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResult) {
        if (requestCode == REQUEST_ENABLE_LOCATION) {
            if (grantResult[0] == getPackageManager().PERMISSION_DENIED) {
                System.out.println("*****   SYSTEM EXIT::onLocationEnableDeny *****");
                finish();
                System.exit(0);
            }
        }
    }

    /** END : ACTIVITY OVERRIDES    */

    /**
     * Check for All Permissions
     */
    void checkPermissions() {
//        System.out.println("*****   DeviceScanActivity::CheckPermissions HAS BEEN CALLED!    *****");
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
     * DEPRECATED : Bluetooth Scan for devices - specifically looking for devices named 'Adafruit'
     */
    public void scanDevices(final boolean enable) {
//        System.out.println("*****   DeviceScanActivity::SCAN_DEVICES HAS BEEN CALLED : "  + enable + "    *****");
        if (enable) {
            if (progressCircle == null)
                progressCircle = findViewById(R.id.loadingPanel);
            else
                progressCircle.setVisibility(View.VISIBLE);

            mScanning = true;

            /** Stops scanning after a pre-defined scan period  */
            if (mRunnable == null) {
                mRunnable = new Runnable() {
                    public void run() {
                        mScanning = false;
                        mBluetoothAdapter.stopLeScan(mScanCallback);
                        invalidateOptionsMenu();

                        if (progressCircle == null)
                            progressCircle = findViewById(R.id.loadingPanel);
                        else
                            progressCircle.setVisibility(View.GONE);

                        InitializeHelmetButton();
                    }
                };
            }
            mHandler.postDelayed(mRunnable, SCAN_PERIOD);

            /** Start the scan  */
            mBluetoothAdapter.startLeScan(mScanCallback);
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
//                                System.out.println("*   Found : " + device.getName());
                                if (device.getName().contains("HelmetLock")) {
                                    if (mBluetoothDevice == null) {
                                        mBluetoothDevice = device;
                                    }
                                }
                            }
                        }
                    });
                }
            };


    /** TODO : API 23+ : Bluetooth Le Scan for devices   */
//    public void scanLeDevices(final boolean enable){
//        System.out.println("*****   DeviceScanActivity::SCAN_LE_DEVICES HAS BEEN CALLED!    *****");
//
//        final BluetoothLeScanner bluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
//
//        if(enable){
//            mHandler.postDelayed(new Runnable() {
//                @Override
//                public void run() {
//                    mScanning = false;
//                    bluetoothLeScanner.stopScan(mLeScanCallback);
//                }
//            }, SCAN_PERIOD);
//
//            mScanning = true;
//            bluetoothLeScanner.startScan(mLeScanCallback);
//        }
//        else{
//            mScanning = false;
//            bluetoothLeScanner.stopScan(mLeScanCallback);
//        }
//    }
//    ScanCallback mLeScanCallback = new ScanCallback() {
//        @Override
//        public void onScanResult(int callbackType, ScanResult result) {
//            super.onScanResult(callbackType, result);
//        }
//        @Override
//        public void onBatchScanResults(List<ScanResult> results) {
//            super.onBatchScanResults(results);
//        }
//        @Override
//        public void onScanFailed(int errorCode) {
//            super.onScanFailed(errorCode);
//        }
//    };

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
        /** Force enable BT access   */
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
//        System.out.println("*****   DeviceScanActivity::ShowSettingsAlert     *****");
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
                    System.out.println("*****   SYSTEM EXIT::onGPDEnableNo *****");
                    finish();
                    System.exit(0);
                }
            });
            alertDialog.show();
        }
    }


    /**
     * Tries to find the helmet button which is created when a device has been found
     * If the helmet button is not found, will do another BLE scan which usually fixes helmet button not found
     */
    void InitializeHelmetButton() {
        System.out.println("*****   DeviceScanActivity::InitializeHelmetButton    *****");
        if (helmetBtn != null) {
            System.out.println("*   Helmet is not NULL");
            if (mBluetoothDevice == null) {
                System.out.println("*   mBluetooth Device is NULL");
                scanDevices(true);
                return;
            }

            System.out.println("*   Setting Helmet Button Visible");
            helmetBtn.setVisibility(View.VISIBLE);

            /** Set Helmet button onClick Function */
            helmetBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    final Intent intent = new Intent(MainActivity.this, BluetoothHelmetLockActivity.class);
                    intent.putExtra(BluetoothHelmetLockActivity.EXTRAS_DEVICE_NAME, mBluetoothDevice.getName());
                    intent.putExtra(BluetoothHelmetLockActivity.EXTRAS_DEVICE_ADDRESS, mBluetoothDevice.getAddress());
                    if (mScanning) {
                        mBluetoothAdapter.stopLeScan(mScanCallback);
                        mScanning = false;
                    }
                    startActivity(intent);
                }
            });
        } else {
            helmetBtn = findViewById(R.id.helmet_button);
            scanDevices(true);
        }
    }
}