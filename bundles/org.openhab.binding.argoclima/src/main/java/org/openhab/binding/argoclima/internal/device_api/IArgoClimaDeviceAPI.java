package org.openhab.binding.argoclima.internal.device_api;

import java.net.InetAddress;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.argoclima.internal.device_api.elements.IArgoElement;
import org.openhab.binding.argoclima.internal.device_api.passthrough.requests.DeviceSidePostRtUpdateDTO;
import org.openhab.binding.argoclima.internal.device_api.types.ArgoDeviceSettingType;
import org.openhab.binding.argoclima.internal.exception.ArgoLocalApiCommunicationException;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;

@NonNullByDefault
public interface IArgoClimaDeviceAPI {

    // TODO: reverse logic of picking the addresses in other places (and update names?)
    InetAddress getIpAddressForDirectCommunication();

    Pair<Boolean, String> isReachable();

    Map<ArgoDeviceSettingType, State> queryDeviceForUpdatedState() throws ArgoLocalApiCommunicationException;

    void sendCommandsToDevice() throws ArgoLocalApiCommunicationException;

    boolean handleSettingCommand(ArgoDeviceSettingType settingType, Command command);

    State getCurrentStateNoPoll(ArgoDeviceSettingType settingType);

    boolean hasPendingCommands();

    List<ArgoApiDataElement<IArgoElement>> getItemsWithPendingUpdates();

    void updateDeviceStateFromPostRtRequest(DeviceSidePostRtUpdateDTO fromDevice);

    void updateDeviceStateFromPushRequest(String hmiStringFromDevice, String deviceIP, String deviceCpuId);

}