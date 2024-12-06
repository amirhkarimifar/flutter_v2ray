package com.github.blueboytm.flutter_v2ray.v2ray.services;

import android.app.Service;
import android.content.Intent;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import com.github.blueboytm.flutter_v2ray.v2ray.core.V2rayCoreManager;
import com.github.blueboytm.flutter_v2ray.v2ray.interfaces.V2rayServicesListener;
import com.github.blueboytm.flutter_v2ray.v2ray.utils.AppConfigs;
import com.github.blueboytm.flutter_v2ray.v2ray.utils.V2rayConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileDescriptor;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;

public class V2rayVPNService extends VpnService implements V2rayServicesListener {
    private ParcelFileDescriptor mInterface;
    private Process process;
    private V2rayConfig v2rayConfig;
    private boolean isRunning = true;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForegroundWithInitialNotification();
        V2rayCoreManager.getInstance().setUpListener(this);
    }
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "V2Ray Background Service",
                NotificationManager.IMPORTANCE_LOW);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void startForegroundWithInitialNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("V2Ray Starting...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true);

        startForeground(NOTIFICATION_ID, builder.build());
    }

    public void updateNotification(V2rayConfig config, String duration, 
                                 long uploadSpeed, long downloadSpeed,
                                 long totalUpload, long totalDownload) {
        this.currentConfig = config;

        // Format traffic statistics
        String uploadText = formatSpeed(uploadSpeed);
        String downloadText = formatSpeed(downloadSpeed);
        String totalUploadText = formatTraffic(totalUpload);
        String totalDownloadText = formatTraffic(totalDownload);

        // Create notification content
        String contentText = String.format("↑ %s  ↓ %s\nTotal: ↑ %s  ↓ %s\n%s", 
            uploadText, downloadText, totalUploadText, totalDownloadText, duration);

        // Create disconnect intent
        Intent stopIntent = new Intent(this, V2rayVPNService.class);
        stopIntent.putExtra("COMMAND", AppConfigs.V2RAY_SERVICE_COMMANDS.STOP_SERVICE);
        PendingIntent pendingIntent = PendingIntent.getService(
            this, 0, stopIntent, 
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? 
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT :
                PendingIntent.FLAG_UPDATE_CURRENT
        );

        // Create content intent
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (launchIntent != null) {
            launchIntent.setAction("FROM_NOTIFICATION");
            launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        }
        PendingIntent contentIntent = PendingIntent.getActivity(
            this, 0, launchIntent, 
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? 
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT :
                PendingIntent.FLAG_UPDATE_CURRENT
        );

        // Build notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(config.APPLICATION_ICON)
            .setContentTitle(config.REMARK)
            .setContentText(contentText)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(contentText))
            .addAction(0, config.NOTIFICATION_DISCONNECT_BUTTON_NAME, pendingIntent)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false);

        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    private String formatSpeed(long bytesPerSecond) {
        if (bytesPerSecond < 1024) return bytesPerSecond + " B/s";
        if (bytesPerSecond < 1024 * 1024) return String.format("%.1f KB/s", bytesPerSecond / 1024.0);
        if (bytesPerSecond < 1024 * 1024 * 1024) return String.format("%.1f MB/s", bytesPerSecond / (1024.0 * 1024.0));
        return String.format("%.1f GB/s", bytesPerSecond / (1024.0 * 1024.0 * 1024.0));
    }

    private String formatTraffic(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
}
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        
        if (intent == null) {
                
            return START_NOT_STICKY;
        }
        AppConfigs.V2RAY_SERVICE_COMMANDS startCommand = (AppConfigs.V2RAY_SERVICE_COMMANDS) intent.getSerializableExtra("COMMAND");
        if (startCommand.equals(AppConfigs.V2RAY_SERVICE_COMMANDS.START_SERVICE)) {
            v2rayConfig = (V2rayConfig) intent.getSerializableExtra("V2RAY_CONFIG");
            if (v2rayConfig == null) {
                this.onDestroy();
            }
            if (V2rayCoreManager.getInstance().isV2rayCoreRunning()) {
                V2rayCoreManager.getInstance().stopCore();
            }
            if (V2rayCoreManager.getInstance().startCore(v2rayConfig)) {
                Log.e(V2rayProxyOnlyService.class.getSimpleName(), "onStartCommand success => v2ray core started.");
            } else {
                this.onDestroy();
            }
        } else if (startCommand.equals(AppConfigs.V2RAY_SERVICE_COMMANDS.STOP_SERVICE)) {
            V2rayCoreManager.getInstance().stopCore();
            AppConfigs.V2RAY_CONFIG = null;
        } else if (startCommand.equals(AppConfigs.V2RAY_SERVICE_COMMANDS.MEASURE_DELAY)) {
            new Thread(() -> {
                Intent sendB = new Intent("CONNECTED_V2RAY_SERVER_DELAY");
                sendB.putExtra("DELAY", String.valueOf(V2rayCoreManager.getInstance().getConnectedV2rayServerDelay()));
                sendBroadcast(sendB);
            }, "MEASURE_CONNECTED_V2RAY_SERVER_DELAY").start();
        } else {
            this.onDestroy();
        }
        return START_STICKY;
    }

    private void stopAllProcess() {
        stopForeground(true);
        isRunning = false;
        if (process != null) {
            process.destroy();
        }
        V2rayCoreManager.getInstance().stopCore();
        try {
            stopSelf();
        } catch (Exception e) {
            //ignore
            Log.e("CANT_STOP", "SELF");
        }
        try {
            mInterface.close();
        } catch (Exception e) {
            // ignored
        }

    }

    private void setup() {
        Intent prepare_intent = prepare(this);
        if (prepare_intent != null) {
            return;
        }
        Builder builder = new Builder();
        builder.setSession(v2rayConfig.REMARK);
        builder.setMtu(1500);
        builder.addAddress("26.26.26.1", 30);

        if (v2rayConfig.BYPASS_SUBNETS == null || v2rayConfig.BYPASS_SUBNETS.isEmpty()) {
            builder.addRoute("0.0.0.0", 0);
        } else {
            for (String subnet : v2rayConfig.BYPASS_SUBNETS) {
                String[] parts = subnet.split("/");
                if (parts.length == 2) {
                    String address = parts[0];
                    int prefixLength = Integer.parseInt(parts[1]);
                    builder.addRoute(address, prefixLength);
                }
            }
        }
        if (v2rayConfig.BLOCKED_APPS != null) {
            for (int i = 0; i < v2rayConfig.BLOCKED_APPS.size(); i++) {
                try {
                    builder.addDisallowedApplication(v2rayConfig.BLOCKED_APPS.get(i));
                } catch (Exception e) {
                    //ignore
                }
            }
        }
        try {
            JSONObject json = new JSONObject(v2rayConfig.V2RAY_FULL_JSON_CONFIG);
            JSONObject dnsObject = json.getJSONObject("dns");
            JSONArray serversArray = dnsObject.getJSONArray("servers");
            for (int i = 0; i < serversArray.length(); i++) {
                String server = serversArray.getString(i);
                builder.addDnsServer(server);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        try {
            mInterface.close();
        } catch (Exception e) {
            //ignore
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false);
        }

        try {
            mInterface = builder.establish();
            isRunning = true;
            runTun2socks();
        } catch (Exception e) {
            stopAllProcess();
        }

    }

    private void runTun2socks() {
        ArrayList<String> cmd = new ArrayList<>(Arrays.asList(new File(getApplicationInfo().nativeLibraryDir, "libtun2socks.so").getAbsolutePath(),
                "--netif-ipaddr", "26.26.26.2",
                "--netif-netmask", "255.255.255.252",
                "--socks-server-addr", "127.0.0.1:" + v2rayConfig.LOCAL_SOCKS5_PORT,
                "--tunmtu", "1500",
                "--sock-path", "sock_path",
                "--enable-udprelay",
                "--loglevel", "error"));
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(cmd);
            processBuilder.redirectErrorStream(true);
            process = processBuilder.directory(getApplicationContext().getFilesDir()).start();
            new Thread(() -> {
                try {
                    process.waitFor();
                    if (isRunning) {
                        runTun2socks();
                    }
                } catch (InterruptedException e) {
                    //ignore
                }
            }, "Tun2socks_Thread").start();
            sendFileDescriptor();
        } catch (Exception e) {
            Log.e("VPN_SERVICE", "FAILED=>", e);
            this.onDestroy();
        }
    }

    private void sendFileDescriptor() {
        String localSocksFile = new File(getApplicationContext().getFilesDir(), "sock_path").getAbsolutePath();
        FileDescriptor tunFd = mInterface.getFileDescriptor();
        new Thread(() -> {
            int tries = 0;
            while (true) {
                try {
                    Thread.sleep(50L * tries);
                    LocalSocket clientLocalSocket = new LocalSocket();
                    clientLocalSocket.connect(new LocalSocketAddress(localSocksFile, LocalSocketAddress.Namespace.FILESYSTEM));
                    if (!clientLocalSocket.isConnected()) {
                        Log.e("SOCK_FILE", "Unable to connect to localSocksFile [" + localSocksFile + "]");
                    } else {
                        Log.e("SOCK_FILE", "connected to sock file [" + localSocksFile + "]");
                    }
                    OutputStream clientOutStream = clientLocalSocket.getOutputStream();
                    clientLocalSocket.setFileDescriptorsForSend(new FileDescriptor[]{tunFd});
                    clientOutStream.write(32);
                    clientLocalSocket.setFileDescriptorsForSend(null);
                    clientLocalSocket.shutdownOutput();
                    clientLocalSocket.close();
                    break;
                } catch (Exception e) {
                    Log.e(V2rayVPNService.class.getSimpleName(), "sendFd failed =>", e);
                    if (tries > 5) break;
                    tries += 1;
                }
            }
        }, "sendFd_Thread").start();
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onRevoke() {
        stopAllProcess();
    }

    @Override
    public boolean onProtect(int socket) {
        return protect(socket);
    }

    @Override
    public Service getService() {
        return this;
    }

    @Override
    public void startService() {
        setup();
    }

    @Override
    public void stopService() {
        stopAllProcess();
    }
}
