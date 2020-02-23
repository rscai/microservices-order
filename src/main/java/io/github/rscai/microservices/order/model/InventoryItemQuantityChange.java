package io.github.rscai.microservices.order.model;

import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Data
@NoArgsConstructor
@RequiredArgsConstructor
public class InventoryItemQuantityChange {
  @NonNull
  private String id;
  @NonNull
  private String inventoryItemId;
  @NonNull
  private int quantityChange;
  private Date createdAt;
}
