/**
 * Copyright (c) 2010-2022 Contributors to the openHAB project
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
package org.openhab.binding.argoclima.internal.device_api;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author bronk
 *
 */
@NonNullByDefault
public class RemoteArgoApiServerStub {

    private final Logger logger = LoggerFactory.getLogger(RemoteArgoApiServerStub.class);
    private static final String RPC_POOL_NAME = "argoclimaRpc";
    private final String ipAddress;
    private final int port;
    private final String id;
    Optional<Server> server = Optional.empty();
    Optional<PassthroughHttpClient> passthroughClient = Optional.empty();
    private final Optional<ArgoClimaLocalDevice> deviceApi;

    public RemoteArgoApiServerStub(String ipAddress, int port, String thingUid,
            Optional<PassthroughHttpClient> passthroughClient, Optional<ArgoClimaLocalDevice> deviceApi) {
        // this.listener = listener;
        // this.config = config;
        this.ipAddress = ipAddress;
        this.port = port;
        this.id = thingUid;
        this.passthroughClient = passthroughClient;
        this.deviceApi = deviceApi;
        // new Socket("31.14.128.210", 80); // server address
    }

    public void start() throws IOException {
        logger.info("Initializing BIN-RPC server at port {}", this.port);
        // TODO: make reentrant (if started --> stop)

        try {
            startJettyServer(this.port);
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        if (this.passthroughClient.isPresent()) {
            try {
                this.passthroughClient.get().start();
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    private String getNtpResponse(Instant time) {

        DateTimeFormatter fmt = DateTimeFormatter
                .ofPattern("'NTP 'yyyy-MM-dd'T'HH:mm:ssxxx' UI SERVER (M.A.V. srl)'", Locale.ENGLISH)
                .withZone(ZoneId.of("GMT"));
        return fmt.format(time);
    }

    private String getFakeResponse() {
        return "{|0|0|1|0|0|0|N,N,N,N,N,N,N,N,N,N,N,N,3,N,N,N,N,N,1,2,1360,N,0,NaN,N,N,N,N,N,N,N,N,N,N,N,N|}[|0|||]ACN_FREE <br>\t\t";
    }

    private void startJettyServer(int port) throws Exception {
        server = Optional.of(new Server(port));

        // Disable sending built-in "Server" header
        // Stream.of(server.getConnectors()).flatMap(connector -> connector.getConnectionFactories().stream())
        // .filter(connFactory -> connFactory instanceof HttpConnectionFactory)
        // .forEach(httpConnFactory -> ((HttpConnectionFactory) httpConnFactory).getHttpConfiguration()
        // .setSendServerVersion(false));
        server.get().setHandler(new ArgoDeviceRequestHandler());
        server.get().start(); // todo-threading

        // server.join();
        //
    }

    public enum DeviceRequestType {
        GET_UI_NTP,
        GET_UI_FLG,
        POST_STH,
        UNKNOWN
    }

    public class DeviceSideUpdate {
        public final String command;
        public final String username;
        public final String passwordHash;
        public final String deviceIp;
        public final String unitFirmware;
        public final String wifiFirmware;
        public final String cpuId;
        public final String currentValues;
        public final String timezoneId;
        public final String setup;
        public final String remoteServerId;

        public DeviceSideUpdate(Map<String, String> parameterMap) {
            this.command = Objects.requireNonNullElse(parameterMap.get("CM"), "");
            this.username = Objects.requireNonNullElse(parameterMap.get("USN"), "");
            this.passwordHash = Objects.requireNonNullElse(parameterMap.get("PSW"), "");
            this.deviceIp = Objects.requireNonNullElse(parameterMap.get("IP"), "");
            this.unitFirmware = Objects.requireNonNullElse(parameterMap.get("FW_OU"), "");
            this.wifiFirmware = Objects.requireNonNullElse(parameterMap.get("FW_UI"), "");
            this.cpuId = Objects.requireNonNullElse(parameterMap.get("CPU_ID"), "");
            this.currentValues = Objects.requireNonNullElse(parameterMap.get("HMI"), "");
            this.timezoneId = Objects.requireNonNullElse(parameterMap.get("TZ"), "");
            this.setup = Objects.requireNonNullElse(parameterMap.get("SETUP"), "");
            this.remoteServerId = Objects.requireNonNullElse(parameterMap.get("SERVER_ID"), "");
        }

        @Override
        public String toString() {
            return String.format(
                    "Device-side update:\n\tCommand=%s,\n\tUser:password=%s:%s,\n\tIP=%s,\n\tFW=[Unit=%s | Wifi=%s],\n\tCPU_ID=%s,\n\tParameters=%s,\n\tRemoteServer=%s.",
                    this.command, this.username, this.passwordHash, this.deviceIp, this.unitFirmware, this.wifiFirmware,
                    this.cpuId, this.currentValues, this.remoteServerId);
        }
    }

    // public class ArgoDeviceRequest {
    // private DeviceRequestType requestType;
    //
    // private ArgoDeviceRequest(HttpServletRequest request) {
    // requestType = DeviceRequestType.UNKNOWN;
    // }
    // }

    public static final String REMOTE_SERVER_PATH = "/UI/UI.php";

    public DeviceRequestType detectRequestType(HttpServletRequest request) {
        logger.info("Incoming request: {} {}://{}:{}{}?{}", request.getMethod(), request.getScheme(),
                request.getLocalAddr(), request.getLocalPort(), request.getPathInfo(), request.getQueryString());

        if (!request.getPathInfo().equalsIgnoreCase(REMOTE_SERVER_PATH)) {
            logger.warn("Unknown Argo device-side request path {}. Ignoring...", request.getPathInfo());
            return DeviceRequestType.UNKNOWN;
        }

        var command = request.getParameter("CM");
        if ("UI_NTP".equalsIgnoreCase(command) && request.getMethod().equalsIgnoreCase("GET")) {
            return DeviceRequestType.GET_UI_NTP;
        }
        if ("UI_FLG".equalsIgnoreCase(command) && request.getMethod().equalsIgnoreCase("GET")) {
            return DeviceRequestType.GET_UI_FLG;
        }
        // TBD
        if ("???".equalsIgnoreCase(command) && request.getMethod().equalsIgnoreCase("POST")) {
            return DeviceRequestType.POST_STH;
        }
        return DeviceRequestType.UNKNOWN;
    }

    public class ArgoDeviceRequestHandler extends AbstractHandler {

        @Override
        public void handle(@Nullable String target, @Nullable Request baseRequest, @Nullable HttpServletRequest request,
                @Nullable HttpServletResponse response) throws IOException, ServletException {
            Objects.requireNonNull(target);
            Objects.requireNonNull(baseRequest);
            Objects.requireNonNull(request);
            Objects.requireNonNull(response);

            var requestType = detectRequestType(request);
            if (requestType == DeviceRequestType.GET_UI_FLG) {
                handleDeviceSideUpdate(request.getParameterMap());
            }

            logger.info("Received request: {} - {}", target, baseRequest);
            var body = PassthroughHttpClient.getRequestBodyAsString(baseRequest);
            // logger.info("Request body: {}", body);
            // logger.info("Request 2: {}", request);
            //

            if (passthroughClient.isPresent()) {

                try {
                    var upstreamResponse = passthroughClient.get().passthroughRequest(baseRequest, body);
                    // logger.info("Remote server said: {}\n\t{}", upstreamResponse,
                    // upstreamResponse.getContentAsString());

                    // TODO restore
                    PassthroughHttpClient.forwardUpstreamResponse(upstreamResponse, response);
                    baseRequest.setHandled(true);
                    return;
                } catch (InterruptedException | TimeoutException | ExecutionException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                    // FAILED upstream request, fallback to faking response
                }

            }

            // TODO Auto-generated method stub
            response.setContentType("text/html");
            response.setCharacterEncoding("ASCII");
            response.setHeader("Server", "Microsoft-IIS/8.5"); // overrides Jetty's default (can be disabled by
                                                               // setSendServerVersion(false))
            response.setHeader("Content-type", "text/html");
            response.setHeader("X-Powered-By", "PHP/5.4.11");
            response.setHeader("Access-Control-Allow-Origin", "*");
            response.setStatus(HttpServletResponse.SC_OK);
            baseRequest.setHandled(true);

            boolean passthroughToRemote = true;

            if (baseRequest.getOriginalURI().contains("UI_NTP")) {
                response.getWriter().println(getNtpResponse(Instant.now()));
            } else {
                response.getWriter().println(getFakeResponse());
            }
            //
            // TrivialHttpRequest response = new TrivialHttpRequest("HTTP/1.1 200 OK");
            // response.setHeader("Content-Type", "text/html");
            // response.setHeader("Server", "Microsoft-IIS/8.5");
            // response.setHeader("X-Powered-By", "PHP/5.4.11");
            // response.setHeader("Access-Control-Allow-Origin", "*");
            // response.setHeader("Date", fmt.format(Instant.now())); // TODO
            // response.setHeader("Content-length", "" + contentToSend.length());
            // response.setBody(contentToSend.toCharArray());
        }

        private void handleDeviceSideUpdate(Map<String, String[]> parameterMap) {
            Map<String, String> flattenedParams = parameterMap.entrySet().stream().collect(
                    Collectors.toMap(Map.Entry::getKey, x -> (x.getValue().length < 1) ? "" : x.getValue()[0]));
            var update = new DeviceSideUpdate(flattenedParams);

            logger.info("Got device-side update: {}", update);
            // Use for new update
            deviceApi.ifPresent(
                    x -> x.updateDeviceStateFromPushRequest(update.currentValues, update.deviceIp, update.cpuId));
        }
    }

    public void shutdown() {
        if (server.isPresent()) {
            try {
                server.get().stop();
                server = Optional.empty();
            } catch (Exception e) {
                logger.warn("Unable to stop Remote Argo API Server Stub. Error: {}", e.getMessage());
            }
        }

        if (this.passthroughClient.isPresent()) {
            try {
                passthroughClient.get().stop();
                passthroughClient = Optional.empty();
            } catch (Exception e) {
                logger.warn("Unable to stop Remote Argo API Passthrough HTTP client. Error: {}", e.getMessage());
            }
        }
    }
}
