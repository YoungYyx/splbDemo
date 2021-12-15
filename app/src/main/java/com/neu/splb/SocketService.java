package com.neu.splb;

import static com.neu.splb.ApiTest.MOBILE_NAME;


import android.util.Log;

import java.io.FileDescriptor;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.DatagramSocketImpl;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;


public class SocketService {
    private static final String TAG = "Socket Service";
    public static int basePort = 16666;
    public static SocketService instance = null;

    //获取类实例
    public static SocketService getInstance(){
        if (instance == null) {
            synchronized (SocketService.class) {
                if (instance == null) {
                    instance = new SocketService();
                }
            }
        }
        return instance;
    }

    public DatagramSocket getUdpSocket() throws SocketException {
        DatagramSocket socket = new DatagramSocket(basePort++);
        return socket;
    }

    public int getSocketFd(DatagramSocket socket){
        if(socket == null) return -1;
        try {
            Field $impl = socket.getClass().getDeclaredField("impl");
            $impl.setAccessible(true);
            DatagramSocketImpl socketImpl = (DatagramSocketImpl) $impl.get(socket);
            Method $getFileDescriptor = DatagramSocketImpl.class.getDeclaredMethod("getFileDescriptor");
            $getFileDescriptor.setAccessible(true);
            FileDescriptor fd = (FileDescriptor) $getFileDescriptor.invoke(socketImpl);
            Field $descriptor = fd.getClass().getDeclaredField("fd");
            $descriptor.setAccessible(true);
            return (Integer) $descriptor.get(fd);
        } catch (NoSuchFieldException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            e.printStackTrace();
        }
        return -1;
    }

    public void testTwoSocketSender(String IP,int dstPort){
        SocketService socketService = SocketService.getInstance();
        ApiTest apiService = ApiTest.getInstance();
        DatagramSocket lteSocket = null,wifiSocket = null;
        try {
            lteSocket = socketService.getUdpSocket();
            wifiSocket = socketService.getUdpSocket();
        } catch (SocketException e) {
            e.printStackTrace();
        }
        int ltefd = socketService.getSocketFd(lteSocket);
        int wififd = socketService.getSocketFd(wifiSocket);
        if(ltefd > 0 && wififd > 0){
            Log.d(TAG,"get fd success");
            boolean lteret = apiService.bindSocketToNetInterface(ltefd,MOBILE_NAME);
            boolean wifiret = apiService.getInstance().bindSocketToNetInterface(wififd,MOBILE_NAME);
            Log.d(TAG,"get fd success");
        }else{
            Log.e(TAG,"error!, ltefd or wifild should > 0!");
        }
        try {
            InetAddress address = InetAddress.getByName(IP);
            String str = "hello";
            byte[] data = str.getBytes();
            DatagramPacket packet = new DatagramPacket(data,data.length,address,dstPort);
            for (int i = 0; i < 100; i++) {
                if (i % 2 == 0) {
                    lteSocket.send(packet);
                } else{
                    wifiSocket.send(packet);
                }
            }
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
