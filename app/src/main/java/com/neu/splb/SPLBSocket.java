package com.neu.splb;

import static java.lang.Thread.sleep;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
    RETRANS((byte)4),   //重传包
    FIN((byte)5),
    PTO((byte)6);

    public byte t;

    PacketType(byte t) {
        this.t= t;
    }
}

enum BBRState{
    STARTUP,
    DRAIN,
    PROBW,
    PROBERTT;
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

    SplbHdr(){

    }

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
            case (byte)5:
                this.type = PacketType.FIN;break;
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
    public static byte[] byteMerger(byte[] bt1, byte[] bt2,int len){
        byte[] bt3 = new byte[bt1.length + len];
        System.arraycopy(bt1, 0, bt3, 0, bt1.length);
        System.arraycopy(bt2, 0, bt3, bt1.length, len);
        return bt3;
    }
    public static byte[] byteMerger(byte[] bt1, byte[] bt2){
        byte[] bt3 = new byte[bt1.length+bt2.length];
        System.arraycopy(bt1, 0, bt3, 0, bt1.length);
        System.arraycopy(bt2, 0, bt3, bt1.length, bt2.length);
        return bt3;
    }
}

class DataBlock{
    int dataSeq;
    byte[] data;
    DataBlock(int dataSeq,byte[] data,int len){
        this.dataSeq = dataSeq;
        this.data = DataUtils.byteMerger(new byte[22],data,len);
    }
}

class DataBuffer{
    private AtomicInteger seqCounter = new AtomicInteger(1);
    public ConcurrentLinkedQueue<DataBlock> buffer = new ConcurrentLinkedQueue<>();
    public int getEndSeq(){
        return seqCounter.get();
    }
    public boolean pushData(byte[] data,int len){
        if(buffer.size() > 1000){
            return false;
        }else{
            DataBlock dataBlock = new DataBlock(seqCounter.getAndIncrement(), data, len);
            buffer.add(dataBlock);
            return true;
        }

    }

    public DataBlock popData(){
        return buffer.poll();
    }
}


class SockControlBlock{
    public boolean fin = false;
    public boolean endSign = false;
    public boolean lostState = false;
    public int pacingGap;   //探测包基础间隔, us
    public int srtt = 0;
    public int minPacingGap;
    public int probeScale = 200;        //探测包对应数据包比例
    public int probeStep = 10;           //间隔减小的步长
    public DatagramSocket socket;
    public InetAddress dstIP;
    public int dstPort;
    public AtomicInteger pathSeq;
    public int lastRetransSeq = 0;
    public int lastAckSeq = 0;
    public long lastAckTimeStamp = 0;
    public int kPackets = 3;
    public long timeThreshold;
    public long lastPTOTimeStamp = 0;
    public long dataNextToSend = 0;
    public  int cwnd = 400;
    public int endPSN;
    public long lastUpdateBwTimeStamp;
    public BBRState state;
    public double[] pacingGain = {1.25, 0.75, 1, 1, 1, 1, 1, 1};
    public long cycleTimeStamp;
    public int pindex = 0;
    public int bwCounter = 0;
    public DataBuffer dataBuffer;
    public ConcurrentHashMap<Integer,DataBlock> dataMap;
    public ConcurrentLinkedQueue<Integer> windowList;
    public ExecutorService probeExecutor;
    public ExecutorService recvExecutor;
    public ExecutorService dataExecutor;
    public ExecutorService ackAndNakExecutor;
    public ExecutorService ptoExecutor;

    public SockControlBlock(DatagramSocket socket, InetAddress dstIP, int dstPort, DataBuffer buffer) {
        this.socket = socket;
        this.dstIP = dstIP;
        this.dstPort = dstPort;
        this.pathSeq = new AtomicInteger(1);
        this.dataBuffer = buffer;
        this.dataMap = new ConcurrentHashMap<>();
        this.windowList = new ConcurrentLinkedQueue<>();
        probeExecutor = Executors.newSingleThreadExecutor();
        recvExecutor = Executors.newSingleThreadExecutor();
        dataExecutor = Executors.newSingleThreadExecutor();
        ackAndNakExecutor = Executors.newSingleThreadExecutor();
        ptoExecutor = Executors.newSingleThreadExecutor();
    }

