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
import java.net.InetAddress;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.util.UrlEncoded;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.openhab.binding.argoclima.internal.device_api.ArgoClimaLocalDevice;
import org.openhab.binding.argoclima.internal.device_api.passthrough.requests.DeviceSidePostRtUpdateDTO;
import org.openhab.binding.argoclima.internal.device_api.passthrough.requests.DeviceSideUpdateDTO;
import org.openhab.binding.argoclima.internal.device_api.passthrough.responses.RemoteGetUiFlgResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Mateusz Bronk - Initial contribution
 */
@NonNullByDefault
public class RemoteArgoApiServerStub {

    private final Logger logger = LoggerFactory.getLogger(RemoteArgoApiServerStub.class);
    private final Set<InetAddress> listenIpAddresses;
    private final int listenPort;
    private final String id;
    private final boolean showCleartextPasswords;
    Optional<Server> server = Optional.empty();
    Optional<PassthroughHttpClient> passthroughClient = Optional.empty();
    private final Optional<ArgoClimaLocalDevice> deviceApi;
    private static final String RPC_POOL_NAME = "OH-jetty-" + BINDING_ID + "_serverStub";

    public RemoteArgoApiServerStub(Set<InetAddress> listenIpAddresses, int listenPort, String thingUid,
            Optional<PassthroughHttpClient> passthroughClient, Optional<ArgoClimaLocalDevice> deviceApi,
            boolean showCleartextPasswords) {
        this.listenIpAddresses = listenIpAddresses;
        this.listenPort = listenPort;
        this.id = thingUid;
        this.passthroughClient = passthroughClient;
        this.deviceApi = deviceApi;
        this.showCleartextPasswords = showCleartextPasswords;
    }

