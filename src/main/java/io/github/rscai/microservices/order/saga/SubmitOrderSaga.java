package io.github.rscai.microservices.order.saga;

import javax.persistence.Column;
import lombok.Data;

@Data
public class SubmitOrderSaga extends Saga {

  public static String DECREASED_INVENTORY = "DECREASED_INVENTORY";
  public static String DECREASED_INVENTORY_ROLLBACK = "DECREASED_INVENTORY_ROLLBACK";
  public static String SUBMITTED_STATUS = "SUBMITTED_STATUS";
  public static String SUBMITTED_STATUS_ROLLBACK = "SUBMITTED_STATUS_ROLLBACK";
  @Column(name = "order_id")
  private String orderId;
}
