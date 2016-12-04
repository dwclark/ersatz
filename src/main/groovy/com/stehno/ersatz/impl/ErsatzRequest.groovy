/*
 * Copyright (C) 2016 Christopher J. Stehno
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.stehno.ersatz.impl

import com.stehno.ersatz.Request
import com.stehno.ersatz.Response
import com.stehno.ersatz.Verifiers
import groovy.transform.CompileStatic
import io.undertow.server.HttpServerExchange
import io.undertow.server.handlers.Cookie
import io.undertow.util.HeaderMap

import java.util.function.Consumer
import java.util.function.Function

/**
 * Abstract base class for request expectation definitions.
 */
@CompileStatic
class ErsatzRequest implements Request {

    private final Map<String, List<String>> queryParams = [:]
    private final Map<String, String> headers = [:]
    private final Map<String, String> cookies = [:]
    private final List<Consumer<Request>> listeners = []
    private final List<Response> responses = []
    private final List<Function<Request, Boolean>> conditions = []
    private final boolean emptyResponse
    private final String path
    private final String method
    private Function<Integer, Boolean> verifier = Verifiers.any()
    private int callCount

    ErsatzRequest(final String method, final String path, final boolean emptyResponse = false) {
        this.method = method
        this.path = path
        this.emptyResponse = emptyResponse
    }

    @Override
    String getPath() {
        path
    }

    @Override
    String getMethod() {
        method
    }

    @Override
    Request headers(final Map<String, String> heads) {
        headers.putAll(heads)
        this
    }

    @Override
    Request queries(final Map<String, List<String>> map) {
        map.each { k, v ->
            if (queryParams.containsKey(k)) {
                queryParams[k].addAll(v)
            } else {
                queryParams[k] = v
            }
        }
        this
    }

    @Override
    Request cookies(Map<String, String> cookies) {
        this.cookies.putAll(cookies)
        this
    }

    Request header(final String name, final String value) {
        headers[name] = value
        this
    }

    String getHeader(final String name) {
        headers[name]
    }

    Request contentType(final String contentType) {
        header('Content-Type', contentType)
        this
    }

    Request query(final String name, final String value) {
        queryParams.computeIfAbsent(name) { k -> [] }.add value
        this
    }

    List<String> getQuery(final String name) {
        queryParams[name].asImmutable()
    }

    Request cookie(final String name, final String value) {
        cookies[name] = value
        this
    }

    String getCookie(final String name) {
        cookies[name]
    }

    Request listener(final Consumer<Request> listener) {
        listeners.add(listener)
        this
    }

    Response responds() {
        Response response = newResponse()
        responses.add(response)
        response
    }

    Request responder(final Consumer<Response> responder) {
        Response response = newResponse()
        responder.accept(response)
        responses.add(response)
        this
    }

    Request responder(final Closure closure) {
        Response response = newResponse()
        closure.setDelegate(response)
        closure.call()

        responses.add(response)

        this
    }

    Request condition(final Function<Request, Boolean> matcher) {
        conditions.add(matcher)
        this
    }

    @SuppressWarnings('ConfusingMethodName')
    Request verifier(final Function<Integer, Boolean> verifier) {
        this.verifier = verifier
        this
    }

    boolean verify() {
        verifier.apply(callCount)
    }

    boolean matches(final HttpServerExchange exchange) {
        exchange.requestPath == path &&
            (conditions.empty || conditions.every { it.apply(this) }) &&
            matchQueryParams(exchange.queryParameters) &&
            containsHeaders(exchange.requestHeaders) &&
            containsCookies(exchange.requestCookies)
    }

    protected Response newResponse() {
        new ErsatzResponse(emptyResponse)
    }

    // header matching is not absolute - the request must contain the specified headers but not necessarily all of them
    // TODO: needs to support more complicated headers
    private boolean containsHeaders(final HeaderMap requestHeads) {
        headers.every { k, v -> v == requestHeads.getFirst(k) }
    }

    private boolean containsCookies(final Map<String, Cookie> requestCookies) {
        cookies.every { k, v -> requestCookies.containsKey(k) && v == requestCookies.get(k).value }
    }

    private boolean matchQueryParams(final Map<String, Deque<String>> requestQs) {
        boolean one = queryParams.every { k, v ->
            requestQs.containsKey(k) && requestQs.get(k).containsAll(v)
        }
        boolean two = requestQs.every { k, v ->
            queryParams.containsKey(k) && queryParams.get(k).containsAll(v)
        }
        one && two
    }

    Response getCurrentResponse() {
        int index = callCount >= responses.size() ? responses.size() - 1 : callCount
        responses.get(index)
    }

    void mark() {
        callCount++

        for (final Consumer<Request> listener : listeners) {
            listener.accept(this)
        }
    }

    @Override String toString() {
        "{ $method $path (query=${queryParams}, headers=$headers, cookies=$cookies): counted=$callCount }"
    }
}