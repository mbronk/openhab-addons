package org.openhab.binding.argoclima.internal.device_api;

import java.net.InetAddress;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.util.URIUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        ContentResponse resp = this.client.GET(url);
        logger.warn("Got response {}", resp.getStatus());
        // new URIBuilder().
        // HttpRequest.newBuilder().uri(null)
        // this.client.GET(null)
        boolean isAvailable = (resp.getStatus() == 200);

        return isAvailable;
    }

    public void updateStateFromDevice() {
        String url = URIUtil.newURI("http", this.ipAddress.getHostName(), this.port, "/", "HMI=&UPD=0");
        // this.client.setAddressResolutionTimeout(10 *1000);
        ContentResponse resp = null;
        try {
            resp = this.client.GET(url);
            this.deviceStatus.fromDeviceString(resp.getContentAsString());
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
        logger.warn("Got response {}", resp.getContentAsString());
    }
}
