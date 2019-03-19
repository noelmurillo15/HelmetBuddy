package com.example.android.bluetoothlegatt;

import android.Manifest;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;


/** Activity for scanning and displaying available Bluetooth LE devices.    */
public class DeviceScanActivity extends ListActivity {
    /**  Bluetooth Adapter & Ble Device List    */
    BluetoothAdapter mBluetoothAdapter;
    LeDeviceListAdapter mLeDeviceListAdapter;

    /**  Booleans for BLE_Scan  */
    boolean mScanning;
    boolean mPermissions;
    boolean mGpsScreenActive;
    boolean mRequestBTActive;
    boolean mRequestLocationActive;

    /**  Buttons for UI */
    Button helmetBtn;
    Button settingsBtn;

    /**  Handler for BLE_ScanCallback   */
    Handler mHandler;
    Runnable mRunnable;

    /**  Permission Requests    */
    final int REQUEST_ENABLE_BT = 99;
    final int REQUEST_ENABLE_LOCATION = 98;
    final int REQUEST_ENABLE_GPS = 1001;

    /** BLE Scan period : 2 seconds */
    static final long SCAN_PERIOD = 750;


    /** START : ACTIVITY OVERRIDES  */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        System.out.println("*****   DeviceScanActivity::onCreate HAS BEEN CALLED!    *****");

        /**  Sets the background photo & hides action bar   */
        getWindow().getDecorView().setBackground(getResources().getDrawable(R.drawable.spinbg));
        getActionBar().hide();

        mRequestLocationActive = false;
        mRequestBTActive = false;
        mGpsScreenActive = false;
        mPermissions = false;
        mHandler = new Handler();

