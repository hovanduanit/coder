document.addEventListener('DOMContentLoaded', () => {
    const stripe = Stripe('pk_test_51Q3Ggq2MBG1ftSLQa7LXnT1k8R7evJChz6qGfjxCZ8wKXqfYOvXI2qzoHnSjeGMT6dh4xhtnYy5sstvNBJLyKUMY00iROG7Hc9'); // Thay bằng public key của bạn
    const elements = stripe.elements();
    const cardElement = elements.create('card');
    cardElement.mount('#card-element');

    const form = document.getElementById('payment-form');
    form.addEventListener('submit', async (event) => {
        event.preventDefault();

        try {
            const response = await fetch('/create-payment-intent', { // Đường dẫn API
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({ amount: 1000 }) // Số tiền thanh toán (ví dụ: 10 USD)
            });

            if (!response.ok) {
                const errorData = await response.json();
                console.error("Lỗi khi tạo Payment Intent:", errorData.error);
                alert(errorData.error || "Có lỗi xảy ra!");
                return; // Dừng lại nếu có lỗi
            }

            const data = await response.json();
            console.log(data);

            if (data.error) {
                console.error("Lỗi từ server:", data.error);
                alert(data.error);
                return;
            }

            const clientSecret = data.clientSecret;

            const result = await stripe.confirmCardPayment(clientSecret, {
                payment_method: {
                    card: cardElement,
                    billing_details: {
                        email: 'customer@example.com', // Bạn có thể thay đổi email
                    },
                },
            });

            if (result.error) {
                console.error('Error:', error);
            } else {

            }
        } catch (error) {
            console.error('Error:', error);
        }
    });
});
