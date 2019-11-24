import Util.AppUtil;
import Util.RequestBodySaver;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

public class PostServlet1 extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {

        String filePath = RequestBodySaver.saveRequestBody(AppUtil.getBody(req));

        PrintWriter out = resp.getWriter();
        out.print(filePath);
        out.flush();
    }
}
