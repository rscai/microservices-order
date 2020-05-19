package io.github.rscai.microservices.order.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.security.oauth2.client.feign.OAuth2FeignRequestInterceptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.OAuth2ClientContext;
import org.springframework.security.oauth2.client.resource.OAuth2ProtectedResourceDetails;
import org.springframework.security.oauth2.client.resource.UserRedirectRequiredException;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.common.exceptions.InvalidGrantException;
import org.springframework.web.client.HttpClientErrorException;

@Slf4j
public class FeignRequestInterceptor extends OAuth2FeignRequestInterceptor {

  private OAuth2ClientContext clientContext;

  public FeignRequestInterceptor(OAuth2ClientContext oAuth2ClientContext,
      OAuth2ProtectedResourceDetails resource) {
    super(oAuth2ClientContext, resource);
    clientContext = oAuth2ClientContext;
  }

  @Override
  public OAuth2AccessToken getToken() {
    OAuth2AccessToken accessToken = clientContext.getAccessToken();

    if (accessToken == null || accessToken.isExpired()) {
      try {
        accessToken = acquireAccessToken();
      } catch (InvalidGrantException e) {
        log.error("Catched invalid grant exception: ", e);

        cleanAccessToken();
        accessToken = acquireAccessToken();
      } catch (HttpClientErrorException e) {
        log.error("Catched oauth http exception: ", e);

        if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
          log.debug("Acquiring access token...");
          cleanAccessToken();
          accessToken = acquireAccessToken();
        } else {
          throw e;
        }
      } catch (UserRedirectRequiredException e) {
        clientContext.setAccessToken(null);
        String stateKey = e.getStateKey();
        if (stateKey != null) {
          Object stateToPreserve = e.getStateToPreserve();
          if (stateToPreserve == null) {
            stateToPreserve = "NONE";
          }
          clientContext.setPreservedState(stateKey, stateToPreserve);
        }
        throw e;
      }
    }
    return accessToken;
  }

  public void cleanAccessToken() {
    log.debug("Reset access token");

    clientContext.setAccessToken(null);
  }
}
