package io.github.rscai.microservices.order.saga;

import java.util.Date;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.UpdateTimestamp;

@Data
public abstract class Saga {

  public static final String CREATED = "CREATED";
  public static final String COMPLETED = "COMPLETED";
  public static final String ROLLBACK = "ROLLBACK";
  @Id
  @GeneratedValue(generator = "system-uuid")
  @GenericGenerator(name = "system-uuid", strategy = "uuid")
  protected String id;
  protected String step;
  @CreationTimestamp
  @Temporal(TemporalType.TIMESTAMP)
  protected Date createdAt;
  @UpdateTimestamp
  @Temporal(TemporalType.TIMESTAMP)
  protected Date updatedAt;
}
