package com.neu.splb;


import static java.lang.Thread.sleep;

import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;



public class SocketService {
    private static final String TAG = "Socket Service";
    private volatile boolean isStartNow = false;    //开始标志
    public static int basePort = 50000;         //分配的基础端口号
   // public static SocketService instance = null;
    public SockControlBlock wifiControlBlock = null;
    public SockControlBlock lteControlBlock = null;
    public DataBuffer databuffer = new DataBuffer();

//    //获取SocketService类实例
//    public static SocketService getInstance(){
//        if (instance == null) {
//            synchronized (SocketService.class) {
//                if (instance == null) {
//                    instance = new SocketService();
//                }
//            }
//        }
//        return instance;
//    }


    public void testSplbMode1(String IP,int dstPort) throws UnknownHostException, SocketException, InterruptedException {
        if(isStartNow) return;
        isStartNow = true;
        AndroidAPITest apiInstance = AndroidAPITest.getInstance();
        final DatagramSocket lteSocket = this.getUdpSocket();
        final DatagramSocket wifiSocket = this.getUdpSocket();
        apiInstance.bindCellularSocket(lteSocket);
        apiInstance.bindWifiSocket(wifiSocket);
        sleep(2000);
        InetAddress address = InetAddress.getByName(IP);
        AtomicInteger dataInteger = new AtomicInteger(1);
        lteControlBlock = new SockControlBlock(lteSocket,address,dstPort+1,databuffer);
        lteControlBlock.initLTESockControlBlock();
        lteControlBlock.probeExecutor.execute(new LTEProbeTask(lteControlBlock));
        lteControlBlock.recvExecutor.execute(new LTERecvTask(lteControlBlock));
        wifiControlBlock = new SockControlBlock(wifiSocket,address,dstPort+1,databuffer);
        wifiControlBlock.initWifiSockControlBlock();
        wifiControlBlock.probeExecutor.execute(new WiFiProbeTask(wifiControlBlock));
        wifiControlBlock.recvExecutor.execute(new WiFiRecvTask(wifiControlBlock));
    }

    public void stopSendPkt(){
        wifiControlBlock.endSign = true;
        wifiControlBlock.probeExecutor.shutdownNow();
        wifiControlBlock.recvExecutor.shutdownNow();
        wifiControlBlock.dataExecutor.shutdownNow();
        wifiControlBlock.ackAndNakExecutor.shutdownNow();
    }



    //测试wifi udp性能
    public void testWiFiUDP(String IP, int dstPort) throws SocketException, InterruptedException, UnknownHostException {
        if(isStartNow){
            return;
        }
        final InetAddress address = InetAddress.getByName(IP);
        AndroidAPITest apiInstance = AndroidAPITest.getInstance();
        final DatagramSocket wifiSocket = this.getUdpSocket();
        apiInstance.bindWifiSocket(wifiSocket);
        sleep(3000);
        Thread udpThread1 = new Thread(() -> {
            System.out.println("running wifi");
            SplbHdr probeHdr = new SplbHdr(PacketType.DATAPKG,(byte)1,0,0,1);
            byte[] realData = new byte[512];
            Arrays.fill(realData, (byte) 1);
            try {
                while(!Thread.currentThread().isInterrupted()){
                    byte[] sendData =  DataUtils.byteMerger(probeHdr.toByteArray(),realData);
                    DatagramPacket packet = new DatagramPacket(sendData,sendData.length,address,dstPort);
                    wifiSocket.send(packet);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            wifiSocket.close();
        });
        Thread udpThread2 = new Thread(() -> {
            System.out.println("running wifi");
            SplbHdr probeHdr = new SplbHdr(PacketType.DATAPKG,(byte)1,0,0,1);
            byte[] realData = new byte[512];
            Arrays.fill(realData, (byte) 1);
            try {
                while(!Thread.currentThread().isInterrupted()){
                    byte[] sendData =  DataUtils.byteMerger(probeHdr.toByteArray(),realData);
                    DatagramPacket packet = new DatagramPacket(sendData,sendData.length,address,dstPort);
                    wifiSocket.send(packet);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            wifiSocket.close();
        });
        udpThread1.start();
       // udpThread2.start();
    }



    //测试wifi udp性能
    public void testLteUDP(String IP, int dstPort) throws SocketException, InterruptedException, UnknownHostException {
        if(isStartNow){
            return;
        }
        final InetAddress address = InetAddress.getByName(IP);
        AndroidAPITest apiInstance = AndroidAPITest.getInstance();
        final DatagramSocket lteSocket = this.getUdpSocket();
        apiInstance.bindCellularSocket(lteSocket);
        sleep(3000);
        Thread udpThread1 = new Thread(() -> {
            System.out.println("running lte");
            SplbHdr probeHdr = new SplbHdr(PacketType.DATAPKG,(byte)0,0,0,1);
            byte[] realData = new byte[512];
            Arrays.fill(realData, (byte) 1);
            DatagramPacket packet = new DatagramPacket(realData,realData.length,address,dstPort);
            try {
                while(!Thread.currentThread().isInterrupted()){
                   // byte[] sendData =  DataUtils.byteMerger(probeHdr.toByteArray(),realData);
                    lteSocket.send(packet);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            lteSocket.close();
        });
        Thread udpThread2 = new Thread(() -> {
            System.out.println("running lte");
            SplbHdr probeHdr = new SplbHdr(PacketType.DATAPKG,(byte)0,0,0,1);
            byte[] realData = new byte[512];
            Arrays.fill(realData, (byte) 1);
            DatagramPacket packet = new DatagramPacket(realData,realData.length,address,dstPort);
            try {
                while(!Thread.currentThread().isInterrupted()){
                    // byte[] sendData =  DataUtils.byteMerger(probeHdr.toByteArray(),realData);
                    lteSocket.send(packet);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            lteSocket.close();
        });
        udpThread1.start();
        //udpThread2.start();
    }


    //测试lte tcp性能
    public void testWiFiTCP(String IP, int dstPort) throws SocketException, InterruptedException, UnknownHostException {
        if(isStartNow){
            return;
        }
        Thread tcpThread = new Thread(() -> {

            byte[] data = new byte[512];
            Arrays.fill(data,(byte)1);

            Socket socket = null;
            OutputStream os = null;
            try {
                socket = new Socket(IP, dstPort);
                os = socket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }
            int counter = 0;
            while(counter < 1000000){
                try {
                    os.write(DataUtils.byteMerger(new SplbHdr(PacketType.DATAPKG,(byte)0,0,0,1).toByteArray(),data));
                    os.flush();
                    counter++;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            try {
                socket.shutdownOutput();
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        tcpThread.start();
    }

    //测试lte tcp性能
    public void testLteTCP(String IP, int dstPort) throws SocketException, InterruptedException, UnknownHostException {
        if(isStartNow){
            return;
        }
        Thread tcpThread = new Thread(() -> {
            byte[] realData = new byte[512];
            Arrays.fill(realData, (byte) 1);
            Socket socket = null;
            OutputStream os = null;
            try {
                socket = new Socket(IP, dstPort);
                os = socket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }
            int counter = 0;
            while(counter < 1000000){
                try {
                    if (os == null) throw new AssertionError();
                    os.write(realData);
                    os.flush();
                    counter++;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            try {
                socket.shutdownOutput();
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        tcpThread.start();
    }
}
