/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.context.propagation;

import io.opentelemetry.context.Context;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Injects and extracts a value as text into carriers that travel in-band across process boundaries.
 * Encoding is expected to conform to the HTTP Header Field semantics. Values are often encoded as
 * RPC/HTTP request headers.
 *
 * <p>The carrier of propagated data on both the client (injector) and server (extractor) side is
 * usually an http request. Propagation is usually implemented via library- specific request
 * interceptors, where the client-side injects values and the server-side extracts them.
 *
 * <p>Specific concern values (traces, correlations, etc) will be read from the specified {@code
 * Context}, and resulting values will be stored in a new {@code Context} upon extraction. It is
 * recommended to use a single {@code Context.Key} to store the entire concern data:
 *
 * <pre>{@code
 * public static final Context.Key CONCERN_KEY = Context.key("my-concern-key");
 * public MyConcernPropagator implements TextMapPropagator {
 *   public <C> void inject(Context context, C carrier, Setter<C> setter) {
 *     Object concern = CONCERN_KEY.get(context);
 *     // Use concern in the specified context to propagate data.
 *   }
 *   public <C> Context extract(Context context, C carrier, Getter<C> getter) {
 *     // Use getter to get the data from the carrier.
 *     return context.withValue(CONCERN_KEY, concern);
 *   }
 * }
 * }</pre>
 */
@ThreadSafe
public interface TextMapPropagator {

  /**
   * Returns a {@link TextMapPropagator} which simply delegates injection and extraction to the
   * provided propagators.
   *
   * <p>Invocation order of {@code TextMapPropagator#inject()} and {@code
   * TextMapPropagator#extract()} for registered trace propagators is undefined.
   */
  static TextMapPropagator composite(TextMapPropagator... propagators) {
    return composite(Arrays.asList(propagators));
  }

  /**
   * Returns a {@link TextMapPropagator} which simply delegates injection and extraction to the
   * provided propagators.
   *
   * <p>Invocation order of {@code TextMapPropagator#inject()} and {@code
   * TextMapPropagator#extract()} for registered trace propagators is undefined.
   */
  static TextMapPropagator composite(Iterable<TextMapPropagator> propagators) {
    List<TextMapPropagator> propagatorsList = new ArrayList<>();
    for (TextMapPropagator propagator : propagators) {
      propagatorsList.add(propagator);
    }
    if (propagatorsList.isEmpty()) {
      return NoopTextMapPropagator.getInstance();
    }
    if (propagatorsList.size() == 1) {
      return propagatorsList.get(0);
    }
    return new MultiTextMapPropagator(propagatorsList);
  }

  /** Returns a {@link TextMapPropagator} which does no injection or extraction. */
  static TextMapPropagator noop() {
    return NoopTextMapPropagator.getInstance();
  }

  /**
   * The propagation fields defined. If your carrier is reused, you should delete the fields here
   * before calling {@link #inject(Context, Object, TextMapSetter)} )}.
   *
   * <p>For example, if the carrier is a single-use or immutable request object, you don't need to
   * clear fields as they couldn't have been set before. If it is a mutable, retryable object,
   * successive calls should clear these fields first.
   *
   * @return the fields that will be used by this formatter.
   */
  // The use cases of this are:
  // * allow pre-allocation of fields, especially in systems like gRPC Metadata
  // * allow a single-pass over an iterator
  Collection<String> fields();

  /**
   * Injects the value downstream, for example as HTTP headers. The carrier may be null to
   * facilitate calling this method with a lambda for the {@link TextMapSetter}, in which case that
   * null will be passed to the {@link TextMapSetter} implementation.
   *
   * @param context the {@code Context} containing the value to be injected.
   * @param carrier holds propagation fields. For example, an outgoing message or http request.
   * @param setter invoked for each propagation key to add or remove.
   * @param <C> carrier of propagation fields, such as an http request
   */
  <C> void inject(Context context, @Nullable C carrier, TextMapSetter<C> setter);

  /**
   * Extracts the value from upstream. For example, as http headers.
   *
   * <p>If the value could not be parsed, the underlying implementation will decide to set an object
   * representing either an empty value, an invalid value, or a valid value. Implementation must not
   * set {@code null}.
   *
   * @param context the {@code Context} used to store the extracted value.
   * @param carrier holds propagation fields. For example, an outgoing message or http request.
   * @param getter invoked for each propagation key to get.
   * @param <C> carrier of propagation fields, such as an http request.
   * @return the {@code Context} containing the extracted value.
   */
  <C> Context extract(Context context, @Nullable C carrier, TextMapGetter<C> getter);
}
