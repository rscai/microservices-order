package io.github.rscai.microservices.order.repository;

import io.github.rscai.microservices.order.model.Order;
import io.github.rscai.microservices.order.model.Order.State;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderRepository extends PagingAndSortingRepository<Order, String> {
  Page<Order> findByCustomerId(String customerId, Pageable pageable);
  Page<Order> findByStateIn(State[] states, Pageable pageable);
}
