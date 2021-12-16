package com.neu.splb;

import static com.neu.splb.ApiTest.MOBILE_NAME;
import static java.lang.Thread.sleep;
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
import java.nio.ByteBuffer;

enum PacketType{
    DATAPKG((byte)0),
    PROBEPKG((byte)1);
    public byte t;
    
    PacketType(byte t) {
        this.t= t;
    }
}
class SplbHdr{
    PacketType type;
    int probeSeq;
    int dataSeq;
    SplbHdr(PacketType t,int probeSeq,int dataSeq){
        this.type = t;
        this.probeSeq = probeSeq;
        this.dataSeq = dataSeq;
    }
    SplbHdr(byte[] hdr){
        ByteBuffer byteBuffer = ByteBuffer.wrap(hdr);
        byte t = byteBuffer.get();
        if(t == 0){
            this.type = PacketType.DATAPKG;
        }else{
            this.type = PacketType.PROBEPKG;
        }
        this.probeSeq = byteBuffer.getInt();
        this.dataSeq = byteBuffer.getInt();
    }
    public byte[] toByteArray() {
        ByteBuffer buf = ByteBuffer.allocate(18);
        buf.put(this.type.t);
        buf.putInt(this.probeSeq);
        buf.putInt(this.dataSeq);
        return buf.array();
    }
}

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

    public void testSimpleSplb(String IP,int dstPort) throws UnknownHostException {
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

        final DatagramSocket finalLteSocket = lteSocket;
        final DatagramSocket finalWifiSocket = wifiSocket;
        final InetAddress address = InetAddress.getByName(IP);

        Thread lteProbeThread = new Thread(new Runnable() { //启动线程在lte网卡发送探测包
            @Override
            public void run() {
                SplbHdr probeHdr = new SplbHdr(PacketType.PROBEPKG,0,0);

                try {
                    while(!Thread.currentThread().isInterrupted()){
                        byte[] probe = probeHdr.toByteArray();
                        DatagramPacket packet = new DatagramPacket(probe,probe.length,address,dstPort);
                        finalLteSocket.send(packet);
                        sleep(1);
                        probeHdr.probeSeq++;
                    }
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });

        Thread wifiProbeThread = new Thread(new Runnable() { //启动线程在wifi网卡发送探测包
            @Override
            public void run() {
                SplbHdr probeHdr = new SplbHdr(PacketType.PROBEPKG,1,0);

                InetAddress address = null;
                try {
                    address = InetAddress.getByName(IP);
                    while(!Thread.currentThread().isInterrupted()){
                        byte[] probe = probeHdr.toByteArray();
                        DatagramPacket packet = new DatagramPacket(probe,probe.length,address,dstPort);
                        finalWifiSocket.send(packet);
                        sleep(1);
                        probeHdr.probeSeq++;
                    }
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        Thread lteListenAndSend = new Thread(new Runnable() { //启动线程在lte网卡监听探测包
            @Override
            public void run() {
                byte[] realData = new byte[530];
                for (int i = 0; i < realData.length; i++) {
                    realData[i] = 1;
                }
                SplbHdr probeHdr = new SplbHdr(PacketType.PROBEPKG,1,0);
                try {
                    while(Thread.currentThread().isInterrupted()){
                        byte[] data = new byte[1024];
                        DatagramPacket probePacket = new DatagramPacket(data,data.length);
                        finalLteSocket.receive(probePacket);
                        InetAddress srcIP = probePacket.getAddress();
                        int srcPort = probePacket.getPort();
                        byte[] msg = probePacket.getData();
                        SplbHdr hdr = new SplbHdr(msg);
                        hdr.type = PacketType.DATAPKG;//改为数据包类型
                        byte[] sendData =  byteMerger(hdr.toByteArray(),realData);
                        DatagramPacket sendPacket = new DatagramPacket(sendData,sendData.length,srcIP,srcPort);
                        finalLteSocket.send(sendPacket);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        Thread wifiListenAndSend = new Thread(new Runnable() { //启动线程在wifi网卡监听探测包
            @Override
            public void run() {
                byte[] realData = new byte[530];
                for (int i = 0; i < realData.length; i++) {
                    realData[i] = 1;
                }
                SplbHdr probeHdr = new SplbHdr(PacketType.PROBEPKG,1,0);
                try {
                    while(Thread.currentThread().isInterrupted()){
                        byte[] data = new byte[1024];
                        DatagramPacket probePacket = new DatagramPacket(data,data.length);
                        finalWifiSocket.receive(probePacket);
                        InetAddress srcIP = probePacket.getAddress();
                        int srcPort = probePacket.getPort();
                        byte[] msg = probePacket.getData();
                        SplbHdr hdr = new SplbHdr(msg);
                        hdr.type = PacketType.DATAPKG;//改为数据包类型
                        byte[] sendData =  byteMerger(hdr.toByteArray(),realData);
                        DatagramPacket sendPacket = new DatagramPacket(sendData,sendData.length,srcIP,srcPort);
                        finalWifiSocket.send(sendPacket);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

    }
    public static byte[] byteMerger(byte[] bt1, byte[] bt2){
        byte[] bt3 = new byte[bt1.length+bt2.length];
        System.arraycopy(bt1, 0, bt3, 0, bt1.length);
        System.arraycopy(bt2, 0, bt3, bt1.length, bt2.length);
        return bt3;
    }
}
