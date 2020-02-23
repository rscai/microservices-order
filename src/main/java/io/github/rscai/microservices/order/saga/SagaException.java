package io.github.rscai.microservices.order.saga;

public class SagaException extends Exception {

  public SagaException(String message) {
    super(message);
  }

  public SagaException(String message, Throwable cause) {
    super(message, cause);
  }
}
