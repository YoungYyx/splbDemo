package com.neu.splb;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

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
import android.widget.TextView;


import java.io.FileDescriptor;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.DatagramSocket;
import java.net.DatagramSocketImpl;
import java.net.SocketException;
import java.net.UnknownHostException;

import static com.neu.splb.ApiTest.MOBILE_NAME;
import static com.neu.splb.ApiTest.MSG_RECEIVE;
import static com.neu.splb.ApiTest.WIFI_NAME;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    private static final String PKG_NAME = "com.huawei.demo";

    private static Context context;

    private static MyHandler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initHandler();
        context = getApplicationContext();
    }

    private void initHandler() {
        handler = new MyHandler(MainActivity.this);
    }

    public void handleClick(View view) {
        int id = view.getId();
        switch (id) {
//            case R.id.open_socket:
//                openSocket();
//                break;
//            case R.id.close_socket:
//                closeSocket();
//                break;
//            case R.id.register:
//                register();
//                break;
//            case R.id.unregister:
//                unregister();
//                break;
//            case R.id.get_available_net_interface:
//                getAvailableNetInterface();
//                break;
//            case R.id.get_mobile_signal_level:
//                getMobileSignalLevel();
//                break;
//            case R.id.get_wifi_signal_level:
//                getWiFiSignalLevel();
//                break;
////            case R.id.bind_to_mobile_interface:
////                bindToMobileInterface();
////                break;
////            case R.id.bind_to_wifi_interface:
////                bindToWiFiInterface();
////                break;
//            case R.id.get_traffic_state:
//                getTrafficStats();
//                break;
//            case R.id.test_website:
//                testWebSite();
//                break;
//            case R.id.test_udp_socket:
//                testNetwork();
            case R.id.test_splb_mode1:
                testSplbMode1();
                break;
            case R.id.test_splb_mode2:
                testSplbMode2();
                break;
            case R.id.test_splb_mode3:
                testSplbMode3();
                break;
            case R.id.test_splb_mode4:
                testSplbMode4();
                break;
            case R.id.test_lte_mode:
                testLteMode();
                break;
            case R.id.test_wifi_mode:
                testWiFiMode();
                break;
            case R.id.stop_test_splb_mode1:
                stopTestSplbMode1();
                break;
            case R.id.stop_test_splb_mode2:
                stopTestSplbMode2();
                break;
            case R.id.stop_test_splb_mode3:
                stopTestSplbMode3();
                break;
            case R.id.stop_test_splb_mode4:
                stopTestSplbMode4();
                break;
            case R.id.stop_test_lte_mode:
                stopTestLteMode();
                break;
            case R.id.stop_test_wifi_mode:
                stopTestWifiMode();
                break;
            default:
                break;
        }
    }

//    private void openSocket() {
//        boolean ret = ApiTest.getInstance().openLocalSocket();
//        Log.d(TAG, "openSocket ret = " + ret);
//    }
//
//    private void closeSocket() {
//        boolean ret = ApiTest.getInstance().closeLocalSocket();
//        Log.d(TAG, "closeSocket ret = " + ret);
//    }
//
//    private void register() {
//        boolean ret = ApiTest.getInstance().registerApp();
//        Log.d(TAG, "register ret = " + ret);
//    }
//
//    private void unregister() {
//        boolean ret = ApiTest.getInstance().unRegisterApp();
//        Log.d(TAG, "unregister ret = " + ret);
//    }
//
//    private void getAvailableNetInterface() {
//        boolean ret = ApiTest.getInstance().getAvailableNetInterface();
//        Log.d(TAG, "getAvailableNetInterface ret = " + ret);
//    }
//
//    private void getMobileSignalLevel() {
//        boolean ret = ApiTest.getInstance().getSignalLevel(MOBILE_NAME);
//        Log.d(TAG, "getMobileSignalLevel ret = " + ret);
//    }
//
//    private void getWiFiSignalLevel() {
//        boolean ret = ApiTest.getInstance().getSignalLevel(WIFI_NAME);
//        Log.d(TAG, "getWiFiSignalLevel ret " + ret);
//    }

//    private void bindToMobileInterface() {
//        boolean ret = ApiTest.getInstance().bindToNetInterface(MOBILE_NAME);
//        Log.d(TAG, "bindToMobileInterface ret = " + ret);
//    }
//
//    private void bindToWiFiInterface() {
//        boolean ret = ApiTest.getInstance().bindToNetInterface(WIFI_NAME);
//        Log.d(TAG, "bindToWiFiInterface ret = " + ret);
//    }

//    private void bindSocketToMobileInterface(int fd) {
//        boolean ret = ApiTest.getInstance().bindSocketToNetInterface(fd,MOBILE_NAME);
//        Log.d(TAG, "bindSocketToMobileInterface ret = " + ret);
//    }
//
//    private void bindSocketToWiFiInterface(int fd) {
//        boolean ret = ApiTest.getInstance().bindSocketToNetInterface(fd,WIFI_NAME);
//        Log.d(TAG, "bindSocketToWiFiInterface ret = " + ret);
//    }

//    private void getTrafficStats() {
//        int uid = getUid();
//        if (uid == Integer.MAX_VALUE) {
//            return;
//        }
//        String ret = ApiTest.getInstance().getNetworkSpeed(uid);
//        TextView text = findViewById(R.id.recv_msg);
//        text.setText(ret);
//    }


