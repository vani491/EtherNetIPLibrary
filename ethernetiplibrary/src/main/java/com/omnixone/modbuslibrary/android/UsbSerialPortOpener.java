package com.omnixone.modbuslibrary.android;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.util.List;

/** Opens the first supported USB-serial port (e.g., FTDI) and returns it via callback. */
public class UsbSerialPortOpener {

    public interface Callback {
        void onReady(AndroidUsbSerialPortIo io);
        void onError(String message);
    }

    private static final String ACTION_USB_PERMISSION = "com.omnixone.USB_PERMISSION";

    private final Context context;
    private final UsbManager usbManager;
    private BroadcastReceiver receiver;
    private UsbDevice device;
    private UsbSerialDriver driver;
    private Callback callback;

    public UsbSerialPortOpener(Context context) {
        this.context = context.getApplicationContext();
        this.usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
    }

    /** Enumerate, request permission if needed, then open and return the first port. */
    public void openFirstSupported(Callback callback) {
        this.callback = callback;

        List<UsbSerialDriver> drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);
        if (drivers == null || drivers.isEmpty()) {
            callback.onError("No USB-serial drivers found");
            return;
        }

        driver = drivers.get(0);
        device = driver.getDevice();

        if (usbManager.hasPermission(device)) {
            openNow();
            return;
        }

        // Ask user for permission (one-time)
        Intent intent = new Intent(ACTION_USB_PERMISSION);
        PendingIntent pi = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context ctx, Intent i) {
                if (!ACTION_USB_PERMISSION.equals(i.getAction())) return;
                try { context.unregisterReceiver(this); } catch (Exception ignore) {}
                boolean granted = i.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                if (!granted) {
                    callback.onError("USB permission denied");
                } else {
                    openNow();
                }
            }
        };
        context.registerReceiver(receiver, new IntentFilter(ACTION_USB_PERMISSION));
        usbManager.requestPermission(device, pi);
    }

    private void openNow() {
        UsbDeviceConnection connection = usbManager.openDevice(device);
        if (connection == null) {
            callback.onError("openDevice() returned null");
            return;
        }
        try {
            UsbSerialPort port = driver.getPorts().get(0);
            AndroidUsbSerialPortIo io = new AndroidUsbSerialPortIo(port, connection);
            callback.onReady(io);
        } catch (Exception e) {
            callback.onError("Failed to get port: " + e.getMessage());
        }
    }
}

