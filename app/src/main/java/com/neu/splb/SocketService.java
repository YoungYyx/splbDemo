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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;


/*
* 数据包类型
*
* */
enum PacketType{
    DATAPKG((byte)0),   //数据包
    PROBEPKG((byte)1),  //探测包
    ACKPKG((byte)2),    //ACK报文
    NAKPKG((byte)3),   //NAK报文
    RETRANS((byte)4);   //重传包

    public byte t;

    PacketType(byte t) {
        this.t= t;
    }
}



/*
* SPLB头部
* */
class SplbHdr{
    long timeStamp = 0L;
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
        this.timeStamp = byteBuffer.getLong();
        this.probeSeq = byteBuffer.getInt();
        this.pathSeq = byteBuffer.getInt();
        this.dataSeq = byteBuffer.getInt();
        byte t = byteBuffer.get();
        switch (t){
            case (byte)0:
                this.type = PacketType.DATAPKG;break;
            case (byte)1:
                this.type = PacketType.PROBEPKG;break;
            case (byte)2:
                this.type = PacketType.ACKPKG;break;
            case (byte)3:
                this.type = PacketType.NAKPKG;break;
            case (byte)4:
                this.type = PacketType.RETRANS;break;
        }
        this.pathNum = byteBuffer.get();

    }
    public byte[] toByteArray() {
        ByteBuffer buf = ByteBuffer.allocate(22);
        buf.putLong(this.timeStamp);
        buf.putInt(this.probeSeq);
        buf.putInt(this.pathSeq);
        buf.putInt(this.dataSeq);
        buf.put(this.type.t);
        buf.put(this.pathNum);
        return buf.array();
    }

}


/*
* 获取发送数据的工具类
* */
class DataUtils{
    static SplbHdr hdr;
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

class WiFiControlBlock{
    public boolean endSign =false;
    public boolean lostState = false;
    public int wifiBaseProbeGap = 40;   //探测包基础间隔, us
    public int wifiACKThreshold = 200;     //连续ack阈值
    public int wifiLastNakSeq = 0;
    public int wifiContinueNAK = 0;
    public int wifiRepeatAck = 0;
    public int wifiContinueAck = 0;
    public int probeScale = 200;        //探测包对应数据包比例
    public int probeStep = 10;           //间隔减小的步长
    public DatagramSocket sock = null;
    public InetAddress dstIP = null;
    public int dstPort = 0;
    public AtomicInteger pathSeq = null;
    public AtomicInteger dataSeq = null;
    public AtomicInteger inflight = new AtomicInteger(0);
    int ackedSeq = 0;           //已经ack的路径序号
    int kPackets = 3;
    long timeThreshold = kPackets * wifiBaseProbeGap + 100;
    public ConcurrentHashMap<Integer,Integer> dataMap = null;
    public ExecutorService wifiProbeExecutor = null;
    public ExecutorService wifiRecvExecutor = null;
    public ExecutorService wifiDataExecutor = null;
    public ExecutorService wifiAckAndNakExecutor = null;
    public ExecutorService wifiPTOExecutor = null;

    public WiFiControlBlock(DatagramSocket sock, InetAddress dstIP, int dstPort, AtomicInteger pathSeq, AtomicInteger dataSeq, ConcurrentHashMap<Integer, Integer> dataMap) {
        this.sock = sock;
        this.dstIP = dstIP;
        this.dstPort = dstPort;
        this.pathSeq = pathSeq;
        this.dataSeq = dataSeq;
        this.dataMap = dataMap;
        wifiProbeExecutor = Executors.newSingleThreadExecutor();
        wifiRecvExecutor = Executors.newSingleThreadExecutor();
        wifiDataExecutor = Executors.newSingleThreadExecutor();
        wifiAckAndNakExecutor = Executors.newSingleThreadExecutor();
        wifiPTOExecutor = Executors.newSingleThreadExecutor();
    }
}
class WiFiProbeTask implements Runnable{

    WiFiControlBlock wifiControlBlock = null;

    WiFiProbeTask(WiFiControlBlock wifiControlBlock){
        this.wifiControlBlock = wifiControlBlock;
    }

