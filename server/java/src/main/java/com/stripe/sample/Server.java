package com.stripe.sample;

import java.util.HashMap;
import java.util.Map;

import static spark.Spark.get;
import static spark.Spark.post;
import static spark.Spark.port;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import com.stripe.Stripe;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.exception.*;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCreateParams;

import io.github.cdimascio.dotenv.Dotenv;

public class Server {
    private static Gson gson = new Gson();

    static class CreatePaymentBody {
        @SerializedName("amount")
        Long amount;

        @SerializedName("currency")
        String currency;

        @SerializedName("payment_method_types")
        String payment_method_types;

        public String getCurrency() {
            return currency;
        }

        public Long getAmount() {
            return amount;
        }

        public String getPaymentMethod() {
            return payment_method_types;
        }
    }

    public static void main(String[] args) {
        port(4242);
        String ENV_PATH = "../../../";
        Dotenv dotenv = Dotenv.configure().directory(ENV_PATH).load();
        Stripe.apiKey = dotenv.get("STRIPE_SECRET_KEY");

        get("/", (request, response) -> {
            response.type("application/json");
            return "Hello from API";
        });

        get("/public-key", (request, response) -> {
            response.type("application/json");

            Map<String, Object> responseData = new HashMap<>();
            responseData.put("publicKey", dotenv.get("STRIPE_PUBLIC_KEY"));
            return gson.toJson(responseData);
        });

        post("/create-payment-intent", (request, response) -> {
            response.type("application/json");

            CreatePaymentBody postBody = gson.fromJson(request.body(), CreatePaymentBody.class);
            PaymentIntentCreateParams createParams = new PaymentIntentCreateParams.Builder()
                    .setCurrency(postBody.getCurrency()).setAmount(postBody.getAmount())
                    .setPaymentMethod(postBody.getPaymentMethod()).build();

            PaymentIntent intent = PaymentIntent.create(createParams);

            return gson.toJson(intent);
        });

        post("/webhook", (request, response) -> {
            System.out.println("Webhook");
            String payload = request.body();
            String sigHeader = request.headers("Stripe-Signature");
            String endpointSecret = System.getenv("STRIPE_WEBHOOK_SECRET");

            Event event = null;

            try {
                event = Webhook.constructEvent(payload, sigHeader, endpointSecret);
            } catch (SignatureVerificationException e) {
                // Invalid signature
                response.status(400);
                return "";
            }

            switch (event.getType()) {
            case "payment_intent.succeeded":
                // Fulfill any orders, e-mail receipts, etc
                System.out.println("💰 Payment received!");
                break;
            case "payment_intent.payment_failed":
                // Notify the customer that their order was not fulfilled
                System.out.println("❌ Payment failed.");
                break;
            default:
                // Unexpected event type
                response.status(400);
                return "";
            }

            response.status(200);
            return "";
        });
    }
}