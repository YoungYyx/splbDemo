package com.neu.splb;

import android.net.TrafficStats;

/**
 * DTrafficStats 获取Mobile和Wi-Fi网卡统计信息
 * 可以针对网卡，也可以针对应用
 */
public final class DTrafficStats {
    private static final String TAG = "DTrafficStats";

    /*
    static long getMobileSpeed() 单位Kbps
    */
    static public long getMobileSpeed() {
        long lastTimeStamp = 0;

        long lastTotalRxBytes = 0;
        long nowTotalRxBytes = getMobileRxBytes();
        long nowTimeStamp = System.currentTimeMillis();

        long speed = ((nowTotalRxBytes - lastTotalRxBytes) * 1000 * 8 / (nowTimeStamp - lastTimeStamp));//毫秒转换
        return speed;
    }

    /*
    static long getWiFiSpeed() 单位Kbps
    */
    static public long getWiFiSpeed() {
        long lastTimeStamp = 0;
        long lastTotalRxBytes = 0;
        long nowTotalRxBytes = getWiFiRxBytes();
        long nowTimeStamp = System.currentTimeMillis();

        long speed = ((nowTotalRxBytes - lastTotalRxBytes) * 1000 * 8 / (nowTimeStamp - lastTimeStamp));//毫秒转换
        return speed;
    }

    /*
    static long getMobileRxBytes() //获取通过Mobile连接收到的字节总数，不包含WiFi
    static long getMobileRxPackets() //获取Mobile连接收到的数据包总数，不包含WiFi
    static long getMobileTxBytes() //Mobile发送的总字节数，不包含WiFi
    static long getMobileTxPackets() //Mobile发送的总数据包数，不包含WiFi
     */
    static public long getMobileRxBytes() { //获取通过Mobile连接收到的字节总数，不包含WiFi
        return TrafficStats.getMobileRxBytes() == TrafficStats.UNSUPPORTED ? 0 : (TrafficStats.getMobileRxBytes() / 1024);
    }

    static public long getMobileRxPackets() {  //获取Mobile连接收到的数据包总数
        return TrafficStats.getMobileRxPackets() == TrafficStats.UNSUPPORTED ? 0 : TrafficStats.getMobileRxPackets();
    }

    static public long getMobileTxBytes() {  //Mobile发送的总字节数
        return TrafficStats.getMobileTxBytes() == TrafficStats.UNSUPPORTED ? 0 : (TrafficStats.getMobileTxBytes() / 1024);
    }

    static public long getMobileTxPackets() {  //Mobile发送的总数据包数
        return TrafficStats.getMobileTxPackets() == TrafficStats.UNSUPPORTED ? 0 : TrafficStats.getMobileTxPackets();
    }

    /*
    static long getWiFiRxBytes() //获取通过Wi-Fi连接收到的字节总数，不包含Mobile
    static long getWiFiRxPackets() //获取Wi-Fi连接收到的数据包总数，不包含Mobile
    static long getWiFiTxBytes() //Wi-Fi发送的总字节数，不包含Mobile
    static long getWiFiTxPackets() //Wi-Fi发送的总数据包数，不包含Mobile
     */
    static public long getWiFiRxBytes() { //获取通过Wi-Fi连接收到的字节总数，不包含Mobile
        return getTotalRxBytes() - getMobileRxBytes();
    }

    static public long getWiFiRxPackets() {  //获取Wi-Fi连接收到的数据包总数，不包含Mobile
        return getTotalRxPackets() - getMobileRxPackets();
    }

    static public long getWiFiTxBytes() {  //Wi-Fi发送的总字节数，不包含Mobile
        return getTotalTxBytes() - getMobileTxBytes();
    }

    static public long getWiFiTxPackets() {  //Wi-Fi发送的总数据包数，不包含Mobile
        return getTotalTxPackets() - getMobileTxPackets();
    }

    /*
    static long getTotalRxBytes() //获取总的接受字节数，包含Mobile和WiFi等
    static long getTotalRxPackets() //总的接受数据包数，包含Mobile和WiFi等
    static long getTotalTxBytes() //总的发送字节数，包含Mobile和WiFi等
    static long getTotalTxPackets() //发送的总数据包数，包含Mobile和WiFi等
     */
    static public long getTotalRxBytes() { //获取总的接受字节数，包含Mobile和WiFi等
        return TrafficStats.getTotalRxBytes() == TrafficStats.UNSUPPORTED ? 0 : (TrafficStats.getTotalRxBytes() / 1024);
    }

    static public long getTotalRxPackets() {  //总的接受数据包数，包含Mobile和WiFi等
        return TrafficStats.getTotalRxPackets() == TrafficStats.UNSUPPORTED ? 0 : TrafficStats.getTotalRxPackets();
    }

    static public long getTotalTxBytes() { //总的发送字节数，包含Mobile和WiFi等
        return TrafficStats.getTotalTxBytes() == TrafficStats.UNSUPPORTED ? 0 : (TrafficStats.getTotalTxBytes() / 1024);
    }

    static public long getTotalTxPackets() {  //发送的总数据包数，包含Mobile和WiFi等
        return TrafficStats.getTotalTxPackets() == TrafficStats.UNSUPPORTED ? 0 : TrafficStats.getTotalTxPackets();
    }

    /*
    static long getUidRxBytes(int uid) // 获取某个网络UID的接受字节数，某一个进程的总接收量
    static long getUidTxBytes(int uid) // 获取某个网络UID的发送字节数，某一个进程的总发送量
    static long getUidRxPackets(int uid) //获取某个网络UID的总数据包数，某一个进程的总接收量
    static long getUidTxPackets(int uid) //获取某个网络UID的总数据包数，某一个进程的总发送量
     */
    static public long getUidRxBytes(int uid) { //获取某个网络UID的接受字节数，某一个进程的总接收量
        return TrafficStats.getUidRxBytes(uid) == TrafficStats.UNSUPPORTED ? 0 : (TrafficStats.getUidRxBytes(uid) / 1024);
    }

    static public long getUidTxBytes(int uid) { //获取某个网络UID的发送字节数，某一个进程的总发送量
        return TrafficStats.getUidTxBytes(uid) == TrafficStats.UNSUPPORTED ? 0 : (TrafficStats.getUidTxBytes(uid) / 1024);
    }

    static public long getUidRxPackets(int uid) { //获取某个网络UID的总数据包数，某一个进程的总接收量
        return TrafficStats.getUidRxPackets(uid) == TrafficStats.UNSUPPORTED ? 0 : TrafficStats.getUidRxPackets(uid);
    }

    static public long getUidTxPackets(int uid) { //获取某个网络UID的发总数据包数，某一个进程的总发送量
        return TrafficStats.getUidTxBytes(uid) == TrafficStats.UNSUPPORTED ? 0 : TrafficStats.getUidTxBytes(uid);
    }
}
