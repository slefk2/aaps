package com.microtechmd.equil.ble;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.text.TextUtils;

import com.microtechmd.equil.manager.EquilManager;
import com.microtechmd.equil.manager.EquilResponse;
import com.microtechmd.equil.manager.Utils;
import com.microtechmd.equil.manager.command.BaseCmd;
import com.microtechmd.equil.manager.command.CmdLargeBasalSet;
import com.microtechmd.equil.manager.command.CmdPair;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.inject.Inject;

import info.nightscout.androidaps.events.EventPumpStatusChanged;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.shared.logging.AAPSLogger;
import info.nightscout.shared.logging.LTag;
import info.nightscout.shared.sharedPreferences.SP;

@SuppressLint("MissingPermission")
public class EquilBLE {

    @Inject RxBus rxBus;
    EquilManager equilManager;
    SP sp;
    BluetoothGattCallback mGattCallback;
    AAPSLogger aapsLogger;
    BluetoothGattCharacteristic notifyChara;
    BluetoothGattCharacteristic wirteChara;
    protected volatile BluetoothGatt mBluetoothGatt;
    public int mStatus;
    Context context;
    BluetoothAdapter mBluetoothAdapter;
    BluetoothDevice equilDevice;
    boolean connected;
    boolean connecting;
    String macAddrss;

    @Inject
    public EquilBLE(AAPSLogger aapsLogger, Context context, SP sp) {
        this.aapsLogger = aapsLogger;
        this.context = context;
        this.sp = sp;

    }

    public boolean isConnecting() {
        return connecting;
    }

    public boolean isConnected() {
        return connected;
    }

