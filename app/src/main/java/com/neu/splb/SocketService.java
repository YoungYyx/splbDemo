package com.neu.splb;


import static java.lang.Thread.sleep;

import androidx.core.util.TimeUtils;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

enum PacketType{
    DATAPKG((byte)0),   //数据包
    PROBEPKG((byte)1),  //探测包
    ACKPKG((byte)2),    //ACK报文
    NAKPKG((byte)3);    //NAK报文

    public byte t;

    PacketType(byte t) {
        this.t= t;
    }
}
class SplbHdr{
    int probeSeq;   //探测序号
    int pathSeq;    //路径序号
    int dataSeq;    //数据序号
    PacketType type;
    byte pathNum;   //子路径编码

    SplbHdr(PacketType t,byte pathNum,int probeSeq,int pathSeq,int dataSeq){
        this.type = t;
        this.pathNum = pathNum;
        this.probeSeq = probeSeq;
        this.pathSeq = pathSeq;
        this.dataSeq = dataSeq;
    }
    SplbHdr(byte[] hdr){
        ByteBuffer byteBuffer = ByteBuffer.wrap(hdr);
        this.probeSeq = byteBuffer.getInt();
        this.pathSeq = byteBuffer.getInt();
        this.dataSeq = byteBuffer.getInt();
        byte t = byteBuffer.get();
        if(t == 0){
            this.type = PacketType.DATAPKG;
        }else if(t == 1){
            this.type = PacketType.PROBEPKG;
        }else if(t == 2){
            this.type = PacketType.ACKPKG;
        }else if(t == 3){
            this.type = PacketType.NAKPKG;
        }
        this.pathNum = byteBuffer.get();

    }
    public byte[] toByteArray() {
        ByteBuffer buf = ByteBuffer.allocate(14);
        buf.putInt(this.probeSeq);
        buf.putInt(this.pathSeq);
        buf.putInt(this.dataSeq);
        buf.put(this.type.t);
        buf.put(this.pathNum);
        return buf.array();
    }
}

public class SocketService {
    private static final String TAG = "Socket Service";
    private static int lteBaseProbeGap = 2000;   //lte探测包基础间隔, us
    private static int wifiBaseProbeGap = 200;  //wifi探测包基础间隔, us
    private static int probeScale = 200;        //探测包对应数据包比例
    private static int probeStep = 200;           //探测步长，接收连续probeStep后，将基础探测间隔进行减小
    public static int basePort = 50000;         //分配的基础端口号
    public static SocketService instance = null;
    private volatile boolean isStartNow = false;    //开始标志
    private Thread lteProbeThread = null;       //lte探测线程
    private Thread wifiProbeThread = null;      //wifi探测线程
    private Thread lteListenAndSend = null;     //lte数据线程
    private Thread wifiListenAndSend = null;    //wifi数据线程
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