    @Override
    public void run() {
        System.out.println("running wifi probe");
        SplbHdr probeHdr = new SplbHdr(PacketType.PROBEPKG,(byte)1,1,0,0);
        try {
            while(!Thread.currentThread().isInterrupted()){
                byte[] probe = probeHdr.toByteArray();
                DatagramPacket packet = new DatagramPacket(probe,probe.length,wifiControlBlock.dstIP,wifiControlBlock.dstPort);
                wifiControlBlock.sock.send(packet);
                TimeUnit.MICROSECONDS.sleep(wifiControlBlock.wifiBaseProbeGap * wifiControlBlock.probeScale);
                probeHdr.probeSeq++;
            }
        } catch (IOException | InterruptedException e) {
            //e.printStackTrace();
        }
    }
}
class WiFiRecvTask implements Runnable{

    WiFiControlBlock wifiControlBlock = null;

    WiFiRecvTask(WiFiControlBlock wifiControlBlock){
        this.wifiControlBlock = wifiControlBlock;
    }

    @Override
    public void run() {
        try {
            while(!wifiControlBlock.endSign){
                byte[] data = new byte[22];
                DatagramPacket probePacket = new DatagramPacket(data,data.length);
                wifiControlBlock.sock.receive(probePacket);
                byte[] msg = probePacket.getData();
                SplbHdr hdr = new SplbHdr(msg);
                if(hdr.type == PacketType.PROBEPKG) //报文为回传探测包类
                {
                    WiFiDataTask dataTask = new WiFiDataTask(hdr,wifiControlBlock);
                    wifiControlBlock.wifiDataExecutor.execute(dataTask);
                }
                else{
                    WiFiAckAndNakTask ackAndNakTask = new WiFiAckAndNakTask(hdr,wifiControlBlock);
                    wifiControlBlock.wifiAckAndNakExecutor.execute(ackAndNakTask);
                }
            }
        } catch (IOException e) {
            //e.printStackTrace();
        }
    }
}
class WiFiDataTask implements Runnable{

    public SplbHdr hdr;
    WiFiControlBlock controblock;

    WiFiDataTask(SplbHdr hdr,WiFiControlBlock controblock){
        super();
        this.hdr = hdr;
        this.controblock = controblock;
    }
    @Override
    public void run() {
        //System.out.println("开始任务-----------");
        for (int i = 0; i < controblock.probeScale; i++) {
            if (controblock.lostState == true || controblock.inflight.get() >= 10000 ) {
                // System.out.println("cwnd已满" + controblock.ackedSeq + controblock.pathSeq);
            } else {
                SplbHdr dataHdr = new SplbHdr(PacketType.DATAPKG,(byte)1,hdr.probeSeq,controblock.pathSeq.getAndIncrement(), controblock.dataSeq.getAndIncrement());
                if(dataHdr.pathSeq == 1000000){
                    controblock.wifiProbeExecutor.shutdownNow();
                }else if(dataHdr.pathSeq > 1000000){
                    return;
                }
                controblock.inflight.getAndIncrement();
                dataHdr.timeStamp = System.nanoTime();
                byte[] sendData =  DataUtils.byteMerger(dataHdr.toByteArray(),DataUtils.getData());
                DatagramPacket sendPacket = new DatagramPacket(sendData,sendData.length,controblock.dstIP,controblock.dstPort);
                try {
                    controblock.sock.send(sendPacket);
                   // System.out.println("发送"+hdr.pathSeq);
                    controblock.dataMap.put(dataHdr.pathSeq,dataHdr.dataSeq);
                  //  controblock.wifiPTOExecutor.execute(new WiFiPacketTimeOutTask(dataHdr,controblock));
                    //TimeUnit.MICROSECONDS.sleep(controblock.wifiBaseProbeGap);
                    TimeUnit.MICROSECONDS.sleep(5);
                } catch (IOException | InterruptedException e) {

                }
            }


        }
       // System.out.println("结束任务-----------");
    }
}

class WiFiAckAndNakTask implements Runnable{
    public SplbHdr hdr;
    WiFiControlBlock WiFiControlBlock;
    WiFiAckAndNakTask(SplbHdr hdr,WiFiControlBlock controblock){
        super();
        this.hdr = hdr;
        this.WiFiControlBlock = controblock;
    }

