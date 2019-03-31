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

	/** Bluetooth Variables    */
    private BluetoothManager mBluetoothManager;
    public BluetoothAdapter mBluetoothAdapter;
    private BluetoothGatt mBluetoothGatt;

    private String mBluetoothDeviceAddress;
    private int mConnectionState = STATE_DISCONNECTED;

	/**	Connection States*/
    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

	/**	Activity Actions*/
    public final static String ACTION_GATT_CONNECTED = "com.example.bluetooth.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED = "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED = "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE = "com.example.bluetooth.le.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA = "com.example.bluetooth.le.EXTRA_DATA";

	/**	Helmet Lock Service UUID's	*/
    public final static UUID UUID_HELMET_LOCK_WRITE = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e");
    public final static UUID UUID_HELMET_LOCK_READ = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e");

    /**	Helmet Lock Service UUID's	*/
    public final static UUID UUID_BATTERY_LEVEL_READ = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb");



    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
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
            String intentAction;

			/**	On successful bluetooth connection	*/
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                System.out.println("*   Connected to GATT server");
                intentAction = ACTION_GATT_CONNECTED;
                mConnectionState = STATE_CONNECTED;
                broadcastUpdate(intentAction);
                System.out.println("*   Attempting to start service discovery:" + mBluetoothGatt.discoverServices());

            } 
			/**	On bluetooth disconnect	*/
			else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                System.out.println("*   Disconnected from GATT server");
                intentAction = ACTION_GATT_DISCONNECTED;
                mConnectionState = STATE_DISCONNECTED;
                broadcastUpdate(intentAction);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
			/**	If Gatt services were found, broadcast services	to Activity	*/
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
            } else {
                System.out.println("*   onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
			/**	On a successful gatt characteristic read	*/
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            System.out.println("*****   BluetoothLeService::BluetoothGattCallback::onCharacteristicChanged");
            super.onCharacteristicChanged(gatt, characteristic);
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
        }
    };

    private void broadcastUpdate(final String action) {
        System.out.println("*****   BluetoothLeService::broadcastUpdate");
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action, final BluetoothGattCharacteristic characteristic) {
        System.out.println("*****   BluetoothLeService::broadcastUpdate::BluetoothGattCharacteristic");

        final Intent intent = new Intent(action);

        if (UUID_HELMET_LOCK_WRITE.equals(characteristic.getUuid())) {
            System.out.println("*   Bluetooth Le Service broadcast update has found UUID_HELMET_LOCK_WRITE");
        } else if (UUID_HELMET_LOCK_READ.equals(characteristic.getUuid())) {
            System.out.println("*   Bluetooth Le Service broadcast update has found UUID_HELMET_LOCK_READ");
        } else if (UUID_BATTERY_LEVEL_READ.equals(characteristic.getUuid())) {
            System.out.println("*   Bluetooth Le Service broadcast update has found UUID_BATTERY_LEVEL_READ");
            System.out.println("*   characteristic.getStringValue() = " + characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0));
            String dataToSend = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0).toString();
            System.out.println("*   dataToSend = " + dataToSend);
            intent.putExtra(EXTRA_DATA, dataToSend);
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
            System.out.println("*****   BluetoothLeService::BroadcastReceiver::onReceive");

			/**	Retrieve Action Type	*/
            String action = intent.getAction();
            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
				/**	If Bluetooth connection is about to shut off	*/
                if (mBluetoothAdapter.getState() == BluetoothAdapter.STATE_TURNING_OFF) {
                    System.out.println("*   ACTION_STATE_CHANGED::STATE_TURNING_OFF");
                }

				/**	If Bluetooth connection has turned off	*/
                if (mBluetoothAdapter.getState() == BluetoothAdapter.STATE_OFF) {
                    System.out.println("*   ACTION_STATE_CHANGED::STATE_OFF");
                    System.exit(0);
                }
            }
        }
    };

    public boolean initialize() {
        System.out.println("*****   BluetoothLeService::initialize");

		/**	Safety Check	*/
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

		/**	Register the listener in charge of checking bluetooth enabled / disabled	*/
        this.registerReceiver(mReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));

        return true;
    }

    public boolean connect(final String address) {
        System.out.println("*****   BluetoothLeService::connect");

		/**	Safety Check	*/
        if (mBluetoothAdapter == null || address == null) {
            System.out.println("*   BluetoothAdapter not initialized or unspecified address");
            return false;
        }

        /** Previously connected device, Try to reconnect	*/
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

        /**	Attempt connection with false auto-connect flag	*/
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
        System.out.println("*****   BluetoothLeService::disconnect");
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            System.out.println("*   BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
    }

    public void close() {
        System.out.println("*****   BluetoothLeService::close");
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    public void readCharacteristic(BluetoothGattCharacteristic characteristic){
        System.out.println("*****   BluetoothLeService::readCharacteristic");
        mBluetoothGatt.readCharacteristic(characteristic);
    }

    public void writeCharacteristic(BluetoothGattCharacteristic characteristic, String data) {
        System.out.println("*****   BluetoothLeService::writeCharacteristic");
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
        System.out.println("*****   BluetoothLeService::setCharacteristicNotification");
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            System.out.println("*   BluetoothAdapter not initialized");
        }
    }

    public List<BluetoothGattService> getSupportedGattServices() {
        System.out.println("*****   BluetoothLeService::getSupportedGattServices");
        if (mBluetoothGatt == null) return null;
        return mBluetoothGatt.getServices();
    }
}