    public void initLTESockControlBlock(){
        this.pacingGap = 1000;
        this.minPacingGap = 10;
        this.probeScale = 200;
        this.probeStep = 10;
        this.state = BBRState.STARTUP;
    }

    public void initWifiSockControlBlock(){
        this.pacingGap = 1000;
        this.minPacingGap = 10;
        this.probeScale = 200;
        this.probeStep = 10;
        this.state = BBRState.STARTUP;
    }
}

class LTEProbeTask implements Runnable{

    SockControlBlock lteControlBlock;

    LTEProbeTask(SockControlBlock lteControlBlock){
        this.lteControlBlock = lteControlBlock;
    }

    @Override
    public void run() {
        System.out.println("running lte probe");
        SplbHdr probeHdr = new SplbHdr(PacketType.PROBEPKG,(byte)1,1,0,0);
        try {
            while(!lteControlBlock.fin){
                probeHdr.timeStamp = System.nanoTime();
                byte[] probe = probeHdr.toByteArray();
                DatagramPacket packet = new DatagramPacket(probe,probe.length,lteControlBlock.dstIP,lteControlBlock.dstPort);
                lteControlBlock.socket.send(packet);
                TimeUnit.MICROSECONDS.sleep(lteControlBlock.pacingGap * lteControlBlock.probeScale);
                probeHdr.probeSeq++;
            }
        } catch (IOException | InterruptedException e) {
            //e.printStackTrace();
        }
    }
}


class LTERecvTask implements Runnable{

    SockControlBlock lteControlBlock;

    LTERecvTask(SockControlBlock lteControlBlock){
        this.lteControlBlock = lteControlBlock;
    }

    @Override
    public void run() {

        try {
            while(!lteControlBlock.endSign){
                byte[] data = new byte[22];
                DatagramPacket probePacket = new DatagramPacket(data,data.length);
                lteControlBlock.socket.receive(probePacket);
                long timeStamp = System.nanoTime();
                byte[] msg = probePacket.getData();
                SplbHdr hdr = new SplbHdr(msg);
                if(hdr.type == PacketType.PROBEPKG) //报文为回传探测包类
                {
                    LTEDataTask dataTask = new LTEDataTask(hdr,lteControlBlock,timeStamp);
                    lteControlBlock.dataExecutor.execute(dataTask);
                } else if(hdr.type == PacketType.FIN){
                    lteControlBlock.endSign = true;
                }else{
                    LTEAckAndNakTask ackAndNakTask = new LTEAckAndNakTask(hdr,lteControlBlock,timeStamp);
                    lteControlBlock.ackAndNakExecutor.execute(ackAndNakTask);
                }
            }
        } catch (IOException e) {
            //e.printStackTrace();
        }
    }
}


class LTEDataTask implements Runnable{
    SplbHdr hdr;
    SockControlBlock lteControlBlock;
    long timeStamp;

