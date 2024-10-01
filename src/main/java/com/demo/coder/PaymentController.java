package com.demo.coder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.*;
import com.stripe.net.Webhook;
import com.stripe.param.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Controller
@RequestMapping("/payment")
public class PaymentController {

    @Value("${stripe.webhook.secret}")
    private String webhookSecret;

    private String id;

    private final PaymentMethodRepository paymentMethodRepository;

    public PaymentController(PaymentMethodRepository customerRepository) {
        this.paymentMethodRepository = customerRepository;
    }

    static class PaymentRequest {
        private int amount;
        private String email;
        private String paymentMethodId;

        public String getPaymentMethodId() {
            return paymentMethodId;
        }

        public void setPaymentMethodId(String paymentMethodId) {
            this.paymentMethodId = paymentMethodId;
        }

        public PaymentRequest() {}

        public int getAmount() {
            return amount;
        }

        public void setAmount(int amount) {
            this.amount = amount;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }
    }

    // Endpoint để hiển thị trang thanh toán


    // Endpoint để xử lý webhook từ Stripe
    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(@RequestHeader("Stripe-Signature") String sigHeader, @RequestBody String payload) {
        ObjectMapper mapper = new ObjectMapper();
        Event event;

        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            // Không xác thực được webhook
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Error processing webhook");
        }

        // Xử lý sự kiện dựa trên loại sự kiện
        switch (event.getType()) {
            case "payment_intent.succeeded":
                PaymentIntent paymentIntent = (PaymentIntent) event.getData().getObject();
                handlePaymentIntentSucceeded(paymentIntent);
                break;
            case "payment_intent.payment_failed":
                PaymentIntent failedIntent = (PaymentIntent) event.getData().getObject();
                handlePaymentIntentFailed(failedIntent);
                break;
            case "payment_method.attached":
                PaymentMethod paymentMethod = (PaymentMethod) event.getData().getObject();
                handlePaymentMethodAttached(paymentMethod);
                break;
            case "payment_method.detached":
                handlePaymentMethodDetached((com.stripe.model.PaymentMethod) event.getData().getObject());
                break;
            default:
                // Các sự kiện khác có thể được xử lý ở đây
                break;
        }

