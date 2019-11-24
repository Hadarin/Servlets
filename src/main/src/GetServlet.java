import Util.RequestBodySaver;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

public class GetServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {

       String filePath = RequestBodySaver.saveRequestBody(req.getParameterMap());

       PrintWriter out = resp.getWriter();
       out.print(filePath);
       out.flush();
    }
}
