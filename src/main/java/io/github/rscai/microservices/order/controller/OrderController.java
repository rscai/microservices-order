package io.github.rscai.microservices.order.controller;

import io.github.rscai.microservices.order.model.InventoryItem;
import io.github.rscai.microservices.order.model.Order;
import io.github.rscai.microservices.order.model.Order.State;
import io.github.rscai.microservices.order.model.OrderItem;
import io.github.rscai.microservices.order.repository.OrderRepository;
import io.github.rscai.microservices.order.service.InventoryClient;
import io.github.rscai.microservices.order.service.OrderService;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import javax.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.PagedModel;
import org.springframework.hateoas.server.EntityLinks;
import org.springframework.hateoas.server.ExposesResourceFor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;


@RestController
@ExposesResourceFor(Order.class)
@RequestMapping("orders")
public class OrderController {

  private static final String AUTHORITY_ORDER_USE = "hasAuthority('SCOPE_order.use')";
  private static final String AUTHORITY_ORDER_OPERATE = "hasAuthority('SCOPE_order.operate')";
  private final EntityLinks entityLinks;
  @Autowired
  private OrderRepository orderRepository;
  @Autowired
  private OrderService orderService;
  @Autowired
  private PagedResourcesAssembler<Order> pagedResourcesAssembler;
  @Autowired
  private InventoryClient inventoryClient;

  public OrderController(EntityLinks entityLinks) {
    this.entityLinks = entityLinks;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize(AUTHORITY_ORDER_USE)
  public EntityModel<Order> create(@RequestBody Order order) {
    order.setState(State.OPEN);
    for (OrderItem item : order.getItems()) {
      Collection<EntityModel<InventoryItem>> inventoryItems = inventoryClient
          .searchByProductIdIn(Collections.singletonList(item.getProductId()), PageRequest.of(0, 1))
          .getContent();
      item.setUnitPrice(Objects.requireNonNull(inventoryItems.stream().findFirst()
          .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
              String.format("Can not find product %s from Inventory", item.getProductId()))
          ).getContent()).getUnitPrice());
    }
    BigDecimal amount = order.getItems().stream().map(orderItem ->
        orderItem.getUnitPrice().multiply(BigDecimal.valueOf(orderItem.getQuantity()))
    ).reduce(BigDecimal::add).orElse(BigDecimal.ZERO);
    order.setAmount(amount);
    
    EntityModel<Order> createdOne = new EntityModel<>(orderRepository.save(order));
    createdOne.add(itemLinks(Objects.requireNonNull(createdOne.getContent())));
    return createdOne;
  }

  @PutMapping("{id}/submit")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize(AUTHORITY_ORDER_USE)
  public void submit(@PathVariable("id") String id) {
    Order order = orderRepository.findById(id).orElseThrow(
        () -> new ResponseStatusException(HttpStatus.NOT_FOUND,
            String.format("Order %s is not found", id)));
    if (!orderService.submit(order)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT,
          String.format("Can not submit Order %s", id));
    }
  }

  @PutMapping("{id}/cancel")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize(AUTHORITY_ORDER_USE)
  public void cancel(@PathVariable("id") String id) {
    Order order = orderRepository.findById(id).orElseThrow(
        () -> new ResponseStatusException(HttpStatus.NOT_FOUND,
            String.format("Order %s is not found", id)));
    if (!order.cancel()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT,
          String.format("Can not cancel Order %s", id));
    }
    orderRepository.save(order);
  }

  @PutMapping("{id}/startDelivery")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize(AUTHORITY_ORDER_OPERATE)
  public void startDelivery(@PathVariable("id") String id) {
    Order order = orderRepository.findById(id).orElseThrow(
        () -> new ResponseStatusException(HttpStatus.NOT_FOUND,
            String.format("Order %s is not found", id)));
    if (!order.startDelivery()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT,
          String.format("Can not start delivery Order %s", id));
    }
    orderRepository.save(order);
  }

  @PutMapping("{id}/completeDelivery")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize(AUTHORITY_ORDER_OPERATE)
  public void completeDelivery(@PathVariable("id") String id) {
    Order order = orderRepository.findById(id).orElseThrow(
        () -> new ResponseStatusException(HttpStatus.NOT_FOUND,
            String.format("Order %s is not found", id)));
    if (!order.completeDelivery()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT,
          String.format("Can not complete delivery Order %s", id));
    }
    orderRepository.save(order);
  }

  @PutMapping("{id}/close")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize(AUTHORITY_ORDER_USE)
  public void close(@PathVariable("id") String id) {
    Order order = orderRepository.findById(id).orElseThrow(
        () -> new ResponseStatusException(HttpStatus.NOT_FOUND,
            String.format("Order %s is not found", id)));
    if (!order.close()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT,
          String.format("Can not close Order %s", id));
    }
    orderRepository.save(order);
  }

  @GetMapping("{id}")
  @PreAuthorize(AUTHORITY_ORDER_USE)
  public EntityModel<Order> getOne(@PathVariable("id") String id) {
    Order order = orderRepository.findById(id).orElseThrow(
        () -> new ResponseStatusException(HttpStatus.NOT_FOUND,
            String.format("Order %s is not found", id)));

    EntityModel<Order> foundOne = new EntityModel<>(order);
    foundOne.add(itemLinks(order));
    return foundOne;
  }

  @GetMapping("search/stateIn")
  @PreAuthorize(AUTHORITY_ORDER_USE)
  public PagedModel<EntityModel<Order>> searchByStateIn(@RequestParam("state") State[] states,
      @NotNull Pageable pageable) {
    Page<Order> orders = orderRepository.findByStateIn(states, pageable);
    return pagedResourcesAssembler.toModel(orders);
  }

  @GetMapping("search/customerId")
  @PreAuthorize(AUTHORITY_ORDER_OPERATE)
  public PagedModel<EntityModel<Order>> searchByCustomerId(
      @RequestParam("customerId") String customerId,
      @NotNull Pageable pageable) {
    Page<Order> orders = orderRepository.findByCustomerId(customerId, pageable);
    return pagedResourcesAssembler.toModel(orders);
  }

  private Link[] itemLinks(final Order item) {
    return new Link[]{
        entityLinks.linkToItemResource(Order.class, item.getId())
    };
  }
}
