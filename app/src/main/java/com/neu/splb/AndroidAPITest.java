package com.neu.splb;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.util.Log;

import java.net.NetworkInterface;

public class AndroidAPITest {

    private static AndroidAPITest instance;

    private static final String TAG = "AndroidAPITest";

    public static AndroidAPITest getInstance(){
        if (instance == null){
            synchronized (AndroidAPITest.class){
                if (instance == null){
                    instance = new AndroidAPITest();
                    //manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
                }
            }
        }
        return instance;
    }
    public void testNetwork(){
        Context context = MainActivity.getContext();
        ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        Network[] nets = manager.getAllNetworks();
        for (int i = 0; i < nets.length; i++) {
            System.out.println(nets[i].describeContents());
        }

    }
}
