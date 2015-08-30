/*
 * Copyright (C) 2015 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package retrofit;

import com.squareup.okhttp.OkHttpClient;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.List;

final class MethodHandler<T> {
  @SuppressWarnings("unchecked")
  static MethodHandler<?> create(Method method, OkHttpClient client, BaseUrl baseUrl,
      List<CallAdapter.Factory> callAdapterFactories, List<Converter.Factory> converterFactories) {
    CallAdapter<Object> callAdapter =
        (CallAdapter<Object>) createCallAdapter(method, callAdapterFactories);
    Converter<Object> responseConverter =
        (Converter<Object>) createResponseConverter(method, callAdapter.responseType(),
            converterFactories);
    RequestFactory requestFactory = RequestFactoryParser.parse(method, baseUrl, converterFactories);
    return new MethodHandler<>(client, requestFactory, callAdapter, responseConverter);
  }

  private static CallAdapter<?> createCallAdapter(Method method,
      List<CallAdapter.Factory> adapterFactories) {
    Type returnType = method.getGenericReturnType();
    if (Utils.hasUnresolvableType(returnType)) {
      throw Utils.methodError(method,
          "Method return type must not include a type variable or wildcard: %s", returnType);
    }
    if (returnType == void.class) {
      throw Utils.methodError(method, "Service methods cannot return void.");
    }
    Annotation[] annotations = method.getAnnotations();
    try {
      return Utils.resolveCallAdapter(adapterFactories, returnType, annotations);
    } catch (RuntimeException e) { // Wide exception range because factories are user code.
      throw Utils.methodError(e, method, "Unable to create call adapter for %s", returnType);
    }

  }

  private static Converter<?> createResponseConverter(Method method, Type responseType,
      List<Converter.Factory> converterFactories) {
    Annotation[] annotations = method.getAnnotations();
    try {
      return Utils.resolveConverter(converterFactories, responseType, annotations);
    } catch (RuntimeException e) { // Wide exception range because factories are user code.
      throw Utils.methodError(e, method, "Unable to create converter for %s", responseType);
    }
  }

  private final OkHttpClient client;
  private final RequestFactory requestFactory;
  private final CallAdapter<T> callAdapter;
  private final Converter<T> responseConverter;

  private MethodHandler(OkHttpClient client, RequestFactory requestFactory,
      CallAdapter<T> callAdapter, Converter<T> responseConverter) {
    this.client = client;
    this.requestFactory = requestFactory;
    this.callAdapter = callAdapter;
    this.responseConverter = responseConverter;
  }

  Object invoke(Object... args) {
    return callAdapter.adapt(new OkHttpCall<>(client, requestFactory, responseConverter, args));
  }
}
