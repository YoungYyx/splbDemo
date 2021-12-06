package com.neu.splb;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.Message;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;

/**
 * 提供打开关系Local socket的能力
 * 对外接口
 * registerApp
 * unRegisterApp
 * bindToNetInterface
 * getAvailableNetInterface
 * getSignalLevel
 * bindToNetInterface
 */
public class ApiTest {
    /* 蜂窝网卡名字 */
    public static final String MOBILE_NAME = "rmnet00";
    /* Wi-Fi网卡名字 */
    public static final String WIFI_NAME = "wlan00";

    public static final int MSG_RECEIVE = 0;

    private static final String TAG = "ApiTest";

    private static final String NAME_SOCKET = "resmon";

    private static volatile ApiTest sInstance;
    protected LocalSocket clientSocket = null;
    private LocalSocketAddress clientSocketAddr = null;
    private final int BUFFER_SIZE = 1024;
    private final int RESP_HEAD_LEN = 44;
    private final int REQ_HEAD_LEN = 46;
    private final int MSGTYPE_BEGIN_INDEX = 6;
    private final int MSGTYPE_END_INDEX = 7;
    /* 注册接口 */
    private final String registerMessage = "{\"apiId\":1000}";
    private final String unRegisterMessage = "{\"apiId\":1001}";
    private final String getAvailableNetInterfaceMessage = "{\"apiId\":2000}";
    private final String getWiFiSignalLevelMessage = "{\"apiId\":2001, \"netInterface\":\"wlan00\"}";
    private final String getMobileSignalLevelMessage = "{\"apiId\":2001, \"netInterface\":\"rmnet00\"}";
    private final String bindToMobileInterfaceMessage = "{\"apiId\":3000, \"netInterface\":\"rmnet00\"}";
    private final String bindToWiFiInterfaceMessage = "{\"apiId\":3000, \"netInterface\":\"wlan00\"}";

    public static ApiTest getInstance() {
        if (sInstance == null) {
            synchronized (ApiTest.class) {
                if (sInstance == null) {
                    sInstance = new ApiTest();
                }
            }
        }
        return sInstance;
    }