    @Override
    public void run() {
        if(hdr.type == PacketType.ACKPKG){
            //System.out.println("wifi recv ack :" + hdr.pathSeq + ","+WiFiControlBlock.pathSeq);
            if(WiFiControlBlock.ackedSeq == hdr.pathSeq){
                WiFiControlBlock.wifiRepeatAck++;
                if(WiFiControlBlock.wifiRepeatAck == 3){
                    hdr.type = PacketType.RETRANS;
                    int lostStartPathSeq = hdr.pathSeq;
                    for (int i = WiFiControlBlock.ackedSeq+1; i < lostStartPathSeq ; i++) { //说明之前的数据已经接收了
                        WiFiControlBlock.dataMap.remove(i);
                    }
                    int lostEndPathSeq = hdr.dataSeq;
                    for (int i = lostStartPathSeq ; i <= lostEndPathSeq ; i++) {
                        Integer lostDataSeq = WiFiControlBlock.dataMap.get(i);
                        if(lostDataSeq == null) continue;
                        hdr.pathSeq = i;
                        hdr.dataSeq = lostDataSeq;
                        byte[] sendData =  DataUtils.byteMerger(hdr.toByteArray(),DataUtils.data);
                        DatagramPacket sendPacket = new DatagramPacket(sendData,sendData.length,WiFiControlBlock.dstIP,WiFiControlBlock.dstPort);
                        try {
                            WiFiControlBlock.sock.send(sendPacket);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                     //   System.out.println("ACK触发wifi重新发送"+i);
                    }
                }
            }else{
                WiFiControlBlock.wifiRepeatAck = 1 ;
                WiFiControlBlock.wifiContinueAck++;
                if(WiFiControlBlock.wifiContinueAck > WiFiControlBlock.wifiACKThreshold){
                    //WiFiControlBlock.wifiBaseProbeGap -= WiFiControlBlock.probeStep;
                }
                for (int i = WiFiControlBlock.ackedSeq+1; i <= hdr.pathSeq ; i++) { //说明之前的数据已经接收了
                    WiFiControlBlock.dataMap.remove(i);
                    WiFiControlBlock.inflight.getAndDecrement();
                }
                WiFiControlBlock.ackedSeq = hdr.pathSeq;
            }
        }else{
            int lostStartPathSeq = hdr.pathSeq;
            int lostEndPathSeq = hdr.dataSeq;
            //System.out.println("recv NAK:" + lostStartPathSeq + " -> " + lostEndPathSeq +"现在已经发送到："+WiFiControlBlock.pathSeq.get());
            if(lostEndPathSeq <= WiFiControlBlock.ackedSeq){
                //do nothing
                System.out.println("已经ack");
            }else{
                for (int i = WiFiControlBlock.ackedSeq+1; i < lostStartPathSeq ; i++) { //说明之前的数据已经接收了
                    WiFiControlBlock.dataMap.remove(i);
                }
                WiFiControlBlock.ackedSeq = lostStartPathSeq - 1; //已经ack的数据为丢失报文前一个
                if(lostStartPathSeq == WiFiControlBlock.wifiLastNakSeq) {
                    WiFiControlBlock.wifiContinueNAK++;
                    if(WiFiControlBlock.wifiContinueNAK == 3){ //连续3个nak
                        WiFiControlBlock.lostState = true;
                        WiFiControlBlock.wifiContinueAck = 0;
                       // WiFiControlBlock.wifiBaseProbeGap += 20;   //增加探测包间隔
                        hdr.type = PacketType.RETRANS;
                        for (int i = lostStartPathSeq ; i <= lostEndPathSeq ; i++) {
                            Integer lostDataSeq = WiFiControlBlock.dataMap.get(i);
                            if(lostDataSeq == null){
                                System.out.println("丢失 path:"+i+"acked:"+WiFiControlBlock.ackedSeq);
                            }
                            hdr.pathSeq = i;
                            hdr.dataSeq = lostDataSeq;
                            byte[] sendData =  DataUtils.byteMerger(hdr.toByteArray(),DataUtils.data);
                            DatagramPacket sendPacket = new DatagramPacket(sendData,sendData.length,WiFiControlBlock.dstIP,WiFiControlBlock.dstPort);
                            try {
                                WiFiControlBlock.sock.send(sendPacket);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }

                        }
                        System.out.println("NAK触发wifi重新发送"+lostStartPathSeq + " -> " + lostEndPathSeq+"现在已经发送到："+WiFiControlBlock.pathSeq.get());
                        WiFiControlBlock.lostState = false;
                    }
                }else{
                    // do nothing
                    WiFiControlBlock.wifiLastNakSeq = lostStartPathSeq;
                    WiFiControlBlock.wifiContinueNAK = 1;
                }
            }
        }

    }
}

//class WiFiPacketTimeOutTask implements Runnable,Comparable<WiFiPacketTimeOutTask>{
//
//    public SplbHdr hdr;
//    WiFiControlBlock controblock;
//
//    WiFiPacketTimeOutTask(SplbHdr hdr,WiFiControlBlock controblock){
//        this.hdr = hdr;
//        this.controblock = controblock;
//    }
//
//    public int getInteval(long start,long end){    // ns -> us;
//        return (int)(end - start)/1000;
//    }
//
//    @Override
//    public void run() {
//        //System.out.println("PTO-》"+hdr.pathSeq +":"+hdr.timeStamp);
//        if(hdr.pathSeq <= controblock.ackedSeq){
//            return;
//        }else{
//            long sendTimeStamp = hdr.timeStamp;
//            int inteval = getInteval(System.nanoTime(),sendTimeStamp);
//            if(inteval < controblock.timeThreshold){
//                try {
//                    TimeUnit.MICROSECONDS.sleep(controblock.timeThreshold - inteval);
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
//            }
//            if(hdr.pathSeq <= controblock.ackedSeq){
//               // System.out.println("已经ack");
//                return;
//            }else{
//                hdr.type = PacketType.RETRANS;
//                hdr.timeStamp = System.nanoTime();
//                byte[] sendData =  DataUtils.byteMerger(hdr.toByteArray(),DataUtils.getData());
//                DatagramPacket sendPacket = new DatagramPacket(sendData,sendData.length,controblock.dstIP,controblock.dstPort);
//               // System.out.println("PTO触发wifi重新发送"+hdr.pathSeq);
//                controblock.wifiPTOExecutor.execute(this);
//                try {
//                    controblock.sock.send(sendPacket);
//                } catch (IOException e) {
//
//                }
//            }
//        }
//    }
//
//    @Override
//    public int compareTo(WiFiPacketTimeOutTask o) {
//        return this.hdr.timeStamp > o.hdr.timeStamp? -1 : 1;
//    }
//}


public class SocketService {
    private static final String TAG = "Socket Service";
    private volatile boolean isStartNow = false;    //开始标志
    public static int basePort = 50000;         //分配的基础端口号
    public static SocketService instance = null;
    private WiFiControlBlock wifiControlBlock = null;
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
     //   apiInstance.bindCellularSocket(lteSocket);
        sleep(2000);
        InetAddress address = InetAddress.getByName(IP);
        AtomicInteger wifiPathSeq = new AtomicInteger(1);
        AtomicInteger dataSeq = new AtomicInteger(1);
        ConcurrentHashMap<Integer,Integer> wifiDataMap = new ConcurrentHashMap<>();
        wifiControlBlock = new WiFiControlBlock(wifiSocket,address,dstPort+1,wifiPathSeq,dataSeq,wifiDataMap);
        wifiControlBlock.wifiProbeExecutor.execute(new WiFiProbeTask(wifiControlBlock));
        wifiControlBlock.wifiRecvExecutor.execute(new WiFiRecvTask(wifiControlBlock));
    }

