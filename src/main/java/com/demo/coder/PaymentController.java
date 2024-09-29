package com.demo.coder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.*;
import com.stripe.net.Webhook;
import com.stripe.param.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Controller
@RequestMapping("/payment")
public class PaymentController {

    @Value("${stripe.api.key}")
    private String stripeApiKey;

    @Value("${stripe.webhook.secret}")
    private String webhookSecret;

    private String id;

    private final CustomerRepository customerRepository;

    public PaymentController(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    static class PaymentRequest {
        private int amount;
        private String email;

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
    @GetMapping("")
    public String paymentPage() {
        return "payment"; // Chỉ định trang thanh toán
    }

    // Endpoint để tạo PaymentIntent cho thanh toán lần đầu
    @PostMapping("/create-payment-intent")
    public ResponseEntity<Map<String, Object>> createPaymentIntent(@RequestBody PaymentRequest paymentRequest) {
        Stripe.apiKey = stripeApiKey;
        Map<String, Object> response = new HashMap<>();

        try {
            // Tạo hoặc lấy Customer
            com.stripe.model.Customer customer = createOrRetrieveCustomer(paymentRequest.getEmail(), "1");

            // Tạo Payment Intent
            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount((long) paymentRequest.getAmount())
                    .setCurrency("usd")
                    .setCustomer(customer.getId())
                    .addPaymentMethodType("card")
                    .setSetupFutureUsage(PaymentIntentCreateParams.SetupFutureUsage.OFF_SESSION)
                    .build();

            PaymentIntent paymentIntent = PaymentIntent.create(params);

            response.put("clientSecret", paymentIntent.getClientSecret());
            response.put("customerId", customer.getId());
        } catch (StripeException e) {
            response.put("error", e.getMessage());
        }

        return ResponseEntity.ok(response);
    }



    // Endpoint để xử lý webhook từ Stripe
    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(@RequestHeader("Stripe-Signature") String sigHeader, @RequestBody String payload) {
        Stripe.apiKey = stripeApiKey;
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
                // Gán PaymentMethod cho Customer và thiết lập làm phương thức mặc định
//                CustomerUpdateParams customerUpdateParams = CustomerUpdateParams.builder()
//                        .addPaymentMethod(paymentMethodId)
//                        .setInvoiceSettings(CustomerUpdateParams.InvoiceSettings.builder()
//                                .setDefaultPaymentMethod(paymentMethodId)
//                                .build())
//                        .build();
//                com.stripe.model.Customer updatedCustomer = com.stripe.model.Customer.update(customerId, customerUpdateParams);


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
                CustomerEntity customerEntity = customerRepository.findByCustomerId(customerId);
                if (customerEntity != null) {
                    customerEntity.setPaymentMethodId(paymentMethodId);
                    customerRepository.save(customerEntity);
                }
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
                CustomerEntity customerEntity = customerRepository.findByCustomerId(customerId);
                if (customerEntity != null) {
                    customerEntity.setPaymentMethodId(paymentMethodId);
                    customerRepository.save(customerEntity);
                }
            } catch (StripeException e) {
                System.err.println("Lỗi khi gán PaymentMethod cho Customer: " + e.getMessage());
            }
        }
    }

    // Endpoint để hiển thị trang thành công
    @GetMapping("/success")
    public ResponseEntity<String> paymentSuccess() {
        return ResponseEntity.ok("<h1>Thanh Toán Thành Công!</h1><p>Cảm ơn bạn đã thanh toán.</p>");
    }

    // Endpoint để hiển thị trang thất bại
    @GetMapping("/failure")
    public ResponseEntity<String> paymentFailure() {
        return ResponseEntity.ok("<h1>Thanh Toán Thất Bại!</h1><p>Xin lỗi, đã có lỗi xảy ra trong quá trình thanh toán. Vui lòng thử lại.</p>");
    }







