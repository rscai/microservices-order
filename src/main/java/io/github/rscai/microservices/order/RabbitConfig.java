package io.github.rscai.microservices.order;

import io.github.rscai.microservices.order.saga.SubmitOrderSaga;
import java.util.HashMap;
import java.util.Map;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.DefaultClassMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Profile("!test")
@Configuration
public class RabbitConfig {

  @Value("${mq.submit-order-saga.hostname}")
  private String hostname;
  @Value("${mq.submit-order-saga.port}")
  private int port;
  @Value("${mq.submit-order-saga.queue.name}")
  private String queueName;
  @Value("${mq.submit-order-saga.exchange}")
  private String exchange;

  @Bean
  public ConnectionFactory connectionFactory() {
    return new CachingConnectionFactory(hostname, port);
  }

  @Bean
  public AmqpAdmin amqpAdmin() {
    return new RabbitAdmin(connectionFactory());
  }

  @Bean
  public RabbitTemplate rabbitTemplate() {
    RabbitTemplate template = new RabbitTemplate(connectionFactory());
    template.setMessageConverter(jsonMessageConverter());
    template.setExchange(exchange);
    return template;
  }

  @Bean
  public Queue myQueue() {
    return new Queue(queueName);
  }

  @Bean
  public Jackson2JsonMessageConverter jsonMessageConverter() {
    Jackson2JsonMessageConverter jsonConverter = new Jackson2JsonMessageConverter();
    jsonConverter.setClassMapper(classMapper());
    return jsonConverter;
  }

  @Bean
  public DefaultClassMapper classMapper() {
    DefaultClassMapper classMapper = new DefaultClassMapper();
    Map<String, Class<?>> idClassMapping = new HashMap<>();
    idClassMapping.put(SubmitOrderSaga.class.getName(), SubmitOrderSaga.class);
    classMapper.setIdClassMapping(idClassMapping);
    return classMapper;
  }
}