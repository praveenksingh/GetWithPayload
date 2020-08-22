package getwithpayload;

public class SocketGetException extends RuntimeException{

    SocketGetException(Exception exception){
        super(exception);
    }

    SocketGetException(String message) {
        super(message);
    }

    SocketGetException(String message, Throwable throwable){
        super(message, throwable);
    }
}
