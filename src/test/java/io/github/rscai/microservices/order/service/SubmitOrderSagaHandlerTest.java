package io.github.rscai.microservices.order.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import feign.FeignException.Conflict;
import feign.Request;
import feign.Request.HttpMethod;
import io.github.rscai.microservices.order.model.InventoryItem;
import io.github.rscai.microservices.order.model.Order;
import io.github.rscai.microservices.order.model.Order.State;
import io.github.rscai.microservices.order.model.OrderItem;
import io.github.rscai.microservices.order.repository.OrderRepository;
import io.github.rscai.microservices.order.saga.Saga;
import io.github.rscai.microservices.order.saga.SubmitOrderSaga;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import org.apache.commons.lang.StringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
public class SubmitOrderSagaHandlerTest {

  private static final String PRODUCT_ID_A = "productA";
  private static final String PRODUCT_ID_B = "productB";
  private static final String CUSTOMER_ID_A = "customerA";
  @MockBean
  private RabbitTemplate mockAmqpTemplate;
  @MockBean
  private InventoryClient mockInventoryClient;
  @Autowired
  private OrderRepository orderRepository;
  @Autowired
  private PagedResourcesAssembler<InventoryItem> pagedResourcesAssembler;
  @Autowired
  private SubmitOrderSagaHandler testObject;

  private String orderIdA;

  @BeforeEach
  public void setUp() {
    Order order = new Order();
    order.setCustomerId(CUSTOMER_ID_A);
    order.setItems(Arrays.asList(new OrderItem(PRODUCT_ID_A, 2, BigDecimal.TEN),
        new OrderItem(PRODUCT_ID_B, 3, BigDecimal.TEN)));
    order.setAmount(BigDecimal.valueOf(50));
    order.setState(State.OPEN);
    orderIdA = orderRepository.save(order).getId();
  }

  @AfterEach
  public void tearDown() {
    orderRepository.deleteAll();
  }

