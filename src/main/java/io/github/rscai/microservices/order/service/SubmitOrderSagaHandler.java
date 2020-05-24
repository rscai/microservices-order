package io.github.rscai.microservices.order.service;

import feign.FeignException;
import io.github.rscai.microservices.order.model.InventoryItem;
import io.github.rscai.microservices.order.model.InventoryItemQuantityChange;
import io.github.rscai.microservices.order.model.Order;
import io.github.rscai.microservices.order.model.OrderItem;
import io.github.rscai.microservices.order.repository.OrderRepository;
import io.github.rscai.microservices.order.saga.Saga;
import io.github.rscai.microservices.order.saga.SagaException;
import io.github.rscai.microservices.order.saga.SubmitOrderSaga;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.hateoas.EntityModel;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Component
public class SubmitOrderSagaHandler {

  @Autowired
  private RabbitTemplate rabbitTemplate;
  @Autowired
  private InventoryClient inventoryClient;
  @Autowired
  private OrderRepository orderRepository;
  @Autowired
  private PlatformTransactionManager transactionManager;
  @Value("${mq.submit-order-saga.routing-key}")
  private String routingKey;

  @RabbitListener(queues = "${mq.submit-order-saga.queue.name}")
  public void processSubmitOrderEvent(@Payload SubmitOrderSaga event) throws SagaException {
    log.debug(String.format("Received message %s", event.toString()));
    if (SubmitOrderSaga.CREATED.equals(event.getStep())) {
      SubmitOrderSaga saga = decreaseInventory(event);
      rabbitTemplate.convertAndSend(routingKey, saga);
    } else if (SubmitOrderSaga.DECREASED_INVENTORY.equals(event.getStep())) {
      SubmitOrderSaga saga = submitStatus(event);
      rabbitTemplate.convertAndSend(routingKey, saga);
    } else if (SubmitOrderSaga.DECREASED_INVENTORY_ROLLBACK.equals(event.getStep())) {
      markRollback(event);
      // final state ROLLBACK
    } else if (SubmitOrderSaga.SUBMITTED_STATUS.equals(event.getStep())) {
      markCompleted(event);
      // final state COMPLETED
    } else if (SubmitOrderSaga.SUBMITTED_STATUS_ROLLBACK.equals(event.getStep())) {
      SubmitOrderSaga saga = rollbackDecreaseInventory(event);
      rabbitTemplate.convertAndSend(routingKey, saga);
    } else {
      log.info(String.format("received ended saga %s", event.toString()));
    }
  }

  private SubmitOrderSaga decreaseInventory(SubmitOrderSaga saga) throws SagaException {
    try {
      Optional<Order> orderOptional = orderRepository.findById(saga.getOrderId());
      if (!orderOptional.isPresent()) {
        saga.setStep(Saga.ROLLBACK);
        return saga;
      }
      Order order = orderOptional.get();
      List<String> productIds = order.getItems().stream().map(OrderItem::getProductId)
          .collect(Collectors.toList());
      try {
        Map<String, InventoryItem> itemIndexByProductId = inventoryClient
            .searchByProductIdIn(productIds, PageRequest.of(0, productIds.size())).getContent()
            .stream().collect(Collectors.toMap(inventoryItemEntityModel -> Objects
                    .requireNonNull(inventoryItemEntityModel.getContent()).getProductId(),
                EntityModel::getContent));
        Stream<Optional<InventoryItemQuantityChange>> changes = order.getItems().stream()
            .map(item -> {
              if (!itemIndexByProductId.containsKey(item.getProductId())) {
                return Optional.empty();
              }
              InventoryItem inventoryItem = itemIndexByProductId.get(item.getProductId());
              InventoryItemQuantityChange change = new InventoryItemQuantityChange();
              change.setId(String.format("order-order%s-%s", order.getId(), inventoryItem.getId()));
              change.setInventoryItemId(inventoryItem.getId());
              change.setQuantityChange(-item.getQuantity());
              return Optional.of(change);
            });
        List<InventoryItemQuantityChange> validChanges = changes.filter(Optional::isPresent).map(
            Optional::get).collect(
            Collectors.toList());
        if (order.getItems().size() != validChanges.size()) {
          throw new SagaException("can not find inventory item for some product");
        }

        inventoryClient.changeInventoryItemQuantity(validChanges);
      } catch (FeignException ex) {
        log.error(ex.getMessage(), ex);
        saga.setStep(SubmitOrderSaga.DECREASED_INVENTORY_ROLLBACK);
        return saga;
      }
      saga.setStep(SubmitOrderSaga.DECREASED_INVENTORY);
      return saga;
    } catch (TransactionException | DataAccessException ex) {
      log.error(ex.getMessage(), ex);
      throw new SagaException(ex.getMessage(), ex);
    }
  }

