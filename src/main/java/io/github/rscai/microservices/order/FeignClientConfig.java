package io.github.rscai.microservices.order;

import feign.Retryer;
import io.github.rscai.microservices.order.service.FeignRequestInterceptor;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.client.DefaultOAuth2ClientContext;
import org.springframework.security.oauth2.client.resource.OAuth2ProtectedResourceDetails;
import org.springframework.security.oauth2.client.token.grant.password.ResourceOwnerPasswordResourceDetails;

@Profile("!test")
@Configuration
public class FeignClientConfig {

  @Value("${feign.oauth.access-token-uri}")
  private String accessTokenUri;
  @Value("${feign.oauth.client.id}")
  private String clientId;
  @Value("${feign.oauth.client.secret}")
  private String clientSecret;
  @Value("${feign.oauth.client.scope}")
  private String scope;
  @Value("${feign.oauth.client.username}")
  private String username;
  @Value("${feign.oauth.client.password}")
  private String password;
  @Value("${feign.oauth.authorization-grant-type}")
  private String grantType = "password";

  @Bean
  FeignRequestInterceptor oauth2FeignRequestInterceptor() {
    return new FeignRequestInterceptor(new DefaultOAuth2ClientContext(), resource());
  }

  private OAuth2ProtectedResourceDetails resource() {
    ResourceOwnerPasswordResourceDetails resourceDetails = new ResourceOwnerPasswordResourceDetails();
    resourceDetails.setUsername(username);
    resourceDetails.setPassword(password);
    resourceDetails.setAccessTokenUri(accessTokenUri);
    resourceDetails.setClientId(clientId);
    resourceDetails.setClientSecret(clientSecret);
    resourceDetails.setGrantType(grantType);
    resourceDetails.setScope(Arrays.asList(scope.split(",")));
    return resourceDetails;
  }

  @Bean
  public Retryer feignRetryer() {
    return new Retryer.Default(100, TimeUnit.SECONDS.toMillis(1), 2); // this will retry once
  }
}
