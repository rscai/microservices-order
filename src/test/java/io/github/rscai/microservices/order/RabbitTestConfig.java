package io.github.rscai.microservices.order;

import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.rabbitmq.client.Channel;
import java.io.IOException;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.test.TestRabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Profile("test")
@Configuration
public class RabbitTestConfig {

  @Bean
  public TestRabbitTemplate template() throws IOException {
    return new TestRabbitTemplate(connectionFactory());
  }

  @Bean
  public ConnectionFactory connectionFactory() throws IOException {
    ConnectionFactory factory = mock(ConnectionFactory.class);
    Connection connection = mock(Connection.class);
    Channel channel = mock(Channel.class);
    when(factory.createConnection()).thenReturn(connection);
    when(connection.createChannel(anyBoolean())).thenReturn(channel);
    when(channel.isOpen()).thenReturn(true);
    return factory;
  }
}