    @PostMapping("/create-payment-intent-2")
    public ResponseEntity<Map<String, Object>> createPaymentIntent(@RequestBody Map<String, Object> request) {
        Stripe.apiKey = stripeApiKey;
        String paymentMethodId = (String) request.get("paymentMethodId");
        Long amount = Long.valueOf((String) request.get("amount")); // Số tiền trong cent

        try {

            // Tạo Payment Intent
            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(amount)
                    .setCurrency("usd")
                    .setCustomer(id)
                    .addPaymentMethodType("card")
                    .setSetupFutureUsage(PaymentIntentCreateParams.SetupFutureUsage.OFF_SESSION)
                    .build();

            PaymentIntent paymentIntent = PaymentIntent.create(params);
            Map<String, Object> response = new HashMap<>();
            response.put("clientSecret", paymentIntent.getClientSecret());
            response.put("customerId", id);
            return ResponseEntity.ok(response);
        } catch (StripeException e) {
            return ResponseEntity.ok(Collections.singletonMap("error", e.getMessage()));
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

    // Endpoint để tạo PaymentIntent cho thanh toán lại
    @PostMapping("/create-payment-intent-for-existing-customer")
    public ResponseEntity<Map<String, Object>> createPaymentIntentForExistingCustomer(@RequestBody PaymentRequest paymentRequest) {
        Stripe.apiKey = stripeApiKey;
        Map<String, Object> response = new HashMap<>();

        try {
            // Lấy Customer từ DB
            CustomerEntity existingCustomer = customerRepository.findByEmail(paymentRequest.getEmail());
            if (existingCustomer == null) {
                throw new Exception("Customer not found");
            }

            // Lấy PaymentMethod ID đã lưu
            String savedPaymentMethodId = existingCustomer.getPaymentMethodId();
            if (savedPaymentMethodId == null || savedPaymentMethodId.isEmpty()) {
                throw new Exception("No saved PaymentMethod for this customer");
            }

            // Tạo Payment Intent với customerId và PaymentMethod đã lưu
            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount((long) paymentRequest.getAmount())
                    .setCurrency("usd")
                    .setCustomer(existingCustomer.getCustomerId())
                    .addPaymentMethodType("card")
                    .setPaymentMethod(savedPaymentMethodId)
                    .setConfirmationMethod(PaymentIntentCreateParams.ConfirmationMethod.AUTOMATIC)
                    .setConfirm(true)
                    .build();

            PaymentIntent paymentIntent = PaymentIntent.create(params);

            response.put("clientSecret", paymentIntent.getClientSecret());
            response.put("customerId", existingCustomer.getCustomerId());
        } catch (StripeException e) {
            response.put("error", e.getMessage());
        } catch (Exception e) {
            response.put("error", e.getMessage());
        }

        return ResponseEntity.ok(response);
    }

    @PostMapping("/add-payment-method")
    public Map<String, Object> addPaymentMethod(@RequestBody Map<String, String> request) {
        Stripe.apiKey = stripeApiKey;
        Map<String, Object> response = new HashMap<>();
        try {
            String paymentMethodId = request.get("paymentMethodId");
            String email = request.get("email");

            if (paymentMethodId == null || paymentMethodId.isEmpty()) {
                response.put("error", "Invalid payment method ID.");
                return response;
            }

            Customer customer = createOrRetrieveCustomer(email, paymentMethodId);
            String customerId = customer.getId();

            // Thêm payment method vào customer
            PaymentMethodAttachParams attachParams = PaymentMethodAttachParams.builder()
                    .setCustomer(customerId)
                    .build();

            PaymentMethod paymentMethod = PaymentMethod.retrieve(paymentMethodId);
            paymentMethod.attach(attachParams);

            // Cập nhật default payment method cho customer
            CustomerUpdateParams customerUpdateParams = CustomerUpdateParams.builder()
                    .setInvoiceSettings(
                            CustomerUpdateParams.InvoiceSettings.builder()
                                    .setDefaultPaymentMethod(paymentMethodId)
                                    .build()
                    )
                    .build();

            customer.update(customerUpdateParams);
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
        Stripe.apiKey = stripeApiKey;
        String customerId = id; // Thay bằng customerId thực tế
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
    private com.stripe.model.Customer createOrRetrieveCustomer(String email, String paymentMethodId) throws StripeException {
        CustomerEntity existingCustomer = customerRepository.findByEmail(email);
        if (existingCustomer != null) {
            return com.stripe.model.Customer.retrieve(existingCustomer.getCustomerId());
        } else {
            com.stripe.param.CustomerCreateParams params = com.stripe.param.CustomerCreateParams.builder()
                    .setEmail(email)
                    .build();

            com.stripe.model.Customer customer = com.stripe.model.Customer.create(params);

            // Lưu vào DB
            CustomerEntity customerEntity = new CustomerEntity();
            customerEntity.setCustomerId(customer.getId());
            customerEntity.setPaymentMethodId(paymentMethodId);
            customerEntity.setEmail(email);
            customerRepository.save(customerEntity);

            return customer;
        }
    }
}
