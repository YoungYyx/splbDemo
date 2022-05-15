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
import java.util.Arrays;



public class SocketService {
    private volatile boolean endSign = false;
    public void testSplbMode(String IP,int dstPort) throws UnknownHostException, SocketException, InterruptedException {
        SPLBSocket socket = new SPLBSocket();
        socket.connect(IP,dstPort);
        byte[] data = new byte[512];
        Arrays.fill(data,(byte)1);
        Thread splbThread = new Thread(() -> {
            while (!endSign){
                socket.sendData(data);
            }
        });
        splbThread.start();
    }

    public void stopSendPkt(){
        endSign = true;
    }



    //测试wifi udp性能
    public void testWiFiUDP(String IP, int dstPort) throws SocketException, InterruptedException, UnknownHostException {
        final InetAddress address = InetAddress.getByName(IP);
        final DatagramSocket wifiSocket = new DatagramSocket(30000);

        AndroidAPITest apiInstance = AndroidAPITest.getInstance();
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
        udpThread1.start();
    }



    //测试wifi udp性能
    public void testLteUDP(String IP, int dstPort) throws SocketException, InterruptedException, UnknownHostException {

        final InetAddress address = InetAddress.getByName(IP);
        final DatagramSocket lteSocket = new DatagramSocket(30001);

        AndroidAPITest apiInstance = AndroidAPITest.getInstance();
        apiInstance.bindCellularSocket(lteSocket);

        sleep(3000);

        Thread udpThread1 = new Thread(() -> {
            System.out.println("running wifi");
            SplbHdr probeHdr = new SplbHdr(PacketType.DATAPKG,(byte)1,0,0,1);
            byte[] realData = new byte[512];
            Arrays.fill(realData, (byte) 1);
            try {
                while(!endSign){
                    byte[] sendData =  DataUtils.byteMerger(probeHdr.toByteArray(),realData);
                    DatagramPacket packet = new DatagramPacket(sendData,sendData.length,address,dstPort);
                    lteSocket.send(packet);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            lteSocket.close();
        });
        udpThread1.start();
    }


    //测试lte tcp性能
    public void testWiFiTCP(String IP, int dstPort) throws SocketException, InterruptedException, UnknownHostException {

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
                    os.write(DataUtils.byteMerger(new SplbHdr(PacketType.DATAPKG,(byte)0,0,0,1).toByteArray(),realData));
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
                    os.write(DataUtils.byteMerger(new SplbHdr(PacketType.DATAPKG,(byte)0,0,0,1).toByteArray(),realData));
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