  private SubmitOrderSaga submitStatus(SubmitOrderSaga saga) throws SagaException {
    Optional<Order> orderOptional = orderRepository.findById(saga.getOrderId());
    if (!orderOptional.isPresent()) {
      saga.setStep(SubmitOrderSaga.SUBMITTED_STATUS_ROLLBACK);
      return saga;
    }
    Order order = orderOptional.get();
    if (!order.submit()) {
      saga.setStep(SubmitOrderSaga.SUBMITTED_STATUS_ROLLBACK);
    } else {
      saga.setStep(SubmitOrderSaga.SUBMITTED_STATUS);
    }
    try {
      return buildTransactionTemplate().execute(status -> {
        orderRepository.save(order);
        return saga;
      });
    } catch (TransactionException | DataAccessException ex) {
      log.error(ex.getMessage(), ex);
      throw new SagaException(ex.getMessage(), ex);
    }
  }

  private SubmitOrderSaga rollbackDecreaseInventory(SubmitOrderSaga saga) throws SagaException {
    try {
      Optional<Order> orderOptional = orderRepository.findById(saga.getOrderId());
      if (!orderOptional.isPresent()) {
        saga.setStep(Saga.ROLLBACK);
        return saga;
      }
      Order order = orderOptional.get();
      List<String> productIds = order.getItems().stream().map(OrderItem::getProductId)
          .collect(Collectors.toList());
      Map<String, InventoryItem> itemIndexByProductId = inventoryClient
          .searchByProductIdIn(productIds, PageRequest.of(0, productIds.size())).getContent()
          .stream().collect(Collectors.toMap(inventoryItemEntityModel -> Objects
                  .requireNonNull(inventoryItemEntityModel.getContent()).getProductId(),
              EntityModel::getContent));
      Stream<Optional<InventoryItemQuantityChange>> changes = order.getItems().stream()
          .map(item -> {
            if (!itemIndexByProductId.containsKey(item.getProductId())) {
              return Optional.empty();
            }
            InventoryItem inventoryItem = itemIndexByProductId.get(item.getProductId());
            InventoryItemQuantityChange change = new InventoryItemQuantityChange();
            change.setId(String.format("order-order%s-%s", order.getId(), inventoryItem.getId()));
            change.setInventoryItemId(inventoryItem.getId());
            change.setQuantityChange(item.getQuantity());
            return Optional.of(change);
          });
      List<InventoryItemQuantityChange> validChanges = changes.filter(Optional::isPresent)
          .map(Optional::get).collect(Collectors.toList());
      if (validChanges.size() != order.getItems().size()) {
        throw new SagaException("can not find inventory item for some product");
      }
      try {
        inventoryClient.changeInventoryItemQuantity(validChanges);
      } catch (FeignException ex) {
        log.error(ex.getMessage(), ex);
        return saga;
      }
      saga.setStep(SubmitOrderSaga.DECREASED_INVENTORY_ROLLBACK);
      return saga;
    } catch (TransactionException | DataAccessException ex) {
      log.error(ex.getMessage(), ex);
      throw new SagaException(ex.getMessage(), ex);
    }
  }

  private SubmitOrderSaga markRollback(SubmitOrderSaga saga) throws SagaException {
    try {
      saga.setStep(Saga.ROLLBACK);
      return saga;
    } catch (TransactionException | DataAccessException ex) {
      log.error(ex.getMessage(), ex);
      throw new SagaException(ex.getMessage(), ex);
    }
  }

  private SubmitOrderSaga markCompleted(SubmitOrderSaga saga) throws SagaException {
    try {
      saga.setStep(Saga.COMPLETED);
      return saga;
    } catch (TransactionException | DataAccessException ex) {
      log.error(ex.getMessage(), ex);
      throw new SagaException(ex.getMessage(), ex);
    }
  }

  private TransactionTemplate buildTransactionTemplate() {
    return new TransactionTemplate(transactionManager);
  }
}
