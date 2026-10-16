package com.tenpay.sdk.net.cgi;

import com.tenpay.sdk.paynet.Net;

/**
 * QQ 钱包网络请求发送器接口 stub。
 *
 * 真实 QQ 里这是【独立的顶层接口】com.tenpay.sdk.net.cgi.INetSenderApi，
 * 不是 NetSender 的嵌套类。若误写成嵌套接口会导致运行时 checkcast 失败。
 */
public interface INetSenderApi {
    INetSenderApi comeFrom(String comeFrom);

    INetSenderApi encrypt(boolean encrypt);

    INetSenderApi tokenID(String tokenId);

    void request(Net.NetListener listener);
}
