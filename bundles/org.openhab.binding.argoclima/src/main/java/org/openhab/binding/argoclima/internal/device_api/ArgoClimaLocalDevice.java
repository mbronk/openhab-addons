package org.openhab.binding.argoclima.internal.device_api;

import java.io.EOFException;
import java.net.InetAddress;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.util.URIUtil;
import org.openhab.binding.argoclima.internal.device_api.types.ArgoDeviceSettingType;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NonNullByDefault
public class ArgoClimaLocalDevice {
    private final Logger logger = LoggerFactory.getLogger(ArgoClimaLocalDevice.class);
    private final InetAddress ipAddress;
    private int port;
    private final HttpClient client;
    private ArgoDeviceStatus deviceStatus;

    public ArgoClimaLocalDevice(InetAddress ipAddress, int port, HttpClient client) {
        this.ipAddress = ipAddress;
        this.port = port;
        this.client = client;
        this.deviceStatus = new ArgoDeviceStatus();
    }

    public boolean isReachable() throws InterruptedException, ExecutionException, TimeoutException {
        String url = URIUtil.newURI("http", this.ipAddress.getHostName(), this.port, "/", "HMI=&UPD=0");
        // this.client.setAddressResolutionTimeout(10 *1000);
        try {
            ContentResponse resp = this.client.GET(url);

            logger.warn("Got response {}", resp.getStatus());
            // new URIBuilder().
            // HttpRequest.newBuilder().uri(null)
            // this.client.GET(null)
            boolean isAvailable = (resp.getStatus() == 200);

            return isAvailable;
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof EOFException) {
                logger.warn("Cause is: EOF: {}", ((EOFException) cause).getMessage());
                return false;
            }
            logger.warn("Cause is:", ex.getCause());
            return false;
        }
    }

    public boolean handleSettingCommand(ArgoDeviceSettingType settingType, Command command) {
        return this.deviceStatus.getSetting(settingType).handleCommand(command);
    }

    public State getCurrentStateNoPoll(ArgoDeviceSettingType settingType) {
        return this.deviceStatus.getSetting(settingType).getState();
    }

    public boolean hasPendingCommands() {
        return this.deviceStatus.hasUpdatesPending();
    }

    public Map<ArgoDeviceSettingType, State> updateStateFromDevice() {
        String url = URIUtil.newURI("http", this.ipAddress.getHostName(), this.port, "/", "HMI=&UPD=0");
        // this.client.setAddressResolutionTimeout(10 *1000);
        // TODO: if not reachable, calling it makes zero sense!

        ContentResponse resp = null;
        try {
            resp = this.client.GET(url);
            this.deviceStatus.fromDeviceString(resp.getContentAsString());
            logger.warn("Got response {}", resp.getContentAsString());
            return this.deviceStatus.getCurrentStateMap();
            // this.deviceStatus.st
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (ExecutionException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (TimeoutException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return Map.of();
    }

    public void sendCommandsToDevice() {
        String url = URIUtil.newURI("http", this.ipAddress.getHostName(), this.port, "/",
                String.format("HMI=%s&UPD=1", this.deviceStatus.getDeviceCommandStatus()));
        logger.info("Sending update request:{}", url);
        // this.client.setAddressResolutionTimeout(10 *1000);
        ContentResponse resp = null;
        try {
            resp = this.client.GET(url);
            // this.deviceStatus.fromDeviceString(resp.getContentAsString());
            logger.warn("Got update response {}", resp.getContentAsString());

            // TODO: check if these values are real!
            this.deviceStatus.fromDeviceString(resp.getContentAsString());

            // this.deviceStatus.st
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (ExecutionException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (TimeoutException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }
}
