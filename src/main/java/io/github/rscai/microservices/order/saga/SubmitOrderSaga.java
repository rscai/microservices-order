package io.github.rscai.microservices.order.saga;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name="saga_submit_order")
public class SubmitOrderSaga extends Saga {
  public static String DECREASED_INVENTORY="DECREASED_INVENTORY";
  public static String DECREASED_INVENTORY_ROLLBACK="DECREASED_INVENTORY_ROLLBACK";
  public static String SUBMITTED_STATUS="SUBMITTED_STATUS";
  public static String SUBMITTED_STATUS_ROLLBACK="SUBMITTED_STATUS_ROLLBACK";
  @Column(name="order_id")
  private String orderId;
}
