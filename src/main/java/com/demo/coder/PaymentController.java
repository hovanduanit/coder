package com.demo.coder;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.PaymentMethod;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.CustomerUpdateParams;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.PaymentMethodCreateParams;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Controller
public class PaymentController {

    @Value("${stripe.api.key}")
    private String stripeApiKey;

    static class PaymentRequest {
        public int amount;
        public String email;
        public String getEmail() {
            return email;
        }
        // constructor, getters, and setters
        public PaymentRequest() {}
        public int getAmount() {
            return amount;
        }
        public void setAmount(int amount) {
            this.amount = amount;
        }
    }

    @PostMapping("/create-payment-intent")
    public ResponseEntity<Map<String, Object>> createPaymentIntent(@RequestBody PaymentRequest paymentRequest) {
        Stripe.apiKey = stripeApiKey;
        Map<String, Object> response = new HashMap<>();

        try {
            Map<String, Object> params = new HashMap<>();
            params.put("amount", paymentRequest.getAmount());
            params.put("currency", "usd");
            PaymentIntent paymentIntent = PaymentIntent.create(params);
            response.put("clientSecret", paymentIntent.getClientSecret());



            CustomerCreateParams paramsCustomer = CustomerCreateParams.builder()
                    .setEmail("customer@example.com")
                    .build();

            Customer customer = Customer.create(paramsCustomer);

            PaymentMethodCreateParams paymentMethodParams = PaymentMethodCreateParams.builder()
                    .setType(PaymentMethodCreateParams.Type.CARD)
                    .setCard(PaymentMethodCreateParams.CardDetails.builder()
                            .setNumber("4242424242424242") // Số thẻ thử nghiệm
                            .setExpMonth(12L) // Tháng hết hạn
                            .setExpYear(2024L) // Năm hết hạn
                            .setCvc("123") // Mã CVC
                            .build())
                    .build();

            PaymentMethod paymentMethod = PaymentMethod.create(paymentMethodParams);

            customer.update(CustomerUpdateParams.builder()
                            .
                    .build());





//            PaymentIntentCreateParams paymentIntentParams = PaymentIntentCreateParams.builder()
//                    .setAmount(1000) // Số tiền thanh toán (ví dụ: 10 USD)
//                    .setCurrency("usd") // Đơn vị tiền tệ
//                    .setCustomer(customer.getId()) // ID của Customer đã tạo
//                    .setPaymentMethod(paymentMethod.getId()) // ID của Payment Method đã lưu
//                    .setConfirmationMethod(PaymentIntentCreateParams.ConfirmationMethod.AUTO) // Tự động xác nhận
//                    .setConfirm(true) // Xác nhận thanh toán ngay lập tức
//                    .build();
//
//            PaymentIntent paymentIntent = PaymentIntent.create(paymentIntentParams);






        } catch (Exception e) {
            response.put("error", e.getMessage());
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping("/payment")
    public String paymentPage() {
        return "payment"; // Chỉ định trang thanh toán
    }




}
