package com.example.aahelmetlock;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.IBinder;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;
import java.util.UUID;


public class BluetoothLeGattService  extends Service {

    private BluetoothManager mBluetoothManager;
    public BluetoothAdapter mBluetoothAdapter;

    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;
    private int mConnectionState = STATE_DISCONNECTED;

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    public final static String ACTION_GATT_CONNECTED = "com.example.bluetooth.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED = "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED = "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE = "com.example.bluetooth.le.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA = "com.example.bluetooth.le.EXTRA_DATA";

    public final static UUID UUID_HELMET_LOCK_WRITE = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e");
    public final static UUID UUID_HELMET_LOCK_READ = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e");


    @Override
    public IBinder onBind(Intent intent) {
//        System.out.println("*****   BluetoothLeService::IBinder::onBind");
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
//        System.out.println("*****   BluetoothLeService::IBinder::onUnbind");
        unregisterReceiver(mReceiver);
        disconnect();
        close();
        return super.onUnbind(intent);
    }

    private final IBinder mBinder = new LocalBinder();

    class LocalBinder extends Binder {
        BluetoothLeGattService getService() {
            return BluetoothLeGattService.this;
        }
    }

    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
//            System.out.println("*****   BluetoothLeService::BluetoothGattCallback::onConnectionStateChange");
            String intentAction;
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                System.out.println("*   Connected to GATT server");
                intentAction = ACTION_GATT_CONNECTED;
                mConnectionState = STATE_CONNECTED;
                broadcastUpdate(intentAction);
                System.out.println("*   Attempting to start service discovery:" + mBluetoothGatt.discoverServices());

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                System.out.println("*   Disconnected from GATT server");
                intentAction = ACTION_GATT_DISCONNECTED;
                mConnectionState = STATE_DISCONNECTED;
                broadcastUpdate(intentAction);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
//            System.out.println("*****   BluetoothLeService::BluetoothGattCallback::onServicesDiscovered");
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
            } else {
                System.out.println("*   onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
//            System.out.println("*****   BluetoothLeService::BluetoothGattCallback::onCharacteristicRead");
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
//            System.out.println("*****   BluetoothLeService::BluetoothGattCallback::onCharacteristicChanged");
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
        }
    };

    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
//        System.out.println("*****   BluetoothLeService::broadcastUpdate");
        sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action, final BluetoothGattCharacteristic characteristic) {
//        System.out.println("*****   BluetoothLeService::broadcastUpdate::BluetoothGattCharacteristic");
        final Intent intent = new Intent(action);

        if (UUID_HELMET_LOCK_WRITE.equals(characteristic.getUuid())) {
            System.out.println("*   Bluetooth Le Service broadcast update has found UUID_HELMET_LOCK_WRITE");
        } else if (UUID_HELMET_LOCK_READ.equals(characteristic.getUuid())) {
            System.out.println("*   Bluetooth Le Service broadcast update has found UUID_HELMET_LOCK_READ");
        } else {
            // For all other profiles, writes the data formatted in HEX.
            final byte[] data = characteristic.getValue();
            if (data != null && data.length > 0) {
                final StringBuilder stringBuilder = new StringBuilder(data.length);
                for (byte byteChar : data)
                    stringBuilder.append(String.format("%02X ", byteChar));
                intent.putExtra(EXTRA_DATA, new String(data) + "\n" + stringBuilder.toString());
            }
        }
        sendBroadcast(intent);
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
//            System.out.println("*****   BluetoothLeService::BroadcastReceiver::onReceive");
            String action = intent.getAction();
            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                if (mBluetoothAdapter.getState() == BluetoothAdapter.STATE_TURNING_OFF) {
                    System.out.println("*   ACTION_STATE_CHANGED::STATE_TURNING_OFF");
                }
                if (mBluetoothAdapter.getState() == BluetoothAdapter.STATE_OFF) {
                    System.out.println("*   ACTION_STATE_CHANGED::STATE_OFF");
                    System.exit(0);
                }
            }
        }
    };

    public boolean initialize() {
//        System.out.println("*****   BluetoothLeService::initialize");
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                System.out.println("*   Unable to initialize BluetoothManager");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            System.out.println("*   Unable to obtain a BluetoothAdapter");
            return false;
        }

        this.registerReceiver(mReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));

        return true;
    }

    public boolean connect(final String address) {
//        System.out.println("*****   BluetoothLeService::connect");
        if (mBluetoothAdapter == null || address == null) {
            System.out.println("*   BluetoothAdapter not initialized or unspecified address");
            return false;
        }

        // Previously connected device.  Try to reconnect.
        if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress) && mBluetoothGatt != null) {
            System.out.println("*   Trying to use an existing mBluetoothGatt for connection");
            if (mBluetoothGatt.connect()) {
                mConnectionState = STATE_CONNECTING;
                return true;
            } else {
                return false;
            }
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            System.out.println("*   Device not found.  Unable to connect");
            return false;
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        System.out.println("*   Trying to create a new connection");
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        mBluetoothDeviceAddress = address;
        mConnectionState = STATE_CONNECTING;
        return true;
    }

    public int GetConnectionState() {
        return mConnectionState;
    }

    public void disconnect() {
//        System.out.println("*****   BluetoothLeService::disconnect");
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            System.out.println("*   BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
    }

    public void close() {
//        System.out.println("*****   BluetoothLeService::close");
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
//        System.out.println("*****   BluetoothLeService::readCharacteristic");
        mBluetoothGatt.readCharacteristic(characteristic);
    }

    public void writeCharacteristic(BluetoothGattCharacteristic characteristic, String data) {
//        System.out.println("*****   BluetoothLeService::writeCharacteristic");
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            System.out.println("*	Bluetooth Adapter not initialized");
            return;
        }
        try {
            System.out.println("*	Sending converted data to Gatt Server :  " + URLEncoder.encode(data, "utf-8"));
            characteristic.setValue(URLEncoder.encode(data, "utf-8"));
            mBluetoothGatt.writeCharacteristic(characteristic);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic, boolean enabled) {
//        System.out.println("*****   BluetoothLeService::setCharacteristicNotification");
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            System.out.println("*   BluetoothAdapter not initialized");
            return;
        }
    }

    public List<BluetoothGattService> getSupportedGattServices() {
//        System.out.println("*****   BluetoothLeService::getSupportedGattServices");
        if (mBluetoothGatt == null) return null;
        return mBluetoothGatt.getServices();
    }
}
