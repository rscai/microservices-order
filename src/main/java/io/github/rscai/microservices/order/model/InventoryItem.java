package io.github.rscai.microservices.order.model;

import java.math.BigDecimal;
import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class InventoryItem {
  private String id;
  private String productId;
  private int quantity;
  private BigDecimal unitPrice;
  private Date createdAt;
  private Date updatedAt;
}