//    private void testBindWithLTE(){
//        Log.d(TAG, "bindSocketToLTEInterface");
//        try {
//            DatagramSocket lteSocket = SocketService.getInstance().getUdpSocket();
//            int fd = SocketService.getInstance().getSocketFd(lteSocket);
//            if(fd != -1){
//                bindSocketToMobileInterface(fd);
//            }else{
//                Log.w(TAG, "socket fd error.it should > 0");
//            }
//        }catch (SocketException e){
//            e.printStackTrace();
//        }
//    }

    private void testNetwork(){

    }
    private void testSplbMode1(){
        try {
            TextView textView = findViewById(R.id.recv_msg);
            textView.setText("splb模式1已开始");
            SocketService.getInstance().testSplbMode1("172.22.5.16",18882);
        } catch (SocketException | InterruptedException | UnknownHostException e) {
            e.printStackTrace();
            TextView textView = findViewById(R.id.recv_msg);
            textView.setText("splb模式1启动失败");
        }
    }
    private void testSplbMode2(){
            TextView textView = findViewById(R.id.recv_msg);
            textView.setText("splb模式2已开始");
    }
    private void testSplbMode3(){

    }
    private void testSplbMode4(){

    }
    private void testLteMode(){
        try {
            TextView textView = findViewById(R.id.recv_msg);
            textView.setText("LTE链路性能测试已开始");
            SocketService.getInstance().testLtePath("47.95.28.241",18882);


        } catch (SocketException | InterruptedException |UnknownHostException e) {
            e.printStackTrace();
            TextView textView = findViewById(R.id.recv_msg);
            textView.setText("LTE链路测试启动失败");
        }
    }
    private void testWiFiMode(){
        try {
            TextView textView = findViewById(R.id.recv_msg);
            textView.setText("WiFi链路性能测试已开始");
            SocketService.getInstance().testWiFiUDP("172.22.5.16",18885);
           // SocketService.getInstance().testWiFiPath("47.95.28.241",18882);

        } catch (SocketException | InterruptedException | UnknownHostException e) {
            e.printStackTrace();
            TextView textView = findViewById(R.id.recv_msg);
            textView.setText("WiFi链路测试启动失败");
        }
    }
    private void stopTestSplbMode1(){
        SocketService.getInstance().stopSendPkt();
        TextView textView = findViewById(R.id.recv_msg);
        textView.setText("splb模式1已结束");
    }
    private void stopTestSplbMode2(){
        SocketService.getInstance().stopSendPkt();
        TextView textView = findViewById(R.id.recv_msg);
        textView.setText("splb模式2已结束");
    }
    private void stopTestSplbMode3(){

    }
    private void stopTestSplbMode4(){

    }
    private void stopTestLteMode(){
        SocketService.getInstance().stopSendPkt();
        TextView textView = findViewById(R.id.recv_msg);
        textView.setText("LTE测速已结束");
    }
    private void stopTestWifiMode(){
        SocketService.getInstance().stopSendPkt();
        TextView textView = findViewById(R.id.recv_msg);
        textView.setText("WiFi测速已结束");
    }
    private void testWebSite() {
        WebView webView = findViewById(R.id.webview);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                //返回值是true的时候控制网页在WebView中去打开，如果为false则调用系统浏览器打开
                view.loadUrl(url);
                return true;
            }
        });
        // 设置js可以直接打开窗口，如window.open()，默认为false
        webView.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);
        // 是否允许执行js，默认为false。设置true时，会提醒可能造成XSS漏洞struct sockaddr_in* get_aviliable_dst_addrs(struct sockaddr_in *dst_addr)
        webView.getSettings().setJavaScriptEnabled(true);
        //是否可以缩放，默认true
        webView.getSettings().setSupportZoom(true);
        // 是否显示缩放按钮，默认false
        webView.getSettings().setBuiltInZoomControls(true);
        // 设置此属性，可任意比例缩放。大视图模式
        webView.getSettings().setUseWideViewPort(true);
        //和setUseWideViewPort(true)一起解决网页自适应问题
        webView.getSettings().setLoadWithOverviewMode(true);
        // 是否使用缓存
        webView.getSettings().setAppCacheEnabled(false);
        // DOM Storage
        webView.getSettings().setDomStorageEnabled(true);
        webView.loadUrl("https://developer.huawei.com/consumer/cn/");
    }

    private int getUid() {
        Log.d(TAG, "getUid");
        ApplicationInfo applicationInfo;
        try {
            PackageManager pm = getPackageManager();
            applicationInfo = pm.getApplicationInfo(PKG_NAME, PackageManager.GET_META_DATA);
            Log.d(TAG, "uid = " + applicationInfo.uid);
            return applicationInfo.uid;
        } catch (PackageManager.NameNotFoundException e) {
            Log.d(TAG, "getUid failed: " + e.getMessage());
            return Integer.MAX_VALUE;
        }
    }

    private void setReceiveeMessage(String text) {
        TextView textView = findViewById(R.id.recv_msg);
        textView.setText(text);
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
                        activity.setReceiveeMessage(text);
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
