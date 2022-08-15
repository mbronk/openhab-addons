package org.openhab.binding.argoclima.internal.device_api;

public class ArgoApiDataElement {
    public enum DataElementType {
        READ_WRITE,
        READ_ONLY,
        WRITE_ONLY
    }

    private int queryResponseIndex;
    private int statusUpdateRequestIndex;
    private DataElementType type;
    private String rawValue;

    private ArgoApiDataElement(int queryIndex, int updateIndex, DataElementType type) {
        this.queryResponseIndex = queryIndex;
        this.statusUpdateRequestIndex = updateIndex;
        this.type = type;
    }

    public static ArgoApiDataElement readWriteElement(int queryIndex, int updateIndex) {
        return new ArgoApiDataElement(queryIndex, updateIndex, DataElementType.READ_WRITE);
    }

    public static ArgoApiDataElement readOnlyElement(int queryIndex) {
        return new ArgoApiDataElement(queryIndex, -1, DataElementType.READ_ONLY);
    }

    public static ArgoApiDataElement writeOnlyElement(int updateIndex) {
        return new ArgoApiDataElement(-1, updateIndex, DataElementType.WRITE_ONLY);
    }

    public void fromDeviceResponse(String[] responseElements) {
        if (this.type == DataElementType.READ_WRITE || this.type == DataElementType.READ_ONLY) {
            this.rawValue = responseElements[queryResponseIndex];
            // TODO: err handling
        }
    }

    public String getValue() {
        return rawValue;
    }

    @Override
    public String toString() {
        return rawValue;
    }
}
