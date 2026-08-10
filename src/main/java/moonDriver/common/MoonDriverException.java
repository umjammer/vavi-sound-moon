package moonDriver.common;

import java.io.Serializable;
import java.util.ResourceBundle;


class MoonDriverException extends Exception implements Serializable {

    private static final ResourceBundle rb = ResourceBundle.getBundle("moonDriver/messages");

    public MoonDriverException() {
    }

    public MoonDriverException(String message) {
        super(message);
    }

    public MoonDriverException(String message, Exception innerException) {
        super(message, innerException);
    }

    public MoonDriverException(String message, int row, int col) {
        super(String.format(rb.getString("E0300"), row, col, message));
    }
}
