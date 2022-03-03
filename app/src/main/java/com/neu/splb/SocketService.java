package com.neu.splb;


import static java.lang.Thread.sleep;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

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
    public static int basePort = 50000;
    public static SocketService instance = null;
    private volatile boolean isStartNow = false;
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
        DatagramSocket lteSocket = null,wifiSocket = null;
        try {
            lteSocket = this.getUdpSocket();
            wifiSocket = this.getUdpSocket();
            apiInstance.bindCellularSocket(lteSocket);
            apiInstance.bindWifiSocket(wifiSocket);
        }catch (SocketException e){
            e.printStackTrace();
            return;
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

    public void testSplbMode1(String IP,int dstPort) throws UnknownHostException, SocketException, InterruptedException {
        if(isStartNow){
            return;
        }
        isStartNow = true;
        AndroidAPITest apiInstance = AndroidAPITest.getInstance();
        final DatagramSocket lteSocket = this.getUdpSocket();
        final DatagramSocket wifiSocket = this.getUdpSocket();
        final InetAddress address = InetAddress.getByName(IP);
        AtomicInteger dataSeq = new AtomicInteger(1);
        apiInstance.bindWifiSocket(wifiSocket);
        apiInstance.bindCellularSocket(lteSocket);
        sleep(3000);
        lteProbeThread = new Thread(new Runnable() { //启动线程在lte网卡发送探测包
            @Override
            public void run() {
                System.out.println("running lte probe");
                SplbHdr probeHdr = new SplbHdr(PacketType.PROBEPKG,1,1);
                try {
                    while(!Thread.currentThread().isInterrupted()){
                        byte[] probe = probeHdr.toByteArray();
                        DatagramPacket packet = new DatagramPacket(probe,probe.length,address,dstPort);
                        lteSocket.send(packet);
                        sleep(1000);
                        probeHdr.probeSeq++;
                    }
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }
                lteSocket.close();
            }
        });
        lteListenAndSend = new Thread(new Runnable() { //启动线程在lte网卡监听探测包
            @Override
            public void run() {
                System.out.println("running lte send probe");
                byte[] realData = new byte[512];
                for (int i = 0; i < realData.length; i++) {
                    realData[i] = 1;
                }
                try {
                    while(!Thread.currentThread().isInterrupted()){
                        byte[] data = new byte[1024];
                        DatagramPacket probePacket = new DatagramPacket(data,data.length);
                        lteSocket.receive(probePacket);
                        InetAddress srcIP = probePacket.getAddress();
                        int srcPort = probePacket.getPort();
                        byte[] msg = probePacket.getData();
                        SplbHdr hdr = new SplbHdr(msg);
                        hdr.type = PacketType.DATAPKG;//改为数据包类型
                        hdr.dataSeq = dataSeq.getAndIncrement();   //数据序号自增
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
                System.out.println("running wifi probe");
                SplbHdr probeHdr = new SplbHdr(PacketType.PROBEPKG,1,1);
                InetAddress address = null;
                try {
                    address = InetAddress.getByName(IP);
                    while(!Thread.currentThread().isInterrupted()){
                        byte[] probe = probeHdr.toByteArray();
                        DatagramPacket packet = new DatagramPacket(probe,probe.length,address,dstPort);
                        wifiSocket.send(packet);
                        sleep(1000);
                        probeHdr.probeSeq++;
                    }
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }
                wifiSocket.close();
            }
        });

        wifiListenAndSend = new Thread(new Runnable() { //启动线程在wifi网卡监听探测包
            @Override
            public void run() {
                byte[] realData = new byte[512];
                for (int i = 0; i < realData.length; i++) {
                    realData[i] = 1;
                }
                try {
                    while(!Thread.currentThread().isInterrupted()){
                        byte[] data = new byte[530];
                        DatagramPacket probePacket = new DatagramPacket(data,data.length);
                        wifiSocket.receive(probePacket);
                        InetAddress srcIP = probePacket.getAddress();
                        int srcPort = probePacket.getPort();
                        byte[] msg = probePacket.getData();
                        SplbHdr hdr = new SplbHdr(msg);
                        hdr.type = PacketType.DATAPKG;//改为数据包类型
                        hdr.dataSeq = dataSeq.getAndIncrement(); //数据序号自增
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
    public void stopSendPkt(){
        if(lteProbeThread != null){
            lteProbeThread.interrupt();
            lteProbeThread = null;
        }
        if(wifiProbeThread != null){
            wifiProbeThread.interrupt();
            wifiProbeThread = null;
        }
        if(lteListenAndSend != null){
            lteListenAndSend .interrupt();
            lteListenAndSend = null;
        }
        if(wifiListenAndSend != null){
            wifiListenAndSend.interrupt();
            wifiListenAndSend = null;
        }
        isStartNow = false;
    }
    public void testWiFiPath(String IP, int dstPort) throws SocketException, InterruptedException, UnknownHostException {
        if(isStartNow){
            return;
        }
        final InetAddress address = InetAddress.getByName(IP);
        AndroidAPITest apiInstance = AndroidAPITest.getInstance();
        final DatagramSocket wifiSocket = this.getUdpSocket();
        apiInstance.bindWifiSocket(wifiSocket);
        sleep(3000);
        wifiListenAndSend = new Thread(new Runnable() { //启动线程在lte网卡发送数据包
            @Override
            public void run() {
                System.out.println("running wifi");
                SplbHdr probeHdr = new SplbHdr(PacketType.DATAPKG,0,1);
                byte[] realData = new byte[512];
                for (int i = 0; i < realData.length; i++) {
                    realData[i] = 1;
                }
                try {
                    while(!Thread.currentThread().isInterrupted()){
                        byte[] sendData =  byteMerger(probeHdr.toByteArray(),realData);
                        DatagramPacket packet = new DatagramPacket(sendData,sendData.length,address,dstPort);
                        wifiSocket.send(packet);
                       // sleep(1000);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                wifiSocket.close();
            }
        });
    }

    public void testLtePath(String IP, int dstPort) throws SocketException, InterruptedException, UnknownHostException {
        if(isStartNow){
            return;
        }
        final InetAddress address = InetAddress.getByName(IP);
        AndroidAPITest apiInstance = AndroidAPITest.getInstance();
        final DatagramSocket lteSocket = this.getUdpSocket();
        apiInstance.bindCellularSocket(lteSocket);
        sleep(3000);
        lteListenAndSend = new Thread(new Runnable() { //启动线程在lte网卡发送数据包
            @Override
            public void run() {
                System.out.println("running wifi");
                SplbHdr probeHdr = new SplbHdr(PacketType.DATAPKG,0,1);
                byte[] realData = new byte[512];
                for (int i = 0; i < realData.length; i++) {
                    realData[i] = 1;
                }
                try {
                    while(!Thread.currentThread().isInterrupted()){
                        byte[] sendData =  byteMerger(probeHdr.toByteArray(),realData);
                        DatagramPacket packet = new DatagramPacket(sendData,sendData.length,address,dstPort);
                        lteSocket.send(packet);
                        // sleep(1000);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
               lteSocket.close();
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
