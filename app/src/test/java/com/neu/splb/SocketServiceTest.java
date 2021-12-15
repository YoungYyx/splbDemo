package com.neu.splb;

import junit.framework.TestCase;

public class SocketServiceTest extends TestCase {

    public void test(){
        SocketService.getInstance().testTwoSocketSender("127.0.0.1",16670);
    }

}