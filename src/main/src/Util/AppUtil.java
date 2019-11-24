package Util;

import com.fasterxml.jackson.databind.ObjectMapper;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

public class AppUtil {


    /**
     * Getting request and map it to the Object
     * @param request input request
     * @return body of the request in Object
     */
    public static Object getBody(HttpServletRequest request) {
        Object body = null;
        try {
            ObjectMapper objectMapper = new ObjectMapper();
             body = objectMapper.readValue(request.getReader(), Object.class);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return body;
    }

}
