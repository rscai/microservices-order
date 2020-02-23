package io.github.rscai.microservices.order.saga;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SubmitOrderSagaRepository extends CrudRepository<SubmitOrderSaga, String> {

}
