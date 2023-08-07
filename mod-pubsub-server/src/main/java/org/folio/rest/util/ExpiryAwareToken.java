package org.folio.rest.util;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
@Getter
@Setter
public class ExpiryAwareToken {
  private String token;
  private long maxAge;
  private OkapiConnectionParams okapiParams;
}
