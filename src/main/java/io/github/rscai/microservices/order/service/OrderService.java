package io.github.rscai.microservices.order.service;

import io.github.rscai.microservices.order.model.Order;
import io.github.rscai.microservices.order.repository.OrderRepository;
import io.github.rscai.microservices.order.saga.Saga;
import io.github.rscai.microservices.order.saga.SubmitOrderSaga;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

  @Autowired
  private OrderRepository repository;
  @Autowired
  private RabbitTemplate amqpTemplate;
  @Value("${mq.submit-order-saga.routing-key}")
  private String routingKey;

  public boolean submit(final Order order) {
    if (!order.submit()) {
      return false;
    }
    // publish create SubmitOrderSaga event
    SubmitOrderSaga declareEvent = new SubmitOrderSaga();
    declareEvent.setOrderId(order.getId());
    declareEvent.setStep(Saga.DECLARED);
    amqpTemplate.convertAndSend(routingKey, declareEvent);
    return true;
  }
}