    public synchronized void start() {
        logger.info("[{}] Initializing Argo API Stub server at: {}", this.id, this.listenIpAddresses.stream()
                .map(x -> String.format("%s:%s", x.toString(), this.listenPort)).collect(Collectors.joining(",")));

        try {
            startJettyServer(this.listenPort);
        } catch (Exception e) {
            throw new RuntimeException(
                    String.format("Starting stub server at port %d failed. %s", this.listenPort, e.getMessage()), e);
        }

        if (this.passthroughClient.isPresent()) {
            try {
                this.passthroughClient.get().start();
            } catch (Exception e) {
                throw new RuntimeException(
                        String.format("Starting passthrough API client for host=%s, port=%d failed. %s",
                                this.passthroughClient.get().upstreamTargetHost,
                                this.passthroughClient.get().upstreamTargetPort, e.getMessage()),
                        e);
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
        if (this.server.isPresent()) {
            stopJettyServer();
        }

        var server = new Server();
        var connectors = this.listenIpAddresses.stream().map(addr -> {
            var connector = new ServerConnector(server);
            connector.setHost(addr.getHostName());
            connector.setPort(port);
            return connector;
        }).toArray(Connector[]::new);
        server.setConnectors(connectors);

        this.server = Optional.of(server);

        var tp = server.getThreadPool();
        if (tp instanceof QueuedThreadPool) {
            ((QueuedThreadPool) tp).setName(RPC_POOL_NAME);
            ((QueuedThreadPool) tp).setDaemon(true); // Lower our priority (just in case)
        }

        server.setHandler(new ArgoDeviceRequestHandler(this.showCleartextPasswords));
        server.start();
    }

    private synchronized void stopJettyServer() {
        if (!this.server.isPresent()) {
            return; // nothing to do
        }

        var s = this.server.get();
        if (!s.isStopped()) {
            try {
                s.stop();
            } catch (Exception e) {
                throw new RuntimeException(String.format("Stopping Jetty server (listening on port %d) failed. %s",
                        this.listenPort, e.getMessage()), e);
            }
        }
        s.destroy();

        this.server = Optional.empty();
    }

    public enum DeviceRequestType {
        /** Purpose unknown */
        GET_UI_ACN,

        /** Get current time from server */
        GET_UI_NTP,

        /** Submit current status (in GET param) and get latest command from remote-side */
        GET_UI_FLG,

        /** Use case not known */
        GET_UI_UPD,

        /** Unit Wifi firmware update request */
        GET_OU_FW,

        /** Unit UI FW update request */
        GET_UI_FW,

        /** Confirm server-side request is fulfilled, respond with extended status */
        POST_UI_RT,

        /** Unrecognized command type */
        UNKNOWN
    }

    // public class ArgoDeviceRequest {
    // private DeviceRequestType requestType;
    //
    // private ArgoDeviceRequest(HttpServletRequest request) {
    // requestType = DeviceRequestType.UNKNOWN;
    // }
    // }

    public static final String REMOTE_SERVER_PATH = "/UI/UI.php";

    public DeviceRequestType detectRequestType(HttpServletRequest request, String requestBody) {
        logger.info("Incoming request: {} {}://{}:{}{}?{}", request.getMethod(), request.getScheme(),
                request.getLocalAddr(), request.getLocalPort(), request.getPathInfo(), request.getQueryString());

        // if (!request.getPathInfo().equalsIgnoreCase(REMOTE_SERVER_PATH)) {
        if (!REMOTE_SERVER_PATH.equalsIgnoreCase(request.getPathInfo())) {
            logger.warn("Unknown Argo device-side request path {}. Ignoring...", request.getPathInfo());
            return DeviceRequestType.UNKNOWN;
        }

        var command = request.getParameter("CM");
        if ("GET".equalsIgnoreCase(request.getMethod())) {
            if ("UI_NTP".equalsIgnoreCase(command)) {
                return DeviceRequestType.GET_UI_NTP; // Get time: GET /UI/UI.php?CM=UI_NTP (
            }
            if ("UI_FLG".equalsIgnoreCase(command)) {
                return DeviceRequestType.GET_UI_FLG; // Param update: GET
                                                     // /UI/UI.php?CM=UI_FLG?USN=%s&PSW=%s&IP=%s&FW_OU=_svn.%s&FW_UI=_svn.%s&CPU_ID=%s&HMI=%s&TZ=%s&SETUP=%s&SERVER_ID=%s
            }
            if ("UI_UPD".equalsIgnoreCase(command)) {
                return DeviceRequestType.GET_UI_UPD; // Unknown: GET /UI/UI.php?CM=UI_UPD?USN=%s&PSW=%s&CPU_ID=%s
            }
            if ("OU_FW".equalsIgnoreCase(command)) {
                return DeviceRequestType.GET_OU_FW;
            }
            if ("UI_FW".equalsIgnoreCase(command)) {
                return DeviceRequestType.GET_UI_FW; // Unit FW update request GET
                                                    // /UI/UI.php?CM=UI_FW&PK=%d&USN=%s&PSW=%s&CPU_ID=%s
            }
            if ("UI_ACN".equalsIgnoreCase(command)) {
                return DeviceRequestType.GET_UI_ACN; // Unknown: GET /UI/UI.php?CM=UI_ACN&USN=%s&PSW=%s&CPU_ID=%s
                                                     // (AT+CIPSERVER=0?)
            }
        }

        var commandFromBody = new UrlEncoded(requestBody).getString("CM");

        // URLDecoder.
        // var commandPost =
        // TBD
        if ("UI_RT".equalsIgnoreCase(commandFromBody) && "POST".equalsIgnoreCase(request.getMethod())) {
            return DeviceRequestType.POST_UI_RT; // Unknown: POST /UI/UI.php body:
                                                 // CM=UI_RT&USN=%s&PSW=%s&CPU_ID=%s&DEL=%d&DATA=
            // WiFi_Psw=UserName=Password=ServerID=TimeZone=uisetup.ddns.net | www.termauno.com | 95.254.67.59
        }

        logger.warn("Unknown command: CM(query)=[{}], CM(body)=[{}]", command, commandFromBody);
        return DeviceRequestType.UNKNOWN;
    }

    public class ArgoDeviceRequestHandler extends AbstractHandler {

        private final boolean showCleartextPasswords;

        public ArgoDeviceRequestHandler(boolean showCleartextPasswords) {
            this.showCleartextPasswords = showCleartextPasswords;
        }

        @Override
        public void handle(@Nullable String target, @Nullable Request baseRequest, @Nullable HttpServletRequest request,
                @Nullable HttpServletResponse response) throws IOException, ServletException {
            Objects.requireNonNull(target);
            Objects.requireNonNull(baseRequest);
            Objects.requireNonNull(request);
            Objects.requireNonNull(response);

            var body = PassthroughHttpClient.getRequestBodyAsString(baseRequest);
            var requestType = detectRequestType(request, body);

            switch (requestType) {
                case GET_UI_FLG:
                    var updateDto = DeviceSideUpdateDTO.fromDeviceRequest(request, this.showCleartextPasswords);
                    logger.debug("Got device-side update: {}", updateDto);
                    // Use for new update
                    deviceApi.ifPresent(x -> x.updateDeviceStateFromPushRequest(updateDto));
                    break;
                case POST_UI_RT:
                    var postRtDto = DeviceSidePostRtUpdateDTO.fromDeviceRequestBody(body);
                    logger.info("Got device-side POST: {}", postRtDto);
                    // Use for new update
                    deviceApi.ifPresent(x -> x.updateDeviceStateFromPostRtRequest(postRtDto));
                    break;
                case GET_UI_NTP:
                case UNKNOWN:
                default:
                    break;
            }

            if (passthroughClient.isPresent()) {

                try {
                    // CONSIDER: only pass-through known requests (vs. everything?)
                    var upstreamResponse = passthroughClient.get().passthroughRequest(baseRequest, body);
                    var overridenBody = postProcessUpstreamResponse(requestType, upstreamResponse);

                    PassthroughHttpClient.forwardUpstreamResponse(upstreamResponse, response,
                            Optional.of(overridenBody));
                    baseRequest.setHandled(true);
                    return;
                } catch (InterruptedException | TimeoutException | ExecutionException e) {
                    // Deliberately not handling the upstream request exception here and allowing to fall-through to a
                    // "response faking" logic
                    logger.warn("Passthrough client fail: {}", e.getMessage());
                }

            }

            response.setContentType("text/html");
            response.setCharacterEncoding("ASCII");
            response.setHeader("Server", "Microsoft-IIS/8.5"); // overrides Jetty's default (can be disabled by
                                                               // setSendServerVersion(false))
            response.setHeader("Content-type", "text/html");
            response.setHeader("X-Powered-By", "PHP/5.4.11");
            response.setHeader("Access-Control-Allow-Origin", "*");
            response.setStatus(HttpServletResponse.SC_OK);
            baseRequest.setHandled(true);

            if (baseRequest.getOriginalURI().contains("UI_NTP")) {
                response.getWriter().println(getNtpResponse(Instant.now()));
            } else {
                response.getWriter().println(getFakeResponse());
            }
        }

        // private void handleDeviceSideUpdate(Map<String, String[]> parameterMap) {
        // Map<String, String> flattenedParams = parameterMap.entrySet().stream().collect(
        // Collectors.toMap(Map.Entry::getKey, x -> (x.getValue().length < 1) ? "" : x.getValue()[0]));
        // var update = new DeviceSideUpdateDTO(flattenedParams);
        //
        // // logger.info("Got device-side update: {}", update);
        // // Use for new update
        // deviceApi.ifPresent(
        // x -> x.updateDeviceStateFromPushRequest(update.currentValues, update.deviceIp, update.cpuId));
        // }
    }

    private String postProcessUpstreamResponse(DeviceRequestType requestType, ContentResponse upstreamResponse) {
        var bodyToReturn = upstreamResponse.getContentAsString();

        if (upstreamResponse.getStatus() != 200) {
            logger.warn("Remote server response for {} command was HTTP {}. Not parsing further", requestType,
                    upstreamResponse.getStatus());
            return bodyToReturn;
        }

        switch (requestType) {
            case GET_UI_FLG:
                var responseDto = RemoteGetUiFlgResponseDTO.fromResponseString(upstreamResponse.getContentAsString());
                // TODO: parse & process
                // responseDto.preamble.Flag_0_Request_POST_UI_RT = 1;
                // responseDto.preamble.Flag_5_Has_New_Update = 1;
                return responseDto.toResponseString();
            // break;
            case POST_UI_RT:
            case GET_UI_NTP:
            case UNKNOWN:
            default:
                break;
        }

        return bodyToReturn;
    }

    public synchronized void shutdown() {
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
