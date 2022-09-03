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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.openhab.core.common.ThreadPoolManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author bronk
 *
 */
@NonNullByDefault
public class RemoteArgoApiServerStub {

    private final Logger logger = LoggerFactory.getLogger(RemoteArgoApiServerStub.class);

    private @Nullable Thread networkServiceThread;
    private @Nullable ArgoStubServerThread networkService;
    // private final HomematicConfig config;
    // private final RpcEventListener listener;
    private final String ipAddress;
    private final int port;
    private final String id;
    @Nullable
    Server server = null;

    public RemoteArgoApiServerStub(String ipAddress, int port, String thingUid) {
        // this.listener = listener;
        // this.config = config;
        this.ipAddress = ipAddress;
        this.port = port;
        this.id = thingUid;
    }

    public void start() throws IOException {
        logger.info("Initializing BIN-RPC server at port {}", this.port);

        try {
            startJettyServer(this.port);
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        // networkService = new ArgoStubServerThread(this.ipAddress, this.port, this.id);
        // networkServiceThread = new Thread(networkService);
        // networkServiceThread.setName("OH-binding-argoclima:" + id + "-rpcServer");
        // networkServiceThread.start();
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
        server = new Server(port);

        // Disable sending built-in "Server" header
        // Stream.of(server.getConnectors()).flatMap(connector -> connector.getConnectionFactories().stream())
        // .filter(connFactory -> connFactory instanceof HttpConnectionFactory)
        // .forEach(httpConnFactory -> ((HttpConnectionFactory) httpConnFactory).getHttpConfiguration()
        // .setSendServerVersion(false));
        server.setHandler(new RequestHandler());
        server.start(); // todo-threading
        // server.join();
    }

    public class RequestHandler extends AbstractHandler {

        @Override
        public void handle(@Nullable String target, @Nullable Request baseRequest, @Nullable HttpServletRequest request,
                @Nullable HttpServletResponse response) throws IOException, ServletException {
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
            logger.info("Received request: {} - {}", target, baseRequest);
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
    }

    public void shutdown() {
        if (networkService != null) {
            logger.debug("Stopping BIN-RPC server");
            try {
                if (networkServiceThread != null) {
                    networkServiceThread.interrupt();
                }
            } catch (Exception e) {
                logger.error("{}", e.getMessage(), e);
            }
            networkService.shutdown();
            networkService = null;
        }
        if (server != null) {
            try {
                server.stop();
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    private enum MessageType {
        HTTP_REQUEST,
        HTTP_RESPONSE,
        UNKNOWN
    };

    private class ArgoStubServerThread implements Runnable {
        private final Logger logger = LoggerFactory.getLogger(RemoteArgoApiServerStub.class);

        // Variables for TCP connection.
        private String ipAddress;
        private int tcpPort;
        private int connectionTimeout;
        private String thingUid;
        private @Nullable Socket tcpSocket = null;
        private @Nullable OutputStreamWriter tcpOutput = null;
        private @Nullable BufferedReader tcpInput = null;
        private ServerSocket serverSocket;
        private boolean doAccept = true;
        private static final String RPC_POOL_NAME = "argoclimaRpc";

        public ArgoStubServerThread(String ipAddress, int port, String thingUid) throws IOException {
            this.ipAddress = ipAddress;
            this.tcpPort = port;
            this.thingUid = thingUid;

            logger.debug("openConnection(): Creating fake server");
            serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(InetAddress.getLocalHost(), tcpPort), 10);
        }

        @Override
        public void run() {
            // TODO Auto-generated method stub
            while (doAccept) {
                try {
                    Socket cs = serverSocket.accept();
                    Socket relay = new Socket("31.14.128.210", 80); // server address
                    BinRpcResponseHandler rpcHandler = new BinRpcResponseHandler(cs, Optional.of(relay));
                    ThreadPoolManager.getPool(RPC_POOL_NAME).execute(rpcHandler);

                    //
                    // new ProxyThread("THIS->REMOTE", relay.getInputStream(), cs.getOutputStream()).start();
                    // new ProxyThread("AC->THIS", cs.getInputStream(), relay.getOutputStream()).start();
                    //
                } catch (IOException ex) {
                    // ignore
                    logger.warn("openConnection(): I/O exception: {} ", ex.getMessage());
                }
            }
        }

        public void shutdown() {
            doAccept = false;
            try {
                serverSocket.close();
            } catch (IOException ioe) {
                // ignore
            }
        }

        private class ProxyThread extends Thread {
            private final Logger logger = LoggerFactory.getLogger(ProxyThread.class);
            private BufferedReader inputStream;
            private DataInputStream inputStream1;
            private DataOutputStream outputStream1;
            private OutputStreamWriter outputStream;
            private String name;

            ProxyThread(String name, InputStream inputStream, OutputStream outputStream)
                    throws UnsupportedEncodingException {
                this.name = name;
                this.inputStream = new BufferedReader(new InputStreamReader(inputStream));
                this.inputStream1 = new DataInputStream(inputStream);
                this.outputStream1 = new DataOutputStream(outputStream);
                this.outputStream = new OutputStreamWriter(outputStream, "US-ASCII");

            }
            //
            // @Override
            // public void run() {
            // try {
            // while (true) {
            //
            // byte[] bytes = inputStream.readAllBytes();
            // if (bytes.length == 0) {
            // Thread.sleep(100);
            // continue;
            // }
            // String requestStr = new String(bytes);
            // logger.info("[{}] Read bytes: {}", name, requestStr);
            // outputStream.write(bytes);
            // outputStream.write(0);
            // // outputStream.write(requestStr);
            // outputStream.flush();
            // Thread.sleep(100);
            // }
            // } catch (IOException e) {
            // // TODO Auto-generated catch block
            // e.printStackTrace();
            // } catch (InterruptedException e) {
            // // TODO Auto-generated catch block
            // e.printStackTrace();
            // }
            //
            // }

            @Override
            public void run() {
                int contentLength = -1;

                try {
                    while (true) {
                        String httpHeader;
                        while (true) {
                            // int firstChar;
                            // firstChar = inputStream.read();
                            //
                            // if (firstChar == -1) {
                            // break;
                            // } else if (firstChar != 'G') {
                            // logger.info("First char: {}", new String(new char[] { (char) firstChar }));
                            // outputStream.write(firstChar);
                            // outputStream.flush();
                            // continue;
                            // }

                            httpHeader = inputStream.readLine();
                            if (httpHeader == null) {
                                break;
                            }
                            if (httpHeader.isEmpty()) {
                                outputStream.write("\r\n");
                                break;
                            }
                            // httpHeader = "G" + httpHeader;
                            // TODO: case-sensitive
                            if (httpHeader.startsWith("Content-Length:")) {
                                String cl = httpHeader.substring("Content-Length:".length()).trim();
                                contentLength = Integer.parseInt(cl);
                            }
                            logger.info("[{}] Read header: {}", name, httpHeader);

                            outputStream.write(httpHeader + "\r\n");
                            outputStream.flush();
                        }
                        if (contentLength != -1) {
                            char[] buf = new char[contentLength];
                            int bytesRead = inputStream.read(buf);
                            if (bytesRead > -1) {
                                logger.info("[{}] Read body: {}", name, new String(buf));
                                outputStream.write(buf);
                            }
                            // outputStream.flush();
                            // char[] temp = new char[3024];
                            // int i = 0;
                            // int extraByte;
                            // while ((extraByte = inputStream.read()) != -1) {
                            // logger.info("{} Read extra byte from stream: {}", name, extraByte);
                            // temp[i] = (char) extraByte;
                            // outputStream.write(extraByte);
                            // }
                            // logger.info("{} Read extra bytes from stream: {}", name, new String(temp));
                        }
                        outputStream.flush();
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                            break;
                        }
                        // outputStream.flush();
                    }
                } catch (IOException e) {
                    // outputStream.flush();
                    e.printStackTrace();
                }
            }
        }

        private class BinRpcResponseHandler implements Runnable {
            private final Logger logger = LoggerFactory.getLogger(BinRpcResponseHandler.class);

            private Socket socket;
            private Optional<Socket> proxySocket;
            // private RpcResponseHandler<byte[]> rpcResponseHandler;
            // private HomematicConfig config;
            private long created;
            private int socketMaxAliveSeconds = 3; // TODO config

            public BinRpcResponseHandler(Socket socket, Optional<Socket> proxySocket) {
                this.socket = socket;
                this.created = System.currentTimeMillis();
                this.proxySocket = proxySocket;
            }

            /**
             * Reads the event from the Homematic gateway and handles the method call.
             */
            @Override
            public void run() {
                try {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    BufferedWriter writer = new BufferedWriter(
                            new OutputStreamWriter(socket.getOutputStream(), "US-ASCII"));

                    BufferedReader proxyReader = null;
                    BufferedWriter proxyWriter = null;
                    if (this.proxySocket.isPresent()) {
                        proxyReader = new BufferedReader(
                                new InputStreamReader(this.proxySocket.get().getInputStream()));
                        proxyWriter = new BufferedWriter(
                                new OutputStreamWriter(this.proxySocket.get().getOutputStream(), "US-ASCII"));
                    }

                    boolean isMaxAliveReached;
                    do {
                        handleIncomingMessage(reader, writer, proxyWriter, proxyReader);
                        Thread.sleep(500);
                        // socket.close();
                        // if (returnValue != null) {
                        // socket.getOutputStream().write(returnValue);
                        // }
                        isMaxAliveReached = System.currentTimeMillis() - created > (socketMaxAliveSeconds * 1000);
                    } while (!isMaxAliveReached);

                } catch (EOFException eof) {
                    // ignore (close?)
                } catch (Exception e) {
                    logger.warn("{}", e.getMessage(), e);
                } finally {
                    try {
                        socket.close();
                        if (this.proxySocket.isPresent()) {
                            this.proxySocket.get().close();
                            // this.proxySocket = Optional.empty();
                        }
                    } catch (IOException ioe) {
                        // ignore
                    }
                }
            }

            private void sendFakeBody(BufferedWriter out, String contentToSend) {
                DateTimeFormatter fmt = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH)
                        .withZone(ZoneId.of("GMT"));

                try {
                    // org.apache.commons.httpclient
                    // org.eclipse.jetty.client.api.Response.
                    TrivialHttpRequest response = new TrivialHttpRequest("HTTP/1.1 200 OK");
                    response.setHeader("Content-Type", "text/html");
                    response.setHeader("Server", "Microsoft-IIS/8.5");
                    response.setHeader("X-Powered-By", "PHP/5.4.11");
                    response.setHeader("Access-Control-Allow-Origin", "*");
                    response.setHeader("Date", fmt.format(Instant.now())); // TODO
                    response.setHeader("Content-length", "" + contentToSend.length());
                    response.setBody(contentToSend.toCharArray());

                    logger.info("Sending response: {}", response);
                    response.writeTo(out);
                    out.flush();

                    // out.write("HTTP/1.1 200 OK\r\n");
                    // out.write("Content-Type: text/html\r\n");
                    // out.write("Server: Microsoft-IIS/8.5\r\n");
                    // out.write("X-Powered-By: PHP/5.4.11\r\n");
                    // out.write("Access-Control-Allow-Origin: *\r\n");
                    // out.write("Date: Sun, 28 Aug 2022 08:51:05 GMT\r\n"); // TODO
                    // out.write(String.format("Content-length: %d\r\n", contentToSend.length())); // TODO
                    // out.write("\r\n");
                    // out.write(contentToSend);
                    // out.flush();
                } catch (SocketException e) {
                    // ignore
                } catch (IOException e1) {
                    // TODO Auto-generated catch block
                    e1.printStackTrace();
                }
            }

            private class TrivialHttpRequest {
                private String requestLine = ""; // requestLine if this is a request, statusLine if this is a response
                private Map<String, String> headers;
                private char[] messageBody;

                MessageType type;

                public TrivialHttpRequest(String firstLine) {
                    this.requestLine = firstLine;
                    this.headers = new HashMap<>();
                    this.messageBody = new char[0];
                    if (this.requestLine.endsWith("HTTP/1.1")) {
                        this.type = MessageType.HTTP_REQUEST;
                    } else if (this.requestLine.startsWith("HTTP/1.1")) {
                        this.type = MessageType.HTTP_RESPONSE;
                    } else {
                        this.type = MessageType.UNKNOWN;
                    }
                }

                public void writeTo(BufferedWriter out) throws IOException {
                    out.write(this.requestLine + "\r\n");
                    StringBuilder sb = new StringBuilder();
                    this.headers.forEach((k, v) -> sb.append(String.format("%s: %s\r\n", k.trim(), v.trim())));
                    out.write(sb.toString());
                    out.write("\r\n");
                    out.write(messageBody);
                    // TODO Auto-generated method stub
                }

                public void setHeader(String name, String value) {
                    this.headers.put(name, value);
                }

                public void setBody(char[] value) {
                    this.messageBody = value;
                }

                @Override
                public String toString() {
                    return String.format("[%s] %s\n%s\n\n%s", this.type, this.requestLine, this.headers.toString(),
                            this.getBodyString());
                }

                public Optional<String> getHeader(String name) {
                    return this.headers.entrySet().stream().filter(x -> x.getKey().equalsIgnoreCase(name))
                            .map(x -> x.getValue().trim()).findFirst();
                }

                public int getContentLength() {
                    return Integer.parseInt(this.getHeader("Content-length").orElse("0"));
                }

                public String getRequestLine() {
                    return this.requestLine;
                }

                public String getBodyString() {
                    return new String(this.messageBody);
                }

            }

            private @Nullable TrivialHttpRequest parseIncomingHttpMessage(BufferedReader in) throws IOException {
                String line;
                while ((line = in.readLine()) != null) {
                    if (!line.isEmpty()) {
                        break;
                    }
                }
                if (line == null) {
                    return null; // no request
                }
                // todo handle status line

                TrivialHttpRequest request = new TrivialHttpRequest(line);
                while ((line = in.readLine()) != null) {
                    if (line.isEmpty()) {
                        break;
                    }
                    // logger.info("Incoming header: {}", line);
                    String[] headerComponents = line.split(":", 2); // TODO assertions
                    request.setHeader(headerComponents[0], headerComponents[1]);
                }

                if (request.getContentLength() > 0) {
                    int contentLength = request.getContentLength();
                    if (contentLength > 0) { // TODO: sensible max
                        char[] buf = new char[contentLength];
                        int bytesRead = in.read(buf);
                        if (bytesRead > -1) {
                            request.setBody(buf);
                        }
                    }
                }
                return request;
            }

            private String getNtpResponse(Instant time) {

                DateTimeFormatter fmt = DateTimeFormatter
                        .ofPattern("'NTP 'yyyy-MM-dd'T'HH:mm:ssxxx' UI SERVER (M.A.V. srl)'", Locale.ENGLISH)
                        .withZone(ZoneId.of("GMT"));
                return fmt.format(time);
            }

            private void handleIncomingMessage(BufferedReader in, BufferedWriter out,
                    @Nullable BufferedWriter passthroughSocketIn, @Nullable BufferedReader passthroughSocketOut)
                    throws IOException {
                // logger.info("Handling incoming message");
                // @Nullable
                TrivialHttpRequest request = parseIncomingHttpMessage(in);
                logger.info("Handling incoming message {}", request);

                if (request == null) {
                    return;
                }

                if (passthroughSocketIn != null && passthroughSocketOut != null) {
                    request.writeTo(passthroughSocketIn);
                    passthroughSocketIn.flush();
                    TrivialHttpRequest remoteResponse = parseIncomingHttpMessage(passthroughSocketOut);
                    if (remoteResponse == null) {
                        throw new RuntimeException("No remote response");
                    }
                    logger.info("Remote response was {}", remoteResponse);
                    remoteResponse.writeTo(out);
                    out.flush();
                    return;
                }

                if (request.getRequestLine().contains("UI_NTP")) {
                    sendFakeBody(out, getNtpResponse(Instant.now()));
                    // sendFakeBody(out, "NTP 2022-08-28T09:32:12+00:00 UI SERVER (M.A.V. srl)");
                } else if (request.getRequestLine().contains("UI_FLG")) {
                    sendFakeBody(out,
                            "{|0|0|1|0|0|0|N,N,N,N,N,N,N,N,N,N,N,N,3,N,N,N,N,N,1,2,1360,N,0,NaN,N,N,N,N,N,N,N,N,N,N,N,N|}[|0|||]ACN_FREE <br>\t\t");
                } else {
                    sendFakeBody(out, "|}|}\\t\\t");
                }

                // BufferedReader tcpInput = new BufferedReader(new InputStreamReader(is));

                // tcpOutput = new OutputStreamWriter(tcpSocket.getOutputStream(), "US-ASCII");
                // tcpInput = new BufferedReader(new InputStreamReader(is));
                //
                // byte[] incomingInput = is.readAllBytes();
                // String message = new String(incomingInput);
                // logger.info("Incoming message: {}", message);
                //
                // String returnMessage = "";
                // return returnMessage.getBytes();
            }

            //
            //
            // private final Logger logger = LoggerFactory.getLogger(TCPListener.class);
            //
            // /**
            // * Run method. Runs the MessageListener thread
            // */
            // @Override
            // public void run() {
            // String messageLine;
            //
            // try {
            // while (isConnected()) {
            // if ((messageLine = read()) != null) {
            // try {
            // handleIncomingMessage(messageLine);
            // } catch (Exception e) {
            // logger.error("TCPListener(): Message not handled by bridge: {}", e.getMessage());
            // }
            // } else {
            // setConnected(false);
            // }
            // }
            // } catch (Exception e) {
            // logger.error("TCPListener(): Unable to read message: {} ", e.getMessage(), e);
            // closeConnection();
            // }
            // }
        }

        //
        // public void openConnection() {
        // try {
        // closeConnection();
        //
        //
        // clientSocket = serverSocket.accept(); //TODO this is blocking
        //
        // tcpSocket = new Socket();
        // SocketAddress tpiSocketAddress = new InetSocketAddress(ipAddress, tcpPort);
        // tcpSocket.bind(tpiSocketAddress);
        // // tcpSocket.connect(tpiSocketAddress, connectionTimeout);
        // tcpOutput = new OutputStreamWriter(tcpSocket.getOutputStream(), "US-ASCII");
        // tcpInput = new BufferedReader(new InputStreamReader(tcpSocket.getInputStream()));
        //
        // Thread tcpListener = new Thread(new TCPListener(), "OH-binding-" + this.thingUid + "-tcplistener");
        // tcpListener.setDaemon(true);
        // tcpListener.start();
        //
        // setConnected(true);
        // } catch (UnknownHostException unknownHostException) {
        // logger.error("openConnection(): Unknown Host Exception: {}", unknownHostException.getMessage());
        // setConnected(false);
        // } catch (SocketException socketException) {
        // logger.error("openConnection(): Socket Exception: {}", socketException.getMessage());
        // setConnected(false);
        // } catch (IOException ioException) {
        // logger.error("openConnection(): IO Exception: {}", ioException.getMessage());
        // setConnected(false);
        // } catch (Exception exception) {
        // logger.error("openConnection(): Unable to open a connection: {} ", exception.getMessage(), exception);
        // setConnected(false);
        // }
        // }
        //
        // private boolean connected;
        //
        // private void setConnected(boolean isConnected) {
        // this.connected = isConnected;
        // }
        //
        // public boolean isConnected() {
        // return connected;
        // }
        //
        // private boolean running;
        //
        // private void setRunning(boolean isRunning) {
        // this.running = isRunning;
        // }
        //
        // public boolean isRunning() {
        // return running;
        // }
        //
        // public void write(String writeString, boolean doNotLog) {
        // try {
        // tcpOutput.write(writeString);
        // tcpOutput.flush();
        // logger.debug("write(): Message Sent: {}", doNotLog ? "***" : writeString);
        // } catch (IOException ioException) {
        // logger.error("write(): {}", ioException.getMessage());
        // setConnected(false);
        // } catch (Exception exception) {
        // logger.error("write(): Unable to write to socket: {} ", exception.getMessage(), exception);
        // setConnected(false);
        // }
        // }
        //
        // public @Nullable String read() {
        // @Nullable
        // String message = "";
        //
        // try {
        // message = tcpInput.readLine();
        // logger.debug("read(): Message Received: {}", message);
        // } catch (IOException ioException) {
        // logger.error("read(): IO Exception: {}", ioException.getMessage());
        // setConnected(false);
        // } catch (Exception exception) {
        // logger.error("read(): Exception: {} ", exception.getMessage(), exception);
        // setConnected(false);
        // }
        //
        // return message;
        // }
        //
        // public void closeConnection() {
        // try {
        // if (tcpSocket != null) {
        // tcpSocket.close();
        // tcpSocket = null;
        // tcpInput = null;
        // tcpOutput = null;
        // }
        // setConnected(false);
        // logger.debug("closeConnection(): Closed TCP Connection!");
        // } catch (IOException ioException) {
        // logger.error("closeConnection(): Unable to close connection - {}", ioException.getMessage());
        // } catch (Exception exception) {
        // logger.error("closeConnection(): Error closing connection - {}", exception.getMessage());
        // }
        // }
        //
        // public synchronized void handleIncomingMessage(String incomingMessage) {
        // if (incomingMessage != null && !incomingMessage.isEmpty()) {
        // }
        // logger.info("Got message: " + incomingMessage);
        // }
        //
        // private class TCPServer implements Runnable {
        // private final Logger logger = LoggerFactory.getLogger(TCPServer.class);
        //
        // @Override
        // public void run() {
        // // TODO Auto-generated method stub
        // try {
        // while (isRunning()) {
        // if ((messageLine = read()) != null) {
        // try {
        // handleIncomingMessage(messageLine);
        // } catch (Exception e) {
        // logger.error("TCPListener(): Message not handled by bridge: {}", e.getMessage());
        // }
        // } else {
        // setRunning(false);
        // }
        // }
        // } catch (Exception e) {
        // logger.error("TCPListener(): Unable to read message: {} ", e.getMessage(), e);
        // closeConnection();
        // }
        //
        // }
        //
        //
        // }
        //
        //
        // /**
        // * TCPMessageListener: Receives messages from the DSC Alarm Panel API.
        // */
        // private class TCPListener implements Runnable {
        // private final Logger logger = LoggerFactory.getLogger(TCPListener.class);
        //
        // /**
        // * Run method. Runs the MessageListener thread
        // */
        // @Override
        // public void run() {
        // String messageLine;
        //
        // try {
        // while (isConnected()) {
        // if ((messageLine = read()) != null) {
        // try {
        // handleIncomingMessage(messageLine);
        // } catch (Exception e) {
        // logger.error("TCPListener(): Message not handled by bridge: {}", e.getMessage());
        // }
        // } else {
        // setConnected(false);
        // }
        // }
        // } catch (Exception e) {
        // logger.error("TCPListener(): Unable to read message: {} ", e.getMessage(), e);
        // closeConnection();
        // }
        // }
        // }

    }
}
