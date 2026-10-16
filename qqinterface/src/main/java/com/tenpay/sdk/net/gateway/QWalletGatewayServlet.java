package com.tenpay.sdk.net.gateway;

/**
 * QQ 钱包网关 Servlet stub。
 *
 * 实际 QQ 中存在两个版本：
 *  - 新 API：com.tenpay.sdk.net.gateway.QWalletGatewayServlet，
 *    sendRequest 有 5 参数与 6 参数两个重载，抢红包使用 6 参数版本：
 *    sendRequest(String service, String method, Req req, Rsp targetRsp, j config, n<Rsp> bizCallback)
 *  - 旧 API：com.tenpay.sdk.net.QWalletGatewayServlet
 *
 * 由于 config 与 callback 类型被 R8 混淆、无法在编译期确定，
 * 这里仅提供 INSTANCE 静态字段占位，实际调用走运行时反射（按 name + paramCount 查找）。
 */
public final class QWalletGatewayServlet {

    public static final QWalletGatewayServlet INSTANCE = new QWalletGatewayServlet();

    private QWalletGatewayServlet() {
    }
}
