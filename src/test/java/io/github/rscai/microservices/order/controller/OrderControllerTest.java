package io.github.rscai.microservices.order.controller;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.hypermedia.HypermediaDocumentation.halLinks;
import static org.springframework.restdocs.hypermedia.HypermediaDocumentation.linkWithRel;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.put;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.subsectionWithPath;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.restdocs.request.RequestDocumentation.requestParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.rscai.microservices.order.model.InventoryItem;
import io.github.rscai.microservices.order.model.Order;
import io.github.rscai.microservices.order.model.Order.State;
import io.github.rscai.microservices.order.model.OrderItem;
import io.github.rscai.microservices.order.repository.OrderRepository;
import io.github.rscai.microservices.order.saga.Saga;
import io.github.rscai.microservices.order.saga.SubmitOrderSaga;
import io.github.rscai.microservices.order.service.InventoryClient;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.http.MediaType;
import org.springframework.restdocs.hypermedia.HypermediaDocumentation;
import org.springframework.restdocs.hypermedia.LinkDescriptor;
import org.springframework.restdocs.hypermedia.LinksSnippet;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.restdocs.payload.PayloadDocumentation;
import org.springframework.restdocs.payload.RequestFieldsSnippet;
import org.springframework.restdocs.payload.ResponseFieldsSnippet;
import org.springframework.restdocs.request.ParameterDescriptor;
import org.springframework.restdocs.request.RequestParametersSnippet;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestDocs
public class OrderControllerTest {

  private static final String CUSTOMER_ID_A = "customerA";
  private static final String PRODUCT_ID_A = "productA";
  private static final String PRODUCT_ID_B = "productB";
  private static final String APPLICATION_HAL = "application/hal+json";
  private static final String SCOPE_ORDER_USE = "SCOPE_order.use";
  private static final String SCOPE_ORDER_OPERATE = "SCOPE_order.operate";
  @Autowired
  private MockMvc mvc;
  @Autowired
  private ObjectMapper objectMapper;
  @Autowired
  private OrderRepository orderRepository;
  @MockBean
  private InventoryClient mockInventoryClient;
  @MockBean
  private RabbitTemplate mockAmqpTemplate;
  @Autowired
  private PagedResourcesAssembler<InventoryItem> pagedResourcesAssembler;

  private String openOrderId;
  private String submittedOrderId;
  private String paidOrderId;
  private String onDeliveryOrderId;
  private String deliveredOrderId;
  private String closedOrderId;
  private String cancelledOrderId;