    public void stopSendPkt(){
        wifiControlBlock.endSign = true;
        wifiControlBlock.wifiProbeExecutor.shutdownNow();
        wifiControlBlock.wifiRecvExecutor.shutdownNow();
        wifiControlBlock.wifiDataExecutor.shutdownNow();
        wifiControlBlock.wifiAckAndNakExecutor.shutdownNow();
        wifiControlBlock.wifiPTOExecutor.shutdownNow();
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
        Thread udpThread = new Thread(new Runnable() {
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
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                wifiSocket.close();
            }
        });
        udpThread.start();
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
//        lteListenAndSend = new Thread(new Runnable() { //启动线程在lte网卡发送数据包
//            @Override
//            public void run() {
//                System.out.println("running lte");
//                SplbHdr probeHdr = new SplbHdr(PacketType.DATAPKG,(byte)0,0,1,1);
//                byte[] realData = new byte[512];
//                for (int i = 0; i < realData.length; i++) {
//                    realData[i] = 1;
//                }
//                try {
//                    while(!Thread.currentThread().isInterrupted()){
//                        byte[] sendData =  DataUtils.byteMerger(probeHdr.toByteArray(),realData);
//                        DatagramPacket packet = new DatagramPacket(sendData,sendData.length,address,dstPort);
//                        lteSocket.send(packet);
//                        probeHdr.pathSeq++;
//                        probeHdr.dataSeq++;
//                        // sleep(1000);
//                    }
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//               lteSocket.close();
//            }
//        });
//        lteListenAndSend.start();
    }

    //测试lte tcp性能
    public void testWiFiTCP(String IP, int dstPort) throws SocketException, InterruptedException, UnknownHostException {
        if(isStartNow){
            return;
        }
        Thread tcpThread = new Thread(new Runnable() {
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
        tcpThread.start();
    }
}
