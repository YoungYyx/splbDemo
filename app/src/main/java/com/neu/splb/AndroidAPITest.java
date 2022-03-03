package com.neu.splb;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.SocketException;


public class AndroidAPITest {

    private static AndroidAPITest instance;

    private static final String TAG = "AndroidAPITest";

    private ConnectivityManager manager = null;

    //
    public static AndroidAPITest getInstance(){
        if (instance == null){
            synchronized (AndroidAPITest.class){
                if (instance == null){
                    instance = new AndroidAPITest();
                }
            }
        }
        return instance;
    }

    //获取ConnectivityManager对象
    private ConnectivityManager getConManager(){
        if(manager == null){
            Context context = MainActivity.getContext();
            manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            return manager;
        }else{
            return manager;
        }
    }

    //获取wifi网络类型的NetworkRequest
    private NetworkRequest getWifiNetworkRequest(){
        NetworkRequest.Builder defaultBuilder = new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        return defaultBuilder.build();
    }

    //获取cellular网络类型的NetworkRequest
    private NetworkRequest getCellularNetworkRequest(){
        NetworkRequest.Builder defaultBuilder = new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        return defaultBuilder.build();
    }

    //只在应用启动时调用一次
//    public void initSocket(){
//        try {
//            wifiSocket = SocketService.getInstance().getUdpSocket();
//            cellularSocket = SocketService.getInstance().getUdpSocket();
//            bindWifiSocket();
//            bindCellularSocket();
//        } catch (SocketException e) {
//            e.printStackTrace();
//        }
//    }


    //绑定到wifi网络的socket
    public void bindWifiSocket(DatagramSocket wifiSocket){
        ConnectivityManager man = getConManager();
        man.requestNetwork(getWifiNetworkRequest(),new ConnectivityManager.NetworkCallback(){
            @Override
            public void onAvailable(@NonNull Network network) {
                super.onAvailable(network);
                try {
                    System.out.println("1-----"+network.toString());
                    network.bindSocket(wifiSocket);
                    System.out.println("绑定wifi到"+network);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onUnavailable() {
                super.onUnavailable();
                System.out.println("1-----网络不可达");
            }
        });
    }

    //绑定到cellular网络的socket
    public void bindCellularSocket(DatagramSocket cellularSocket){
        ConnectivityManager man = getConManager();
        man.requestNetwork(getCellularNetworkRequest(),new ConnectivityManager.NetworkCallback(){
            @Override
            public void onAvailable(@NonNull Network network) {
                super.onAvailable(network);
                try {
                    System.out.println("2----"+network.toString());
                    network.bindSocket(cellularSocket);
                    System.out.println("绑定lte到"+network);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            @Override
            public void onUnavailable() {
                super.onUnavailable();
                System.out.println("2-----网络不可达");
            }
        });
    }
}
