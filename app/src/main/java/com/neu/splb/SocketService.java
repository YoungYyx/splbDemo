package com.neu.splb;


import static java.lang.Thread.sleep;

import androidx.core.util.TimeUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
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
class DataUtils{
    static byte[] data;
    static {
        data = new byte[512];
        for (int i = 0; i < data.length; i++) {
            data[i] = 1;
        }
    }
    public static byte[] byteMerger(byte[] bt1, byte[] bt2){
        byte[] bt3 = new byte[bt1.length+bt2.length];
        System.arraycopy(bt1, 0, bt3, 0, bt1.length);
        System.arraycopy(bt2, 0, bt3, bt1.length, bt2.length);
        return bt3;
    }
    public static byte[] getData(){
        return data;
    }
}
class LTEState{
    public int lteBaseProbeGap = 300;   //lte探测包基础间隔, us
    public int lteACKThreshold = 3;     //连续ack阈值
    public int lteContinueAck = 0;
    public int probeScale = 200;        //探测包对应数据包比例
    public int probeStep = 10;           //间隔减小的步长
    public DatagramSocket sock = null;
    public InetAddress dstIP = null;
    public int dstPort = 0;
    public AtomicInteger pathSeq = null;
    public AtomicInteger dataSeq = null;
    int ackedSeq = 0;           //已经ack的路径序号
    public HashMap<Integer,Integer> dataMap = null;

    public LTEState(DatagramSocket sock, InetAddress dstIP, int dstPort, AtomicInteger pathSeq, AtomicInteger dataSeq, HashMap<Integer, Integer> dataMap) {
        this.sock = sock;
        this.dstIP = dstIP;
        this.dstPort = dstPort;
        this.pathSeq = pathSeq;
        this.dataSeq = dataSeq;
        this.dataMap = dataMap;
    }
}
class WiFiState{
    public int wifiBaseProbeGap = 300;   //探测包基础间隔, us
    public int wifiACKThreshold = 3;     //连续ack阈值
    public int wifiContinueAck = 0;
    public int probeScale = 200;        //探测包对应数据包比例
    public int probeStep = 10;           //间隔减小的步长
    public DatagramSocket sock = null;
    public InetAddress dstIP = null;
    public int dstPort = 0;
    public AtomicInteger pathSeq = null;
    public AtomicInteger dataSeq = null;
    int ackedSeq = 0;           //已经ack的路径序号
    public HashMap<Integer,Integer> dataMap = null;

    public WiFiState(DatagramSocket sock, InetAddress dstIP, int dstPort, AtomicInteger pathSeq, AtomicInteger dataSeq, HashMap<Integer, Integer> dataMap) {
        this.sock = sock;
        this.dstIP = dstIP;
        this.dstPort = dstPort;
        this.pathSeq = pathSeq;
        this.dataSeq = dataSeq;
        this.dataMap = dataMap;
    }
}
class LTETask implements Runnable {

    public SplbHdr hdr;
    LTEState state;

    LTETask(SplbHdr hdr,LTEState state){
        super();
        this.hdr = hdr;
        this.state = state;
    }
    @Override
    public void run() {
        for (int i = 0; i < state.probeScale; i++) {
            hdr.pathSeq = state.pathSeq.getAndIncrement();  //路径序号自增
            hdr.dataSeq = state.dataSeq.getAndIncrement();   //数据序号自增
            byte[] sendData =  DataUtils.byteMerger(hdr.toByteArray(),DataUtils.getData());
            DatagramPacket sendPacket = new DatagramPacket(sendData,sendData.length,state.dstIP,state.dstPort);
            try {
                state.sock.send(sendPacket);
                state.dataMap.put(hdr.pathSeq,hdr.dataSeq);
                TimeUnit.MICROSECONDS.sleep(state.lteBaseProbeGap);
            } catch (IOException | InterruptedException e) {

            }
        }
    }
}

class WiFiTask implements Runnable {

