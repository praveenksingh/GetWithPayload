package getwithpayload;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.regex.Pattern;

public class SocketGet {
    private static final Logger logger = LoggerFactory.getLogger(SocketGet.class);

    private final String HOST = "www.google.com";
    private final int PORT = 443;
    private final String RESOURCE_PATH = "/get/api/path";
    private static final String LINE_END = "\r\n";
    private final String RESPONSE_STATUS_OK = "HTTP/1.1 200 OK";

    private final Pattern hexPattern;
    private final byte[] requestHeader;
    private final ObjectMapper mapper;

    SocketGet() {
        hexPattern = Pattern.compile("\\p{XDigit}+"); // Only hexadecimal
        requestHeader = createRequestHeader();
        mapper = new ObjectMapper().findAndRegisterModules();
    }

    public Object getResponseFromServerGETRequestWithPayload(Object payload){
        try {
            String requestQuery = mapper.writeValueAsString(payload);
            String response = getRequestWithPayload(requestQuery);
            return mapper.readValue(response, Object.class);
        } catch (IOException e) {
            throw new SocketGetException(e);
        }
    }

    private String getRequestWithPayload(String requestQuery) {
        SSLSocketFactory factory = (SSLSocketFactory)SSLSocketFactory.getDefault();

        try (SSLSocket sslSocket = (SSLSocket) factory.createSocket(HOST, PORT);
             OutputStream outputStream = sslSocket.getOutputStream();
             InputStream inputStream = sslSocket.getInputStream()) {// try-with-resources, this will close all these streams when complete or when error occurs

            sslSocket.startHandshake();

            // Write request to socket
            outputStream.write(requestHeader); //We created the constant request header to save cpu if this Request is called multiple times
            outputStream.write(("Content-Length: " + requestQuery.length() + LINE_END).getBytes()); // Specify the length of PayLoad
            outputStream.write(LINE_END.getBytes()); // Blank Line to Specify the headers end here
            outputStream.write((requestQuery + LINE_END).getBytes()); //body
            outputStream.flush();

            // Read the response from socket, and return it.
            final StringBuilder responseBody;
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));

            // Tests the response code for the request, if this is not OK we throw exception here and do not care about the rest.
            String responseStatus = bufferedReader.readLine();
            if (!RESPONSE_STATUS_OK.equals(responseStatus)) {
                throw new SocketGetException("Request Failed, Status : " + responseStatus);
            }

            responseBody = new StringBuilder();
            StringBuilder responseHeader = new StringBuilder();
            boolean headerDone = false;
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                if(!headerDone) {
                    responseHeader.append(line);
                    if(line.isEmpty()) {
                        // Empty line in response denotes that the headers are complete, next its response.
                        headerDone = true;
                        logger.debug("Response Headers received from API : {}", responseHeader);
                    }
                }

                if (headerDone) {
                    // We just care about the response and not header.
                    if (line.length() <= 4 && hexPattern.matcher(line).matches()) {
                        /*
                         "headerDone" : We do this check only for response body and not response header.
                         "line.length() <= 4" : verify the response size from the server, usually 4K (hex value 1000 ~ length 4).
                                                If not sure remove the length check, this length check helps reduce the hexPatter matching,
                                                hence reducing cpu usage.

                         When response is big, then the server sends response size in new line before every response line.
                         We dont need this in our response, or else writing this response to POJO fails with jsonProcessingException
                         */
                        continue;
                    }
                    responseBody.append(line);
                }
            }

            String responseString = responseBody.toString().trim();
            if(responseString.isEmpty()){
                // if the response has only response header and no response, parsing it to POJO would fail.
                throw new SocketGetException("No response body found in socket response");
            }
            return responseString;

        } catch (IOException e){
            throw new SocketGetException("Exception occurred while fetching socket response", e);
        }
    }

    private byte[] createRequestHeader() {
        String requestBuilder =
                        // Request type and resource path
                        "GET " + RESOURCE_PATH + " HTTP/1.1" + LINE_END +
                        // User Agent
                        "User-Agent: Java Socket" + LINE_END +
                        // Content Type
                        "Content-Type: application/json;charset=utf-8" + LINE_END +
                        // AcceptType
                        "Accept: application/json;charset=utf-8" + LINE_END;
        return requestBuilder.getBytes();
    }
}

