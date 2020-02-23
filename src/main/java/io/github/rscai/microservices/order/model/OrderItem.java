package io.github.rscai.microservices.order.model;

import java.math.BigDecimal;
import javax.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Embeddable
public class OrderItem {
  private String productId;
  private int quantity;
  private BigDecimal unitPrice;
}
