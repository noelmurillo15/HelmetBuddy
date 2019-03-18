package com.example.android.bluetoothlegatt;

import android.Manifest;
import android.app.Activity;
import android.app.ListActivity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
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
import java.util.List;

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
    boolean mRequestLoactionActive;

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
    static final long SCAN_PERIOD = 2000;


    /** START : ACTIVITY OVERRIDES  */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        System.out.println("*****   DeviceScanActivity::onCreate HAS BEEN CALLED!    *****");

        /**  Sets the background photo & hides action bar   */
        getWindow().getDecorView().setBackground(getResources().getDrawable(R.drawable.spinbg));
        getActionBar().hide();

        mRequestLoactionActive = false;
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
        System.out.println("*****   DeviceScanActivity::ON_RESUME HAS BEEN CALLED!    *****");

        /** Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
         fire an intent to display a dialog asking the user to grant permission to enable it. */
        if (!mBluetoothAdapter.isEnabled()) {
            if (!mBluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        }

        /** Initializes list view adapter.   */
        if(mLeDeviceListAdapter == null) {
            mLeDeviceListAdapter = new LeDeviceListAdapter();
            setListAdapter(mLeDeviceListAdapter);
        }

        /**  Check All permissions needed for BLE Scan   */
        do {
            checkPermissions();
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ex) { }

        }while(!mPermissions);

        /**  Start Initial Scan  */
        scanDevices(true);
    }
    @Override
    protected void onPause() {
        super.onPause();
        System.out.println("*****   DeviceScanActivity::ON_PAUSE HAS BEEN CALLED!    *****");
        if(mScanning) {
            scanDevices(false);
            mLeDeviceListAdapter.clear();
        }
    }
    @Override
    public void onBackPressed(){
        super.onBackPressed();
        System.out.println("*****   DeviceScanActivity::ON_BACK_PRESSED HAS BEEN CALLED!    *****");
        mLeDeviceListAdapter.clear();
        scanDevices(true);
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }
    /** END : ACTIVITY OVERRIDES    */

    void checkPermissions() {
        if (!hasPermissions()) {
            if (!mRequestLoactionActive && !mRequestBTActive && !hasGPSActive()) {
                System.out.println("*****   requestGPS has been called!");
                requestGPS();
                return;
            }
            mGpsScreenActive = false;
            if (!mGpsScreenActive && !mRequestBTActive && !hasLocationPermission()) {
                System.out.println("*****   requestLocationPermission has been called!");
                requestLocationPermission();
                return;
            }
            mRequestLoactionActive = false;
            if (!mGpsScreenActive && !mRequestLoactionActive && !hasBluetoothPermission()) {
                System.out.println("*****   requestBluetoothEnable has been called!");
                requestBluetoothEnable();
                return;
            }
            mRequestBTActive = false;
            return;
        }

        mPermissions = true;
    }

    /** Bluetooth Scan for devices - specifically looking for devices named 'Adafruit'    */
    public void scanDevices(final boolean enable) {
        System.out.println("*****   DeviceScanActivity::SCAN_DEVICES HAS BEEN CALLED!    *****");

        if (enable) {

            mScanning = true;
            helmetBtn = null;
            settingsBtn = null;

            /** Stops scanning after a pre-defined scan period.  */
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

            /** Start the scan using a handler  */
            mHandler.postDelayed(mRunnable, SCAN_PERIOD);
            mBluetoothAdapter.startLeScan(mScanCallback);
        } else {
            mScanning = false;
            mBluetoothAdapter.stopLeScan(mScanCallback);
        }
        invalidateOptionsMenu();
    }
    BluetoothAdapter.LeScanCallback mScanCallback =
            new BluetoothAdapter.LeScanCallback() {
                /** BLE Device scan callback - will only find devices with 'Adafruit' in their name
                 remove if (device.getName().contains("Adafruit")) if you want to find all available BLE devices */
                @Override
                public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (device.getName() != null) {
                                if (device.getName().contains("Adafruit")) {
                                    if(mLeDeviceListAdapter.checkIfExists(device)) {
                                        System.out.println("***** Adding New Device that does not exist : " + device.getName());
                                        mLeDeviceListAdapter.addDevice(device);
                                    }
                                }
                            }
                        }
                    });
                }
            };


    /** Bluetooth Le Scan for devices   */
    public void scanLeDevices(final boolean enable){
        System.out.println("*****   DeviceScanActivity::SCAN_LE_DEVICES HAS BEEN CALLED!    *****");

        final BluetoothLeScanner bluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();

        if(enable){
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    bluetoothLeScanner.stopScan(mLeScanCallback);
                }
            }, SCAN_PERIOD);

            mScanning = true;
            bluetoothLeScanner.startScan(mLeScanCallback);
        }
        else{
            mScanning = false;
            bluetoothLeScanner.stopScan(mLeScanCallback);
        }
    }
    ScanCallback mLeScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
        }
        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
        }
        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
        }
    };



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
//                viewHolder.deviceAddress = view.findViewById(R.id.device_address);
//                viewHolder.deviceName = view.findViewById(R.id.device_name);
                view.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) view.getTag();
            }

            BluetoothDevice device = mLeDevices.get(i);
            final String deviceName = device.getName();
//            if (deviceName != null && deviceName.length() > 0)
//                viewHolder.deviceName.setText("Spin Helmet");
//            else
//                viewHolder.deviceName.setText(R.string.unknown_device);
//            viewHolder.deviceAddress.setText(device.getAddress());

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
            System.out.println("*****   FORCING mRequestBTActive");
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
        if(!mRequestLoactionActive && !hasLocationPermission()) {
            System.out.println("*****   FORCING requestLocationPermission");
            mRequestLoactionActive = true;
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
        if(!mGpsScreenActive && !hasGPSActive()) {
            System.out.println("*****   FORCING requestGPS");
            mGpsScreenActive = true;
            startActivityForResult(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS), REQUEST_ENABLE_GPS);
        }
    }


    /** Tries to find the helmet button which is created when a device has been found
        If the helmet button is not found, will do another BLE scan which usually fixes helmet button not found */
    void InitializeHelmetButton(){
        System.out.println("*****   DeviceScanActivity::INITIALIZE_HELMET_BUTTON HAS BEEN CALLED!    *****");
        if(helmetBtn == null){  //  Tries to find the Helmet and Settings button
            helmetBtn = findViewById(R.id.helmet_button);
            settingsBtn = findViewById(R.id.settings_button);
        }

        /** If helmet & settings button were created    */
        if(helmetBtn != null  && settingsBtn != null){
            /** Set Helmet button onClick Function */
            helmetBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    System.out.println("*****   HELMET BUTTON HAS BEEN CLICKED");
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

            /** Set Settings button onClick Function   */
            settingsBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    System.out.println("*****   SETTINGS BUTTON HAS BEEN CLICKED");
                }
            });
        }
        else{   /** If the Helmet button was not created, run the BLE scan once more and it should work!    */
            System.out.println("*****   HELMET & SETTINGS BUTTON = NULL    *****");
            scanDevices(false);

            try {
                Thread.sleep(2000);
            } catch (InterruptedException ex) { }

            scanDevices(true);
        }
    }

    /** View Holder Class   */
    static class ViewHolder {
        TextView deviceName;
        TextView deviceAddress;
    }
}