    public void initAdapter() {
        final BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager == null) {
            return;
        }
        mBluetoothAdapter = bluetoothManager.getAdapter();
    }

    public synchronized void unBond(String transmitterMAC) {

        if (transmitterMAC == null) return;
        try {
            final BluetoothAdapter mBluetoothAdapter =
                    ((BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();
            final Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
            if (pairedDevices.size() > 0) {
                for (BluetoothDevice device : pairedDevices) {
                    if (device.getAddress() != null) {
                        if (device.getAddress().equals(transmitterMAC)) {
                            try {
                                Method m = device.getClass().getMethod("removeBond", (Class[]) null);
                                m.invoke(device, (Object[]) null);
                            } catch (Exception e) {
                            }
                        }

                    }
                }
            }
        } catch (Exception e) {
        }
    }

    public void init(EquilManager equilManager) {
        macAddrss = equilManager.getAddress();
        this.equilManager = equilManager;
        aapsLogger.warn(LTag.PUMPBTCOMM, "initGatt======= ");
        initAdapter();
        mGattCallback = new BluetoothGattCallback() {
            @Override
            public synchronized void onConnectionStateChange(BluetoothGatt gatt, int status, int i2) {
                super.onConnectionStateChange(gatt, status, i2);

//            BluetoothProfile.STATE_CONNECTED
                String str = i2 == 2 ? "CONNECTED" : "DISCONNECTED";
                StringBuilder sb = new StringBuilder();
                sb.append("onConnectionStateChange called with status:");
                sb.append(status);
                sb.append(", state:");
                sb.append(str);
                sb.append("， i2: ");
                sb.append(i2);
                sb.append("， error133: ");
                aapsLogger.debug(LTag.EQUILBLE, "onConnectionStateChange " + sb.toString());
                mStatus = status;
                connecting = false;
                if (status == 133) {
                    baseCmd = null;
                    disconnect();
                    SystemClock.sleep(50);
                    unBond(macAddrss);
                    aapsLogger.debug(LTag.EQUILBLE, "error133 ");
                    return;
                }
                if (i2 == 2) {

                    connected = true;
                    handler.removeMessages(TIME_OUT_CONNECT_WHAT);
                    if (mBluetoothGatt != null) {
                        mBluetoothGatt.discoverServices();
                    }
                    rxBus.send(new EventPumpStatusChanged(EventPumpStatusChanged.Status.CONNECTED));
                } else if (i2 == 0) {
                    baseCmd = null;
                    disconnect();
                }

            }

            @Override
            public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
                super.onReadRemoteRssi(gatt, rssi, status);
            }

            @Override
            public synchronized void onServicesDiscovered(BluetoothGatt gatt, int status) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    aapsLogger.debug(LTag.EQUILBLE, "onServicesDiscovered received: " + status);
                    return;
                }
                final BluetoothGattService service =
                        gatt.getService(UUID.fromString(GattAttributes.SERVICE_RADIO));
                if (service != null) {
                    notifyChara =
                            service.getCharacteristic(UUID.fromString(GattAttributes.NRF_UART_NOTIFY));
                    wirteChara = service.getCharacteristic(UUID.fromString(GattAttributes.NRF_UART_WIRTE));
                    rxBus.send(new EventPumpStatusChanged(EventPumpStatusChanged.Status.CONNECTED));
                    openNotification();
                    requestHighPriority();
                }

            }

            @Override
            public void onCharacteristicWrite(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic, final int status) {
//                Log.e(TAG, "onCharacteristicWrite: status===" + status);
                try {
//                    Thread.sleep(20);
                    SystemClock.sleep(20);
                    writeData();
                } catch (Exception e) {
                }
            }

            @Override
            public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                onCharacteristicChanged(gatt, characteristic);
            }

            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
                requestHighPriority();
                decode(characteristic.getValue(), 1);
            }

            @Override
            public synchronized void onDescriptorWrite(BluetoothGatt gatt, final BluetoothGattDescriptor descriptor,
                                                       int status) {
                aapsLogger.debug(LTag.EQUILBLE, "onDescriptorWrite received: " + status);

                if (status == BluetoothGatt.GATT_SUCCESS) {
                    aapsLogger.debug(LTag.EQUILBLE, "onDescriptorWrite: Wrote GATT Descriptor successfully.");
                    ready();
                } else {
                }
            }
        };
    }

    public void openNotification() {
        aapsLogger.debug(LTag.EQUILBLE, "openNotification: " + isConnected());
        boolean r0 = mBluetoothGatt.setCharacteristicNotification(notifyChara, true);
        if (r0) {
            BluetoothGattDescriptor descriptor = notifyChara
                    .getDescriptor(GattAttributes.mCharacteristicConfigDescriptor);
            byte[] v = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;
            descriptor.setValue(v);
            boolean flag = mBluetoothGatt.writeDescriptor(descriptor);
            aapsLogger.debug(LTag.EQUILBLE, "openNotification: " + flag);
        }
    }

    @SuppressLint("MissingPermission")
    public void requestHighPriority() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && mBluetoothGatt != null) {
            mBluetoothGatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH);
        }
    }

    public void ready() {
        aapsLogger.debug(LTag.EQUILBLE, "ready: " + "===" + baseCmd);
        runNext = false;
        dataList = new ArrayList<>();
        flag = true;
        writeConf = false;
        if (baseCmd != null) {
            equilResponse = baseCmd.getEquilResponse();
            indexData = 0;
            writeData();
        }
    }


    public void nextCmd2() {
        dataList = new ArrayList<>();
        flag = true;
        writeConf = false;

        aapsLogger.debug(LTag.EQUILBLE, "nextCmd===== " + baseCmd.isEnd);
        if (baseCmd != null) {
            runNext = false;
            equilResponse = baseCmd.getNextEquilResponse();
            if (equilResponse == null || equilResponse.getSend() == null || equilResponse.getSend().size() == 0) {
                return;
            }
            indexData = 0;
            writeData();
        }
    }

    public void disconnect() {
        connected = false;
        aapsLogger.debug(LTag.EQUILBLE, "Closing GATT connection");
        // Close old connection
        if (mBluetoothGatt != null) {
            mBluetoothGatt.disconnect();
            mBluetoothGatt.close();
            mBluetoothGatt = null;
        }
        if (baseCmd != null) {
            if (baseCmd instanceof CmdLargeBasalSet) {
                return;
            }
        }

        rxBus.send(new EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTED));
    }

    boolean autoScan = false;

    public void findEquil(String mac) {
        initAdapter();
        aapsLogger.debug(LTag.EQUILBLE, "findEquil: " + mac + "=====" + connected);
        if (TextUtils.isEmpty(mac)) {
            return;
        }
        this.equilDevice = this.mBluetoothAdapter.getRemoteDevice(mac);
        if (connected) {
            return;
        }
        if (autoScan) {
            startScan();
        } else {
            connectEquil(equilDevice);
        }
    }

    public void connectEquil(BluetoothDevice device) {
//        disconnect();

        handler.postDelayed(new Runnable() {
            @Override public void run() {
                if (device != null) {
                    aapsLogger.debug(LTag.EQUILBLE, "connectEquil======");
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        mBluetoothGatt = device.connectGatt(context, false, mGattCallback, BluetoothDevice.TRANSPORT_LE);
                    } else {
                        mBluetoothGatt = device.connectGatt(context, false, mGattCallback);
                    }
                    rxBus.send(new EventPumpStatusChanged(EventPumpStatusChanged.Status.CONNECTING));
                    connecting = true;
                }
            }
        }, 500);

    }


    BaseCmd baseCmd;
    BaseCmd preCmd;

    public void writeCmd(BaseCmd baseCmd) {
        aapsLogger.debug(LTag.EQUILBLE, "writeCmd: " + baseCmd.getStatusDescription());
        this.baseCmd = baseCmd;
        String macAddrss;
        if (baseCmd instanceof CmdPair) {
            macAddrss = ((CmdPair) baseCmd).getAddress();
            autoScan = false;
        } else {
            macAddrss = equilManager.getAddress();
            autoScan = true;
        }
        if (connected && preCmd != null) {
            baseCmd.setRunCode(preCmd.getRunCode());
            baseCmd.setRunPwd(preCmd.getRunPwd());
            nextCmd2();
            return;
        }
        preCmd = baseCmd;
        handler.sendEmptyMessageDelayed(TIME_OUT_CONNECT_WHAT, baseCmd.getConnectTimeOut());
        findEquil(macAddrss);
    }

    EquilResponse equilResponse;
    int indexData;

    public void writeData() {
        if (indexData < equilResponse.getSend().size()) {
            byte[] data = equilResponse.getSend().get(indexData).array();
            write(data);
            indexData++;
        } else {
        }
    }

    public void write(byte[] bytes) {
        if (wirteChara == null || mBluetoothGatt == null) {
            return;
        }
        if (bytes == null) {
            return;
        }
        wirteChara.setValue(bytes);
        aapsLogger.debug(LTag.EQUILBLE, "write: " + Utils.bytesToHex(bytes));
        mBluetoothGatt.writeCharacteristic(wirteChara);
    }

    List<String> dataList = new ArrayList<>();
    boolean flag = true;
    boolean runNext = false;

    public synchronized void decode(byte[] buffer, int len) {
        String str = Utils.bytesToHex(buffer);
        aapsLogger.debug(LTag.EQUILBLE, "decode=====" + str);
        if (flag) {
            EquilResponse equilResponse = baseCmd.decodeEquilPacket(buffer);
            if (equilResponse != null) {
                if (!writeConf && equilResponse != null) {
                    writeConf(equilResponse);
                }
                dataList = new ArrayList<>();
            }
        }
    }

    boolean writeConf;

    public void writeConf(EquilResponse equilResponse) {
        try {
            dataList = new ArrayList<>();
            flag = true;
            this.equilResponse = equilResponse;
            indexData = 0;
            writeData();
            writeConf = false;
            runNext = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static final int TIME_OUT = 90000;
    public static final int TIME_OUT_WHAT = 0x12;
    public static final int TIME_OUT_CONNECT_WHAT = 0x13;
    public Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case TIME_OUT_WHAT:
                    stopScan();
                    break;
                case TIME_OUT_CONNECT_WHAT:
                    stopScan();
                    aapsLogger.debug(LTag.EQUILBLE, "TIME_OUT_CONNECT_WHAT====");
                    disconnect();
                    synchronized (baseCmd) {
                        baseCmd.setCmdStatus(false);
                        baseCmd.notify();
                    }
                    break;
            }
        }
    };
    private boolean startTrue;

    public void startScan() {
        macAddrss = equilManager.getAddress();
        if (TextUtils.isEmpty(macAddrss) || macAddrss == null) {
            return;
        }
        aapsLogger.debug(LTag.EQUILBLE,
                "startScan====" + startTrue + "====" + macAddrss + "===");
        if (startTrue) {
            return;
        }
        startTrue = true;
        BluetoothLeScanner bluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
        bluetoothLeScanner.startScan(buildScanFilters(), buildScanSettings(), scanCallback);

    }

    public void getEquilStatus() {
        aapsLogger.debug(LTag.EQUILBLE,
                "getEquilStatus====" + startTrue + "====" + connected);
        if (startTrue || connected) {
            return;
        }
        baseCmd = null;
        autoScan = false;
        startScan();
    }

    private List<ScanFilter> buildScanFilters() {
        ArrayList scanFilterList = new ArrayList<>();
        if (TextUtils.isEmpty(macAddrss)) {
            return scanFilterList;
        }

        ScanFilter.Builder scanFilterBuilder = new ScanFilter.Builder();
        scanFilterBuilder.setDeviceAddress(macAddrss);
        scanFilterList.add(scanFilterBuilder.build());
        return scanFilterList;
    }

    @TargetApi(Build.VERSION_CODES.M)
    private ScanSettings buildScanSettings() {
        ScanSettings.Builder builder = new ScanSettings.Builder();
        builder.setReportDelay(0);
        return builder.build();
    }

    ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            String name = result.getDevice().getName();
            if (!TextUtils.isEmpty(name)) {
                try {
                    equilManager.decodeData(result.getScanRecord().getBytes());
                    stopScan();
                    if (autoScan) {
                        connectEquil(result.getDevice());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
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

    public void stopScan() {
        startTrue = false;
        handler.removeMessages(TIME_OUT_WHAT);
        if (mBluetoothAdapter == null) {
            return;
        }
        BluetoothLeScanner bluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
        if (isBluetoothAvailable()) {
            bluetoothLeScanner.stopScan(scanCallback);
        }
    }

    public boolean isBluetoothAvailable() {
        return (mBluetoothAdapter != null &&
                mBluetoothAdapter.isEnabled() &&
                mBluetoothAdapter.getState() == BluetoothAdapter.STATE_ON);
    }

}
