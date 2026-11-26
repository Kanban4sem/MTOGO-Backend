package mtogo.feature.order.domain;

import mtogo.exceptions.ConflictException;
import mtogo.exceptions.NotFoundException;
import mtogo.feature.order.data.OrderFeedbackRepository;
import mtogo.feature.order.data.OrderRepository;
import mtogo.feature.order.presentation.dto.request.CreateOrderFeedbackDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderFeedbackService
{
    private final OrderRepository orders;
    private final OrderFeedbackRepository feedback;

    public OrderFeedbackService(OrderRepository orders, OrderFeedbackRepository feedback)
    {
        this.orders = orders;
        this.feedback = feedback;
    }

    @Transactional
    public void create(Long customerId, Long orderId, CreateOrderFeedbackDTO request)
    {
        // The order lock serializes feedback submissions for the same order.
        Order order = orders.findCustomerOrderForUpdate(orderId, customerId)
                .orElseThrow(() -> new NotFoundException("Order not found with id: " + orderId));
        if (order.getStatus() != OrderStatus.DELIVERED)
        {
            throw new ConflictException("Feedback is only available for delivered orders");
        }
        if (feedback.existsById(orderId))
        {
            throw new ConflictException("Feedback has already been submitted for this order");
        }
        feedback.save(
                new OrderFeedback(order, request.title(), request.description(), request.foodRating().intValueExact(),
                        request.overallRating().intValueExact(), request.deliveryRating().intValueExact()));
    }
}