  @Test
  public void testSubmitOrderSuccess() throws Exception {

    // mock for decreasing inventory item quantity
    InventoryItem itemA = new InventoryItem();
    itemA.setId("itemA");
    itemA.setProductId(PRODUCT_ID_A);
    itemA.setQuantity(100);
    itemA.setUnitPrice(BigDecimal.TEN);
    InventoryItem itemB = new InventoryItem();
    itemB.setId("itemA");
    itemB.setProductId(PRODUCT_ID_B);
    itemB.setQuantity(200);
    itemB.setUnitPrice(BigDecimal.TEN);
    PageImpl<InventoryItem> page = new PageImpl<>(Arrays.asList(itemA, itemB),
        PageRequest.of(0, 10), 2);
    PagedModel<EntityModel<InventoryItem>> inventoryItems = pagedResourcesAssembler.toModel(page);
    when(mockInventoryClient.searchByProductIdIn(anyList(), any(Pageable.class)))
        .thenReturn(inventoryItems);
    when(mockInventoryClient.changeInventoryItemQuantity(anyList()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    ArgumentCaptor<Object> sagaCaptor = ArgumentCaptor.forClass(Object.class);

    SubmitOrderSaga saga = new SubmitOrderSaga();
    saga.setOrderId(orderIdA);
    saga.setStep(Saga.CREATED);

    testObject.processSubmitOrderEvent(saga);

    // verify mq
    verify(mockAmqpTemplate).convertAndSend(anyString(), sagaCaptor.capture());
    assertThat(sagaCaptor.getValue(), instanceOf(SubmitOrderSaga.class));
    SubmitOrderSaga decreasedInventorySaga = (SubmitOrderSaga) sagaCaptor.getValue();
    assertThat(decreasedInventorySaga.getStep(), is(SubmitOrderSaga.DECREASED_INVENTORY));
    assertThat(decreasedInventorySaga.getOrderId(), is(orderIdA));
    // verify DB
    assertThat(orderRepository.findById(orderIdA)
            .<AssertionError>orElseThrow(() -> fail("can not find order")).getState(),
        is(State.OPEN));

    // submit status
    clearInvocations(mockAmqpTemplate);

    testObject.processSubmitOrderEvent(decreasedInventorySaga);

    // verify mq
    verify(mockAmqpTemplate).convertAndSend(anyString(), sagaCaptor.capture());
    assertThat(sagaCaptor.getValue(), instanceOf(SubmitOrderSaga.class));
    SubmitOrderSaga submittedStatusSaga = (SubmitOrderSaga) sagaCaptor.getValue();
    assertThat(submittedStatusSaga.getStep(), is(SubmitOrderSaga.SUBMITTED_STATUS));
    assertThat(submittedStatusSaga.getOrderId(), is(orderIdA));
    // verify DB
    assertThat(
        orderRepository.findById(orderIdA).<AssertionError>orElseThrow(
            () -> fail("can not find order")).getState(),
        is(State.SUBMITTED));

    // complete saga
    clearInvocations(mockAmqpTemplate);

    testObject.processSubmitOrderEvent(submittedStatusSaga);

    // verify mq
    verify(mockAmqpTemplate, times(0)).convertAndSend(anyString(), sagaCaptor.capture());

    // verify DB
    assertThat(
        orderRepository.findById(orderIdA).<AssertionError>orElseThrow(
            () -> fail("can not find order")).getState(),
        is(State.SUBMITTED));

  }

  @Test
  public void testSubmitOrderFailOnDecreaseInventory() throws Exception {
    // mock for decreasing inventory fail
    InventoryItem itemA = new InventoryItem();
    itemA.setId("itemA");
    itemA.setProductId(PRODUCT_ID_A);
    itemA.setQuantity(100);
    itemA.setUnitPrice(BigDecimal.TEN);
    InventoryItem itemB = new InventoryItem();
    itemB.setId("itemA");
    itemB.setProductId(PRODUCT_ID_B);
    itemB.setQuantity(200);
    itemB.setUnitPrice(BigDecimal.TEN);
    PageImpl<InventoryItem> page = new PageImpl<>(Arrays.asList(itemA, itemB),
        PageRequest.of(0, 10), 2);
    PagedModel<EntityModel<InventoryItem>> inventoryItems = pagedResourcesAssembler.toModel(page);
    when(mockInventoryClient.searchByProductIdIn(anyList(), any(Pageable.class)))
        .thenReturn(inventoryItems);
    when(mockInventoryClient.changeInventoryItemQuantity(anyList()))
        .thenThrow(new Conflict("decrease inventory failed", Request.create(HttpMethod.POST,
            StringUtils.EMPTY, Collections.emptyMap(), new byte[]{}, StandardCharsets.UTF_8),
            new byte[]{}));

    ArgumentCaptor<Object> sagaCaptor = ArgumentCaptor.forClass(Object.class);

    SubmitOrderSaga saga = new SubmitOrderSaga();
    saga.setOrderId(orderIdA);
    saga.setStep(Saga.CREATED);

    testObject.processSubmitOrderEvent(saga);

    // verify mq
    verify(mockAmqpTemplate).convertAndSend(anyString(), sagaCaptor.capture());
    assertThat(sagaCaptor.getValue(), instanceOf(SubmitOrderSaga.class));
    SubmitOrderSaga decreaseInventoryRollbackSaga = (SubmitOrderSaga) sagaCaptor.getValue();
    assertThat(decreaseInventoryRollbackSaga.getStep(),
        is(SubmitOrderSaga.DECREASED_INVENTORY_ROLLBACK));
    assertThat(decreaseInventoryRollbackSaga.getOrderId(), is(orderIdA));
    // verify DB
    assertThat(orderRepository.findById(orderIdA)
            .orElseThrow(() -> new AssertionError("required order, but missed")).getState(),
        is(State.OPEN));

    // mark rollback
    clearInvocations(mockAmqpTemplate);

    testObject.processSubmitOrderEvent(decreaseInventoryRollbackSaga);

    // verify mq
    verify(mockAmqpTemplate, times(0)).convertAndSend(anyString(), sagaCaptor.capture());

    // verify DB
    assertThat(orderRepository.findById(orderIdA)
            .orElseThrow(() -> new AssertionError("required order, but missed")).getState(),
        is(State.OPEN));

  }

  @Test
  public void testSubmitOrderFailOnSubmitStatus() throws Exception {

    // mock for decreasing inventory item quantity
    clearInvocations(mockAmqpTemplate);
    InventoryItem itemA = new InventoryItem();
    itemA.setId("itemA");
    itemA.setProductId(PRODUCT_ID_A);
    itemA.setQuantity(100);
    itemA.setUnitPrice(BigDecimal.TEN);
    InventoryItem itemB = new InventoryItem();
    itemB.setId("itemA");
    itemB.setProductId(PRODUCT_ID_B);
    itemB.setQuantity(200);
    itemB.setUnitPrice(BigDecimal.TEN);
    PageImpl<InventoryItem> page = new PageImpl<>(Arrays.asList(itemA, itemB),
        PageRequest.of(0, 10), 2);
    PagedModel<EntityModel<InventoryItem>> inventoryItems = pagedResourcesAssembler.toModel(page);
    when(mockInventoryClient.searchByProductIdIn(anyList(), any(Pageable.class)))
        .thenReturn(inventoryItems);
    when(mockInventoryClient.changeInventoryItemQuantity(anyList()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    ArgumentCaptor<Object> sagaCaptor = ArgumentCaptor.forClass(Object.class);

    SubmitOrderSaga saga = new SubmitOrderSaga();
    saga.setOrderId(orderIdA);
    saga.setStep(Saga.CREATED);

    testObject.processSubmitOrderEvent(saga);

    // verify mq
    verify(mockAmqpTemplate).convertAndSend(anyString(), sagaCaptor.capture());
    assertThat(sagaCaptor.getValue(), instanceOf(SubmitOrderSaga.class));
    SubmitOrderSaga decreasedInventorySaga = (SubmitOrderSaga) sagaCaptor.getValue();
    assertThat(decreasedInventorySaga.getStep(), is(SubmitOrderSaga.DECREASED_INVENTORY));
    assertThat(decreasedInventorySaga.getOrderId(), is(orderIdA));
    // verify DB
    assertThat(orderRepository.findById(orderIdA)
            .<AssertionError>orElseThrow(() -> fail("can not find order")).getState(),
        is(State.OPEN));

    // submit status
    clearInvocations(mockAmqpTemplate);
    // set order not able to be submitted
    Order existedOrder = orderRepository
        .findById(decreasedInventorySaga.getOrderId()).<AssertionError>orElseThrow(
            () -> fail("can not find order"));
    existedOrder.setState(State.SUBMITTED);
    orderRepository.save(existedOrder);
    testObject.processSubmitOrderEvent(decreasedInventorySaga);

    // verify mq
    verify(mockAmqpTemplate).convertAndSend(anyString(), sagaCaptor.capture());
    assertThat(sagaCaptor.getValue(), instanceOf(SubmitOrderSaga.class));
    SubmitOrderSaga submittedStatusRollbackSaga = (SubmitOrderSaga) sagaCaptor.getValue();
    assertThat(submittedStatusRollbackSaga.getStep(),
        is(SubmitOrderSaga.SUBMITTED_STATUS_ROLLBACK));
    assertThat(submittedStatusRollbackSaga.getOrderId(), is(orderIdA));
    // revert order to OPEN
    existedOrder.setState(State.OPEN);
    orderRepository.save(existedOrder);

    // rollback decrease inventory
    clearInvocations(mockAmqpTemplate);
    clearInvocations(mockInventoryClient);

    testObject.processSubmitOrderEvent(submittedStatusRollbackSaga);

    // verify inventory invocation
    verify(mockInventoryClient).changeInventoryItemQuantity(any());
    // verify mq
    verify(mockAmqpTemplate).convertAndSend(anyString(), sagaCaptor.capture());
    assertThat(sagaCaptor.getValue(), instanceOf(SubmitOrderSaga.class));
    SubmitOrderSaga decreaseInventoryRollbackSaga = (SubmitOrderSaga) sagaCaptor.getValue();
    assertThat(decreaseInventoryRollbackSaga.getStep(),
        is(SubmitOrderSaga.DECREASED_INVENTORY_ROLLBACK));
    assertThat(decreaseInventoryRollbackSaga.getOrderId(), is(orderIdA));
    // verify DB
    assertThat(orderRepository.findById(orderIdA).<AssertionError>orElseThrow(
        () -> fail("can not find order")).getState(), is(State.OPEN));

    // mark rollback
    clearInvocations(mockAmqpTemplate);

    testObject.processSubmitOrderEvent(decreaseInventoryRollbackSaga);

    // verify mq

    // verify DB
    assertThat(orderRepository.findById(orderIdA).<AssertionError>orElseThrow(
        () -> fail("can not find order")).getState(), is(State.OPEN));

  }
}
