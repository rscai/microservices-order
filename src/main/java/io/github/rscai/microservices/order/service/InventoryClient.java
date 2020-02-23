package io.github.rscai.microservices.order.service;

import io.github.rscai.microservices.order.model.InventoryItem;
import io.github.rscai.microservices.order.model.InventoryItemQuantityChange;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.data.domain.Pageable;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient("inventory")
public interface InventoryClient {

  @GetMapping(value = "/inventoryItems/search/productIdIn", consumes = "application/hal+json")
  PagedModel<EntityModel<InventoryItem>> searchByProductIdIn(
      @RequestParam("productId") List<String> productIds, Pageable pageable);

  @PostMapping(value = "/inventoryItemQuantityChanges", consumes = "application/hal+json")
  List<EntityModel<InventoryItemQuantityChange>> changeInventoryItemQuantity(
      @RequestBody List<InventoryItemQuantityChange> changes);
}