  @BeforeEach
  private void setUp() {
    Order openOrder = new Order();
    openOrder.setCustomerId(CUSTOMER_ID_A);
    openOrder
        .setItems(Arrays.asList(new OrderItem(PRODUCT_ID_A, 2, BigDecimal.ONE),
            new OrderItem(PRODUCT_ID_B, 3, BigDecimal.ONE)));
    openOrder.setAmount(BigDecimal.valueOf(123.45));
    openOrder.setState(State.OPEN);

    openOrderId = orderRepository.save(openOrder).getId();

    Order submittedOrder = new Order();
    submittedOrder.setCustomerId(CUSTOMER_ID_A);
    submittedOrder
        .setItems(Arrays.asList(new OrderItem(PRODUCT_ID_A, 2, BigDecimal.ONE),
            new OrderItem(PRODUCT_ID_B, 3, BigDecimal.ONE)));
    submittedOrder.setAmount(BigDecimal.valueOf(456.78));
    submittedOrder.setState(State.SUBMITTED);
    submittedOrderId = orderRepository.save(submittedOrder).getId();

    Order paidOrder = new Order();
    paidOrder.setCustomerId(CUSTOMER_ID_A);
    paidOrder
        .setItems(Arrays.asList(new OrderItem(PRODUCT_ID_A, 2, BigDecimal.ONE),
            new OrderItem(PRODUCT_ID_B, 3, BigDecimal.ONE)));
    paidOrder.setAmount(BigDecimal.valueOf(789.01));
    paidOrder.setState(State.PAID);
    paidOrderId = orderRepository.save(paidOrder).getId();

    Order onDeliveryOrder = new Order();
    onDeliveryOrder.setCustomerId(CUSTOMER_ID_A);
    onDeliveryOrder
        .setItems(Arrays.asList(new OrderItem(PRODUCT_ID_A, 2, BigDecimal.ONE),
            new OrderItem(PRODUCT_ID_B, 2, BigDecimal.ONE)));
    onDeliveryOrder.setAmount(BigDecimal.valueOf(789.01));
    onDeliveryOrder.setState(State.ON_DELIVERY);
    onDeliveryOrderId = orderRepository.save(onDeliveryOrder).getId();

    Order deliveredOrder = new Order();
    deliveredOrder.setCustomerId(CUSTOMER_ID_A);
    deliveredOrder
        .setItems(Arrays.asList(new OrderItem(PRODUCT_ID_A, 2, BigDecimal.ONE),
            new OrderItem(PRODUCT_ID_B, 3, BigDecimal.ONE)));
    deliveredOrder.setAmount(BigDecimal.valueOf(123.45));
    deliveredOrder.setState(State.DELIVERED);
    deliveredOrderId = orderRepository.save(deliveredOrder).getId();

    Order closedOrder = new Order();
    closedOrder.setCustomerId(CUSTOMER_ID_A);
    closedOrder
        .setItems(Arrays.asList(new OrderItem(PRODUCT_ID_A, 2, BigDecimal.ONE),
            new OrderItem(PRODUCT_ID_B, 3, BigDecimal.ONE)));
    closedOrder.setAmount(BigDecimal.valueOf(456.78));
    closedOrder.setState(State.CLOSED);
    closedOrderId = orderRepository.save(closedOrder).getId();

    Order cancelledOrder = new Order();
    cancelledOrder.setCustomerId(CUSTOMER_ID_A);
    cancelledOrder
        .setItems(Arrays.asList(new OrderItem(PRODUCT_ID_A, 2, BigDecimal.ONE),
            new OrderItem(PRODUCT_ID_B, 3, BigDecimal.ONE)));
    cancelledOrder.setAmount(BigDecimal.valueOf(123.45));
    cancelledOrder.setState(State.CANCELLED);
    cancelledOrderId = orderRepository.save(cancelledOrder).getId();
  }

  @AfterEach
  private void tearDown() {
    orderRepository.deleteAll();
  }


