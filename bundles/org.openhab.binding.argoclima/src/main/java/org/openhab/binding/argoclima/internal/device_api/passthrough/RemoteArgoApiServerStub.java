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
package org.openhab.binding.argoclima.internal.device_api.passthrough;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.util.UrlEncoded;
import org.openhab.binding.argoclima.internal.device_api.ArgoClimaLocalDevice;
import org.openhab.binding.argoclima.internal.device_api.passthrough.responses.RemoteGetUiFlgResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Mateusz Bronk - Initial contribution
 */
@NonNullByDefault
public class RemoteArgoApiServerStub {

    private final Logger logger = LoggerFactory.getLogger(RemoteArgoApiServerStub.class);
    private final String listenIpAddress;
    private final int listenPort;
    private final String id;
    Optional<Server> server = Optional.empty();
    Optional<PassthroughHttpClient> passthroughClient = Optional.empty();
    private final Optional<ArgoClimaLocalDevice> deviceApi;

    public RemoteArgoApiServerStub(String listenIpAddress, int listenPort, String thingUid,
            Optional<PassthroughHttpClient> passthroughClient, Optional<ArgoClimaLocalDevice> deviceApi) {
        // this.listener = listener;
        // this.config = config;
        this.listenIpAddress = listenIpAddress;
        this.listenPort = listenPort;
        this.id = thingUid;
        this.passthroughClient = passthroughClient;
        this.deviceApi = deviceApi;
        // new Socket("31.14.128.210", 80); // server address
    }

    public void start() throws IOException {
        logger.info("Initializing BIN-RPC server at port {}", this.listenPort);
        // TODO: make reentrant (if started --> stop)

        try {
            startJettyServer(this.listenPort);
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
        GET_UI_ACN,
        GET_UI_NTP,
        GET_UI_FLG,
        GET_UI_UPD,
        GET_OU_FW,
        GET_UI_FW,
        POST_UI_RT,
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

        if (!request.getPathInfo().equalsIgnoreCase(REMOTE_SERVER_PATH)) {
            logger.warn("Unknown Argo device-side request path {}. Ignoring...", request.getPathInfo());
            return DeviceRequestType.UNKNOWN;
        }

        var command = request.getParameter("CM");
        if (request.getMethod().equalsIgnoreCase("GET")) {
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
        if ("UI_RT".equalsIgnoreCase(commandFromBody) && request.getMethod().equalsIgnoreCase("POST")) {
            return DeviceRequestType.POST_UI_RT; // Unknown: POST /UI/UI.php body:
                                                 // CM=UI_RT&USN=%s&PSW=%s&CPU_ID=%s&DEL=%d&DATA=
            // WiFi_Psw=UserName=Password=ServerID=TimeZone=uisetup.ddns.net | www.termauno.com | 95.254.67.59
        }

        logger.warn("Unknown command: CM(query)=[{}], CM(body)=[{}]", command, commandFromBody);
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

            var body = PassthroughHttpClient.getRequestBodyAsString(baseRequest);
            var requestType = detectRequestType(request, body);

            switch (requestType) {
                case GET_UI_FLG:
                    var updateDto = DeviceSideUpdateDTO.fromDeviceRequest(request);
                    logger.debug("Got device-side update: {}", updateDto);
                    // Use for new update
                    deviceApi.ifPresent(x -> x.updateDeviceStateFromPushRequest(updateDto.currentValues,
                            updateDto.deviceIp, updateDto.cpuId));
                    break;
                case POST_UI_RT:
                    var postRtDto = DeviceSidePostRtUpdateDTO.fromDeviceRequestBody(body);
                    logger.info("Got device-side POST: {}", postRtDto);
                    break;
                case GET_UI_NTP:
                case UNKNOWN:
                default:
                    break;
            }

            if (passthroughClient.isPresent()) {

                try {
                    var upstreamResponse = passthroughClient.get().passthroughRequest(baseRequest, body);
                    // logger.info("Remote server said: {}\n\t{}", upstreamResponse,
                    // upstreamResponse.getContentAsString());
                    // logger.info("XXXXXXX BEFORE: {}", upstreamResponse.getContentAsString());
                    var overridenBody = postProcessUpstreamResponse(requestType, upstreamResponse);
                    // logger.info("XXXXXXX AFTER: {}", overridenBody);

                    // TODO restore
                    PassthroughHttpClient.forwardUpstreamResponse(upstreamResponse, response,
                            Optional.of(overridenBody));
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