        return ResponseEntity.ok("Webhook received");
    }

    public void handlePaymentMethodDetached(PaymentMethod paymentMethod) {
        String paymentMethodId = paymentMethod.getId();

        // Xóa thông tin thẻ khỏi cơ sở dữ liệu

        System.out.println("PaymentMethod detached: " + paymentMethodId);
    }

    // Xử lý sự kiện khi PaymentIntent thành công
    private void handlePaymentIntentSucceeded(PaymentIntent paymentIntent) {
        String customerId = paymentIntent.getCustomer();
        String paymentMethodId = paymentIntent.getPaymentMethod();

        if (customerId != null && paymentMethodId != null) {
            try {
                PaymentMethodAttachParams attachParams = PaymentMethodAttachParams.builder()
                        .setCustomer(customerId)
                        .build();

                PaymentMethod paymentMethod = PaymentMethod.retrieve(paymentMethodId);
                paymentMethod.attach(attachParams);

                CustomerUpdateParams customerUpdateParams = CustomerUpdateParams.builder()
                        .setInvoiceSettings(
                                CustomerUpdateParams.InvoiceSettings.builder()
                                        .setDefaultPaymentMethod(paymentMethodId)
                                        .build()
                        )
                        .build();

                Customer customer = Customer.retrieve(customerId);
                customer = customer.update(customerUpdateParams);


                System.out.println("PaymentMethod " + paymentMethodId + " đã được thêm vào Customer " + customerId);

                // Lưu PaymentMethod ID vào cơ sở dữ liệu
//                PaymentMethodEntity customerEntity = paymentMethodRepository.findByCustomerId(customerId);
//                if (customerEntity != null) {
//                    customerEntity.setPaymentMethodId(paymentMethodId);
//                    paymentMethodRepository.save(customerEntity);
//                }
            } catch (StripeException e) {
                System.err.println("Lỗi khi gán PaymentMethod cho Customer: " + e.getMessage());
            }
        }
    }

    // Xử lý sự kiện khi PaymentIntent thất bại
    private void handlePaymentIntentFailed(PaymentIntent paymentIntent) {
        String customerId = paymentIntent.getCustomer();
        System.out.println("PaymentIntent " + paymentIntent.getId() + " thất bại cho Customer " + customerId);
        // Bạn có thể cập nhật trạng thái trong cơ sở dữ liệu hoặc gửi thông báo cho khách hàng
    }

    // Xử lý sự kiện khi một PaymentMethod được gán cho Customer
    private void handlePaymentMethodAttached(PaymentMethod paymentMethod) {
        String customerId = paymentMethod.getCustomer();
        String paymentMethodId = paymentMethod.getId();

        if (customerId != null && paymentMethodId != null) {
            try {


                PaymentMethodAttachParams attachParams = PaymentMethodAttachParams.builder()
                        .setCustomer(customerId)
                        .build();
                paymentMethod.attach(attachParams);

                CustomerUpdateParams customerUpdateParams = CustomerUpdateParams.builder()
                        .setInvoiceSettings(
                                CustomerUpdateParams.InvoiceSettings.builder()
                                        .setDefaultPaymentMethod(paymentMethodId)
                                        .build()
                        )
                        .build();

                Customer customer = Customer.retrieve(customerId);
                customer = customer.update(customerUpdateParams);







                System.out.println("PaymentMethod " + paymentMethodId + " đã được thêm vào Customer " + customerId);

                // Lưu PaymentMethod ID vào cơ sở dữ liệu
//                PaymentMethodEntity customerEntity = paymentMethodRepository.findByCustomerId(customerId);
//                if (customerEntity != null) {
//                    customerEntity.setPaymentMethodId(paymentMethodId);
//                    paymentMethodRepository.save(customerEntity);
//                }
            } catch (StripeException e) {
                System.err.println("Lỗi khi gán PaymentMethod cho Customer: " + e.getMessage());
            }
        }
    }

    @GetMapping("/add")
    public String addCard() {
        return "addCard"; // Chỉ định trang thanh toán
    }
    @GetMapping("/pay")
    public String pay() {
        return "pay"; // Chỉ định trang thanh toán
    }

    @GetMapping("/delete")
    public String delete() {
        return "delete"; // Chỉ định trang thanh toán
    }

    // Endpoint để tạo PaymentIntent cho thanh toán lại
    @PostMapping("/create-payment-intent-for-existing-customer")
    public ResponseEntity<Map<String, Object>> createPaymentIntentForExistingCustomer(@RequestBody PaymentRequest paymentRequest) {
        Map<String, Object> response = new HashMap<>();
        String userId = "9876543210";
        try {
            // Lấy Customer từ DB
            List<PaymentMethodEntity> existingCustomer = paymentMethodRepository.findAllByUserId(userId);
            if (existingCustomer.isEmpty()) {
                throw new Exception("Customer not found");
            }

            // Lấy PaymentMethod ID đã lưu
//            String savedPaymentMethodId = existingCustomer.getPaymentMethodId();
//            if (savedPaymentMethodId == null || savedPaymentMethodId.isEmpty()) {
//                throw new Exception("No saved PaymentMethod for this customer");
//            }

            // Tạo Payment Intent với customerId và PaymentMethod đã lưu
            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount((long) paymentRequest.getAmount())
                    .setCurrency("usd")
                    .setCustomer(existingCustomer.get(0).getCustomerId())
                    .addPaymentMethodType("card")
                    .setPaymentMethod(paymentRequest.paymentMethodId)
                    .setConfirmationMethod(PaymentIntentCreateParams.ConfirmationMethod.AUTOMATIC)
                    .setConfirm(true)
                    .build();

            PaymentIntent paymentIntent = PaymentIntent.create(params);

            response.put("clientSecret", paymentIntent.getClientSecret());
            response.put("customerId", existingCustomer.get(0).getCustomerId());
        } catch (StripeException e) {
            response.put("error", e.getMessage());
        } catch (Exception e) {
            response.put("error", e.getMessage());
        }

        return ResponseEntity.ok(response);
    }

    @PostMapping("/add-payment-method")
    public Map<String, Object> addPaymentMethod(@RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();
        try {
            String paymentMethodId = request.get("paymentMethodId");
//            String email = request.get("email");
            String userId = "9876543210";
            String email = "helloworld@gmail.com";

            if (paymentMethodId == null || paymentMethodId.isEmpty()) {
                response.put("error", "Invalid payment method ID.");
                return response;
            }

            Customer customer = createOrRetrieveCustomer(userId, email);
            String customerId = customer.getId();

            // Thêm payment method vào customer
            PaymentMethodAttachParams attachParams = PaymentMethodAttachParams.builder()
                    .setCustomer(customerId)
                    .build();

            PaymentMethod paymentMethod = PaymentMethod.retrieve(paymentMethodId);
            paymentMethod.attach(attachParams);

            // Cập nhật default payment method cho customer
//            CustomerUpdateParams customerUpdateParams = CustomerUpdateParams.builder()
//                    .setInvoiceSettings(
//                            CustomerUpdateParams.InvoiceSettings.builder()
//                                    .setDefaultPaymentMethod(paymentMethodId)
//                                    .build()
//                    )
//                    .build();
//
//            customer.update(customerUpdateParams);
            id = customerId;
            response.put("success", true);
            response.put("customerId", customerId); // Trả về customerId nếu cần
        } catch (StripeException e) {
            response.put("error", e.getMessage());
        } catch (Exception e) {
            response.put("error", "An unexpected error occurred: " + e.getMessage());
        }
        return response;
    }

    @GetMapping("/get-payment-methods")
    public ResponseEntity<List<PaymentMethod>> getPaymentMethods() {
        String userId = "9876543210";
        List<PaymentMethodEntity> paymentMethodEntities = paymentMethodRepository.findAllByUserId(userId);
        String customerId = paymentMethodEntities.get(0).getCustomerId();
        try {
            PaymentMethodListParams params = PaymentMethodListParams.builder()
                    .setCustomer(customerId)
                    .build();

            PaymentMethodCollection paymentMethods = PaymentMethod.list(params);
            return ResponseEntity.ok(paymentMethods.getData());
        } catch (StripeException e) {
            return ResponseEntity.ok(Collections.emptyList());
        }
    }

    // Phương thức tạo hoặc lấy Customer
    private com.stripe.model.Customer createOrRetrieveCustomer(String userId, String email) throws StripeException {
        List<PaymentMethodEntity> paymentMethodEntities = paymentMethodRepository.findAllByUserId(userId);
        if (!paymentMethodEntities.isEmpty()) {
            return com.stripe.model.Customer.retrieve(paymentMethodEntities.get(0).getCustomerId());
        } else {
            com.stripe.param.CustomerCreateParams params = com.stripe.param.CustomerCreateParams.builder()
                    .setEmail(email)
                    .build();

            com.stripe.model.Customer customer = com.stripe.model.Customer.create(params);

            // Lưu vào DB
            PaymentMethodEntity paymentMethod = new PaymentMethodEntity();
            paymentMethod.setCustomerId(customer.getId());
            paymentMethod.setUserId(userId);
            paymentMethodRepository.save(paymentMethod);

            return customer;
        }
    }

    @PostMapping("/delete-payment-methods")
    public void removePaymentMethod(@RequestBody PaymentRequest paymentRequest) throws StripeException {
        String paymentMethodId = paymentRequest.getPaymentMethodId();
        // Tìm PaymentMethodEntity từ DB
        String userId = "9876543210";
        List<PaymentMethodEntity> paymentMethodEntity = paymentMethodRepository.findAllByUserId(userId);

        // Kiểm tra xem PaymentMethod thuộc về người dùng hiện tại không



        PaymentMethodListParams params = PaymentMethodListParams.builder()
                .setCustomer(paymentMethodEntity.get(0).getCustomerId())
                .build();

        PaymentMethodCollection paymentMethods = PaymentMethod.list(params);
        List<String> paymentMethodIds = paymentMethods.getData().stream().map(PaymentMethod::getId).toList();
        if (!paymentMethodIds.contains(paymentMethodId)) {
            throw new RuntimeException("Unauthorized to remove this payment method");
        }

        // Gỡ kết nối PaymentMethod khỏi khách hàng trong Stripe
        PaymentMethod paymentMethod = PaymentMethod.retrieve(paymentMethodId);
        PaymentMethodDetachParams paymentMethodDetachParams = PaymentMethodDetachParams.builder().build();
        paymentMethod.detach(paymentMethodDetachParams);

    }
}
