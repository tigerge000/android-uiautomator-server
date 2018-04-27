package com.github.uiautomator;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;

public class MainActivity extends Activity {
    private final String TAG = "ATXMainActivity";

    private ShellHttpServer shellHttpServer;

    private static int PORT = 9999;

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            Log.i(TAG, "service connected");
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.i(TAG, "service disconnected");

            // restart service
            Intent intent = new Intent(MainActivity.this, Service.class);
            startService(intent);
//            bindService(intent, connection, BIND_IMPORTANT | BIND_AUTO_CREATE);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Intent serviceIntent = new Intent(this, Service.class);
        startService(serviceIntent);
        bindService(serviceIntent, connection, BIND_IMPORTANT | BIND_AUTO_CREATE);


        shellHttpServer = new ShellHttpServer(PORT);
        try {
            shellHttpServer.start();
        } catch (IOException e) {
            e.printStackTrace();
        }

        Button btnFinish = (Button) findViewById(R.id.btn_finish);
        btnFinish.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                unbindService(connection);
                stopService(new Intent(MainActivity.this, Service.class));
                finish();
            }
        });

        Button btnIdentify = (Button) findViewById(R.id.btn_identify);
        btnIdentify.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, IdentifyActivity.class);
                Bundle bundle = new Bundle();
                bundle.putString("theme", "RED");
                intent.putExtras(bundle);
                startActivity(intent);
            }
        });

        ((Button) findViewById(R.id.accessibility)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            }
        });

        ((Button) findViewById(R.id.development_settings)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
            }
        });

        ((Button) findViewById(R.id.stop_uiautomator)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Request request = new Request.Builder()
                        .url("http://127.0.0.1:7912/uiautomator")
                        .delete()
                        .build();
                new OkHttpClient().newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        e.printStackTrace();
                        Looper.prepare();
                        Toast.makeText(MainActivity.this, "Uiautomator already stopped ", Toast.LENGTH_SHORT).show();
                        Looper.loop();
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        Looper.prepare();
                        Toast.makeText(MainActivity.this, "Uiautomator stopped", Toast.LENGTH_SHORT).show();
                        Looper.loop();
                    }
                });
            }
        });


        ((Button) findViewById(R.id.start_uiautomator)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Request request = new Request.Builder()
                        .url("http://127.0.0.1:7912/uiautomator")
                        .post(null)
                        .build();
                new OkHttpClient().newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        e.printStackTrace();
                        Looper.prepare();
                        Toast.makeText(MainActivity.this, "Uiautomator already stared ", Toast.LENGTH_SHORT).show();
                        Looper.loop();
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        Looper.prepare();
                        Toast.makeText(MainActivity.this, "Uiautomator stared", Toast.LENGTH_SHORT).show();
                        Looper.loop();
                    }
                });
            }
        });

        ((Button) findViewById(R.id.stop_atx_agent)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Request request = new Request.Builder()
                        .url("http://127.0.0.1:7912/stop")
                        .get()
                        .build();
                new OkHttpClient().newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        e.printStackTrace();
                        Looper.prepare();
                        Toast.makeText(MainActivity.this, "server already stopped", Toast.LENGTH_SHORT).show();
                        Looper.loop();
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        Looper.prepare();
                        Toast.makeText(MainActivity.this, "server stopped", Toast.LENGTH_SHORT).show();
                        Looper.loop();
                    }
                });
            }
        });

        Intent intent = getIntent();
        boolean isHide = intent.getBooleanExtra("hide", false);
        if (isHide) {
            Log.i(TAG, "launch args hide:true, move to background");
            moveTaskToBack(true);
        }

        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        int ip = wifiManager.getConnectionInfo().getIpAddress();
        String ipStr = (ip & 0xFF) + "." + ((ip >> 8) & 0xFF) + "." + ((ip >> 16) & 0xFF) + "." + ((ip >> 24) & 0xFF);
        TextView textViewIP = (TextView) findViewById(R.id.ip_address);
        textViewIP.setText("IP地址:" + ipStr);
        textViewIP.setTextColor(Color.BLUE);

        TextView textViewAtx = (TextView) findViewById(R.id.atx_port);
        textViewAtx.setText("ATX端口:7912");
        textViewAtx.setTextColor(Color.BLUE);

        TextView textViewUi = (TextView) findViewById(R.id.ui_port);
        textViewUi.setText("UiAutomator端口:9008");
        textViewUi.setTextColor(Color.BLUE);

        TextView textViewShell = (TextView) findViewById(R.id.shell_port);
        textViewShell.setText("Shell端口:9999");
        textViewShell.setTextColor(Color.BLUE);

    }

    @Override
    protected void onRestart() {
        super.onRestart();
    }

    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
