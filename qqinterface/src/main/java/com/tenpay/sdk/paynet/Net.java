package com.tenpay.sdk.paynet;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * QQ 钱包 paynet 网络回调监听器 stub。
 *
 * 实际签名：
 *  - onSuccess(String, JSONObject)
 *  - onError(String, JSONObject)
 *  - onBlError(String, JSONObject)
 */
public final class Net {

    private Net() {
    }

    public interface NetListener {
        void onSuccess(String url, JSONObject response);

        void onError(String url, JSONObject response) throws JSONException;

        void onBlError(String url, JSONObject response) throws JSONException;
    }
}
