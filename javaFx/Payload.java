//Paload Creation

import java.io.Serializable;

public class Payload implements Serializable {
    private static final long serialVersionUID = 1L;
    public final String type;
    public final String data;

    public Payload(String type, String data) {
        this.type = type;
        this.data = data;
    }

    @Override
    public String toString() {
        return "Payload[type=" + type + ", data=" + data + "]";
    }
}