  @Test
  @WithMockUser(username = "customer1", authorities = {SCOPE_ORDER_USE})
  public void testCreateAndGetOne() throws Exception {
    Map<String, InventoryItem> mockInventoryItems = new HashMap<>();
    InventoryItem inventoryItemA = new InventoryItem("1", PRODUCT_ID_A, 100,
        BigDecimal.valueOf(100.00F), new Date(), new Date());
    mockInventoryItems.put(inventoryItemA.getProductId(), inventoryItemA);
    InventoryItem inventoryItemB = new InventoryItem("2", PRODUCT_ID_B, 100,
        BigDecimal.valueOf(200.00F), new Date(), new Date());
    mockInventoryItems.put(inventoryItemB.getProductId(), inventoryItemB);
    when(mockInventoryClient.searchByProductIdIn(anyList(), any())).thenAnswer(invocation -> {
      final List<?> productIds = invocation.getArgument(0, List.class);
      final InventoryItem inventoryItem = mockInventoryItems.get(productIds.get(0));
      return pagedResourcesAssembler.toModel(new PageImpl<>(
          Collections.singletonList(inventoryItem), PageRequest.of(0, 10), 1));
    });
    Order newOne = new Order();
    newOne.setCustomerId(CUSTOMER_ID_A);
    newOne.setItems(Arrays.asList(new OrderItem(PRODUCT_ID_A, 2, BigDecimal.ONE),
        new OrderItem(PRODUCT_ID_B, 3, BigDecimal.ONE)));
    newOne.setAmount(BigDecimal.ZERO);

    final String createdOneJson = mvc.perform(
        post("/orders").contentType(MediaType.APPLICATION_JSON).accept(APPLICATION_HAL)
            .content(objectMapper.writeValueAsString(newOne))).andExpect(status().isCreated())
        .andExpect(content().contentTypeCompatibleWith(APPLICATION_HAL))
        .andExpect(jsonPath("$.customerId", is(CUSTOMER_ID_A)))
        .andExpect(jsonPath("$.items", hasSize(2)))
        .andExpect(jsonPath("$.items[0].productId", is(PRODUCT_ID_A)))
        .andExpect(jsonPath("$.items[0].quantity", is(2)))
        .andExpect(jsonPath("$.state", is(State.OPEN.toString())))
        .andExpect(jsonPath("$.amount", is(closeTo(800.00, 0.001))))
        .andExpect(jsonPath("$.createdAt", notNullValue()))
        .andExpect(jsonPath("$.updatedAt", notNullValue()))
        .andDo(document("order/create", itemLinks(), itemRequestFields(), itemResponseFields()))
        .andReturn().getResponse().getContentAsString();

    String newId = Stream
        .of(objectMapper.readTree(createdOneJson).at("/_links/self/href").asText().split("/"))
        .reduce((first, second) -> second).orElse(null);

    mvc.perform(get("/orders/{id}", newId).accept(APPLICATION_HAL))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(APPLICATION_HAL))
        .andExpect(jsonPath("$.customerId", is(CUSTOMER_ID_A)))
        .andExpect(jsonPath("$.items", hasSize(2)))
        .andExpect(jsonPath("$.items[0].productId", is(PRODUCT_ID_A)))
        .andExpect(jsonPath("$.items[0].quantity", is(2)))
        .andExpect(jsonPath("$.state", is(State.OPEN.toString())))
        .andExpect(jsonPath("$.amount", is(closeTo(800.00, 0.001))))
        .andExpect(jsonPath("$.createdAt", notNullValue()))
        .andExpect(jsonPath("$.updatedAt", notNullValue()))
        .andDo(document("order/getOne",
            pathParameters(parameterWithName("id").description("order's unique identifier")),
            itemLinks(), itemResponseFields()));
  }

  @Test
  @WithMockUser(username = "customer1", authorities = {SCOPE_ORDER_USE})
  public void testSubmitPass() throws Exception {
    mvc.perform(put("/orders/{id}/submit", openOrderId).accept(APPLICATION_HAL))
        .andExpect(status().isNoContent())
        .andDo(document("order/submit",
            pathParameters(parameterWithName("id").description("order's unique identifier"))));

    ArgumentCaptor<Object> submitOrderEventCaptor = ArgumentCaptor.forClass(Object.class);
    verify(mockAmqpTemplate).convertAndSend(anyString(), submitOrderEventCaptor.capture());
    Object submitOrderSaga = submitOrderEventCaptor.getValue();
    assertThat(submitOrderSaga, instanceOf(SubmitOrderSaga.class));
    assertThat(((SubmitOrderSaga) submitOrderSaga).getOrderId(), is(openOrderId));
    assertThat(((SubmitOrderSaga) submitOrderSaga).getStep(), is(Saga.CREATED));
  }

  @Test
  @WithMockUser(username = "customer1", authorities = {SCOPE_ORDER_USE})
  public void testSubmitFail() throws Exception {
    mvc.perform(put("/orders/{id}/submit", submittedOrderId).accept(APPLICATION_HAL))
        .andExpect(status().isConflict());
  }

  @Test
  @WithMockUser(username = "order_ops", authorities = {SCOPE_ORDER_OPERATE, SCOPE_ORDER_USE})
  public void testStartDeliveryPass() throws Exception {
    mvc.perform(put("/orders/{id}/startDelivery", paidOrderId))
        .andExpect(status().isNoContent())
        .andDo(document("order/startDelivery",
            pathParameters(parameterWithName("id").description("order's unique identifier"))));
    mvc.perform(get("/orders/{id}", paidOrderId).accept(APPLICATION_HAL))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state", is(State.ON_DELIVERY.toString())));
  }

  @Test
  @WithMockUser(username = "order_ops", authorities = {SCOPE_ORDER_OPERATE, SCOPE_ORDER_USE})
  public void testStartDeliveryFail() throws Exception {
    mvc.perform(put("/orders/{id}/startDelivery", onDeliveryOrderId).accept(APPLICATION_HAL))
        .andExpect(status().isConflict());
  }

  @Test
  @WithMockUser(username = "order_ops", authorities = {SCOPE_ORDER_OPERATE, SCOPE_ORDER_USE})
  public void testCompleteDeliveryPass() throws Exception {
    mvc.perform(put("/orders/{id}/completeDelivery", onDeliveryOrderId))
        .andExpect(status().isNoContent())
        .andDo(document("order/completeDelivery",
            pathParameters(parameterWithName("id").description("order's unique identifier"))));
    mvc.perform(get("/orders/{id}", onDeliveryOrderId).accept(APPLICATION_HAL))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state", is(State.DELIVERED.toString())));
  }

  @Test
  @WithMockUser(username = "order_ops", authorities = {SCOPE_ORDER_OPERATE, SCOPE_ORDER_USE})
  public void testCompleteDeliveryFail() throws Exception {
    mvc.perform(put("/orders/{id}/completeDelivery", deliveredOrderId))
        .andExpect(status().isConflict());
  }

  @Test
  @WithMockUser(username = "customer1", authorities = {SCOPE_ORDER_USE})
  public void testClosePass() throws Exception {
    mvc.perform(put("/orders/{id}/close", deliveredOrderId))
        .andExpect(status().isNoContent())
        .andDo(document("order/close",
            pathParameters(parameterWithName("id").description("order's unique identifier"))));
    mvc.perform(get("/orders/{id}", deliveredOrderId).accept(APPLICATION_HAL))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state", is(State.CLOSED.toString())));
  }

  @Test
  @WithMockUser(username = "customer1", authorities = {SCOPE_ORDER_USE})
  public void testCloseFail() throws Exception {
    mvc.perform(put("/orders/{id}/close", closedOrderId))
        .andExpect(status().isConflict());
  }

  @Test
  @WithMockUser(username = "customer1", authorities = {SCOPE_ORDER_USE})
  public void testCancelPass() throws Exception {
    mvc.perform(put("/orders/{id}/cancel", openOrderId))
        .andExpect(status().isNoContent())
        .andDo(document("order/cancel",
            pathParameters(parameterWithName("id").description("order's unique identifier"))));
    mvc.perform(get("/orders/{id}", openOrderId).accept(APPLICATION_HAL))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state", is(State.CANCELLED.toString())));
  }

  @Test
  @WithMockUser(username = "customer1", authorities = {SCOPE_ORDER_USE})
  public void testCancelFail() throws Exception {
    mvc.perform(put("/orders/{id}/cancel", cancelledOrderId))
        .andExpect(status().isConflict());
  }

  @Test
  @WithMockUser(username = "customer1", authorities = {SCOPE_ORDER_USE})
  public void testGetSearchByStates() throws Exception {
    mvc.perform(
        get("/orders/search/stateIn?state={state1}&state={state2}&page={page}&size={size}&sort={sort}",
            State.SUBMITTED, State.ON_DELIVERY, 0, 10, "state,ASC").accept(APPLICATION_HAL))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(APPLICATION_HAL))
        .andExpect(jsonPath("$._embedded.orders", hasSize(2)))
        .andExpect(jsonPath("$._embedded.orders[0].state", is(State.ON_DELIVERY.toString())))
        .andExpect(jsonPath("$._embedded.orders[1].state", is(State.SUBMITTED.toString())))
        .andDo(document("order/search/stateIn", pageRequestParameters(
            parameterWithName("state").description("state, support multiple values")),
            pageLinks(), pageResponseFields()));
  }

  @Test
  @WithMockUser(username = "order_ops", authorities = {SCOPE_ORDER_OPERATE, SCOPE_ORDER_USE})
  public void testSearchByCustomerId() throws Exception {
    mvc.perform(
        get("/orders/search/customerId?customerId={customerId}&page={page}&size={size}&sort={sort}",
            CUSTOMER_ID_A, 0, 10, "createdAt,DESC").accept(APPLICATION_HAL))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(APPLICATION_HAL))
        .andExpect(jsonPath("$._embedded.orders", hasSize(7)))
        .andDo(document("order/search/customerId", pageRequestParameters(
            parameterWithName("customerId").description("customer unique identifier of order")),
            pageLinks(), pageResponseFields()));
  }

  private RequestParametersSnippet pageRequestParameters(ParameterDescriptor... descriptors) {
    return requestParameters(parameterWithName("page").description("0-based page index"),
        parameterWithName("size").description("page size, default 10"),
        parameterWithName("sort").description("sort expression"))
        .and(descriptors);
  }

  private LinksSnippet pageLinks(LinkDescriptor... descriptors) {
    return HypermediaDocumentation.links(halLinks(),
        linkWithRel("self").description("self link"))
        .and(descriptors);
  }

  private ResponseFieldsSnippet pageResponseFields(FieldDescriptor... descriptors) {
    return responseFields(subsectionWithPath("_links").description("links to other resources"),
        subsectionWithPath("_embedded.orders").type(JsonFieldType.ARRAY)
            .description("order collection"),
        fieldWithPath("page.size").type(JsonFieldType.NUMBER).description("page size"),
        fieldWithPath("page.number").type(JsonFieldType.NUMBER)
            .description("0-based page index"),
        fieldWithPath("page.totalElements").type(JsonFieldType.NUMBER)
            .description("the count of items which matched the search criteria"),
        fieldWithPath("page.totalPages").type(JsonFieldType.NUMBER)
            .description("the count of pages"))
        .and(descriptors);
  }

  private LinksSnippet itemLinks(LinkDescriptor... descriptors) {
    return HypermediaDocumentation.links(halLinks(), linkWithRel("self").description("self link"))
        .and(descriptors);
  }

  private RequestFieldsSnippet itemRequestFields(FieldDescriptor... descriptors) {
    return PayloadDocumentation.requestFields(
        fieldWithPath("id").type(JsonFieldType.STRING).ignored()
            .description("order's unique identifier"),
        fieldWithPath("customerId").type(JsonFieldType.STRING)
            .description("The id of customer who create the order"),
        fieldWithPath("state").type(JsonFieldType.STRING).ignored().description("order's state"),
        fieldWithPath("items").type(JsonFieldType.ARRAY).description("order items"),
        fieldWithPath("amount").type(JsonFieldType.NUMBER).description("amount of order"),
        fieldWithPath("createdAt").type("Date").description("creation timestamp"),
        fieldWithPath("updatedAt").type("Date").description("update timestamp"))
        .andWithPrefix("items[]",
            fieldWithPath("productId").type(JsonFieldType.STRING).description("product id"),
            fieldWithPath("quantity").type(JsonFieldType.NUMBER).description("number"),
            fieldWithPath("unitPrice").type(JsonFieldType.NUMBER).description("unit price of item"))
        .and(descriptors);
  }

  private ResponseFieldsSnippet itemResponseFields(FieldDescriptor... descriptors) {
    return PayloadDocumentation.responseFields(
        subsectionWithPath("_links").description("links to other resources"),
        fieldWithPath("id").type(JsonFieldType.STRING)
            .description("order's unique identifier"),
        fieldWithPath("customerId").type(JsonFieldType.STRING)
            .description("The id of customer who create the order"),
        fieldWithPath("state").type(JsonFieldType.STRING).description("order's state"),
        fieldWithPath("items").type(JsonFieldType.ARRAY).description("order items"),
        fieldWithPath("amount").type(JsonFieldType.NUMBER).description("amount of order"),
        fieldWithPath("createdAt").type("Date").description("creation timestamp"),
        fieldWithPath("updatedAt").type("Date").description("update timestamp"))
        .andWithPrefix("items[]",
            fieldWithPath("productId").type(JsonFieldType.STRING).description("product id"),
            fieldWithPath("quantity").type(JsonFieldType.NUMBER).description("number"),
            fieldWithPath("unitPrice").type(JsonFieldType.NUMBER).description("unit price of item"))
        .and(descriptors);
  }
}
