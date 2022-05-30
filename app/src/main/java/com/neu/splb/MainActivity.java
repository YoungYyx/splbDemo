package com.neu.splb;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.SocketException;
import java.net.UnknownHostException;

import static com.neu.splb.ApiTest.MSG_RECEIVE;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    private static final String PKG_NAME = "com.huawei.demo";

    private static Context context;

    private static MyHandler handler;

    private static final String yunIP = "47.95.28.241";

    private static final String localIP = "172.22.5.16";

    private static final int splbPort = 18000;

    private static final int splbWifiPort = 18001;

    private static final int splbLtePort = 18002;

    private static final int udpPort = 19000;

    private static final int lteTCPPort = 16000;

    private static final int wifiTCPport = 17000;
    //Service service=new Service();
    SocketService service = new SocketService();

    Button buttonStart,buttonEnd,buttonTrx;

    ArrayAdapter<String> adapter1,adapter2,adapter3;

    private String[] document={"document1","document2","a.txt"};

    private String[] trxMode={"LTE单路传输","WIFI单路传输","WIFI+LTE双路聚合"};

    private String[] trxOccasion={"手机->云端 数据传输","手机->手机 数据传输"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initHandler();
        initSpinner(adapter1,document,R.id.spinner1);
        initSpinner(adapter2,trxMode,R.id.spinner2);
        initSpinner(adapter3,trxOccasion,R.id.spinner3);
        initButton1();
        initButtonTrx();
        initButton2();
        context = getApplicationContext();
    }
    private void initSpinner(ArrayAdapter<String> adapter,String[] data,int id){
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, data);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        Spinner spinner = findViewById(id);
        spinner.setAdapter(adapter);
    }

    private void initButton1(){
        buttonStart=findViewById(R.id.button1);
        buttonStart.setOnClickListener(new View.OnClickListener() {
            @SuppressLint("SetTextI18n")
            @Override
            public void onClick(View view) {
                Spinner spinner1=findViewById(R.id.spinner1);
                Spinner spinner2=findViewById(R.id.spinner2);
                Spinner spinner3=findViewById(R.id.spinner3);
                String filename=spinner1.getSelectedItem().toString();
                String trxmode=spinner2.getSelectedItem().toString();
                String trxoccasion=spinner3.getSelectedItem().toString();
                EditText editText1=findViewById(R.id.editText1);
                EditText editText2=findViewById(R.id.editText2);
                EditText editText3=findViewById(R.id.editText3);
                String desIP=editText1.getText().toString();
                String desWifiIp = editText2.getText().toString();
                String desLteIp = editText3.getText().toString();
                if(desIP==null || "".equals(desIP)){
                    desIP = yunIP;
                }
                if(desIP==null || "".equals(desIP)){
                    desIP = localIP;
                }
                if(desWifiIp==null || "".equals(desWifiIp)){
                    desWifiIp = localIP;
                }
                if(desLteIp==null || "".equals(desLteIp)){
                    desLteIp = localIP;
                }
                if(trxOccasion[0].equals(trxoccasion)){
                    if(trxMode[0].equals(trxmode)){
                        try {
                            service.testLteTCP(desIP,lteTCPPort);
                        } catch (SocketException | InterruptedException | UnknownHostException e) {
                            e.printStackTrace();
                        }
                    }else if(trxMode[1].equals(trxmode)){
                        try {
                            service.testWiFiTCP(desIP,wifiTCPport);
                        } catch (SocketException | InterruptedException | UnknownHostException e) {
                            e.printStackTrace();
                        }
                    }else{
                        try {
                            service.testSplbMode(desIP,splbPort);
                        } catch (UnknownHostException | SocketException | InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }else{
                    if(trxMode[0].equals(trxmode)){
                        try {
                            service.testLteTCP(desLteIp,lteTCPPort);
                        } catch (SocketException | InterruptedException | UnknownHostException e) {
                            e.printStackTrace();
                        }
                    }else if(trxMode[1].equals(trxmode)){
                        try {
                            service.testWiFiTCP(desWifiIp,wifiTCPport);
                        } catch (SocketException | InterruptedException | UnknownHostException e) {
                            e.printStackTrace();
                        }
                    }else{
                        try {
                            service.testSplbMode(desLteIp,splbLtePort,desWifiIp,splbWifiPort);
                        } catch (UnknownHostException | SocketException | InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }



            }
        });
    }

    private void initButtonTrx(){
        buttonTrx=findViewById(R.id.buttonTrx);
        buttonTrx.setOnClickListener(new View.OnClickListener() {
            @SuppressLint("SetTextI18n")
            @Override
            public void onClick(View view) {
//                int data=Integer.parseInt(chattxt.getText().toString());
                int data=(int)(Math.random() * 400);
//                try {
//                   // service.connectToServer(data);
//                } catch (IOException e) {
//                    throw new RuntimeException(e);
//                }
                TextView textView=findViewById(R.id.debug2);
                textView.setText(""+data);
            }
        });
    }

    private void initButton2(){
        buttonEnd=findViewById(R.id.button2);
        buttonEnd.setOnClickListener(new View.OnClickListener() {
            @SuppressLint("SetTextI18n")
            @Override
            public void onClick(View view) {
                Toast.makeText(MainActivity.this,"关闭连接",Toast.LENGTH_SHORT).show();
               // service.clientEnd();
                TextView textView=findViewById(R.id.debug1);
                textView.setText("Disconnected");
                service.stopSendPkt();
            }
        });
    }
    private void initHandler() {
        handler = new MyHandler(MainActivity.this);
    }


    public static class MyHandler extends Handler {
        private final WeakReference<MainActivity> mActivity;

        MyHandler(MainActivity activity) {
            mActivity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            if (mActivity.get() == null) {
                Log.e(TAG, "current activity is null");
                return;
            }
            MainActivity activity = mActivity.get();
            switch (msg.what) {
                case MSG_RECEIVE:
                    Log.d(TAG, "receive--------------------------");
                    if (msg.obj instanceof String) {
                        String text = (String) msg.obj;
                   //     activity.setReceiveeMessage(text);
                    }
                    break;
                default:
                    Log.e(TAG, "get unknown message " + msg.what);
                    break;
            }
        }
    }

    public static MyHandler getMyHandler() {
        return handler;
    }

    public static Context getContext(){
        return context;
    }
}
