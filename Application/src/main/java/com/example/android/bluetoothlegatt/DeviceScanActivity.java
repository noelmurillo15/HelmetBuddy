package com.example.android.bluetoothlegatt;

import android.Manifest;
import android.app.Activity;
import android.app.ListActivity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
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
    LeDeviceListAdapter mLeDeviceListAdapter;
    BluetoothAdapter mBluetoothAdapter;
    boolean mScanning;

    Button helmetBtn;
    Button settingsBtn;

    Handler mHandler;
    Runnable mRunnable;

    /**  Permission Requests   */
    final int REQUEST_ENABLE_BT = 99;
    final int REQUEST_ENABLE_GPS = 1001;

    /** BLE Scan period   */
    static final long SCAN_PERIOD = 2000;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        System.out.println("*****   DeviceScanActivity::onCreate HAS BEEN CALLED!    *****");

        getWindow().getDecorView().setBackground(getResources().getDrawable(R.drawable.spinbg));
        getActionBar().hide();

        /**  Check All permissions needed for BLE Scan   */
        int permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED){
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)){
                Toast.makeText(this, "The permission to get BLE location data is required", Toast.LENGTH_SHORT).show();
            }else{
                requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            }
        }else{
            Toast.makeText(this, "Location permissions already granted", Toast.LENGTH_SHORT).show();
        }

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
    public void onBackPressed(){
        super.onBackPressed();
        System.out.println("*****   DeviceScanActivity::ON_BACK_PRESSED HAS BEEN CALLED!    *****");
        scanLeDevice(true);
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

        /**  Check All permissions needed for BLE Scan   */
        boolean permissionsGranted = hasPermissions();
        if(!permissionsGranted){
            System.out.println("*****   *****   PERMISSIONS HAVE NOT BEEN GRANTED");
        }
        else {
            System.out.println("*****   Permissions have been granted!");
        }

        /** Initializes list view adapter.   */
        mLeDeviceListAdapter = new LeDeviceListAdapter();
        setListAdapter(mLeDeviceListAdapter);

        /**  Start Initial Scan  */
        scanLeDevice(true);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        System.out.println("*****   DeviceScanActivity::ON_ACTIVITY_RESULT HAS BEEN CALLED!    *****");

        /** User chose not to enable Bluetooth.  */
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            finish();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onPause() {
        super.onPause();
        System.out.println("*****   DeviceScanActivity::ON_PAUSE HAS BEEN CALLED!    *****");
        scanLeDevice(false);
        mLeDeviceListAdapter.clear();
    }

    /** BLE Scan for devices - specifically looking for devices named 'Adafruit'    */
    public void scanLeDevice(final boolean enable) {
        System.out.println("*****   DeviceScanActivity::SCAN_LE_DEVICE HAS BEEN CALLED!    *****");

        if (enable) {
            mScanning = true;
            helmetBtn = null;
            settingsBtn = null;

            /** Stops scanning after a pre-defined scan period.  */
            if(mRunnable == null) {
                mRunnable = new Runnable() {
                    public void run() {
                        mScanning = false;
                        mBluetoothAdapter.stopLeScan(mLeScanCallback);
                        mLeDeviceListAdapter.notifyDataSetChanged();
                        invalidateOptionsMenu();
                        InitializeHelmetButton();
                    }
                };
            }

            /** Start the scan using a handler  */
            mHandler.postDelayed(mRunnable, SCAN_PERIOD);
            mBluetoothAdapter.startLeScan(mLeScanCallback);
        } else {
            mScanning = false;
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
        }
        invalidateOptionsMenu();
    }

    /** Adapter for holding devices found through scanning.  */
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

        public BluetoothDevice getDevice(int position) {
            return mLeDevices.get(position);
        }

        public void clear() {
            mLeDevices.clear();
        }

        public boolean checkIfExists(final BluetoothDevice device){
            if(mLeDeviceListAdapter.getCount() > 0){
                if(mLeDeviceListAdapter.getDevice(0).getAddress().equals(device.getAddress())){
                    return false;
                }
            }
            return true;
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

    /** This function makes sure that the required permissions for BLE Scan are enabled */
    boolean hasPermissions() {
        System.out.println("*****   DeviceScanActivity::HAS_PERMISSIONS HAS BEEN CALLED!    *****");

        if (!hasBluetoothPermission()) {
            requestBluetoothEnable();
        } else if (!hasLocationPermission()) {
            requestLocationPermission();
        }

        return (hasBluetoothPermission() && hasLocationPermission());
    }

    /** Checks if bluetooth is enabled  */
    boolean hasBluetoothPermission() {
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            return false;
        }
        return true;
    }

    /** Checks if GPS is enabled    */
    boolean hasLocationPermission() {
        boolean fineGPS = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == getPackageManager().PERMISSION_GRANTED;
        boolean courseGPS = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == getPackageManager().PERMISSION_GRANTED;
        return (fineGPS && courseGPS);
    }

    /** Attempts to turn on Bluetooth   */
    void requestBluetoothEnable() {
        if (mBluetoothAdapter == null)
            mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        /** Force enable BT access   */
        Intent enableBTIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(enableBTIntent, REQUEST_ENABLE_BT);
    }
    /** Attempts to turn on GPS    */
    void requestLocationPermission() {
        /**  Force enable GPS access */
        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_ENABLE_GPS);
    }

    /** BLE Device scan callback - will only find devices with 'Adafruit' in their name
        remove if (device.getName().contains("Adafruit")) if you want to find all available BLE devices */
    BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (device.getName() != null) {
                                System.out.println("*****   Found Bluetooth Device : " + device.getName());
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

    /** Tries to find the helmet button which is created when a device has been found
        If the helmet button is not found, will do another BLE scan which usually fixes helmet button not found */
    void InitializeHelmetButton(){
        System.out.println("*****   DeviceScanActivity::INITIALIZE_HELMET_BUTTON HAS BEEN CALLED!    *****");
        if(helmetBtn == null){  //  Tries to find the Helmet and Settings button
            helmetBtn = findViewById(R.id.helmet_button);
            settingsBtn = findViewById(R.id.settings_button);
        }

        /** If helmet button was created    */
        if(helmetBtn != null){
            /** Set Helmet buttons onClick Function */
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
                        mBluetoothAdapter.stopLeScan(mLeScanCallback);
                        mScanning = false;
                    }
                    startActivity(intent);
                }
            });

            /** Set Settings buttons onClick Function   */
            settingsBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    System.out.println("*****   SETTINGS BUTTON HAS BEEN CLICKED");
                }
            });
        }
        else{   /** If the Helmet button was not created, run the BLE scan once more and it should work!    */
            System.out.println("*****   HELMET BUTTON = NULL    *****");
            scanLeDevice(true);
        }
    }

    /** View Holder Class   */
    static class ViewHolder {
        TextView deviceName;
        TextView deviceAddress;
    }
}