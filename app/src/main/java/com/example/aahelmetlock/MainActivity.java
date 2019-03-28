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

    /** Bluetooth Adapter & Ble Device List */
    BluetoothAdapter mBluetoothAdapter;
    BluetoothDevice mBluetoothDevice;

    /** Booleans for BLE_Scan */
    boolean mScanning;
    boolean mPermissions;
    boolean mGpsScreenActive;
    boolean mRequestBTActive;
    boolean mRequestLocationActive;

    /** Buttons for UI */
    Button mHelmetButton;

    /** Handler for BLE_ScanCallback */
    Handler mHandler;
    Runnable mRunnable;
    RelativeLayout mProgressCircle;

    /** Permission Requests	*/
    final int REQUEST_ENABLE_BT = 99;
    final int REQUEST_ENABLE_LOCATION = 98;
    final int REQUEST_ENABLE_GPS = 1001;

    /**	BLE Scan period : 1 seconds	*/
    static final long SCAN_PERIOD = 1000;



    /*****	START : ACTIVITY OVERRIDES	*****/
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        /**	Sets the background photo & hides action bar   */
        getWindow().getDecorView().setBackground(getResources().getDrawable(R.drawable.spinbg));

		/**	Finds the Helmet Button and disables it until Bluetooth Device is found	*/
        mHelmetButton = findViewById(R.id.helmet_button);
        if (mHelmetButton != null)
            mHelmetButton.setVisibility(View.GONE);

		/**	Reset activity booleans	*/
        mRequestLocationActive = false;
        mRequestBTActive = false;
        mGpsScreenActive = false;
        mPermissions = false;
        mHandler = new Handler();

        /** Check to determine whether BLE is supported on the device	*/
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            System.out.println("*****   SYSTEM EXIT::DeviceScanActivity::onCreate	*****");
            finish(); System.exit(0);
        }

        /** Initializes a Bluetooth adapter	*/
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        /** Checks if Bluetooth is supported on the device.  */
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show();
            System.out.println("*****   SYSTEM EXIT::DeviceScanActivity::onCreate	*****");
            finish(); System.exit(0);
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

		/**	Make mProgressCircle visible while app re-scans for bluetooth devices*/
        if (mProgressCircle == null)
            mProgressCircle = findViewById(R.id.loadingPanel);
        else
            mProgressCircle.setVisibility(View.VISIBLE);

		/**	Make sure user didn't remove any permissions*/
        checkPermissions();

        /**  Start Re-Scan  */
        if (hasPermissions())
            scanDevices(true);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
		/**	Listens for Bluetooth enable/disable	*/
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
		/**	Checks if user denied Permission request*/
        if (requestCode == REQUEST_ENABLE_LOCATION) {
            if (grantResult[0] == getPackageManager().PERMISSION_DENIED) {
                System.out.println("*****   SYSTEM EXIT::onLocationEnableDeny *****");
                finish(); System.exit(0);
            }
        }
    }
    /*****	END : ACTIVITY OVERRIDES	*****/

    
	/*****	START : BLUETOOTH FUNCTIONS	*****/

    /**	Bluetooth Scan for BLE devices*/
    public void scanDevices(final boolean enable) {
//        System.out.println("*****   DeviceScanActivity::SCAN_DEVICES HAS BEEN CALLED : "  + enable + "    *****");
        if (enable) {
			/**	Make sure mProgressCircle is active during scanning	*/
            if (mProgressCircle == null)
                mProgressCircle = findViewById(R.id.loadingPanel);
            else
                mProgressCircle.setVisibility(View.VISIBLE);

            mScanning = true;

            /** Stops scanning after a pre-defined scan period  */
            if (mRunnable == null) {
                mRunnable = new Runnable() {
                    public void run() {
                        mScanning = false;
                        mBluetoothAdapter.stopLeScan(mScanCallback);
                        invalidateOptionsMenu();

						/**	Scan has ended, disable mProgressCircle	*/
                        if (mProgressCircle == null)
                            mProgressCircle = findViewById(R.id.loadingPanel);
                        else
                            mProgressCircle.setVisibility(View.GONE);

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

    /** Bluetooth Scan Callback Override	*/
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

	/*****	END : BLUETOOTH FUNCTIONS	*****/


	/*****	START : PRIVATE FUNCTIONS	*****/

	/** Check for All Permissions */
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

    /** This function makes sure that the required permissions for BLE Scan are enabled	*/
    boolean hasPermissions() {
        return (hasBluetoothPermission() && hasLocationPermission() && hasGPSActive());
    }

    /** Checks if bluetooth is enabled */
    boolean hasBluetoothPermission() {
        if (mBluetoothAdapter == null)
            mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        return (mBluetoothAdapter != null && mBluetoothAdapter.isEnabled());
    }

    /** Attempts to turn on Bluetooth */
    void requestBluetoothEnable() {
        if (!mRequestBTActive && !hasBluetoothPermission()) {
            Intent enableBTIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBTIntent, REQUEST_ENABLE_BT);
        }
    }

    /** Checks if Location permissions have been granted */
    boolean hasLocationPermission() {
        boolean fineLocation = (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == getPackageManager().PERMISSION_GRANTED);
        boolean courseLocation = (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == getPackageManager().PERMISSION_GRANTED);
        return (fineLocation && courseLocation);
    }

    /** Attempts to turn on Bluetooth */
    void requestLocationPermission() {
        if (!mRequestLocationActive && !hasLocationPermission()) {
            mRequestLocationActive = true;
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_ENABLE_LOCATION);
        }
    }

    /** Checks if GPS has been turned ON */
    boolean hasGPSActive() {
        final LocationManager manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        return manager.isProviderEnabled(manager.GPS_PROVIDER);
    }

    /** Asks user to turn on GPS */
    void requestGPS() {
        if (mGpsScreenActive && !hasGPSActive()) {
            startActivityForResult(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS), REQUEST_ENABLE_GPS);
        }
    }

    /** Display Alert Window to prompt GPS */
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


    /** Tries to find the helmet button which is created when a device has been found
     * If the helmet button is not found, will do another BLE scan which usually fixes helmet button not found
     */
    void InitializeHelmetButton() {
        System.out.println("*****   DeviceScanActivity::InitializeHelmetButton    *****");

		/**	Safety check before displaying helmet button for Bluetooth Gatt Server Connection	*/
        if (mHelmetButton != null) {
            if (mBluetoothDevice == null) {
                scanDevices(true);
                return;
            }

			/**	Display Helmet button	*/
            mHelmetButton.setVisibility(View.VISIBLE);

            /** Set Helmet button onClick Function */
            mHelmetButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
					/**	Switches to BluetoothHelmetLockActivity which will attemp to connect to Gatt Server	*/
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
			/**	If helmet button was not found, try to find and re-scan*/
            mHelmetButton = findViewById(R.id.helmet_button);
            scanDevices(true);
        }

		/*****	END : PRIVATE FUNCTIONS	*****/
    }
}