        /** Use this check to determine whether BLE is supported on the device.  Then you can
            selectively disable BLE-related features.    */
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
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
            finish();
        }
    }
    @Override
    protected void onResume() {
        super.onResume();
//        System.out.println("*****   DeviceScanActivity::ON_RESUME HAS BEEN CALLED!    *****");

        /**  Check All permissions needed for BLE Scan   */
        checkPermissions();

        /** Initializes list view adapter.   */
        if(mLeDeviceListAdapter == null) {
            mLeDeviceListAdapter = new LeDeviceListAdapter();
            setListAdapter(mLeDeviceListAdapter);
        }

        /**  Start Initial Scan  */
        if(hasPermissions())
            scanDevices(true);
    }
    @Override
    protected void onPause() {
        super.onPause();
//        System.out.println("*****   DeviceScanActivity::ON_PAUSE HAS BEEN CALLED!    *****");
//        if(mScanning) {
//            scanDevices(false);
//        }
    }
    @Override
    public void onBackPressed(){
        super.onBackPressed();
//        System.out.println("*****   DeviceScanActivity::ON_BACK_PRESSED HAS BEEN CALLED!    *****");
//        scanDevices(false);
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }
    /** END : ACTIVITY OVERRIDES    */

    /** Check for All Permissions   */
    void checkPermissions() {
        if (!hasPermissions()) {
            if (!mRequestLocationActive && !mRequestBTActive && !hasGPSActive()) {
//                System.out.println("*****   requestGPS has been called!");
                ShowSettingsAlert();
                return;
            }
            mGpsScreenActive = false;
            if (!mGpsScreenActive && !mRequestBTActive && !hasLocationPermission()) {
//                System.out.println("*****   requestLocationPermission has been called!");
                requestLocationPermission();
                return;
            }
            mRequestLocationActive = false;
            if (!mGpsScreenActive && !mRequestLocationActive && !hasBluetoothPermission()) {
//                System.out.println("*****   requestBluetoothEnable has been called!");
                requestBluetoothEnable();
                return;
            }
            mRequestBTActive = false;
            return;
        }
        mPermissions = true;
    }

    /** DEPRECATED : Bluetooth Scan for devices - specifically looking for devices named 'Adafruit'    */
    public void scanDevices(final boolean enable) {
//        System.out.println("*****   DeviceScanActivity::SCAN_DEVICES HAS BEEN CALLED : "  + enable + "    *****");
        if (enable) {
            mScanning = true;
            helmetBtn = null;
            settingsBtn = null;
//            mLeDeviceListAdapter.clear();

            /** Stops scanning after a pre-defined scan period  */
            if(mRunnable == null) {
                mRunnable = new Runnable() {
                    public void run() {
                        mScanning = false;
                        mBluetoothAdapter.stopLeScan(mScanCallback);
                        mLeDeviceListAdapter.notifyDataSetChanged();
                        invalidateOptionsMenu();
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
    /** Bluetooth Scan Callback Override    */
    BluetoothAdapter.LeScanCallback mScanCallback =
            new BluetoothAdapter.LeScanCallback() {
                /**
                 * BLE Device scan callback - will only find devices with 'Adafruit' in their name
                 * remove if (device.getName().contains("Adafruit")) if you want to find all available BLE devices
                 */
                @Override
                public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (device.getName() != null) {
//                                System.out.println("*****   Found bluetooth device : " + device.getName());
                                if(device.getName().contains("Adafruit")) {
//                                    System.out.println("*****   Device is an Adafruit! ");
                                    if (mLeDeviceListAdapter.checkIfExists(device)) {
//                                        System.out.println("*****   Adding Adafruit Device : ");
                                        mLeDeviceListAdapter.addDevice(device);
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



    /** CLASS LE_DEVICE_LIST_ADAPTER : BEGIN
     * Adapter for holding devices found through scanning.  */
    class LeDeviceListAdapter extends BaseAdapter {
        ArrayList<BluetoothDevice> mLeDevices;
        LayoutInflater mInflator;


        public LeDeviceListAdapter() {
            super();
            mLeDevices = new ArrayList<>();
            mInflator = DeviceScanActivity.this.getLayoutInflater();
        }

        public void addDevice(BluetoothDevice device) {
            if (!mLeDevices.contains(device)) {
                mLeDevices.add(device);
            }
        }

        public boolean checkIfExists(final BluetoothDevice device){
            if(mLeDeviceListAdapter.getCount() > 0){
                if(mLeDeviceListAdapter.getDevice(0).getAddress().equals(device.getAddress())){
                    return false;
                }
            }
            return true;
        }

        public BluetoothDevice getDevice(int position) {
            return mLeDevices.get(position);
        }

        public void clear() {
            mLeDevices.clear();
        }

        @Override
        public int getCount() {
            return mLeDevices.size();
        }
        @Override
        public Object getItem(int i) {
            return mLeDevices.get(i);
        }
        @Override
        public long getItemId(int i) {
            return i;
        }
        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            ViewHolder viewHolder;
            if (view == null) {
                view = mInflator.inflate(R.layout.listitem_device, null);
                viewHolder = new ViewHolder();
                view.setTag(viewHolder);
            }
            return view;
        }
    }
    /** CLASS LE_DEVICE_LIST_ADAPTER : END    */



    /** This function makes sure that the required permissions for BLE Scan are enabled */
    boolean hasPermissions() {
        return (hasBluetoothPermission() && hasLocationPermission() && hasGPSActive());
    }

    /** Checks if bluetooth is enabled  */
    boolean hasBluetoothPermission() {
        if (mBluetoothAdapter == null)
            mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            return false;
        }
        return true;
    }
    /** Attempts to turn on Bluetooth   */
    void requestBluetoothEnable() {
        /** Force enable BT access   */
        if(!mRequestBTActive && !hasBluetoothPermission()) {
//            System.out.println("*****   FORCING mRequestBTActive");
            mRequestBTActive = true;
            Intent enableBTIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBTIntent, REQUEST_ENABLE_BT);
        }
    }

    /** Checks if Location permissions have been granted    */
    boolean hasLocationPermission() {
        boolean fineLocation = (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == getPackageManager().PERMISSION_GRANTED);
        boolean courseLocation = (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == getPackageManager().PERMISSION_GRANTED);
        return (fineLocation && courseLocation);
    }
    /** Attempts to turn on Bluetooth   */
    void requestLocationPermission(){
        if(!mRequestLocationActive && !hasLocationPermission()) {
//            System.out.println("*****   FORCING requestLocationPermission");
            mRequestLocationActive = true;
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_ENABLE_LOCATION);
        }
    }

    /** Checks if GPS has been turned ON    */
    boolean hasGPSActive(){
        final LocationManager manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        return manager.isProviderEnabled(manager.GPS_PROVIDER);
    }
    /** Asks user to turn on GPS    */
    void requestGPS(){
        if(mGpsScreenActive && !hasGPSActive()) {
//            System.out.println("*****   FORCING requestGPS");
            startActivityForResult(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS), REQUEST_ENABLE_GPS);
        }
    }
    /** Display Alert Window to prompt GPS  */
    void ShowSettingsAlert(){
        if(!mGpsScreenActive) {
            mGpsScreenActive = true;
            AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
            alertDialog.setTitle("Error - GPS is needed for this App");
            alertDialog.setMessage("Please press Ok to enable GPS.");
            alertDialog.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
//                    System.out.println("Clicked OK");
                    requestGPS();
                }
            });
            alertDialog.setNegativeButton("No", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
//                    System.out.println("Clicked No");
                    finish();System.exit(0);
                }
            });
            alertDialog.show();
        }
    }


    /** Tries to find the helmet button which is created when a device has been found
        If the helmet button is not found, will do another BLE scan which usually fixes helmet button not found */
    void InitializeHelmetButton(){
//        System.out.println("*****   DeviceScanActivity::INITIALIZE_HELMET_BUTTON HAS BEEN CALLED!    *****");
        if(helmetBtn == null){  //  Tries to find the Helmet and Settings button
            helmetBtn = findViewById(R.id.helmet_button);
            settingsBtn = findViewById(R.id.settings_button);
        }

        /** If helmet & settings button were created    */
        if(helmetBtn != null){
            /** Set Helmet button onClick Function */
            helmetBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
//                    System.out.println("*****   HELMET BUTTON HAS BEEN CLICKED");
                    final BluetoothDevice device = mLeDeviceListAdapter.getDevice(0);
                    if (device == null) return;
                    final Intent intent = new Intent(DeviceScanActivity.this, DeviceControlActivity.class);
                    intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_NAME, device.getName());
                    intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_ADDRESS, device.getAddress());
                    if (mScanning) {
                        mBluetoothAdapter.stopLeScan(mScanCallback);
                        mScanning = false;
                    }
                    startActivity(intent);
                }
            });

            /** TODO : Set Settings button onClick Function   */
            settingsBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    System.out.println("*****   SETTINGS BUTTON HAS BEEN CLICKED");
                }
            });
        }
        else{   /** If the Helmet button was not created, run the BLE scan once more and it should work!    */
            scanDevices(true);
        }
    }

    /** View Holder Class   */
    static class ViewHolder {
        TextView deviceName;
        TextView deviceAddress;
    }
}