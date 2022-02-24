package com.neu.splb;

import static com.neu.splb.ApiTest.MOBILE_NAME;
import static java.lang.Thread.sleep;
import android.util.Log;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
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

    private Thread lteProbeThread = null;
    private Thread wifiProbeThread = null;
    private Thread lteListenAndSend = null;
    private Thread wifiListenAndSend = null;
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



    public void testTwoSocketSender(String IP,int dstPort){
        AndroidAPITest apiInstance = AndroidAPITest.getInstance();
        DatagramSocket lteSocket = apiInstance.getCellularSocket();
        DatagramSocket wifiSocket = apiInstance.getWifiSocket();

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
        AndroidAPITest apiInstance = AndroidAPITest.getInstance();
        final DatagramSocket lteSocket = apiInstance.getCellularSocket();
        final DatagramSocket wifiSocket = apiInstance.getWifiSocket();
        final InetAddress address = InetAddress.getByName(IP);

        lteProbeThread = new Thread(new Runnable() { //启动线程在lte网卡发送探测包
            @Override
            public void run() {
                SplbHdr probeHdr = new SplbHdr(PacketType.PROBEPKG,0,0);

                try {
                    while(!Thread.currentThread().isInterrupted()){
                        byte[] probe = probeHdr.toByteArray();
                        DatagramPacket packet = new DatagramPacket(probe,probe.length,address,dstPort);
                        lteSocket.send(packet);
                        sleep(1);
                        probeHdr.probeSeq++;
                    }
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        lteListenAndSend = new Thread(new Runnable() { //启动线程在lte网卡监听探测包
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
                        lteSocket.receive(probePacket);
                        InetAddress srcIP = probePacket.getAddress();
                        int srcPort = probePacket.getPort();
                        byte[] msg = probePacket.getData();
                        SplbHdr hdr = new SplbHdr(msg);
                        hdr.type = PacketType.DATAPKG;//改为数据包类型
                        byte[] sendData =  byteMerger(hdr.toByteArray(),realData);
                        DatagramPacket sendPacket = new DatagramPacket(sendData,sendData.length,srcIP,srcPort);
                        lteSocket.send(sendPacket);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        wifiProbeThread = new Thread(new Runnable() { //启动线程在wifi网卡发送探测包
            @Override
            public void run() {
                SplbHdr probeHdr = new SplbHdr(PacketType.PROBEPKG,1,0);
                InetAddress address = null;
                try {
                    address = InetAddress.getByName(IP);
                    while(!Thread.currentThread().isInterrupted()){
                        byte[] probe = probeHdr.toByteArray();
                        DatagramPacket packet = new DatagramPacket(probe,probe.length,address,dstPort);
                        wifiSocket.send(packet);
                        sleep(1);
                        probeHdr.probeSeq++;
                    }
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });

        wifiListenAndSend = new Thread(new Runnable() { //启动线程在wifi网卡监听探测包
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
                        wifiSocket.receive(probePacket);
                        InetAddress srcIP = probePacket.getAddress();
                        int srcPort = probePacket.getPort();
                        byte[] msg = probePacket.getData();
                        SplbHdr hdr = new SplbHdr(msg);
                        hdr.type = PacketType.DATAPKG;//改为数据包类型
                        byte[] sendData =  byteMerger(hdr.toByteArray(),realData);
                        DatagramPacket sendPacket = new DatagramPacket(sendData,sendData.length,srcIP,srcPort);
                        wifiSocket.send(sendPacket);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        lteProbeThread.start();
        wifiProbeThread.start();
        lteListenAndSend.start();
        wifiListenAndSend.start();
    }
    public void stopTest(){
        if (lteProbeThread != null){
            lteProbeThread.interrupt();
            wifiProbeThread.interrupt();
            lteListenAndSend.interrupt();
            wifiListenAndSend.interrupt();
            lteProbeThread = null;
            wifiProbeThread = null;
            lteListenAndSend = null;
            wifiListenAndSend = null;
        }
    }
    public static byte[] byteMerger(byte[] bt1, byte[] bt2){
        byte[] bt3 = new byte[bt1.length+bt2.length];
        System.arraycopy(bt1, 0, bt3, 0, bt1.length);
        System.arraycopy(bt2, 0, bt3, bt1.length, bt2.length);
        return bt3;
    }
}
