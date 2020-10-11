package io.github.rscai.microservices.order.model;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.JoinColumn;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.hateoas.server.core.Relation;

@Relation(collectionRelation = "orders")
@Data
@Entity
@Table(name = "t_order",
    indexes = {@Index(name = "idx_state", columnList = "state", unique = false),
        @Index(name = "idx_customer_id", columnList = "customer_id", unique = false)})
public class Order {

  public enum State {
    OPEN,
    SUBMITTED,
    CANCELLED,
    ON_DELIVERY,
    DELIVERED,
    CLOSED
  }

  @Id
  @GeneratedValue(generator = "system-uuid")
  @GenericGenerator(name = "system-uuid", strategy = "uuid")
  private String id;
  @Column(nullable = false, scale = 2)
  private BigDecimal amount;
  @Column(name="customer_id", nullable = false)
  private String customerId;
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "order_item", joinColumns = @JoinColumn(name = "order_id"))
  private List<OrderItem> items;
  @Enumerated(EnumType.STRING)
  private State state;
  @CreationTimestamp
  @Temporal(TemporalType.TIMESTAMP)
  private Date createdAt;
  @UpdateTimestamp
  @Temporal(TemporalType.TIMESTAMP)
  private Date updatedAt;

  public boolean submit() {
    if (state == State.OPEN) {
      state = State.SUBMITTED;
      return true;
    }
    return false;
  }

  public boolean cancel() {
    if (state == State.OPEN) {
      state = State.CANCELLED;
      return true;
    }
    return false;
  }

  public boolean startDelivery() {
    if (state == State.SUBMITTED) {
      state = State.ON_DELIVERY;
      return true;
    }
    return false;
  }

  public boolean completeDelivery() {
    if (state == State.ON_DELIVERY) {
      state = State.DELIVERED;
      return true;
    }
    return false;
  }

  public boolean close() {
    if (state == State.DELIVERED) {
      state = State.CLOSED;
      return true;
    }
    return false;
  }
}
