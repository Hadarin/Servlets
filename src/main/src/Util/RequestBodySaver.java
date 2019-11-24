package Util;

import com.google.gson.Gson;

import java.io.*;

public class RequestBodySaver {

    public static Gson gson = new Gson();

    public static String filePath = System.getProperty("java.io.tmpdir") + File.separator + "requestBodyTemp.txt";

    /**
     *Method that convert to string  the request body and save it into the temp directory of Tomcat.
     * @param requestBody the request boy from any request
     * @return path to temp directory
     */
    public  static String saveRequestBody(Object requestBody) {
        BufferedWriter writer = null;
        try {
            String request = gson.toJson(requestBody);
            System.out.println("REQUEST STRING IS: " + request);

            File logFile = new File(filePath);

            writer = new BufferedWriter(new FileWriter(logFile));
            writer.write(request);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                writer.close();
            } catch (Exception e) {
            }
            return filePath;
        }
    }
}
