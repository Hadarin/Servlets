import javax.servlet.http.HttpServlet;

public class TestServlet extends HttpServlet {


    private static final Logger log = LoggerFactory.getLogger(AzureAuthServlet.class);

    private AzureAuthHelper authHelper;

    protected void processRequest(HttpServletRequest request,
                                  HttpServletResponse response)
            throws IOException {
        try {
            this.authHelper = AzureAuthHelper.getAuthHelper();
            String currentUri = request.getRequestURL().toString();
            String queryStr = request.getQueryString();
            String fullUrl = currentUri + (queryStr != null ? "?" + queryStr : "");
            if (containsAuthenticationCode(request)) {
                // response should have authentication code, which will be used to acquire access token

                authHelper.processAuthenticationCodeRedirect(request, currentUri, fullUrl);
                // remove query params so that containsAuthenticationCode will not be true on future requests
                ((HttpServletResponse) response).sendRedirect(currentUri);
                return;
            }
            if (isAccessTokenExpired(request)) {
                updateAuthDataUsingSilentFlow(request, response);
            }

            if (isAuthenticated(request)) {
                String principal = AzureAuthHelper.getAuthenticatedUser(request);
                log.info("AZURE USER AUTHENTICATED: " + principal);
                String url = "j_security_check?j_username=" + principal + "&j_password=" + principal;
                response.sendRedirect(encodeURI(url));
            }
        } catch (MsalException authException) {
            // something went wrong (like expiration or revocation of token)
            // we should invalidate AuthData stored in session and redirect to Authorization server
            SessionManagementHelper.removePrincipalFromSession(request);
            authHelper.sendAuthRedirect(
                    request,
                    response,
                    null,
                    authHelper.getRedirectUriSignIn());
        } catch (Throwable exc) {
            log.error(exc.getMessage());
        }
    }

    public static String encodeURI(String s) {
        String result;
        try {
            result = URLEncoder.encode(s, "UTF-8").replaceAll("\\+", "%20").replaceAll("\\%21", "!")
                    .replaceAll("\\%27", "'").replaceAll("\\%28", "(").replaceAll("\\%29", ")")
                    .replaceAll("\\%7E", "~");
        } // This exception should never occur.
        catch (Exception e) {
            result = s;
        }

        return result;
    }

    private boolean isAccessTokenExpired(HttpServletRequest httpRequest) {
        IAuthenticationResult result = SessionManagementHelper.getAuthSessionObject(httpRequest);
        return result.expiresOnDate().before(new Date());
    }

    private boolean isAuthenticated(HttpServletRequest request) {
        return request.getSession().getAttribute(AzureAuthHelper.PRINCIPAL_SESSION_NAME) != null;
    }

    private boolean containsAuthenticationCode(HttpServletRequest httpRequest) {
        Map<String, String[]> httpParameters = httpRequest.getParameterMap();

        boolean isPostRequest = httpRequest.getMethod().equalsIgnoreCase("POST");
        boolean containsErrorData = httpParameters.containsKey("error");
        boolean containIdToken = httpParameters.containsKey("id_token");
        boolean containsCode = httpParameters.containsKey("code");

        log.info("isPostRequest && containsErrorData || containsCode || containIdToken" +
                isPostRequest + ":" + containsErrorData + ":" + containsCode + ":" + containIdToken);

        boolean res = isPostRequest && containsErrorData || containsCode || containIdToken;
        log.info("containsAuthenticationCode=" + (res));
        return res;
    }

    private void updateAuthDataUsingSilentFlow(HttpServletRequest httpRequest, HttpServletResponse httpResponse)
            throws Throwable {
        IAuthenticationResult authResult = authHelper.getAuthResultBySilentFlow(httpRequest, httpResponse);
        SessionManagementHelper.setSessionPrincipal(httpRequest, authResult);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        processRequest(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        processRequest(req, resp);
    }

}