    public SplbHdr hdr;
    WiFiState state;

    WiFiTask(SplbHdr hdr,WiFiState state){
        super();
        this.hdr = hdr;
        this.state = state;
    }
    @Override
    public void run() {
        System.out.println("开始任务-----------");
        for (int i = 0; i < state.probeScale; i++) {
            hdr.pathSeq = state.pathSeq.getAndIncrement();  //路径序号自增
            hdr.dataSeq = state.dataSeq.getAndIncrement();   //数据序号自增
            byte[] sendData =  DataUtils.byteMerger(hdr.toByteArray(),DataUtils.getData());
            DatagramPacket sendPacket = new DatagramPacket(sendData,sendData.length,state.dstIP,state.dstPort);
            try {
                state.sock.send(sendPacket);
                System.out.println("发送数据："+hdr.pathSeq);
                state.dataMap.put(hdr.pathSeq,hdr.dataSeq);
                TimeUnit.MICROSECONDS.sleep(state.wifiBaseProbeGap);
            } catch (IOException | InterruptedException e) {

            }
        }
        System.out.println("结束任务-----------");
    }
}
public class SocketService {
    private static final String TAG = "Socket Service";
    private volatile boolean isStartNow = false;    //开始标志
    public static int basePort = 50000;         //分配的基础端口号
    private Thread lteProbeThread = null;       //lte探测线程
    private Thread wifiProbeThread = null;      //wifi探测线程
    private Thread lteListenAndSend = null;     //lte数据线程
    private Thread wifiListenAndSend = null;    //wifi数据线程
    public static SocketService instance = null;


    private LTEState lteState = null;
    private ExecutorService lteExecutor = null;
    private WiFiState wifiState = null;
    private ExecutorService wifiExecutor = null;


    //获取SocketService类实例
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