    LTEDataTask(SplbHdr hdr,SockControlBlock lteControlBlock,long timeStamp){
        super();
        this.timeStamp  = timeStamp;
        this.lteControlBlock = lteControlBlock;
        this.hdr = hdr;
    }
    @Override
    public void run() {

        updateBW();
        int rtt = (int) ((timeStamp - hdr.timeStamp)/ 1000);// 转换微秒
        if(lteControlBlock.srtt == 0){
            lteControlBlock.srtt = rtt;
        }else{
            lteControlBlock.srtt = (int) (0.8 * lteControlBlock.srtt + 0.2 * rtt);
        }
        lteControlBlock.timeThreshold = (long) (lteControlBlock.srtt * 1.5);
        int dataCounter = 0;
        while(!lteControlBlock.endSign && dataCounter <= lteControlBlock.probeScale){
            if (!lteControlBlock.lostState && ( lteControlBlock.windowList.size() <= lteControlBlock.cwnd )) {
                long now = System.nanoTime();
                if (now < lteControlBlock.dataNextToSend && lteControlBlock.dataNextToSend != 0) {
                    continue;
                } else {
                    DataBlock dataBlock = lteControlBlock.dataBuffer.popData();
                    if(dataBlock == null){
                        if(lteControlBlock.fin){
                            int EPSN = lteControlBlock.endPSN == 0 ? lteControlBlock.pathSeq.getAndIncrement():lteControlBlock.endPSN;
                            lteControlBlock.endPSN = EPSN;
                            int endSeq = lteControlBlock.dataBuffer.getEndSeq();
                            SplbHdr finHdr = new SplbHdr(PacketType.FIN,(byte)0,0,EPSN,endSeq);
                            byte[] dataToSend = finHdr.toByteArray();
                            lteControlBlock.dataNextToSend = now + lteControlBlock.pacingGap* 1000L;
                            DatagramPacket sendPacket = new DatagramPacket(dataToSend,dataToSend.length,lteControlBlock.dstIP,lteControlBlock.dstPort);
                            try {
                                lteControlBlock.socket.send(sendPacket);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }else{
                            continue;
                        }
                    }else{
                        dataCounter++;
                        byte[] dataToSend = dataBlock.data;
                        int PSN = lteControlBlock.pathSeq.getAndIncrement();
                        ByteBuffer bbuffer = ByteBuffer.wrap(dataToSend);
                        bbuffer.putLong(now);
                        bbuffer.putInt(hdr.probeSeq);
                        bbuffer.putInt(PSN);
                        bbuffer.putInt(dataBlock.dataSeq);
                        bbuffer.put((byte)0);
                        bbuffer.put((byte)0);
                        lteControlBlock.dataNextToSend = now + lteControlBlock.pacingGap* 1000L;
                        DatagramPacket sendPacket = new DatagramPacket(dataToSend,dataToSend.length,lteControlBlock.dstIP,lteControlBlock.dstPort);
                        try {
                            lteControlBlock.socket.send(sendPacket);
                            lteControlBlock.windowList.add(PSN);
                            lteControlBlock.dataMap.put(PSN,dataBlock);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }

    public void updateBW(){
        double gain = 1.5;
        int newGap = hdr.dataSeq;
        if (newGap == 0 || newGap == lteControlBlock.pacingGap) {
            return;
        } else {
            // System.out.println("newgap:"+newGap);
            if (lteControlBlock.state == BBRState.STARTUP) {
                gain = lteControlBlock.pacingGap / newGap;
                //System.out.println(lteControlBlock.pacingGap + ","+newGap+",gain:"+gain);
                if (gain > 1.1) {
                    lteControlBlock.pacingGap = (int) ( newGap / 1.5 );
                } else {
                    lteControlBlock.pacingGap = (int) (newGap/1.25);
                    lteControlBlock.state = BBRState.DRAIN;
                }
            } else if (lteControlBlock.state == BBRState.DRAIN) {
                drainToTarget();
                lteControlBlock.state = BBRState.PROBW;
                lteControlBlock.cycleTimeStamp = System.nanoTime();
            } else {
                newGap = (int)(newGap / gain);
                long nowTime = System.nanoTime();
                int elapsedUS = (int) ( ( nowTime - lteControlBlock.cycleTimeStamp ) / 1000 );
                if (elapsedUS > lteControlBlock.srtt) {
                    lteControlBlock.pindex = ( lteControlBlock.pindex + 1 ) % 8;
                    lteControlBlock.cycleTimeStamp = nowTime;
                }
                double pacing_gain = lteControlBlock.pacingGain[lteControlBlock.pindex];
                lteControlBlock.pacingGap = (int) ( newGap / pacing_gain );
            }
        }
    }

    public void drainToTarget(){

    }

}


class LTEAckAndNakTask implements Runnable{

    public SplbHdr hdr;

    public SockControlBlock lteControlBlock;

    public long timeStamp;

    LTEAckAndNakTask(SplbHdr hdr,SockControlBlock lteControlBlock,long timeStamp){
        super();
        this.hdr = hdr;
        this.lteControlBlock = lteControlBlock;
        this.timeStamp = timeStamp;
    }

    @Override
    public void run() {

        int rtt = (int) ((timeStamp - hdr.timeStamp)/ 1000);// 转换微秒
        lteControlBlock.srtt = (int) (0.8 * lteControlBlock.srtt + 0.2 * rtt);
        lteControlBlock.timeThreshold = lteControlBlock.srtt * 2L + (long) lteControlBlock.kPackets * lteControlBlock.pacingGap;
        Integer ackedSeq = hdr.pathSeq;
        Integer wantedSeq = hdr.dataSeq;
       // System.out.println(hdr.type+","+ ackedSeq + ","+wantedSeq);
        ConcurrentLinkedQueue<Integer> wList = lteControlBlock.windowList;
        ConcurrentHashMap<Integer, DataBlock> dataMap = lteControlBlock.dataMap;

        while((wList.size() > 0) && (wList.peek() < wantedSeq)){
            Integer poll = wList.poll();
            dataMap.remove(poll);
        }
        if(hdr.type==PacketType.ACKPKG){
            lteControlBlock.lastAckSeq = ackedSeq;
            lteControlBlock.lastAckTimeStamp = timeStamp;
        }else{
            if (wList.contains(ackedSeq)){
                wList.remove(ackedSeq);
                dataMap.remove(ackedSeq);
                if(ackedSeq - wantedSeq > lteControlBlock.kPackets){
                    lteControlBlock.lostState = true;
                }
                if(lteControlBlock.lostState){
                    for (Integer next : wList) {
                        int lostPathSeq = next;
                        if (lostPathSeq <= lteControlBlock.lastRetransSeq) {
                            continue;
                        }
                        if(lostPathSeq > ackedSeq){
                            break;
                        }
                        DataBlock lostData = dataMap.get(lostPathSeq);
                        if (lostData == null) continue;
                        byte[] sendData = lostData.data;
                        ByteBuffer bbuffer = ByteBuffer.wrap(sendData);
                        long now =  System.nanoTime();
                        bbuffer.putLong(now);
                        bbuffer.putInt(hdr.probeSeq);
                        bbuffer.putInt(lostPathSeq);
                        bbuffer.putInt(lostData.dataSeq);
                        bbuffer.put((byte)4);
                        bbuffer.put((byte)0);
                        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, lteControlBlock.dstIP, lteControlBlock.dstPort);
                        try {
                            // System.out.println("acked:"+ackedSeq+"retrans:" + lostPathSeq);
                            lteControlBlock.socket.send(sendPacket);
                            lteControlBlock.lastRetransSeq = lostPathSeq;
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    lteControlBlock.lostState = false;
                }
            }
        }
    }
}


class LTEPTOTask implements Runnable{

    SockControlBlock lteControlBlock;
    ConcurrentLinkedQueue<Integer> wList;
    ConcurrentHashMap<Integer, DataBlock> dataMap;

    LTEPTOTask(SockControlBlock lteControlBlock){
        this.lteControlBlock = lteControlBlock;
        wList = lteControlBlock.windowList;
        dataMap = lteControlBlock.dataMap;
    }

    @Override
    public void run() {
        while(!lteControlBlock.endSign){
            long cur = System.nanoTime();
            long rtogap = Math.min((cur - lteControlBlock.lastAckTimeStamp),(cur - lteControlBlock.lastPTOTimeStamp))/1000;
            if(rtogap >= lteControlBlock.timeThreshold){
                Iterator<Integer> iterator = wList.iterator();
                int counter = 0;
                while(iterator.hasNext()){
                    Integer lost = iterator.next();
                    DataBlock lostData = dataMap.get(lost);
                    if(lostData==null){
                        continue;
                    }
                    if(counter==10) break;
                    byte[] sendData = lostData.data;
                    ByteBuffer bbuffer = ByteBuffer.wrap(sendData);
                    long now =  System.nanoTime();
                    bbuffer.putLong(now);
                    bbuffer.putInt(0);
                    bbuffer.putInt(lost);
                    bbuffer.putInt(lostData.dataSeq);
                    bbuffer.put((byte)4);
                    bbuffer.put((byte)0);
                    DatagramPacket sendPacket = new DatagramPacket(sendData,sendData.length,lteControlBlock.dstIP,lteControlBlock.dstPort);
                    try {
                        lteControlBlock.socket.send(sendPacket);
                        counter++;
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                lteControlBlock.lastPTOTimeStamp = System.nanoTime();
            }
        }
    }
}

class WiFiProbeTask implements Runnable{

    SockControlBlock wifiControlBlock;

    WiFiProbeTask(SockControlBlock wifiControlBlock){
        this.wifiControlBlock = wifiControlBlock;
    }

    @Override
    public void run() {
        System.out.println("running wifi probe");
        SplbHdr probeHdr = new SplbHdr(PacketType.PROBEPKG,(byte)1,1,0,0);
        try {
            while(!wifiControlBlock.fin){
                probeHdr.timeStamp = System.nanoTime();
                byte[] probe = probeHdr.toByteArray();
                DatagramPacket packet = new DatagramPacket(probe,probe.length,wifiControlBlock.dstIP,wifiControlBlock.dstPort);
                wifiControlBlock.socket.send(packet);
                TimeUnit.MICROSECONDS.sleep(wifiControlBlock.pacingGap * wifiControlBlock.probeScale);
                probeHdr.probeSeq++;
            }
        } catch (IOException | InterruptedException e) {
            //e.printStackTrace();
        }
    }
}


class WiFiRecvTask implements Runnable{

    SockControlBlock wifiControlBlock;

    WiFiRecvTask(SockControlBlock wifiControlBlock){
        this.wifiControlBlock = wifiControlBlock;
    }

    @Override
    public void run() {

        try {
            while(!wifiControlBlock.endSign){
                byte[] data = new byte[22];
                DatagramPacket probePacket = new DatagramPacket(data,data.length);
                wifiControlBlock.socket.receive(probePacket);
                long timeStamp = System.nanoTime();
                byte[] msg = probePacket.getData();
                SplbHdr hdr = new SplbHdr(msg);
                if(hdr.type == PacketType.PROBEPKG) //报文为回传探测包类
                {
                    WiFiDataTask dataTask = new WiFiDataTask(hdr,wifiControlBlock,timeStamp);
                    wifiControlBlock.dataExecutor.execute(dataTask);
                } else if(hdr.type == PacketType.FIN){
                    wifiControlBlock.endSign = true;
                }else{
                    WiFiAckAndNakTask ackAndNakTask = new WiFiAckAndNakTask(hdr,wifiControlBlock,timeStamp);
                    wifiControlBlock.ackAndNakExecutor.execute(ackAndNakTask);
                }
            }
        } catch (IOException e) {
            //e.printStackTrace();
        }
    }
}


class WiFiDataTask implements Runnable{
    SplbHdr hdr;
    SockControlBlock wifiControlBlock;
    long timeStamp;

    WiFiDataTask(SplbHdr hdr,SockControlBlock wifiControlBlock,long timeStamp){
        super();
        this.timeStamp  = timeStamp;
        this.wifiControlBlock = wifiControlBlock;
        this.hdr = hdr;
    }
    @Override
    public void run() {

        updateBW();
        int rtt = (int) ((timeStamp - hdr.timeStamp)/ 1000);// 转换微秒
        if(wifiControlBlock.srtt == 0){
            wifiControlBlock.srtt = rtt;
        }else{
            wifiControlBlock.srtt = (int) (0.8 * wifiControlBlock.srtt + 0.2 * rtt);
        }
        wifiControlBlock.timeThreshold = (long) (wifiControlBlock.srtt * 1.5);
        int dataCounter = 0;
        while(!wifiControlBlock.endSign && dataCounter <= wifiControlBlock.probeScale){
            if (!wifiControlBlock.lostState && ( wifiControlBlock.windowList.size() <= wifiControlBlock.cwnd )) {
                long now = System.nanoTime();
                if (now < wifiControlBlock.dataNextToSend && wifiControlBlock.dataNextToSend != 0) {
                    continue;
                } else {
                    DataBlock dataBlock = wifiControlBlock.dataBuffer.popData();
                    if(dataBlock == null){
                        if(wifiControlBlock.fin){
                            int EPSN = wifiControlBlock.endPSN == 0 ? wifiControlBlock.pathSeq.getAndIncrement():wifiControlBlock.endPSN;
                            wifiControlBlock.endPSN = EPSN;
                            int endSeq = wifiControlBlock.dataBuffer.getEndSeq();
                            SplbHdr finHdr = new SplbHdr(PacketType.FIN,(byte)1,0,EPSN,endSeq);
                            byte[] dataToSend = finHdr.toByteArray();
                            wifiControlBlock.dataNextToSend = now + wifiControlBlock.pacingGap* 1000L;
                            DatagramPacket sendPacket = new DatagramPacket(dataToSend,dataToSend.length,wifiControlBlock.dstIP,wifiControlBlock.dstPort);
                            try {
                                wifiControlBlock.socket.send(sendPacket);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }else{
                            continue;
                        }
                    }else{
                        dataCounter++;
                        byte[] dataToSend = dataBlock.data;
                        int PSN = wifiControlBlock.pathSeq.getAndIncrement();
                        ByteBuffer bbuffer = ByteBuffer.wrap(dataBlock.data);
                        bbuffer.putLong(now);
                        bbuffer.putInt(hdr.probeSeq);
                        bbuffer.putInt(PSN);
                        bbuffer.putInt(dataBlock.dataSeq);
                        bbuffer.put((byte)0);
                        bbuffer.put((byte)1);
                        wifiControlBlock.dataNextToSend = now + wifiControlBlock.pacingGap* 1000L;
                        DatagramPacket sendPacket = new DatagramPacket(dataToSend,dataToSend.length,wifiControlBlock.dstIP,wifiControlBlock.dstPort);
                        try {
                            wifiControlBlock.socket.send(sendPacket);
                            wifiControlBlock.windowList.add(PSN);
                            wifiControlBlock.dataMap.put(PSN,dataBlock);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }

    public void updateBW(){
        double gain = 1.6;
        int newGap = hdr.dataSeq;
        if (newGap == 0 || newGap == wifiControlBlock.pacingGap) {
            return;
        } else {
            if (wifiControlBlock.state == BBRState.STARTUP) {
                gain = wifiControlBlock.pacingGap / newGap;
                if (gain > 1.1) {
                    wifiControlBlock.pacingGap = (int) ( newGap / 1.5 );
                } else {
                    wifiControlBlock.pacingGap = (int) (newGap/1.25);
                    wifiControlBlock.state = BBRState.DRAIN;
                }
            } else if (wifiControlBlock.state == BBRState.DRAIN) {
                drainToTarget();
                wifiControlBlock.state = BBRState.PROBW;
                wifiControlBlock.cycleTimeStamp = System.nanoTime();
            } else {
                newGap  = (int) (newGap/gain);
                long nowTime = System.nanoTime();
                int elapsedUS = (int) ( ( nowTime - wifiControlBlock.cycleTimeStamp ) / 1000 );
                if (elapsedUS > wifiControlBlock.srtt) {
                    wifiControlBlock.pindex = ( wifiControlBlock.pindex + 1 ) % 8;
                    wifiControlBlock.cycleTimeStamp = nowTime;
                }
                double pacing_gain = wifiControlBlock.pacingGain[wifiControlBlock.pindex];

                wifiControlBlock.pacingGap = (int) ( newGap / pacing_gain );
                //System.out.println(wifiControlBlock.pacingGap);
            }
        }
    }

    public void drainToTarget(){

    }

}


class WiFiAckAndNakTask implements Runnable{

    public SplbHdr hdr;

    public SockControlBlock wifiControlBlock;

    public long timeStamp;

    WiFiAckAndNakTask(SplbHdr hdr,SockControlBlock wifiControlBlock,long timeStamp){
        super();
        this.hdr = hdr;
        this.wifiControlBlock = wifiControlBlock;
        this.timeStamp = timeStamp;
    }

    @Override
    public void run() {

        int rtt = (int) ((timeStamp - hdr.timeStamp)/ 1000);// 转换微秒
        wifiControlBlock.srtt = (int) (0.8 * wifiControlBlock.srtt + 0.2 * rtt);
        wifiControlBlock.timeThreshold = wifiControlBlock.srtt * 2L + (long) wifiControlBlock.kPackets * wifiControlBlock.pacingGap;
        Integer ackedSeq = hdr.pathSeq;
        Integer wantedSeq = hdr.dataSeq;
        ConcurrentLinkedQueue<Integer> wList = wifiControlBlock.windowList;
        ConcurrentHashMap<Integer, DataBlock> dataMap = wifiControlBlock.dataMap;

        while((wList.size() > 0) && (wList.peek() < wantedSeq)){
            Integer poll = wList.poll();
            dataMap.remove(poll);
        }
        if(hdr.type==PacketType.ACKPKG){
            wifiControlBlock.lastAckSeq = ackedSeq;
            wifiControlBlock.lastAckTimeStamp = timeStamp;
        }else{
            if (wList.contains(ackedSeq)){
                wList.remove(ackedSeq);
                dataMap.remove(ackedSeq);
                if(ackedSeq - wantedSeq > wifiControlBlock.kPackets){
                    wifiControlBlock.lostState = true;
                }
                if(wifiControlBlock.lostState){
                    for (Integer next : wList) {
                        int lostPathSeq = next;
                        if (lostPathSeq <= wifiControlBlock.lastRetransSeq) {
                            continue;
                        }
                        if(lostPathSeq > ackedSeq){
                            break;
                        }
                        DataBlock lostData = dataMap.get(lostPathSeq);
                        if (lostData == null) continue;
                        byte[] sendData = lostData.data;
                        ByteBuffer bbuffer = ByteBuffer.wrap(sendData);
                        long now =  System.nanoTime();
                        bbuffer.putLong(now);
                        bbuffer.putInt(hdr.probeSeq);
                        bbuffer.putInt(lostPathSeq);
                        bbuffer.putInt(lostData.dataSeq);
                        bbuffer.put((byte)4);
                        bbuffer.put((byte)1);
                        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, wifiControlBlock.dstIP, wifiControlBlock.dstPort);
                        try {
                           // System.out.println("acked:"+ackedSeq+"retrans:" + lostPathSeq);
                            wifiControlBlock.socket.send(sendPacket);
                            wifiControlBlock.lastRetransSeq = lostPathSeq;
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    wifiControlBlock.lostState = false;
                }
            }
        }
    }
}


class WiFiPTOTask implements Runnable{

    SockControlBlock wifiControlBlock;
    ConcurrentLinkedQueue<Integer> wList;
    ConcurrentHashMap<Integer, DataBlock> dataMap;

    WiFiPTOTask(SockControlBlock wifiControlBlock){
        this.wifiControlBlock = wifiControlBlock;
        wList = wifiControlBlock.windowList;
        dataMap = wifiControlBlock.dataMap;
    }

    @Override
    public void run() {
        while(!wifiControlBlock.endSign){
            long cur = System.nanoTime();
            long rtogap = Math.min((cur - wifiControlBlock.lastAckTimeStamp),(cur - wifiControlBlock.lastPTOTimeStamp))/1000;
            if(rtogap >= wifiControlBlock.timeThreshold){
                Iterator<Integer> iterator = wList.iterator();
                int counter = 0;
                while(iterator.hasNext()){
                    Integer lost = iterator.next();
                    DataBlock lostData = dataMap.get(lost);
                    if(lostData==null){
                        continue;
                    }
                    if(counter==10) break;
                    byte[] sendData = lostData.data;
                    ByteBuffer bbuffer = ByteBuffer.wrap(sendData);
                    long now =  System.nanoTime();
                    bbuffer.putLong(now);
                    bbuffer.putInt(0);
                    bbuffer.putInt(lost);
                    bbuffer.putInt(lostData.dataSeq);
                    bbuffer.put((byte)4);
                    bbuffer.put((byte)1);
                    DatagramPacket sendPacket = new DatagramPacket(sendData,sendData.length,wifiControlBlock.dstIP,wifiControlBlock.dstPort);
                    try {
                        wifiControlBlock.socket.send(sendPacket);
                        counter++;
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                wifiControlBlock.lastPTOTimeStamp = System.nanoTime();
            }
        }
    }
}


public class SPLBSocket {

    private volatile boolean isActive =  false;    //开始标志
    public static int basePort = 50000;         //分配的基础端口号
    private SockControlBlock wifiControlBlock = null;
    private SockControlBlock lteControlBlock = null;
    private DataBuffer databuffer;

    private DatagramSocket getUdpSocket() throws SocketException {
        return new DatagramSocket(basePort++);
    }

    public void connect(String IP, int dstPort){
        try{
            AndroidAPITest apiInstance = AndroidAPITest.getInstance();
            final DatagramSocket lteSocket = this.getUdpSocket();
            final DatagramSocket wifiSocket = this.getUdpSocket();
            apiInstance.bindCellularSocket(lteSocket);
            apiInstance.bindWifiSocket(wifiSocket);
            sleep(3000);
            InetAddress address = InetAddress.getByName(IP);
            this.databuffer = new DataBuffer();
            lteControlBlock = new SockControlBlock(lteSocket,address,dstPort,databuffer);
            lteControlBlock.initLTESockControlBlock();
            lteControlBlock.probeExecutor.execute(new LTEProbeTask(lteControlBlock));
            lteControlBlock.recvExecutor.execute(new LTERecvTask(lteControlBlock));
            lteControlBlock.ptoExecutor.execute(new LTEPTOTask(lteControlBlock));
            wifiControlBlock = new SockControlBlock(wifiSocket,address,dstPort+1,databuffer);
            wifiControlBlock.initWifiSockControlBlock();
            wifiControlBlock.probeExecutor.execute(new WiFiProbeTask(wifiControlBlock));
            wifiControlBlock.recvExecutor.execute(new WiFiRecvTask(wifiControlBlock));
            wifiControlBlock.ptoExecutor.execute(new WiFiPTOTask(wifiControlBlock));
        }catch (SocketException | InterruptedException | UnknownHostException e) {
            e.printStackTrace();
        }
    }

    public void disConnect(){
        wifiControlBlock.fin = true;
        lteControlBlock.fin = true;
    }



    public boolean sendData(byte[] data,int len){
        return this.databuffer.pushData(data,len);
    }
}
