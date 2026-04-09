package com.eventregistration.payment;

import com.eventregistration.util.ConfigLoader;
import com.razorpay.RazorpayClient;
import com.razorpay.Order;
import org.json.JSONObject;

public class RazorpayHandler {

    private final String keyId;
    private final String keySecret;

    public RazorpayHandler() throws Exception {
        ConfigLoader loader = new ConfigLoader();
        JSONObject config = loader.getConfig();
        keyId = config.getString("razorpay_key_id");
        keySecret = config.getString("razorpay_key_secret");
    }

    public Order createOrder(int amountInPaise, String receiptId) throws Exception {
        RazorpayClient client = new RazorpayClient(keyId, keySecret);

        JSONObject orderRequest = new JSONObject();
        orderRequest.put("amount", amountInPaise); // amount in paise
        orderRequest.put("currency", "INR");
        orderRequest.put("receipt", receiptId);
        orderRequest.put("payment_capture", 1);

        return client.orders.create(orderRequest);
    }
}