/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.armeria.v1_3;

import com.linecorp.armeria.client.ClientRequestContext;
import com.linecorp.armeria.client.HttpClient;
import com.linecorp.armeria.client.SimpleDecoratingHttpClient;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.logging.RequestLog;
import io.netty.util.AttributeKey;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;

/** Decorates an {@link HttpClient} to trace outbound {@link HttpResponse}s. */
final class OpenTelemetryClient extends SimpleDecoratingHttpClient {

  private static final AttributeKey<Boolean> OTEL_DECORATOR_SET =
      AttributeKey.valueOf(OpenTelemetryClient.class, "OTEL_DECORATOR_SET");

  private final Instrumenter<ClientRequestContext, RequestLog> instrumenter;

  OpenTelemetryClient(
      HttpClient delegate, Instrumenter<ClientRequestContext, RequestLog> instrumenter) {
    super(delegate);
    this.instrumenter = instrumenter;
  }

  @Override
  public HttpResponse execute(ClientRequestContext ctx, HttpRequest req) throws Exception {
    // just in case the decorator is set in the chain more than once
    final Boolean isOtelDecoratorSet = ctx.attr(OTEL_DECORATOR_SET);
    if (Boolean.TRUE.equals(isOtelDecoratorSet)) {
      return unwrap().execute(ctx, req);
    }
    ctx.setAttr(OTEL_DECORATOR_SET, true);

    Context parentContext = Context.current();
    if (!instrumenter.shouldStart(parentContext, ctx)) {
      return unwrap().execute(ctx, req);
    }

    Context context = instrumenter.start(Context.current(), ctx);

    ctx.log()
        .whenComplete()
        .thenAccept(log -> instrumenter.end(context, ctx, log, log.responseCause()));

    try (Scope ignored = context.makeCurrent()) {
      return unwrap().execute(ctx, req);
    }
  }
}