    /**
     * 1.create socket, socketName is “resmon”
     * 2.connect server
     * 3.new Thread to receive msg from server
     * 4.received msg from server, include msg head and body, head len is 42 Bytes
     * 5.get body msg. HEAD_LEN=42 (up to interface document)
     * 6.get json string, like this:{“apiId”:2000, “retCode”:0, “netInfo”:[{“netInterface”:wlan00, “label”:0}, {“netInterface”:rmnet00, “label”:1}]}
     * 7.do something by yourself
     */
    public Boolean openLocalSocket() {
        try {
            Log.d(TAG, "Enter OpenSocket");
            // 1.create socket, socketName is resmon
            clientSocket = new LocalSocket();
            clientSocketAddr = new LocalSocketAddress(NAME_SOCKET, LocalSocketAddress.Namespace.ABSTRACT);
            // 2.connect server
            clientSocket.connect(clientSocketAddr);
            Log.i(TAG, "openLocalSocket success");
            // 3.new Thread to receive msg from server
            final BufferedReader readStream = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        String respMsg;
                        int readed = 0;
                        while (readed != -1) {
                            char[] revMsg = new char[BUFFER_SIZE];
                            char[] msgType = new char[2];
                            // 4.received msg from server, include msg head and body, head len is 42 Bytes
                            readed = readStream.read(revMsg, 0, BUFFER_SIZE);

                            /**
                             * 5. get msgType(MSGTYPE_BEGIN_INDEX = 6,
                             * MSGTYPE_END_INDEX = 7),
                             * revMsg[MSGTYPE_BEGIN_INDEX] == 1 is reqMsg,
                             * revMsg[MSGTYPE_BEGIN_INDEX] == 2 is respMsg
                             */
                            msgType[0] = revMsg[MSGTYPE_BEGIN_INDEX];
                            msgType[1] = revMsg[MSGTYPE_END_INDEX];
                            int bodyBegin = RESP_HEAD_LEN;
                            if (msgType[0] == 1) {
                                bodyBegin = REQ_HEAD_LEN;
                            }
                            /**
                             * 6. get body msg. bodyBegin is REQ_HEAD_LEN=44 or RESP_HEAD_LEN=46
                             * and translate to json string
                             */
                            respMsg = String.valueOf(revMsg, bodyBegin, revMsg.length - bodyBegin);
                            respMsg = respMsg.trim();
                            MainActivity.MyHandler handler = MainActivity.getMyHandler();
                            Message msg = new Message();
                            msg.what = MSG_RECEIVE;
                            msg.obj = respMsg;
                            handler.sendMessage(msg);
                            Log.d(TAG, "---------> revMsg is : " + respMsg);
                        }
                    } catch (IOException e) {
                        Log.d(TAG, e.getMessage());
                    }
                }
            }).start();
        } catch (IOException e) {
            Log.d(TAG, e.getMessage());
            return false;
        }
        return true;
    }

    /**
     * close socket
     */
    public Boolean closeLocalSocket() {
        try {
            if (clientSocket == null) {
                Log.e(TAG, "please openSocket first!!!");
                return false;
            }
            // close socket
            clientSocket.close();
            Log.i(TAG, "closeLocalSocket success");
        } catch (IOException e) {
            Log.e(TAG, "closeLocalSocket failed: " + e.getMessage());
            return false;
        }
        return true;
    }

    /**
     * App在使用任何LinkTurbo API接口前需要先注册成功，在App进程的生命周期内无需重复注册。
     * 当App退出/后台被杀死/被卸载，再使用LinkTurbo API时需要重新注册。注册结果会通过Response返
     * 回给调用者。
     * Request 消息体 原型：{“apiId”: 1000}
     * * Response 消息体 原型举例：{“apiId”: 1000 , “retCode”: 0}
     */
    public Boolean registerApp() {
        try {
            if (clientSocket == null) {
                Log.w(TAG, "please open socket first!!!");
                return false;
            }
            MsgHeader msgHeader = new MsgHeader();
            // 1.generate msg head,hdrData is byte[]
            byte[] hdrData = msgHeader.getNewHeaderMsg(registerMessage.length());
            // 2.generate msg head and body, body is as this:
            byte[] msgByte = registerMessage.getBytes();
            byte[] newByte = concatByte(hdrData, msgByte);
            writeSocket(newByte);
            Log.d(TAG, "send msg: " + registerMessage);
        } catch (IOException e) {
            Log.d(TAG, e.getMessage());
            return false;
        }
        return true;
    }

    /**
     * LinkTurbo仅保留Android默认网络接口，关闭其余网口，清空记录的业务规则，所有数据流绑回
     * 到Andoird默认网口。
     * Tips: 该方法可用于APP重置系统状态，例如APP进程挂掉后丢失网络绑定状态，可通过该接口
     * 重置网络。
     * Request 消息体 原型：
     * {“apiId”: 1001} Response
     * 消息体 原型举例：
     * {“apiId”: 1001, “retCode”: 0}
     */
    public Boolean unRegisterApp() {
        try {
            if (clientSocket == null) {
                Log.w(TAG, "please open socket first");
                return false;
            }
            MsgHeader msgHeader = new MsgHeader();
            byte[] hdrData = msgHeader.getNewHeaderMsg(unRegisterMessage.length());
            Log.d(TAG, "unRegisterApp hdrData = " + Arrays.toString(hdrData));
            byte[] msgByte = unRegisterMessage.getBytes();
            byte[] newByte = concatByte(hdrData, msgByte);
            writeSocket(newByte);
            Log.d(TAG, "send msg: " + unRegisterMessage);
        } catch (IOException e) {
            Log.d(TAG, e.getMessage());
            return false;
        }
        return true;
    }

    /**
     * 获取可用的网络接口，返回当前系统处于active状态的所有网络接口信息，每个网络接口信息包
     * 括接口名、能力描述等。返回的接口是一个json格式接口，例如
     * "netInfo": [{"name": "wlan00", "label": 0},  {"name": "rmnet00", "label": 1} ]
     * Request 消息体 原型：
     * {"apiId": 2000}
     * Response 消息体 原型举例：
     * {"apiId": 2000, "retCode": 0,   "netInfo": [{"netInterface": "wlan00", "label": 0}, {"netInterface": "rmnet00", "label": 1}] }
     *
     * netInterface:
     * wlan AB 或 rmnet AB，预留支持更多的网络接口 A:物理网络接口编号；  B:逻辑链路编号。
     * 显然一个物理网口可以同时拥有多个逻辑链路， 例如rmnet01表示 第1张sim卡网络中，第1个网络切片承载。
     * Label:
     * normal: 0 low latency: 1 high bandwidth: 2 预留支持更多的业务类型
     */
    public Boolean getAvailableNetInterface() {
        try {
            if (clientSocket == null) {
                Log.e(TAG, "please open socket first");
                return false;
            }
            MsgHeader msgHeader = new MsgHeader();
            byte[] hdrData = msgHeader.getNewHeaderMsg(getAvailableNetInterfaceMessage.length());
            Log.d(TAG, "hdrData = " + Arrays.toString(hdrData));
            byte[] msgByte = getAvailableNetInterfaceMessage.getBytes();
            byte[] newByte = concatByte(hdrData, msgByte);
            Log.d(TAG, "newByte = " + Arrays.toString(newByte));
            writeSocket(newByte);
            Log.d(TAG, "send msg: " + getAvailableNetInterfaceMessage);
        } catch (IOException e) {
            Log.d(TAG, e.getMessage());
            return false;
        }
        return true;
    }

    /**
     * 获取特定网口的信号质量，以等级表示。注意：每次仅可以获取一个网卡的信号等级。多个网
     * 卡的信号等级需要多次调用该方法。
     * Request
     * 消息体 原型：
     * {"apiId": 2001，"netInterface": "wlan00"}
     * //netInterface必须通过getAvailableNetInterface获取
     * Response
     * 消息体 原型举例：
     * { "apiId": 2001，"retCode": 0, "signalLeveL": 3}
     * signalLeveL返回值：
     * 值: 1-3
     * 1 信号差；2 中等；3 信号好
     * e.g.,
     * 1格信号对应：1
     * 2-3格信号对应：2
     * 4格信号以上对应：3
     */
    public Boolean getSignalLevel(String netName) {
        try {
            if (clientSocket == null) {
                Log.w(TAG, "getSignalLevel: please opensocket first");
                return false;
            }
            byte[] hdrData;
            MsgHeader msgHeader = new MsgHeader();

            if (netName.equals(WIFI_NAME)) {
                hdrData = msgHeader.getNewHeaderMsg(getWiFiSignalLevelMessage.length());
                Log.d(TAG, "getSignalLevel wifi hdrData = " + Arrays.toString(hdrData));
                byte[] msgByte = getWiFiSignalLevelMessage.getBytes();
                byte[] newByte = concatByte(hdrData, msgByte);
                writeSocket(newByte);
                Log.d(TAG, "send msg: " + getWiFiSignalLevelMessage);
            } else if (netName.equals(MOBILE_NAME)) {
                hdrData = msgHeader.getNewHeaderMsg(getMobileSignalLevelMessage.length());
                byte[] msgByte = getMobileSignalLevelMessage.getBytes();
                byte[] newByte = concatByte(hdrData, msgByte);
                writeSocket(newByte);
                Log.d(TAG, "send msg: " + getMobileSignalLevelMessage);
            }
            Log.d(TAG, "send getNetSignalLevelMsg OK");
        } catch (IOException e) {
            Log.d(TAG, "getSignalLevel: " + e.getMessage());
            return false;
        }
        return true;
    }

    /**
     * 将APP所有新建的socket数据流绑定到netInterface指定的网口，当收到回调通知绑定成功后，才
     * 代表生效。当接收到网卡状态变化或LinkTurbo可用性变化时(主要来自UX设置变化，例如用户手动
     * 关闭该业务的LinkTurbo特性)，应用可能需要考虑更新自己的网络使用策略。
     *  注意：
     * 1） 对于 TCP 连接：仅对后续创建的 socket 以及已创建但未 connect 的 socket，绑定到
     * netInterface指定的网口；对于已创建并且 connect的 socket不会生效，会维持在原网口。
     * 2） 对于 UDP 连接：则对当前以及后续的 socket 连接，立即生效，绑定到 netInterface 指定的
     * 网口。
     * 3） NetInterface 必须是通过 getAvailableNetInterface 获取到的名称方可绑定，APP 根据需要选
     * 择绑定的网口。
     * 4） 当 LinkTurbo 不可用时，会通知应用；并且若此时有 Wi-Fi 网络，所有被绑定到蜂窝的
     * socket 数据流，可能会被中断，新流全部绑定到 WLAN 网络（注意：当 LinkTurbo 不可用，
     * 且没有可用 Wi-Fi 网络时，将全部消耗蜂窝流量，APP应当处理此场景）。
     *  Tips: 充分利用本接口可以实现新、旧 socket 同时在多个网口工作，承担包括多个网络质量
     * 探测、多路并发传输等功能。
     * Request 消息体 原型：
     * {"apiId": 3000，"netInterface": "wlan00"} //netInterface必须通过
     * getAvailableNetInterface获取
     * Response 消息体 原型举例：
     * { "apiId": 3000, "retCode": 0}
     * 当绑定成功后，返回绑定的网卡变化：
     * {"ltEventId":3, "boundedNetwork":"wlan00"}; 代表当前绑定的网卡发生变化
     * 注：超时3s后，APP仍未得到绑定成功的消息通知，则默认绑定失败
     */
    public Boolean bindToNetInterface(String netName) {
        try {
            if (clientSocket == null) {
                Log.w(TAG, "bindToNetInterface: please opensocket first");
                return false;
            }
            byte[] hdrData;
            MsgHeader msgHeader = new MsgHeader();

            if (netName.equals(WIFI_NAME)) {
                hdrData = msgHeader.getNewHeaderMsg(bindToWiFiInterfaceMessage.length());
                Log.d(TAG, "bindToNetInterface wifi hdrData = " + Arrays.toString(hdrData));
                byte[] msgByte = bindToWiFiInterfaceMessage.getBytes();
                byte[] newByte = concatByte(hdrData, msgByte);
                writeSocket(newByte);
                Log.d(TAG, "send msg: " + bindToWiFiInterfaceMessage);
            } else if (netName.equals(MOBILE_NAME)) {
                hdrData = msgHeader.getNewHeaderMsg(bindToMobileInterfaceMessage.length());
                byte[] msgByte = bindToMobileInterfaceMessage.getBytes();
                byte[] newByte = concatByte(hdrData, msgByte);
                writeSocket(newByte);
                Log.d(TAG, "send msg: " + bindToMobileInterfaceMessage);
            }
            Log.d(TAG, "send bindToNetIfMsg OK");
        } catch (IOException e) {
            Log.d(TAG, e.getMessage());
            return false;
        }
        return true;
    }

    /*
     * 获取网卡速率
     * */
    public String getNetworkSpeed(int uid) {
        String result = new String();
        result = result + "mobileRxBytes:" + String.valueOf(DTrafficStats.getUidRxBytes(uid)) + System.lineSeparator()
                + "mobileTxBytes:" + String.valueOf(DTrafficStats.getUidTxBytes(uid)) + System.lineSeparator()
                + "wiFiRxBytes:" + String.valueOf(DTrafficStats.getUidRxPackets(uid)) + System.lineSeparator()
                + "wiFiTxBytes:" + String.valueOf(DTrafficStats.getUidTxPackets(uid));
        Log.d(TAG, "getNetworkSpeed: " + result);
        return result;
    }

    private byte[] concatByte(byte[] byte1, byte[] byte2) {
        byte[] byte3 = new byte[byte1.length + byte2.length];
        System.arraycopy(byte1, 0, byte3, 0, byte1.length);
        System.arraycopy(byte2, 0, byte3, byte1.length, byte2.length);
        return byte3;
    }

    private void writeSocket(byte[] byteValue) throws IOException {
        clientSocket.getOutputStream().write(byteValue);
    }
}
