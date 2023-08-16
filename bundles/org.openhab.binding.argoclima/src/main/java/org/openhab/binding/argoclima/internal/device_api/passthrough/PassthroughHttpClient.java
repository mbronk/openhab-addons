/**
 * Copyright (c) 2010-2023 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.argoclima.internal.device_api.passthrough;

import static org.openhab.binding.argoclima.internal.ArgoClimaBindingConstants.BINDING_ID;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletResponse;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.util.HttpCookieStore;
import org.openhab.core.io.net.http.HttpClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP client, forwarding (proxy-like) original device's request (downstream) to a remote server
 * (upstream) and passing the response through back to the device (with ability to intercept
 * content and change it - MitM)
 *
 * @author Mateusz Bronk - Initial contribution
 */
@NonNullByDefault
public class PassthroughHttpClient {
    private static final Logger logger = LoggerFactory.getLogger(PassthroughHttpClient.class);
    private HttpClient rawHttpClient;
    public final String upstreamTargetHost;
    public final int upstreamTargetPort;
    private boolean isStarted = false;
    private static final String RPC_POOL_NAME = BINDING_ID + "_apiProxy";
    private static final List<String> HEADERS_TO_IGNORE = List.of("content-length", "content-type", "content-encoding",
            "host", "accept-encoding");

    public PassthroughHttpClient(String upstreamIpAddress, int upstreamPort, HttpClientFactory clientFactory) {
        // Impl. note: Using Openhab's (globally-configurable) settings for custom threadpool. We technically may need
        // less threads (and longer TTL) here... but not fiddling with thread pool settings post-creation, to avoid
        // corner cases
        this.rawHttpClient = clientFactory.createHttpClient(RPC_POOL_NAME);

        this.rawHttpClient.setFollowRedirects(false);
        this.rawHttpClient.setUserAgentField(null); // The device doesn't set it, and we want to be a transparent proxy
        this.rawHttpClient.setCookieStore(new HttpCookieStore.Empty());

        this.rawHttpClient.setRequestBufferSize(1024);
        this.rawHttpClient.setResponseBufferSize(1024);

        this.upstreamTargetHost = upstreamIpAddress;
        this.upstreamTargetPort = upstreamPort;
    }

    public synchronized void start() throws Exception {
        if (this.isStarted) {
            stop();
        }
        logger.info("Starting passthrough http client...");
        this.rawHttpClient.start();
        logger.info("Starting passthrough http client...STARTED");
        this.rawHttpClient.getContentDecoderFactories().clear(); // Prevent decoding gzip (device doesn't support it).
                                                                 // Stops sending Accept header
        this.isStarted = true;
    }

    public synchronized void stop() throws Exception {
        this.rawHttpClient.stop();
        this.rawHttpClient.destroy();
        this.isStarted = false;
    }

    /**
     *
     * @param downstreamHttpRequest
     * @return
     * @throws IOException
     */
    public static String getRequestBodyAsString(Request downstreamHttpRequest) throws IOException {
        return downstreamHttpRequest.getReader().lines().collect(Collectors.joining(System.lineSeparator()));
    }

    public ContentResponse passthroughRequest(Request downstreamHttpRequest, String downstreamHttpRequestBody)
            throws InterruptedException, TimeoutException, ExecutionException {
        var request = this.rawHttpClient.newRequest(this.upstreamTargetHost, this.upstreamTargetPort)
                .method(downstreamHttpRequest.getMethod()).path(downstreamHttpRequest.getOriginalURI())
                .version(downstreamHttpRequest.getHttpVersion())
                .content(new StringContentProvider(downstreamHttpRequestBody));

        // re-add headers
        for (var headerName : Collections.list(downstreamHttpRequest.getHeaderNames())) {
            if (HEADERS_TO_IGNORE.stream().noneMatch(x -> x.equalsIgnoreCase(headerName))) {
                request.header(headerName, downstreamHttpRequest.getHeader(headerName));
            }
        }

        logger.info("Pass-through: DEVICE --> UPSTREAM_API: [{} {}], body=[{}]", request.getMethod(), request.getURI(),
                downstreamHttpRequestBody);

        return request.send();
    }

    public static void forwardUpstreamResponse(ContentResponse response, HttpServletResponse targetResponse,
            Optional<String> overrideBodyToReturn) throws IOException {
        targetResponse.setContentType(Objects.requireNonNullElse(response.getMediaType(), "text/html"));

        // NOTE: Argo servers send responses **without** charset, whereas Jetty's default includes it.
        // The device seems to be fine w/ it, note though it is a difference in the protocol
        // Merely setting the Encoding to null or overriding the header to MimeTypes.getContentTypeWithoutCharset(x)
        // has no-effect as Jetty overrides it at writer creation. Would require more sophisticated filtering
        // and possibly subclassing org.eclipse.jetty.server.Response to get 1:1 matching w/ remote response, so leaving
        // as-is.
        targetResponse.setCharacterEncoding(Objects.requireNonNullElse(response.getEncoding(), "ASCII"));

        for (var header : response.getHeaders()) {
            if (HEADERS_TO_IGNORE.stream().noneMatch(x -> x.equalsIgnoreCase(header.getName()))) {
                targetResponse.setHeader(header.getName(), header.getValue());
            }
        }

        String responseBodyToReturn = overrideBodyToReturn.orElse(response.getContentAsString());
        targetResponse.getWriter().write(responseBodyToReturn);
        targetResponse.setStatus(response.getStatus());
        logger.info("  [response]: DEVICE <-- UPSTREAM_API: [{} {} {} - {} bytes], body=[{}]", response.getVersion(),
                response.getStatus(), response.getReason(), response.getContent().length, responseBodyToReturn);
    }
}
