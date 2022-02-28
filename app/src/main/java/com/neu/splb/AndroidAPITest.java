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

    private DatagramSocket wifiSocket = null;

    private DatagramSocket cellularSocket = null;

    //
    public static AndroidAPITest getInstance(){
        if (instance == null){
            synchronized (AndroidAPITest.class){
                if (instance == null){
                    instance = new AndroidAPITest();
                }
            }
        }
        instance.initSocket();
        return instance;
    }
    public DatagramSocket  getWifiSocket(){
        return wifiSocket;
    }

    public DatagramSocket getCellularSocket(){
        return cellularSocket;
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
        NetworkRequest.Builder defaultBuilder = new NetworkRequest.Builder();
        NetworkRequest.Builder builder = defaultBuilder.addTransportType(NetworkCapabilities.TRANSPORT_WIFI);
        return builder.build();
    }

    //获取cellular网络类型的NetworkRequest
    private NetworkRequest getCellularNetworkRequest(){
        NetworkRequest.Builder defaultBuilder = new NetworkRequest.Builder();
        NetworkRequest.Builder builder = defaultBuilder.addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR);
        return builder.build();
    }

    public void initSocket(){
        try {
            wifiSocket = SocketService.getInstance().getUdpSocket();
            cellularSocket = SocketService.getInstance().getUdpSocket();
            bindWifiSocket();
            bindCellularSocket();
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }


    //绑定到wifi网络的socket
    private void bindWifiSocket(){
        ConnectivityManager man = getConManager();
        man.requestNetwork(getWifiNetworkRequest(),new ConnectivityManager.NetworkCallback(){
            @Override
            public void onAvailable(@NonNull Network network) {
                super.onAvailable(network);
                try {
                    System.out.println("----"+network.toString());
                    network.bindSocket(wifiSocket);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    //绑定到cellular网络的socket
    private void bindCellularSocket(){
        ConnectivityManager man = getConManager();
        man.requestNetwork(getCellularNetworkRequest(),new ConnectivityManager.NetworkCallback(){
            @Override
            public void onAvailable(@NonNull Network network) {
                super.onAvailable(network);
                try {
                    System.out.println("----"+network.toString());
                    network.bindSocket(cellularSocket);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }
}
