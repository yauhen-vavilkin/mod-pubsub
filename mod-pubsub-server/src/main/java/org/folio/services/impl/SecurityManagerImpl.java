package org.folio.services.impl;

import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.folio.HttpStatus;
import org.folio.dao.PubSubUserDao;
import org.folio.rest.util.OkapiConnectionParams;
import org.folio.services.SecurityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static org.folio.rest.util.OkapiConnectionParams.OKAPI_TOKEN_HEADER;
import static org.folio.rest.util.RestUtil.doRequest;

@Component
public class SecurityManagerImpl implements SecurityManager {

  private static final Logger LOGGER = LoggerFactory.getLogger(SecurityManagerImpl.class);

  private static final String LOGIN_URL = "/authn/login";

  private PubSubUserDao pubSubUserDao;

  public SecurityManagerImpl(@Autowired PubSubUserDao pubSubUserDao) {
    this.pubSubUserDao = pubSubUserDao;
  }

  @Override
  public Future<Boolean> loginPubSubUser(OkapiConnectionParams params) {
    return pubSubUserDao.getPubSubUserCredentials(params.getTenantId())
      .compose(userCredentials -> doRequest(userCredentials.encode(), LOGIN_URL, params))
      .compose(response -> {
        if (response.statusCode() == HttpStatus.HTTP_CREATED.toInt()) {
          LOGGER.info("Logged in pub-sub user");
          return pubSubUserDao.savePubSubJWTToken(response.getHeader(OKAPI_TOKEN_HEADER), params.getTenantId());
        }
        LOGGER.error("pub-sub user was not logged in, received status {}", response.statusCode());
        return Future.succeededFuture(false);
      });
  }

  @Override
  public Future<String> getJWTToken(String tenantId) {
    return pubSubUserDao.getPubSubJWTToken(tenantId);
  }

}
