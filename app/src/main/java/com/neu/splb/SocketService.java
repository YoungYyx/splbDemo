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
    static byte[] data;

    static {
        data = new byte[512];
        Arrays.fill(data, (byte) 1);
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
class DataBlock{
    int dataSeq;
    byte[] data;
    DataBlock(int dataSeq,byte[] data){
        this.dataSeq = dataSeq;
        this.data = data;
    }
}

class LTEControlBlock{
    public boolean lteEndSign =false;
    public boolean lteLostState = false;
    public int lteBaseProbeGap = 213;   //探测包基础间隔, us
    public int lteProbeScale = 200;        //探测包对应数据包比例
    public int lteProbeStep = 10;           //间隔减小的步长
    public DatagramSocket lteSock;
    public InetAddress lteDstIP;
    public int lteDstPort;
    public AtomicInteger ltePathSeq;
    public AtomicInteger lteDataSeq;
    public int lteLastRetransSeq = 0;
    public int lteLastRRSeq = 0;
    public int lteLastAckSeq = 0;
    public AtomicInteger lteInflight = new AtomicInteger(0);
    public int kPackets = 3;
    long timeThreshold = (long) kPackets * lteBaseProbeGap + 100;
    long probeNextToSend = 0;
    long dataNextToSend = 0;
    public ConcurrentHashMap<Integer,DataBlock> dataMap;
    public ConcurrentLinkedQueue<Integer> windowList;
    public ConcurrentLinkedQueue<Integer> retransList;
    public ExecutorService lteProbeExecutor;
    public ExecutorService lteRecvExecutor;
    public ExecutorService lteDataExecutor;
    public ExecutorService lteAckAndNakExecutor;
    public ExecutorService ltePTOExecutor;

    public LTEControlBlock(DatagramSocket sock, InetAddress dstIP, int dstPort,AtomicInteger integer) {
        this.lteSock = sock;
        this.lteDstIP = dstIP;
        this.lteDstPort = dstPort;
        this.ltePathSeq = new AtomicInteger(1);
        this.lteDataSeq = integer;
        this.dataMap = new ConcurrentHashMap<>();
        this.windowList = new ConcurrentLinkedQueue<>();
        this.retransList = new ConcurrentLinkedQueue<>();
        lteProbeExecutor = Executors.newSingleThreadExecutor();
        lteRecvExecutor = Executors.newSingleThreadExecutor();
        lteDataExecutor = Executors.newSingleThreadExecutor();
        lteAckAndNakExecutor = Executors.newSingleThreadExecutor();
        ltePTOExecutor = Executors.newSingleThreadExecutor();
    }
}
class LTEProbeTask implements Runnable{

    LTEControlBlock lteControlBlock = null;

    LTEProbeTask(LTEControlBlock lteControlBlock){
        this.lteControlBlock = lteControlBlock;
    }

    @Override
    public void run() {
        System.out.println("running wifi probe");
        SplbHdr probeHdr = new SplbHdr(PacketType.PROBEPKG,(byte)0,1,0,0);
        try {
            while(!Thread.currentThread().isInterrupted()){
                byte[] probe = probeHdr.toByteArray();
                DatagramPacket packet = new DatagramPacket(probe,probe.length,lteControlBlock.lteDstIP,lteControlBlock.lteDstPort);
                lteControlBlock.lteSock.send(packet);
                TimeUnit.MICROSECONDS.sleep(lteControlBlock.lteBaseProbeGap * lteControlBlock.lteProbeScale);
                probeHdr.probeSeq++;
            }
        } catch (IOException | InterruptedException e) {
            //e.printStackTrace();
        }
    }
}
class LTERecvTask implements Runnable{

    LTEControlBlock lteControlBlock = null;

    LTERecvTask(LTEControlBlock lteControlBlock){
        this.lteControlBlock = lteControlBlock;
    }

    @Override
    public void run() {
        try {
            while(!lteControlBlock.lteEndSign){
                byte[] data = new byte[22];
                DatagramPacket probePacket = new DatagramPacket(data,data.length);
                lteControlBlock.lteSock.receive(probePacket);
                byte[] msg = probePacket.getData();
                SplbHdr hdr = new SplbHdr(msg);
                if(hdr.type == PacketType.PROBEPKG) //报文为回传探测包类
                {
                    LTEDataTask dataTask = new LTEDataTask(hdr,lteControlBlock);
                    lteControlBlock.lteDataExecutor.execute(dataTask);
                }
                else{
                    LTEAckAndNakTask ackAndNakTask = new LTEAckAndNakTask(hdr,lteControlBlock);
                    lteControlBlock.lteAckAndNakExecutor.execute(ackAndNakTask);
                }
            }
        } catch (IOException e) {
            //e.printStackTrace();
        }
    }
}
class LTEDataTask implements Runnable{

    public SplbHdr hdr;
    LTEControlBlock controblock;

    LTEDataTask(SplbHdr hdr,LTEControlBlock controblock){
        super();
        this.hdr = hdr;
        this.controblock = controblock;
    }
    @Override
    public void run() {
        int probeCounter = 0;
        while(probeCounter < controblock.lteProbeScale && (!controblock.lteEndSign)){
            if(controblock.lteLostState == true || (controblock.retransList.size() > 10 && controblock.windowList.size() > 200)){
                continue;
            }
            long now = System.nanoTime();
            if(now < controblock.dataNextToSend && controblock.dataNextToSend != 0){
                continue;
            }else{
                probeCounter++;
                SplbHdr dataHdr = new SplbHdr(PacketType.DATAPKG,(byte)1,hdr.probeSeq,controblock.ltePathSeq.getAndIncrement(), controblock.lteDataSeq.getAndIncrement());
                if(dataHdr.dataSeq>=1000001){
                    break;
                }
                controblock.lteInflight.getAndIncrement();
                dataHdr.timeStamp = System.nanoTime();
                controblock.dataNextToSend = dataHdr.timeStamp + controblock.lteBaseProbeGap*1000;
                byte[] data = DataUtils.getData();
                byte[] sendData =  DataUtils.byteMerger(dataHdr.toByteArray(),data);
                DatagramPacket sendPacket = new DatagramPacket(sendData,sendData.length,controblock.lteDstIP,controblock.lteDstPort);
                try {
                    controblock.lteSock.send(sendPacket);
                    controblock.windowList.add(dataHdr.pathSeq);
                    controblock.dataMap.put(dataHdr.pathSeq,new DataBlock(dataHdr.dataSeq,data));
                    LTEPacketTimeOutTask ltePacketTimeOutTask = new LTEPacketTimeOutTask(dataHdr, controblock);
                    controblock.ltePTOExecutor.execute(ltePacketTimeOutTask);

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
class LTEAckAndNakTask implements Runnable{
    public SplbHdr hdr;
    LTEControlBlock lteControlBlock;
    LTEAckAndNakTask(SplbHdr hdr,LTEControlBlock controblock){
        super();
        this.hdr = hdr;
        this.lteControlBlock = controblock;
    }

    @Override
    public void run() {
        int ackedSeq = hdr.pathSeq;
        int wantedSeq = hdr.dataSeq;

        if(lteControlBlock.windowList.contains(ackedSeq)){
            //System.out.println("ack" + ackedSeq);
            Integer minWaitAckSeq = lteControlBlock.windowList.peek();
            int minACKSeq = minWaitAckSeq.intValue();
            if(ackedSeq == minACKSeq){
                lteControlBlock.windowList.poll();
                lteControlBlock.dataMap.remove(ackedSeq);
                lteControlBlock.lteLastAckSeq = ackedSeq;
            }else if(ackedSeq - minACKSeq <= lteControlBlock.kPackets){ //小于阈值时可能是由于乱序引起的
                lteControlBlock.windowList.remove(ackedSeq);
                lteControlBlock.dataMap.remove(ackedSeq);
                lteControlBlock.lteLastAckSeq = ackedSeq;
            }else{
                lteControlBlock.windowList.remove(ackedSeq);
                lteControlBlock.dataMap.remove(ackedSeq);
                lteControlBlock.lteLastAckSeq =ackedSeq;
                lteControlBlock.lteLostState = true;
            }
            Iterator<Integer> iterator = lteControlBlock.windowList.iterator();
            while(iterator.hasNext()){
                Integer next = iterator.next();
                if(next.intValue() < wantedSeq){//当前序号小于 接受端顺序接受的序号，说明已经被正确接受了
                    iterator.remove();
                    lteControlBlock.dataMap.remove(next);
                }else{
                    break;
                }
            }
            if(lteControlBlock.lteLostState == true){
                while (lteControlBlock.windowList.size() >0 && lteControlBlock.windowList.peek().intValue() < ackedSeq) {
                    Integer lostPathSeqInteger = lteControlBlock.windowList.poll();
                    int lostPathSeq = lostPathSeqInteger.intValue();
                    System.out.println("lte--------------ack缺失引起重传:"+lostPathSeq);
                    lteControlBlock.retransList.add(lostPathSeqInteger);
                    DataBlock lostData = lteControlBlock.dataMap.get(lostPathSeq);
                    SplbHdr retransHdr = new SplbHdr(PacketType.RETRANS, (byte) 0, hdr.probeSeq, lostPathSeq, lostData.dataSeq);
                    retransHdr.timeStamp = System.nanoTime();
                    byte[] sendData = DataUtils.byteMerger(retransHdr.toByteArray(), lostData.data);
                    DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, lteControlBlock.lteDstIP, lteControlBlock.lteDstPort);
                    try {
                        lteControlBlock.lteSock.send(sendPacket);
                        lteControlBlock.lteLastRetransSeq = lostPathSeq;
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                lteControlBlock.lteLostState = false;
            }
        }

        if(lteControlBlock.retransList.contains(ackedSeq)){
            boolean retransLostState = false;
            //System.out.println("retrans ack" + ackedSeq);
            Integer retransMinAckSeq = lteControlBlock.retransList.peek();
            if(retransMinAckSeq == null){
                return;
            }else if (ackedSeq == retransMinAckSeq.intValue()){
                lteControlBlock.retransList.remove(ackedSeq);
                lteControlBlock.dataMap.remove(ackedSeq);

            }else if(ackedSeq - retransMinAckSeq.intValue() <= lteControlBlock.kPackets){
                lteControlBlock.retransList.remove(ackedSeq);
                lteControlBlock.dataMap.remove(ackedSeq);
            }else{
                lteControlBlock.retransList.remove(hdr.pathSeq);
                lteControlBlock.dataMap.remove(hdr.pathSeq);
                retransLostState = true;
            }
            Iterator<Integer> iterator = lteControlBlock.retransList.iterator();
            while(iterator.hasNext()){
                Integer next = iterator.next();
                if(next.intValue() < wantedSeq){
                    iterator.remove();
                    lteControlBlock.dataMap.remove(next);
                }else{
                    break;
                }
            }
            if(retransLostState == true){
                Iterator<Integer> retransIterator = lteControlBlock.retransList.iterator();
                while (retransIterator.hasNext()){
                    Integer next = retransIterator.next();
                    int rrSeq = next.intValue();
                    if(rrSeq < hdr.pathSeq && rrSeq > lteControlBlock.lteLastRRSeq){
                        DataBlock lostData = lteControlBlock.dataMap.get(next);
                        SplbHdr retransHdr = new SplbHdr(PacketType.RETRANS,(byte)1,hdr.probeSeq,rrSeq,lostData.dataSeq);
                        retransHdr.timeStamp = System.nanoTime();
                        byte[] sendData =  DataUtils.byteMerger(retransHdr.toByteArray(),lostData.data);
                       // System.out.println("--------------------->path-seq:"+hdr.pathSeq+"重传包缺失引起重传:"+rrSeq);
                        DatagramPacket sendPacket = new DatagramPacket(sendData,sendData.length,lteControlBlock.lteDstIP,lteControlBlock.lteDstPort);
                        try {
                            lteControlBlock.lteSock.send(sendPacket);
                            lteControlBlock.lteLastRRSeq = rrSeq;
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }else{
                        break;
                    }
                }
                retransLostState = false;
            }

        }

        //-------------------------------------------------------
//        if(hdr.type == PacketType.ACKPKG){
//            //System.out.println("wifi recv ack :" + hdr.pathSeq + ","+WiFiControlBlock.pathSeq);
//            if(WiFiControlBlock.ackedSeq == hdr.pathSeq){
//                WiFiControlBlock.wifiRepeatAck++;
//                if(WiFiControlBlock.wifiRepeatAck == 3){
//                    hdr.type = PacketType.RETRANS;
//                    int lostStartPathSeq = hdr.pathSeq;
//                    for (int i = WiFiControlBlock.ackedSeq+1; i < lostStartPathSeq ; i++) { //说明之前的数据已经接收了
//                        WiFiControlBlock.dataMap.remove(i);
//                    }
//                    int lostEndPathSeq = hdr.dataSeq;
//                    for (int i = lostStartPathSeq ; i <= lostEndPathSeq ; i++) {
//                        Integer lostDataSeq = WiFiControlBlock.dataMap.get(i);
//                        if(lostDataSeq == null) continue;
//                        hdr.pathSeq = i;
//                        hdr.dataSeq = lostDataSeq;
//                        byte[] sendData =  DataUtils.byteMerger(hdr.toByteArray(),DataUtils.data);
//                        DatagramPacket sendPacket = new DatagramPacket(sendData,sendData.length,WiFiControlBlock.dstIP,WiFiControlBlock.dstPort);
//                        try {
//                            WiFiControlBlock.sock.send(sendPacket);
//                        } catch (IOException e) {
//                            e.printStackTrace();
//                        }
//                     //   System.out.println("ACK触发wifi重新发送"+i);
//                    }
//                }
//            }else{
//                WiFiControlBlock.wifiRepeatAck = 1 ;
//                WiFiControlBlock.wifiContinueAck++;
//                if(WiFiControlBlock.wifiContinueAck > WiFiControlBlock.wifiACKThreshold){
//                    //WiFiControlBlock.wifiPacingGap -= WiFiControlBlock.probeStep;
//                }
//                for (int i = WiFiControlBlock.ackedSeq+1; i <= hdr.pathSeq ; i++) { //说明之前的数据已经接收了
//                    WiFiControlBlock.dataMap.remove(i);
//                    WiFiControlBlock.inflight.getAndDecrement();
//                }
//                WiFiControlBlock.ackedSeq = hdr.pathSeq;
//            }
//        }else{
//            int lostStartPathSeq = hdr.pathSeq;
//            int lostEndPathSeq = hdr.dataSeq;
//            //System.out.println("recv NAK:" + lostStartPathSeq + " -> " + lostEndPathSeq +"现在已经发送到："+WiFiControlBlock.pathSeq.get());
//            if(lostEndPathSeq <= WiFiControlBlock.ackedSeq){
//                //do nothing
//                System.out.println("已经ack");
//            }else{
//                for (int i = WiFiControlBlock.ackedSeq+1; i < lostStartPathSeq ; i++) { //说明之前的数据已经接收了
//                    WiFiControlBlock.dataMap.remove(i);
//                }
//                WiFiControlBlock.ackedSeq = lostStartPathSeq - 1; //已经ack的数据为丢失报文前一个
//                if(lostStartPathSeq == WiFiControlBlock.wifiLastNakSeq) {
//                    WiFiControlBlock.wifiContinueNAK++;
//                    if(WiFiControlBlock.wifiContinueNAK == 3){ //连续3个nak
//                        WiFiControlBlock.lostState = true;
//                        WiFiControlBlock.wifiContinueAck = 0;
//                       // WiFiControlBlock.wifiPacingGap += 20;   //增加探测包间隔
//                        hdr.type = PacketType.RETRANS;
//                        for (int i = lostStartPathSeq ; i <= lostEndPathSeq ; i++) {
//                            Integer lostDataSeq = WiFiControlBlock.dataMap.get(i);
//                            if(lostDataSeq == null){
//                                System.out.println("丢失 path:"+i+"acked:"+WiFiControlBlock.ackedSeq);
//                            }
//                            hdr.pathSeq = i;
//                            hdr.dataSeq = lostDataSeq;
//                            byte[] sendData =  DataUtils.byteMerger(hdr.toByteArray(),DataUtils.data);
//                            DatagramPacket sendPacket = new DatagramPacket(sendData,sendData.length,WiFiControlBlock.dstIP,WiFiControlBlock.dstPort);
//                            try {
//                                WiFiControlBlock.sock.send(sendPacket);
//                            } catch (IOException e) {
//                                e.printStackTrace();
//                            }
//
//                        }
//                        System.out.println("NAK触发wifi重新发送"+lostStartPathSeq + " -> " + lostEndPathSeq+"现在已经发送到："+WiFiControlBlock.pathSeq.get());
//                        WiFiControlBlock.lostState = false;
//                    }
//                }else{
//                    // do nothing
//                    WiFiControlBlock.wifiLastNakSeq = lostStartPathSeq;
//                    WiFiControlBlock.wifiContinueNAK = 1;
//                }
//            }
//        }

    }
}
class LTEPacketTimeOutTask implements Runnable,Comparable<LTEPacketTimeOutTask>{

    public SplbHdr hdr;
    LTEControlBlock controblock;

    LTEPacketTimeOutTask(SplbHdr hdr,LTEControlBlock controblock){
        this.hdr = hdr;
        this.controblock = controblock;
    }

    public int getInteval(long start,long end){    // ns -> us;
        return (int)(end - start)/1000;
    }

    @Override
    public void run() {
        if(!(controblock.windowList.contains(hdr.pathSeq) || controblock.retransList.contains(hdr.pathSeq))){
            return;
        }
        else{
            long sendTimeStamp = hdr.timeStamp;
            int inteval = getInteval(System.nanoTime(),sendTimeStamp);
            if(inteval < controblock.timeThreshold){
                try {
                    TimeUnit.MICROSECONDS.sleep(controblock.timeThreshold - inteval);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            DataBlock dataBlock = controblock.dataMap.get(hdr.pathSeq);
            if(dataBlock == null){
               // System.out.println("已经ack");
                return;
            }else{
                hdr.type = PacketType.RETRANS;
                hdr.timeStamp = System.nanoTime();
                byte[] sendData =  DataUtils.byteMerger(hdr.toByteArray(),dataBlock.data);
                DatagramPacket sendPacket = new DatagramPacket(sendData,sendData.length,controblock.lteDstIP,controblock.lteDstPort);
               // System.out.println("PTO触发wifi重新发送"+hdr.pathSeq);
                controblock.ltePTOExecutor.execute(this);
                try {
                    controblock.lteSock.send(sendPacket);
                } catch (IOException e) {

                }
            }
        }
    }

    @Override
    public int compareTo(LTEPacketTimeOutTask o) {
        return this.hdr.timeStamp > o.hdr.timeStamp? -1 : 1;
    }
}


class WiFiControlBlock{
    public boolean wifiEndSign = false;
    public boolean wifiLostState = false;
    public int wifiPacingGap = 106;   //探测包基础间隔, us
    public int wifiMinPacingGap = 50;
    public int wifiProbeScale = 200;        //探测包对应数据包比例
    public int probeStep = 10;           //间隔减小的步长
    public DatagramSocket wifiSock;
    public InetAddress wifiDstIP;
    public int wifiDstPort = 0;
    public AtomicInteger wifiPathSeq;
    public AtomicInteger wifiDataSeq;
    public int wifiLastRetransSeq = 0;
    public int wifiLastRRSeq = 0;
    public int wifiLastAckSeq = 0;
    public int wifiContinueAck = 0;
    public long wifiLastProbeTimeStamp = 0;
    public AtomicInteger wifiInflight = new AtomicInteger(0);
    public int kPackets = 3;
    long timeThreshold = kPackets * wifiPacingGap + 100;
    long probeNextToSend = 0;
    long dataNextToSend = 0;
    public ConcurrentHashMap<Integer,DataBlock> dataMap;
    public ConcurrentLinkedQueue<Integer> windowList;
    public ConcurrentLinkedQueue<Integer> retransList ;
    public ExecutorService wifiProbeExecutor;
    public ExecutorService wifiRecvExecutor;
    public ExecutorService wifiDataExecutor;
    public ExecutorService wifiAckAndNakExecutor;
    public ExecutorService wifiPTOExecutor;

    public WiFiControlBlock(DatagramSocket wifiSock, InetAddress wifiDstIP, int wifiDstPort,AtomicInteger integer) {
        this.wifiSock = wifiSock;
        this.wifiDstIP = wifiDstIP;
        this.wifiDstPort = wifiDstPort;
        this.wifiPathSeq = new AtomicInteger(1);
        this.wifiDataSeq = integer;
        this.dataMap = new ConcurrentHashMap<>();
        this.windowList = new ConcurrentLinkedQueue<>();
        this.retransList = new ConcurrentLinkedQueue<>();
        wifiProbeExecutor = Executors.newSingleThreadExecutor();
        wifiRecvExecutor = Executors.newSingleThreadExecutor();
        wifiDataExecutor = Executors.newSingleThreadExecutor();
        wifiAckAndNakExecutor = Executors.newSingleThreadExecutor();
        wifiPTOExecutor = Executors.newSingleThreadExecutor();
    }
}


class WiFiProbeTask implements Runnable{

    WiFiControlBlock wifiControlBlock;

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
                DatagramPacket packet = new DatagramPacket(probe,probe.length,wifiControlBlock.wifiDstIP,wifiControlBlock.wifiDstPort);
                wifiControlBlock.wifiSock.send(packet);
                TimeUnit.MICROSECONDS.sleep(wifiControlBlock.wifiPacingGap * wifiControlBlock.wifiProbeScale);
                System.out.println("");
                probeHdr.probeSeq++;
            }
        } catch (IOException | InterruptedException e) {
            //e.printStackTrace();
        }
    }
}


class WiFiRecvTask implements Runnable{

    WiFiControlBlock wifiControlBlock;

    WiFiRecvTask(WiFiControlBlock wifiControlBlock){
        this.wifiControlBlock = wifiControlBlock;
    }

    @Override
    public void run() {

        try {
            while(!wifiControlBlock.wifiEndSign){
                byte[] data = new byte[22];
                DatagramPacket probePacket = new DatagramPacket(data,data.length);
                wifiControlBlock.wifiSock.receive(probePacket);
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
    SplbHdr hdr;
    WiFiControlBlock controblock;

    WiFiDataTask(SplbHdr hdr,WiFiControlBlock controblock){
        super();
        this.controblock = controblock;
        this.hdr = hdr;
    }
    @Override
    public void run() {

        int dataCounter = 0;
        while(!controblock.wifiEndSign && dataCounter <= controblock.wifiProbeScale){
            if(controblock.wifiLostState == true || (controblock.retransList.size() + controblock.windowList.size() >= 400)){
                continue;
            }
            long now = System.nanoTime();
            if(now < controblock.dataNextToSend && controblock.dataNextToSend != 0){
                continue;
            }else{
                dataCounter++;
                SplbHdr dataHdr = new SplbHdr(PacketType.DATAPKG,(byte)1,hdr.probeSeq,controblock.wifiPathSeq.getAndIncrement(), controblock.wifiDataSeq.getAndIncrement());
                if(dataHdr.dataSeq>=1000001){
                    break;
                }
                controblock.wifiInflight.getAndIncrement();
                dataHdr.timeStamp = System.nanoTime();
                controblock.dataNextToSend = dataHdr.timeStamp + controblock.wifiPacingGap*1000;
                byte[] data = DataUtils.getData();
                byte[] sendData =  DataUtils.byteMerger(dataHdr.toByteArray(),data);
                DatagramPacket sendPacket = new DatagramPacket(sendData,sendData.length,controblock.wifiDstIP,controblock.wifiDstPort);
                try {
                    controblock.wifiSock.send(sendPacket);
                    controblock.windowList.add(dataHdr.pathSeq);
                    controblock.dataMap.put(dataHdr.pathSeq,new DataBlock(dataHdr.dataSeq,data));
                    WiFiPacketTimeOutTask wiFiPacketTimeOutTask = new WiFiPacketTimeOutTask(dataHdr, controblock);
                    controblock.wifiPTOExecutor.execute(wiFiPacketTimeOutTask);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}

class WiFiAckAndNakTask implements Runnable{
    public SplbHdr hdr;
    WiFiControlBlock wifiControlBlock;
    WiFiAckAndNakTask(SplbHdr hdr,WiFiControlBlock controblock){
        super();
        this.hdr = hdr;
        this.wifiControlBlock = controblock;
    }

    @Override
    public void run() {
        int ackedSeq = hdr.pathSeq;
        int wantedSeq = hdr.dataSeq;

        if(wifiControlBlock.windowList.contains(ackedSeq)){
           // System.out.println("ack" + ackedSeq);
            Integer minWaitAckSeq = wifiControlBlock.windowList.peek();
            int minACKSeq = minWaitAckSeq.intValue();
            if(ackedSeq == minACKSeq){
                wifiControlBlock.windowList.poll();
                wifiControlBlock.dataMap.remove(ackedSeq);
                wifiControlBlock.wifiLastAckSeq = ackedSeq;
            }else if(ackedSeq - minACKSeq <= wifiControlBlock.kPackets){ //小于阈值时可能是由于乱序引起的
                wifiControlBlock.windowList.remove(ackedSeq);
                wifiControlBlock.dataMap.remove(ackedSeq);
                wifiControlBlock.wifiLastAckSeq = ackedSeq;
            }else{
                wifiControlBlock.windowList.remove(ackedSeq);
                wifiControlBlock.dataMap.remove(ackedSeq);
                wifiControlBlock.wifiLastAckSeq =ackedSeq;
                wifiControlBlock.wifiLostState = true;
            }
            Iterator<Integer> iterator = wifiControlBlock.windowList.iterator();
            while(iterator.hasNext()){
                Integer next = iterator.next();
                if(next.intValue() < wantedSeq){//当前序号小于 接受端顺序接受的序号，说明已经被正确接受了
                    iterator.remove();
                    wifiControlBlock.dataMap.remove(next);
                }else{
                    break;
                }
            }
            if(wifiControlBlock.wifiLostState == true){
                wifiControlBlock.wifiContinueAck = 0;
              //  wifiControlBlock.wifiPacingGap += 2;
               // System.out.println("-----------------------");
                while (wifiControlBlock.windowList.size() >0 && wifiControlBlock.windowList.peek().intValue() < ackedSeq) {
                    Integer lostPathSeqInteger = wifiControlBlock.windowList.poll();
                    int lostPathSeq = lostPathSeqInteger.intValue();
                    System.out.println("wifi----------ack缺失引起重传:"+lostPathSeq);
                    wifiControlBlock.retransList.add(lostPathSeqInteger);
                    DataBlock lostData = wifiControlBlock.dataMap.get(lostPathSeq);
                    SplbHdr retransHdr = new SplbHdr(PacketType.RETRANS, (byte) 1, hdr.probeSeq, lostPathSeq, lostData.dataSeq);
                    retransHdr.timeStamp = System.nanoTime();
                    byte[] sendData = DataUtils.byteMerger(retransHdr.toByteArray(), lostData.data);
                    DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, wifiControlBlock.wifiDstIP, wifiControlBlock.wifiDstPort);
                    try {
                        wifiControlBlock.wifiSock.send(sendPacket);
                        wifiControlBlock.wifiLastRetransSeq = lostPathSeq;
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
              //  System.out.println("-----------------------");
                wifiControlBlock.wifiLostState = false;
            }else{
//                wifiControlBlock.wifiContinueAck++;
//                if(wifiControlBlock.wifiContinueAck == 50){
                  //  wifiControlBlock.wifiContinueAck = 0;
                  //  wifiControlBlock.wifiPacingGap -= 1;
                  //  wifiControlBlock.wifiPacingGap = Math.max(wifiControlBlock.wifiPacingGap,wifiControlBlock.wifiMinPacingGap);
             //   }
            }
        }

        if(wifiControlBlock.retransList.contains(ackedSeq)){
            boolean retransLostState = false;
            Integer retransMinAckSeq = wifiControlBlock.retransList.peek();
            if(retransMinAckSeq == null){
                return;
            }else if (ackedSeq == retransMinAckSeq.intValue()){
                wifiControlBlock.retransList.remove(ackedSeq);
                wifiControlBlock.dataMap.remove(ackedSeq);

            }else if(ackedSeq - retransMinAckSeq.intValue() <= wifiControlBlock.kPackets){
                wifiControlBlock.retransList.remove(ackedSeq);
                wifiControlBlock.dataMap.remove(ackedSeq);
            }else{
                wifiControlBlock.retransList.remove(hdr.pathSeq);
                wifiControlBlock.dataMap.remove(hdr.pathSeq);
                retransLostState = true;
            }
            Iterator<Integer> iterator = wifiControlBlock.retransList.iterator();
            while(iterator.hasNext()){
                Integer next = iterator.next();
                if(next.intValue() < wantedSeq){
                    iterator.remove();
                    wifiControlBlock.dataMap.remove(next);
                }else{
                    break;
                }
            }
            if(retransLostState == true){
                Iterator<Integer> retransIterator = wifiControlBlock.retransList.iterator();
                while (retransIterator.hasNext()){
                    Integer next = retransIterator.next();
                    int rrSeq = next.intValue();
                    if(rrSeq < hdr.pathSeq && rrSeq > wifiControlBlock.wifiLastRRSeq){
                        DataBlock lostData = wifiControlBlock.dataMap.get(next);
                        SplbHdr retransHdr = new SplbHdr(PacketType.RETRANS,(byte)1,hdr.probeSeq,rrSeq,lostData.dataSeq);
                        retransHdr.timeStamp = System.nanoTime();
                        byte[] sendData =  DataUtils.byteMerger(retransHdr.toByteArray(),lostData.data);
                        // System.out.println("--------------------->path-seq:"+hdr.pathSeq+"重传包缺失引起重传:"+rrSeq);
                        DatagramPacket sendPacket = new DatagramPacket(sendData,sendData.length,wifiControlBlock.wifiDstIP,wifiControlBlock.wifiDstPort);
                        try {
                            wifiControlBlock.wifiSock.send(sendPacket);
                            wifiControlBlock.wifiLastRRSeq = rrSeq;
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }else{
                        break;
                    }
                }
                retransLostState = false;
            }

        }
    }
}


class WiFiPacketTimeOutTask implements Runnable,Comparable<WiFiPacketTimeOutTask>{

    public SplbHdr hdr;
    WiFiControlBlock controblock;

    WiFiPacketTimeOutTask(SplbHdr hdr,WiFiControlBlock controblock){
        this.hdr = hdr;
        this.controblock = controblock;
    }

    public int getInteval(long start,long end){    // ns -> us;
        return (int)(end - start)/1000;
    }

    @Override
    public void run() {
        if(!(controblock.windowList.contains(hdr.pathSeq) || controblock.retransList.contains(hdr.pathSeq))){
            return;
        }
        else{
            long sendTimeStamp = hdr.timeStamp;
            int inteval = getInteval(System.nanoTime(),sendTimeStamp);
            if(inteval < controblock.timeThreshold){
                try {
                    TimeUnit.MICROSECONDS.sleep(controblock.timeThreshold - inteval);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            DataBlock dataBlock = controblock.dataMap.get(hdr.pathSeq);
            if(dataBlock == null){
                // System.out.println("已经ack");
                return;
            }else{
                hdr.type = PacketType.RETRANS;
                hdr.timeStamp = System.nanoTime();
                byte[] sendData =  DataUtils.byteMerger(hdr.toByteArray(),dataBlock.data);
                DatagramPacket sendPacket = new DatagramPacket(sendData,sendData.length,controblock.wifiDstIP,controblock.wifiDstPort);
                // System.out.println("PTO触发wifi重新发送"+hdr.pathSeq);
                controblock.wifiPTOExecutor.execute(this);
                try {
                    controblock.wifiSock.send(sendPacket);
                } catch (IOException e) {

                }
            }
        }
    }

    @Override
    public int compareTo(WiFiPacketTimeOutTask o) {
        return this.hdr.timeStamp > o.hdr.timeStamp? -1 : 1;
    }
}


public class SocketService {
    private static final String TAG = "Socket Service";
    private volatile boolean isStartNow = false;    //开始标志
    public static int basePort = 50000;         //分配的基础端口号
    public static SocketService instance = null;
    private WiFiControlBlock wifiControlBlock = null;
    private LTEControlBlock lteControlBlock = null;


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
        apiInstance.bindCellularSocket(lteSocket);
        apiInstance.bindWifiSocket(wifiSocket);
        sleep(2000);
        InetAddress address = InetAddress.getByName(IP);
        AtomicInteger dataInteger = new AtomicInteger(1);
        lteControlBlock = new LTEControlBlock(lteSocket,address,dstPort,dataInteger);
        lteControlBlock.lteProbeExecutor.execute(new LTEProbeTask(lteControlBlock));
        lteControlBlock.lteRecvExecutor.execute(new LTERecvTask(lteControlBlock));
        wifiControlBlock = new WiFiControlBlock(wifiSocket,address,dstPort+1,dataInteger);
        wifiControlBlock.wifiProbeExecutor.execute(new WiFiProbeTask(wifiControlBlock));
        wifiControlBlock.wifiRecvExecutor.execute(new WiFiRecvTask(wifiControlBlock));
    }

    public void stopSendPkt(){
        wifiControlBlock.wifiEndSign = true;
        wifiControlBlock.wifiProbeExecutor.shutdownNow();
        wifiControlBlock.wifiRecvExecutor.shutdownNow();
        wifiControlBlock.wifiDataExecutor.shutdownNow();
        wifiControlBlock.wifiAckAndNakExecutor.shutdownNow();
        wifiControlBlock.wifiPTOExecutor.shutdownNow();
        lteControlBlock.lteEndSign = true;
        lteControlBlock.lteProbeExecutor.shutdownNow();
        lteControlBlock.lteRecvExecutor.shutdownNow();
        lteControlBlock.lteDataExecutor.shutdownNow();
        lteControlBlock.lteAckAndNakExecutor.shutdownNow();
        lteControlBlock.ltePTOExecutor.shutdownNow();
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
        Thread udpThread1 = new Thread(new Runnable() {
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
        Thread udpThread2 = new Thread(new Runnable() {
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
        Thread udpThread1 = new Thread(new Runnable() {
            @Override
            public void run() {
                System.out.println("running lte");
                SplbHdr probeHdr = new SplbHdr(PacketType.DATAPKG,(byte)0,0,0,1);
                byte[] realData = new byte[512];
                for (int i = 0; i < realData.length; i++) {
                    realData[i] = 1;
                }
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
            }
        });
        Thread udpThread2 = new Thread(new Runnable() {
            @Override
            public void run() {
                System.out.println("running lte");
                SplbHdr probeHdr = new SplbHdr(PacketType.DATAPKG,(byte)0,0,0,1);
                byte[] realData = new byte[512];
                for (int i = 0; i < realData.length; i++) {
                    realData[i] = 1;
                }
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
            }
        });
        udpThread1.start();
        //udpThread2.start();
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



    //测试lte tcp性能
    public void testLteTCP(String IP, int dstPort) throws SocketException, InterruptedException, UnknownHostException {
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
