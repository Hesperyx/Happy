package com.tenpay.sdk.net.cgi;

import android.content.Context;

import java.util.Map;

/**
 * QQ 钱包网络请求发送器 stub（新 API）。
 *
 * 实际 QQ 中存在两个版本：
 *  - 新 API：com.tenpay.sdk.net.cgi.NetSender
 *  - 旧 API：com.tenpay.sdk.net.NetSender
 *
 * 抢红包最终请求通过：
 *   NetSender.with(null, url, params, uin)
 *       .comeFrom("2").encrypt(true).tokenID(null).request(listener)
 * 发起，最终打到 https://mqq.tenpay.com/cgi-bin/hongbao/qpay_hb_na_grap.cgi
 *
 * with() 返回类型为独立的顶层接口 com.tenpay.sdk.net.cgi.INetSenderApi。
 */
public final class NetSender {

    private NetSender() {
    }

    public static INetSenderApi with(Context context, String url, Map<String, String> params, String uin) {
        return null;
    }
}