    public void testSplbMode1(String IP,int dstPort) throws UnknownHostException, SocketException, InterruptedException {
        if(isStartNow) return;
        isStartNow = true;
        AndroidAPITest apiInstance = AndroidAPITest.getInstance();
        final DatagramSocket lteSocket = this.getUdpSocket();
        final DatagramSocket wifiSocket = this.getUdpSocket();
        apiInstance.bindWifiSocket(wifiSocket);
        apiInstance.bindCellularSocket(lteSocket);
        sleep(2000);
        InetAddress address = InetAddress.getByName(IP);
        AtomicInteger ltePathSeq = new AtomicInteger(1);
        AtomicInteger wifiPathSeq = new AtomicInteger(1);
        AtomicInteger dataSeq = new AtomicInteger(1);
        HashMap<Integer,Integer> lteDataMap = new HashMap<>();
        HashMap<Integer,Integer> wifiDataMap = new HashMap<>();
        lteState = new LTEState(lteSocket,address,dstPort,ltePathSeq,dataSeq,lteDataMap);
        wifiState = new WiFiState(wifiSocket,address,dstPort,wifiPathSeq,dataSeq,wifiDataMap);
        lteExecutor = Executors.newSingleThreadExecutor();
        wifiExecutor = Executors.newSingleThreadExecutor();
        lteProbeThread = new Thread(new Runnable() { //启动线程在lte网卡发送探测包
            @Override
            public void run() {
                System.out.println("running lte probe");
                SplbHdr probeHdr = new SplbHdr(PacketType.PROBEPKG,(byte)0,1,0,0);
                try {
                    while(!Thread.currentThread().isInterrupted()){
                        byte[] probe = probeHdr.toByteArray();
                        DatagramPacket packet = new DatagramPacket(probe,probe.length,lteState.dstIP,lteState.dstPort);
                        lteState.sock.send(packet);
                        TimeUnit.MICROSECONDS.sleep(lteState.lteBaseProbeGap * lteState.probeScale);
                        probeHdr.probeSeq++;
                    }
                } catch (IOException | InterruptedException e) {
                    //e.printStackTrace();
                }
                lteSocket.close();
            }
        });

        lteListenAndSend = new Thread(new Runnable() { //启动线程在lte网卡监听探测包
            @Override
            public void run() {
                //填充一些数据作为payload,假的数据，后续可改为从文件读取

                try {
                    while(!Thread.currentThread().isInterrupted()){
                        byte[] data = new byte[14];
                        DatagramPacket probePacket = new DatagramPacket(data,data.length);
                        lteSocket.receive(probePacket);

                        InetAddress srcIP = probePacket.getAddress();
                        int srcPort = probePacket.getPort();
                        byte[] msg = probePacket.getData();
                        SplbHdr hdr = new SplbHdr(msg);


                        if(hdr.type == PacketType.PROBEPKG) //报文为回传探测包类
                        {
                            hdr.type = PacketType.DATAPKG;                  //改为数据包类型
                            LTETask work = new LTETask(hdr,lteState);
                            lteExecutor.execute(work);

                        }
                        else if(hdr.type == PacketType.NAKPKG)//NAK包，丢包了
                        {
                            lteState.lteContinueAck = 0;
                            System.out.println("----------recv nak :" + hdr.pathSeq);
                            lteState.lteBaseProbeGap += lteState.probeStep;   //增加探测包间隔
                            hdr.type = PacketType.DATAPKG;
                            int lostStartPathSeq = hdr.pathSeq;
                            int lostEndPathSeq = hdr.dataSeq;
                            for (int i = lteState.ackedSeq+1; i < lostStartPathSeq ; i++) { //说明之前的数据已经接收了
                                lteState.dataMap.remove(i);
                            }
                            lteState.ackedSeq = lostStartPathSeq - 1; //已经ack的数据为丢失报文前一个
                            for (int i = lostStartPathSeq ; i <= lostEndPathSeq ; i++) {
                                Integer lostDataSeq = lteState.dataMap.get(i);
                                hdr.pathSeq = lostDataSeq;
                                byte[] sendData =  DataUtils.byteMerger(hdr.toByteArray(),DataUtils.data);
                                DatagramPacket sendPacket = new DatagramPacket(sendData,sendData.length,srcIP,srcPort);
                                lteState.sock.send(sendPacket);
                            }
                        }
                        else if(hdr.type == PacketType.ACKPKG) //ACK包
                        {
                            lteState.lteContinueAck++;
                            if(lteState.lteContinueAck > lteState.lteACKThreshold){
                                lteState.lteBaseProbeGap -= lteState.probeStep;
                            }
                            for (int i = lteState.ackedSeq+1; i <= hdr.pathSeq ; i++) { //说明之前的数据已经接收了
                                lteState.dataMap.remove(i);
                            }
                            lteState.ackedSeq = hdr.pathSeq;
                        }
                    }
                } catch (IOException e) {
                    //e.printStackTrace();
                }
            }
        });

        wifiProbeThread = new Thread(new Runnable() { //启动线程在wifi网卡发送探测包
            @Override
            public void run() {
                System.out.println("running wifi probe");
                SplbHdr probeHdr = new SplbHdr(PacketType.PROBEPKG,(byte)0,1,0,0);
                try {
                    while(!Thread.currentThread().isInterrupted()){
                        byte[] probe = probeHdr.toByteArray();
                        DatagramPacket packet = new DatagramPacket(probe,probe.length,wifiState.dstIP,wifiState.dstPort);
                        wifiState.sock.send(packet);
                        TimeUnit.MICROSECONDS.sleep(wifiState.wifiBaseProbeGap * wifiState.probeScale);
                        probeHdr.probeSeq++;
                    }
                } catch (IOException | InterruptedException e) {
                    //e.printStackTrace();
                }
                wifiSocket.close();
            }
        });

        wifiListenAndSend = new Thread(new Runnable() { //启动线程在wifi网卡监听探测包
            @Override
            public void run() {
                //填充一些数据作为payload,假的数据，后续可改为从文件读取

                try {
                    while(!Thread.currentThread().isInterrupted()){
                        byte[] data = new byte[14];
                        DatagramPacket probePacket = new DatagramPacket(data,data.length);
                        wifiSocket.receive(probePacket);

                        InetAddress srcIP = probePacket.getAddress();
                        int srcPort = probePacket.getPort();
                        byte[] msg = probePacket.getData();
                        SplbHdr hdr = new SplbHdr(msg);


                        if(hdr.type == PacketType.PROBEPKG) //报文为回传探测包类
                        {
                            hdr.type = PacketType.DATAPKG;                  //改为数据包类型
                            WiFiTask work = new WiFiTask(hdr,wifiState);
                            wifiExecutor.execute(work);

                        }
                        else if(hdr.type == PacketType.NAKPKG)//NAK包，丢包了
                        {
                            wifiState.wifiContinueAck = 0;
                            System.out.println("----------recv nak :" + hdr.pathSeq);
                            wifiState.wifiBaseProbeGap += wifiState.probeStep;   //增加探测包间隔
                            hdr.type = PacketType.DATAPKG;
                            int lostStartPathSeq = hdr.pathSeq;
                            int lostEndPathSeq = hdr.dataSeq;
                            for (int i = wifiState.ackedSeq+1; i < lostStartPathSeq ; i++) { //说明之前的数据已经接收了
                                wifiState.dataMap.remove(i);
                            }
                            wifiState.ackedSeq = lostStartPathSeq - 1; //已经ack的数据为丢失报文前一个
                            for (int i = lostStartPathSeq ; i <= lostEndPathSeq ; i++) {
                                Integer lostDataSeq = wifiState.dataMap.get(i);
                                hdr.pathSeq = lostDataSeq;
                                byte[] sendData =  DataUtils.byteMerger(hdr.toByteArray(),DataUtils.data);
                                DatagramPacket sendPacket = new DatagramPacket(sendData,sendData.length,srcIP,srcPort);
                                wifiSocket.send(sendPacket);
                            }
                        }
                        else if(hdr.type == PacketType.ACKPKG) //ACK包
                        {
                            wifiState.wifiContinueAck++;
                            if(wifiState.wifiContinueAck > wifiState.wifiACKThreshold){
                                wifiState.wifiBaseProbeGap -= wifiState.probeStep;
                            }
                            for (int i = wifiState.ackedSeq+1; i <= hdr.pathSeq ; i++) { //说明之前的数据已经接收了
                                wifiState.dataMap.remove(i);
                            }
                            wifiState.ackedSeq = hdr.pathSeq;
                        }
                    }
                } catch (IOException e) {
                    //e.printStackTrace();
                }
            }
        });


        wifiProbeThread.start();
        wifiListenAndSend.start();
    }
    public void initParameter(){
        lteState.lteBaseProbeGap = 300;
        lteState.probeScale = 200;
        lteState.probeStep = 10;
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
        initParameter();
    }

    //测试wifi udp性能
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
                        byte[] sendData =  DataUtils.byteMerger(probeHdr.toByteArray(),realData);
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


    //测试lte udp性能
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
                        byte[] sendData =  DataUtils.byteMerger(probeHdr.toByteArray(),realData);
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

    //测试lte tcp性能
    public void testLteTCP(String IP, int dstPort) throws SocketException, InterruptedException, UnknownHostException {
        if(isStartNow){
            return;
        }
            //建立连接
            lteProbeThread =  new Thread(new Runnable() {
                @Override
                public void run() {
                    Socket socket = null;
                    OutputStream os = null;
                    try {
                        socket = new Socket(IP, dstPort);
                        os = socket.getOutputStream();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    while(!Thread.currentThread().isInterrupted()){
                        try {
                            os.write(DataUtils.getData());
                            os.flush();
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
                }
            });
        lteProbeThread.start();
    }
}
