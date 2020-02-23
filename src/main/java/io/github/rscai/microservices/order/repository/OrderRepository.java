package io.github.rscai.microservices.order.repository;

import io.github.rscai.microservices.order.model.Order;
import io.github.rscai.microservices.order.model.Order.State;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.PagingAndSortingRepository;

public interface OrderRepository extends PagingAndSortingRepository<Order, String> {

  Page<Order> findByCustomerId(String customerId, Pageable pageable);

  Page<Order> findByCustomerIdAndState(String customerId, State state, Pageable pageable);

  Page<Order> findByStateIn(State[] states, Pageable pageable);

  int countByStateIn(State[] states);
}