    public void testTwoSocketSender(String IP,int dstPort) throws SocketException, UnknownHostException, InterruptedException {
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
        SplbHdr probeHdr = new SplbHdr(PacketType.DATAPKG,(byte)0,0,0,1);
        byte[] realData = new byte[512];
        for (int i = 0; i < realData.length; i++) {
            realData[i] = 1;
        }
        lteProbeThread = new Thread(new Runnable() { //启动线程在lte网卡发送探测包
            @Override
            public void run() {
                System.out.println("running lte probe");
                SplbHdr probeHdr = new SplbHdr(PacketType.PROBEPKG,(byte)0,1,1,1);
                while (!Thread.currentThread().isInterrupted()) {
                    byte[] sendData = byteMerger(probeHdr.toByteArray(), realData);
                    DatagramPacket packet = new DatagramPacket(sendData, sendData.length, address, dstPort);
                    try {
                        lteSocket.send(packet);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                lteSocket.close();
            }
        });
        wifiProbeThread = new Thread(new Runnable() { //启动线程在wifi网卡发送探测包
            @Override
            public void run() {
                System.out.println("running wifi probe");
                SplbHdr probeHdr = new SplbHdr(PacketType.PROBEPKG,(byte)1,1,1,1);
                while(!Thread.currentThread().isInterrupted()){
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
                }
                wifiSocket.close();
            }
        });
        lteProbeThread.start();
        wifiProbeThread.start();
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
                SplbHdr probeHdr = new SplbHdr(PacketType.PROBEPKG,(byte)0,1,0,0);
                try {
                    while(!Thread.currentThread().isInterrupted()){
                        byte[] probe = probeHdr.toByteArray();
                        DatagramPacket packet = new DatagramPacket(probe,probe.length,address,dstPort);
                        lteSocket.send(packet);
                        TimeUnit.MICROSECONDS.sleep(lteBaseProbeGap * probeScale );

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
                //填充一些数据作为payload,假的数据
                byte[] realData = new byte[512];
                for (int i = 0; i < realData.length; i++) {
                    realData[i] = 1;
                }
                int continueProbeNum = 0;   //记录连续接收的回传数据包数目
                final AtomicInteger pathSeq = new AtomicInteger(1);
                int ackedSeq = 0;           //已经ack的路径序号
                HashMap<Integer,Integer> dataMap = new HashMap<>();
                try {
                    while(!Thread.currentThread().isInterrupted()){
                        byte[] data = new byte[14];
                        DatagramPacket probePacket = new DatagramPacket(data,data.length);
                        lteSocket.receive(probePacket);
                        InetAddress srcIP = probePacket.getAddress();
                        int srcPort = probePacket.getPort();
                        byte[] msg = probePacket.getData();
                        SplbHdr hdr = new SplbHdr(msg);
                        //System.out.println("类型"+hdr.type+"，探测编号"+hdr.probeSeq+"，路径编号"+hdr.pathSeq+",数据编号"+hdr.dataSeq);
                        if(hdr.type == PacketType.PROBEPKG){ //报文为回传探测包类型
                           // System.out.println(hdr.probeSeq);
                            hdr.type = PacketType.DATAPKG;//改为数据包类型

                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    for (int i = 0; i < probeScale; i++) {
                                        hdr.dataSeq = dataSeq.getAndIncrement();   //数据序号自增
                                        hdr.pathSeq = pathSeq.getAndIncrement();
                                        byte[] sendData =  byteMerger(hdr.toByteArray(),realData);
                                        DatagramPacket sendPacket = new DatagramPacket(sendData,sendData.length,srcIP,srcPort);

                                       // System.out.println("put:"+hdr.pathSeq+","+hdr.dataSeq);
                                        try {
                                            lteSocket.send(sendPacket);
                                        } catch (IOException e) {
                                            e.printStackTrace();
                                        }
                                        try {
                                            TimeUnit.MICROSECONDS.sleep(lteBaseProbeGap);
                                        } catch (InterruptedException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                }
                            }).start();

                            continueProbeNum++;
                            if(continueProbeNum > probeStep){
                                continueProbeNum = 0;
                                lteBaseProbeGap-= probeStep;
                            }
                        }else if(hdr.type == PacketType.NAKPKG){    //NAK包，丢包了
                            continueProbeNum = 0;
                            lteBaseProbeGap += probeStep;   //增加探测包间隔
                            hdr.type = PacketType.DATAPKG;
                            int lostPathSeq = hdr.pathSeq;
                            System.out.println("丢包了:"+lostPathSeq);
                            Integer lostDataSeq = dataMap.get(lostPathSeq);
                            if(lostDataSeq == null){
                                System.out.println("--------");
                                for (int i:dataMap.keySet()) {
                                    System.out.println(i);
                                }
                            }else{
                                hdr.dataSeq = lostDataSeq.intValue();
                            }
//                            for (int i = ackedSeq+1; i < lostPathSeq ; i++) { //说明之前的数据已经接收了
//                                dataMap.remove(i);
//                            }
//                            ackedSeq = lostPathSeq - 1; //已经ack的数据为丢失报文前一个

                            hdr.pathSeq = lostPathSeq;

                            byte[] sendData =  byteMerger(hdr.toByteArray(),realData);
                            DatagramPacket sendPacket = new DatagramPacket(sendData,sendData.length,srcIP,srcPort);
                            lteSocket.send(sendPacket);
                        }else if(hdr.type == PacketType.ACKPKG){    //ACK包
                           dataMap.remove(hdr.pathSeq);
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        wifiProbeThread = new Thread(new Runnable() { //启动线程在wifi网卡发送探测包
            @Override
            public void run() {
                System.out.println("runnin wifi probe");
                SplbHdr probeHdr = new SplbHdr(PacketType.PROBEPKG,(byte)1,1,0,0);
                try {

                    while(!Thread.currentThread().isInterrupted()){
                        byte[] probe = probeHdr.toByteArray();
                        DatagramPacket packet = new DatagramPacket(probe,probe.length,address,dstPort);
                        wifiSocket.send(packet);
                        TimeUnit.MICROSECONDS.sleep(wifiBaseProbeGap * probeScale );
                        //sleep(1000);
                        probeHdr.probeSeq++;
                    }
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }
                lteSocket.close();
            }
        });

        wifiListenAndSend = new Thread(new Runnable() { //启动线程在wifi网卡监听探测包
            @Override
            public void run() {

                System.out.println("running wifi send probe");
                //填充一些数据作为payload,假的数据
                byte[] realData = new byte[512];
                for (int i = 0; i < realData.length; i++) {
                    realData[i] = 1;
                }
                int continueProbeNum = 0;   //记录连续接收的回传数据包数目
                int pathSeq = 1;            //起始路径序号
                int ackedSeq = 0;           //已经ack的路径序号
                HashMap<Integer,Integer> dataMap = new HashMap<>();
                try {
                    int counter = 0;
                    while(!Thread.currentThread().isInterrupted()){
                        byte[] data = new byte[14];
                        DatagramPacket probePacket = new DatagramPacket(data,data.length);
                        wifiSocket.receive(probePacket);
                        InetAddress srcIP = probePacket.getAddress();
                        int srcPort = probePacket.getPort();
                        byte[] msg = probePacket.getData();
                        System.out.println("长度"+msg.length);
                        for (int i = 0; i < msg.length; i++) {
                            System.out.print(msg[i]);
                        }
                        System.out.println("输出完成");

                        SplbHdr hdr = new SplbHdr(msg);
                        System.out.println("类型"+hdr.type+"，探测编号"+hdr.probeSeq+"，路径编号"+hdr.pathSeq+",数据编号"+hdr.dataSeq);
                        if(hdr.type == PacketType.PROBEPKG){ //报文为回传探测包类型
                            hdr.type = PacketType.DATAPKG;//改为数据包类型
                            for (int i = 0; i < probeScale; i++) {
                                hdr.dataSeq = dataSeq.getAndIncrement();   //数据序号自增
                                hdr.pathSeq = pathSeq++;
                                byte[] sendData =  byteMerger(hdr.toByteArray(),realData);
                                DatagramPacket sendPacket = new DatagramPacket(sendData,sendData.length,srcIP,srcPort);
                                dataMap.put(hdr.pathSeq,hdr.dataSeq);
                                wifiSocket.send(sendPacket);
                                TimeUnit.MICROSECONDS.sleep(10);
                            }
                            continueProbeNum++;
                            if(continueProbeNum > probeStep){
                                continueProbeNum = 0;
                                wifiBaseProbeGap -= probeStep;
                            }
                        }else if(hdr.type == PacketType.NAKPKG){    //NAK包
                            continueProbeNum = 0;
                            wifiBaseProbeGap += probeStep;   //增加探测包间隔
                            hdr.type = PacketType.DATAPKG;
                            int lostPathSeq = hdr.pathSeq;
                            int lostDataSeq = dataMap.get(lostPathSeq);
                            for (int i = ackedSeq+1; i < lostPathSeq ; i++) { //说明之前的数据已经接收了
                                dataMap.remove(i);
                            }
                            ackedSeq = lostPathSeq - 1; //已经ack的数据为丢失报文前一个
                            hdr.dataSeq = lostDataSeq;
                            hdr.pathSeq = lostPathSeq;
                            byte[] sendData =  byteMerger(hdr.toByteArray(),realData);
                            DatagramPacket sendPacket = new DatagramPacket(sendData,sendData.length,srcIP,srcPort);
                            wifiSocket.send(sendPacket);
                        }else if(hdr.type == PacketType.ACKPKG){    //ACK包
                            for (int i = ackedSeq+1; i <= hdr.pathSeq; i++) {
                                dataMap.remove(i);      //移除对应关系
                            }
                            ackedSeq = hdr.pathSeq;
                        }
                    }
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        lteProbeThread.start();
       // wifiProbeThread.start();
        lteListenAndSend.start();
       // wifiListenAndSend.start();
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
        wifiListenAndSend = new Thread(new Runnable() {
            @Override
            public void run() {
                System.out.println("running wifi");
                SplbHdr probeHdr = new SplbHdr(PacketType.DATAPKG,(byte)1,0,0,1);
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
        wifiListenAndSend.start();
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
                SplbHdr probeHdr = new SplbHdr(PacketType.DATAPKG,(byte)1,0,1,1);
                byte[] realData = new byte[512];
                for (int i = 0; i < realData.length; i++) {
                    realData[i] = 1;
                }
                try {
                    while(!Thread.currentThread().isInterrupted()){
                        byte[] sendData =  byteMerger(probeHdr.toByteArray(),realData);
                        DatagramPacket packet = new DatagramPacket(sendData,sendData.length,address,dstPort);
                        lteSocket.send(packet);
                        probeHdr.pathSeq++;
                        probeHdr.dataSeq++;
                        // sleep(1000);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
               lteSocket.close();
            }
        });
        lteListenAndSend.start();
    }
    public static byte[] byteMerger(byte[] bt1, byte[] bt2){
        byte[] bt3 = new byte[bt1.length+bt2.length];
        System.arraycopy(bt1, 0, bt3, 0, bt1.length);
        System.arraycopy(bt2, 0, bt3, bt1.length, bt2.length);
        return bt3;
    }
}
