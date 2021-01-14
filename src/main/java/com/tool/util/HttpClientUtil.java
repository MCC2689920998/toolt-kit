package com.tool.util;


import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.*;
import org.apache.http.client.HttpClient;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.DeflateDecompressingEntity;
import org.apache.http.client.entity.GzipDecompressingEntity;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.config.ConnectionConfig;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.LayeredConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContexts;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.cookie.Cookie;
import org.apache.http.cookie.CookieOrigin;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.impl.cookie.BestMatchSpec;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HttpContext;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基于 httpclient 4.3版本的 HttpClient工具类
 *
 * @author sundy
 */
@Slf4j
public class HttpClientUtil {

    private static int bufferSize = 1024;

    private static HttpClientUtil instance;//同步使用 volatile

    private ConnectionConfig connConfig;

    private SocketConfig socketConfig;

    private ConnectionSocketFactory plainSF;

    private KeyStore trustStore;

    private SSLContext sslContext;

    private LayeredConnectionSocketFactory sslSF;

    private Registry<ConnectionSocketFactory> registry;

    private PoolingHttpClientConnectionManager connManager;

    private HttpClient client;//同步使用 volatile

    private BasicCookieStore cookieStore;//同步使用 volatile

    public static String defaultEncoding = "utf-8";

    class AnyTrustStrategy implements TrustStrategy {

        public boolean isTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            return true;
        }

    }

    private static List<NameValuePair> paramsConverter(Map<String, String> params) {
        List<NameValuePair> nvps = new LinkedList<NameValuePair>();
        Set<Entry<String, String>> paramsSet = params.entrySet();
        for (Entry<String, String> paramEntry : paramsSet) {
            nvps.add(new BasicNameValuePair(paramEntry.getKey(), paramEntry.getValue()));
        }
        return nvps;
    }

    public static String readStream(InputStream in, String encoding) {
        if (in == null) {
            return null;
        }
        InputStreamReader inReader = null;
        try {
            if (encoding == null) {
                inReader = new InputStreamReader(in, defaultEncoding);
            } else {
                inReader = new InputStreamReader(in, encoding);
            }
            char[] buffer = new char[bufferSize];
            int readLen = 0;
            StringBuffer sb = new StringBuffer();
            while ((readLen = inReader.read(buffer)) != -1) {
                sb.append(buffer, 0, readLen);
            }
            //            inReader.close();
            return sb.toString();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (inReader != null)
                try {
                    inReader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
        }
        return null;
    }

    private HttpClientUtil() {
        //设置连接参数
        connConfig = ConnectionConfig.custom().setCharset(Charset.forName(defaultEncoding)).build();
        socketConfig = SocketConfig.custom().setSoTimeout(5000).setSoKeepAlive(true).setTcpNoDelay(true).build();//https socket连接超时设置5秒
        RegistryBuilder<ConnectionSocketFactory> registryBuilder = RegistryBuilder.<ConnectionSocketFactory>create();
        plainSF = new PlainConnectionSocketFactory();
        registryBuilder.register("http", plainSF);
        //指定信任密钥存储对象和连接套接字工厂
        try {
            trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            sslContext = SSLContexts.custom().useTLS().loadTrustMaterial(trustStore, new AnyTrustStrategy()).build();
            sslSF = new SSLConnectionSocketFactory(sslContext, SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
            registryBuilder.register("https", sslSF);
        } catch (KeyStoreException e) {
            throw new RuntimeException(e);
        } catch (KeyManagementException e) {
            throw new RuntimeException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        registry = registryBuilder.build();
        //设置连接管理器
        connManager = new PoolingHttpClientConnectionManager(registry);
        connManager.setDefaultConnectionConfig(connConfig);
        connManager.setDefaultSocketConfig(socketConfig);
        connManager.setMaxTotal(200);
        connManager.setDefaultMaxPerRoute(connManager.getMaxTotal());
        //指定cookie存储对象
        cookieStore = new BasicCookieStore();
        //构建客户端重试控制器   并设置重试次数
        HttpRequestRetryHandler myRetryHandler = setRetryHandler(1);

        HttpClientBuilder httpClientBuilder = HttpClientBuilder.create().setDefaultCookieStore(cookieStore).setConnectionManager(connManager)
                .setRetryHandler(myRetryHandler);

        //启用gzip deflate压缩传输
        useGzip(httpClientBuilder);

        client = httpClientBuilder.build();

    }

    /**
     * @return
     */
    private HttpRequestRetryHandler setRetryHandler(final int retryTimes) {
        HttpRequestRetryHandler myRetryHandler = new HttpRequestRetryHandler() {

            @Override
            public boolean retryRequest(IOException exception, int executionCount, HttpContext context) {

                if (executionCount >= retryTimes) {
                    //如果已经重试了1次，就放弃 
                    return false;
                }
                if (exception instanceof InterruptedIOException) {
                    // Timeout  
                    return false;
                }
                if (exception instanceof UnknownHostException) {
                    // Unknown host  
                    return false;
                }
                if (exception instanceof ConnectTimeoutException) {
                    // Connection refused  
                    return false;
                }
                if (exception instanceof SSLException) {
                    // SSL handshake exception  
                    return false;
                }
                HttpClientContext clientContext = HttpClientContext.adapt(context);
                HttpRequest request = clientContext.getRequest();
                boolean idempotent = !(request instanceof HttpEntityEnclosingRequest);
                if (idempotent) {
                    // Retry if the request is considered idempotent  
                    return true;
                }
                return false;

            }
        };
        return myRetryHandler;
    }

    /**
     * @param httpClientBuilder
     */
    private void useGzip(HttpClientBuilder httpClientBuilder) {
        httpClientBuilder.addInterceptorFirst(new HttpRequestInterceptor() {

            public void process(HttpRequest req, HttpContext arg1) throws HttpException, IOException {
                if (!req.containsHeader("Accept-Encoding")) {
                    req.addHeader("Accept-Encoding", "gzip,deflate,sdch");
                }
            }

        });

        httpClientBuilder.addInterceptorFirst(new HttpResponseInterceptor() {
            public void process(HttpResponse resp, HttpContext arg1) throws HttpException, IOException {
                Header[] headers = resp.getHeaders("Content-Encoding");
                for (Header header : headers) {
                    if ("gzip".equals(header.getValue())) {
                        resp.setEntity(new GzipDecompressingEntity(resp.getEntity()));
                        return;
                    } else if ("deflate".equals(header.getValue())) {
                        resp.setEntity(new DeflateDecompressingEntity(resp.getEntity()));
                        return;
                    }
                }

            }
        });
    }

    public static HttpClientUtil getInstance() {
        if (HttpClientUtil.instance == null) {
            synchronized (HttpClientUtil.class) {
                if (HttpClientUtil.instance == null) {
                    instance = new HttpClientUtil();
                }
            }
        }
        return instance;
    }

    public InputStream doGetForStream(String url) throws Exception {
        @SuppressWarnings("unchecked")
        HttpResponse response = this.doGet(url, null, null);
        return response != null ? response.getEntity().getContent() : null;
    }

    public InputStream doGetForStream(String url, Map<String, String> header) throws Exception {
        @SuppressWarnings("unchecked")
        HttpResponse response = this.doGet(url, null, header);
        return response != null ? response.getEntity().getContent() : null;
    }


    /**
     * <pre>
     * @param url
     * @param charset  默认utf-8
     * @return String
     * @throws Exception
     * </pre>
     */
    public String doGetForString(String url, String charset) throws Exception {
        return HttpClientUtil.readStream(this.doGetForStream(url), charset);
    }

    public String doGetForString(String url, Map<String, String> header, String charset) throws Exception {
        return HttpClientUtil.readStream(this.doGetForStream(url, header), charset);
    }

    public Map<String, Object> doGetForMap(String url, String charset) throws Exception {
        @SuppressWarnings("unchecked")
        HttpResponse response = doGet(url, null, null);
        if (200 == response.getStatusLine().getStatusCode()) {
            String data = HttpClientUtil.readStream(response.getEntity().getContent(), charset);
            @SuppressWarnings("unchecked")
            Map<String, Object> map = JSON.parseObject(data, Map.class);
            return map;
        } else
            throw new IOException("请求出现错误：" + response.getStatusLine().getStatusCode());
    }

    public Map<String, Object> doGetForMap(String url, Map<String, String> header, String charset) throws Exception {
        @SuppressWarnings("unchecked")
        HttpResponse response = doGet(url, null, header);
        if (200 == response.getStatusLine().getStatusCode()) {
            String data = HttpClientUtil.readStream(response.getEntity().getContent(), charset);
            @SuppressWarnings("unchecked")
            Map<String, Object> map = JSON.parseObject(data, Map.class);
            return map;
        } else
            throw new IOException("请求出现错误：" + response.getStatusLine().getStatusCode());
    }

    /**
     * 基本的Get请求
     *
     * @param url         请求url
     * @param queryParams 请求头的查询参数
     * @return
     * @throws Exception
     */
    public HttpResponse doGet(String url, Map<String, String> queryParams, Map<String, String> header, @SuppressWarnings("unchecked") Map<String, Object>... map) throws Exception {
        HttpGet gm = new HttpGet();
        RequestConfig.Builder requestConfig = RequestConfig.custom().setConnectTimeout(30000).setConnectionRequestTimeout(30000).setSocketTimeout(30000);

        if (map.length > 0) {//目前只处理了第一个ip代理
            HttpHost proxy = new HttpHost(map[0].get("ip").toString().trim(), Integer.parseInt(map[0].get("port").toString().trim()), "http");
            requestConfig.setProxy(proxy);
        }
        gm.setConfig(requestConfig.build());
        //如果有请求头参数，添加进header中
        if (null != header && !header.isEmpty()) {
            for (String key : header.keySet()) {
                gm.addHeader(key, header.get(key));
            }
        }
        URIBuilder builder = new URIBuilder(url);
        //填入查询参数
        if (queryParams != null && !queryParams.isEmpty()) {
            builder.setParameters(HttpClientUtil.paramsConverter(queryParams));
        }
        gm.setURI(builder.build());
        HttpResponse response = client.execute(gm);
        //保存Cookies
        saveCookies(url, response);
        return response;
    }

    public InputStream doPostForStream(String url, Map<String, String> formParams) throws Exception {
        HttpResponse response = this.doPost(url, null, formParams, null);
        return response != null ? response.getEntity().getContent() : null;
    }

    public InputStream doPostForStream(String url, Map<String, String> formParams, Map<String, String> header) throws Exception {
        HttpResponse response = this.doPost(url, null, formParams, header);
        return response != null ? response.getEntity().getContent() : null;
    }

    public InputStream doJsonPostForStream(String url, String params, Map<String, String> header, String charset) throws Exception {
        HttpResponse response = this.doJsonPost(url, null, params, header, charset);
        return response != null ? response.getEntity().getContent() : null;
    }

    public String doPostForString(String url, Map<String, String> formParams, String charset) throws Exception {
        return HttpClientUtil.readStream(this.doPostForStream(url, formParams), charset);
    }

    public String doPostForString(String url, Map<String, String> formParams, Map<String, String> header, String charset) throws Exception {
        return HttpClientUtil.readStream(this.doPostForStream(url, formParams, header), charset);
    }

    public String doJsonPostForString(String url, String params, String charset) throws Exception {
        return HttpClientUtil.readStream(this.doJsonPostForStream(url, params, null, charset), charset);
    }

    public String doJsonPostForString(String url, String params) throws Exception {
        return HttpClientUtil.readStream(this.doJsonPostForStream(url, params, null, defaultEncoding), defaultEncoding);
    }

    public String doJsonPostForString(String url, String params, Map<String, String> header, String charset) throws Exception {
        return HttpClientUtil.readStream(this.doJsonPostForStream(url, params, header, charset), charset);
    }

    public Map<String, Object> doPostForMap(String url, Map<String, String> formParams, String charset) throws Exception {
        HttpResponse response = doPost(url, null, formParams, null);
        if (200 == response.getStatusLine().getStatusCode()) {
            String data = HttpClientUtil.readStream(response.getEntity().getContent(), charset);
            @SuppressWarnings("unchecked")
            Map<String, Object> map = JSON.parseObject(data, Map.class);
            return map;
        } else
            throw new IOException("请求出现错误：" + response.getStatusLine().getStatusCode());
    }

    public Map<String, Object> doPostForMap(String url, Map<String, String> formParams, Map<String, String> header, String charset) throws Exception {
        HttpResponse response = doPost(url, null, formParams, header);
        if (200 == response.getStatusLine().getStatusCode()) {
            String data = HttpClientUtil.readStream(response.getEntity().getContent(), charset);
            @SuppressWarnings("unchecked")
            Map<String, Object> map = JSON.parseObject(data, Map.class);
            return map;
        } else
            throw new IOException("请求出现错误：" + response.getStatusLine().getStatusCode());
    }

    /**
     * 基本的Post请求
     *
     * @param url         请求url
     * @param queryParams 请求头的查询参数
     * @param formParams  post表单的参数
     * @return
     * @throws Exception
     */
    private HttpResponse doPost(String url, Map<String, String> queryParams, Map<String, String> formParams, Map<String, String> header) throws Exception {
        HttpPost pm = new HttpPost();
        URIBuilder builder = new URIBuilder(url);
        //填入查询参数
        if (queryParams != null && !queryParams.isEmpty()) {
            builder.setParameters(HttpClientUtil.paramsConverter(queryParams));
        }
        pm.setURI(builder.build());

        //如果有请求头参数，添加进header中
        if (null != header && !header.isEmpty()) {
            for (String key : header.keySet()) {
                pm.addHeader(key, header.get(key));
            }
        }

        //填入表单参数
        if (formParams != null && !formParams.isEmpty()) {
            pm.setEntity(new UrlEncodedFormEntity(HttpClientUtil.paramsConverter(formParams)));
        }

        //设置超时
        RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(30000).setConnectionRequestTimeout(30000).setSocketTimeout(30000).build();
        pm.setConfig(requestConfig);

        return client.execute(pm);
    }


    /**
     * 基本json方式post请求
     * <pre>
     * @param url
     * @param queryParams
     * @param params
     * @param header
     * @param charset
     * @return
     * @throws Exception
     * Modifications:
     * Modifier wangzeng; 2016年10月10日; Create new Method doJsonPost
     * </pre>
     */
    private HttpResponse doJsonPost(String url, Map<String, String> queryParams, String params, Map<String, String> header, String charset) throws Exception {
        HttpPost pm = new HttpPost();
        URIBuilder builder = new URIBuilder(url);
        //填入查询参数
        if (queryParams != null && !queryParams.isEmpty()) {
            builder.setParameters(HttpClientUtil.paramsConverter(queryParams));
        }
        pm.setURI(builder.build());

        //如果有请求头参数，添加进header中
        if (null != header && !header.isEmpty()) {
            for (String key : header.keySet()) {
                pm.addHeader(key, header.get(key));
            }
        }

        //填入json请求参数
        StringEntity s = new StringEntity(params, ContentType.create("application/json", charset));
        pm.setEntity(s);

        //设置超时
        RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(30000).setConnectionRequestTimeout(30000).setSocketTimeout(30000).build();
        pm.setConfig(requestConfig);

        return client.execute(pm);
    }

    /**
     * 模拟浏览器表单上传文件
     *
     * @param file
     * @param url
     * @param params
     * @param header
     * @return
     */
//    public static String doUploadFile(File file, String filename, String url, Map<String, String> params, Map<String, String> header) {
//        if (StringUtils.isBlank(url) || null == file) {
//            return null;
//        }
//        CloseableHttpClient httpClient = CloseableHttpClientGenerator.getInstance().generateClient();
//        CloseableHttpResponse response = null;
//        try {
//
//            MultipartEntityBuilder multipartEntityBuilder = MultipartEntityBuilder.create();
//            multipartEntityBuilder.setCharset(Charset.defaultCharset());
//            multipartEntityBuilder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
//            multipartEntityBuilder.setBoundary("----------ThIs_Is_tHe_bouNdaRY_$");
//            //multipartEntityBuilder.addPart("file", new FileBody(file, ContentType.DEFAULT_BINARY));
//            multipartEntityBuilder.addBinaryBody("file", file, ContentType.DEFAULT_BINARY, filename);
//            //如果有额外的请求参数
//            if (!params.isEmpty()) {
//                for (String key : params.keySet()) {
//                    multipartEntityBuilder.addPart(key, new StringBody(params.get(key), ContentType.DEFAULT_TEXT));
//                }
//            }
//
//            HttpPost httpPost = new HttpPost(url);
//            httpPost.setEntity(multipartEntityBuilder.build());
//            httpPost.addHeader("Content-Type", "multipart/form-data; boundary=----------ThIs_Is_tHe_bouNdaRY_$");
//            //如果有请求头参数，添加进header中
//            if (null != header && !header.isEmpty()) {
//                for (String key : header.keySet()) {
//                    httpPost.addHeader(key, header.get(key));
//                }
//            }
//
//            response = httpClient.execute(httpPost);
//            int statusCode = response.getStatusLine().getStatusCode();
//            if (statusCode != 200) {
//                httpPost.abort();
//                throw new RuntimeException("HttpClient,error status code :" + statusCode);
//            }
//            HttpEntity entity = response.getEntity();
//            String result = null;
//            if (entity != null) {
//                result = EntityUtils.toString(entity, "UTF-8");
//            }
//            EntityUtils.consume(entity);
//            return result;
//        } catch (Exception e) {
//            e.printStackTrace();
//        } finally {
//            try {
//                if (null != response) {
//                    response.close();
//                }
//
//                if (null != httpClient) {
//                    httpClient.close();
//                }
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        }
//
//        return null;
//    }

    /**
     * 获取当前Http客户端状态中的Cookie
     *
     * @param domain    作用域
     * @param port      端口 传null 默认80
     * @param path      Cookie路径 传null 默认"/"
     * @param useSecure Cookie是否采用安全机制 传null 默认false
     * @return
     */
    public Map<String, Cookie> getCookie(String domain, Integer port, String path, Boolean useSecure) {
        if (domain == null) {
            return null;
        }
        if (port == null) {
            port = 80;
        }
        if (path == null) {
            path = "/";
        }
        if (useSecure == null) {
            useSecure = false;
        }
        List<Cookie> cookies = cookieStore.getCookies();
        if (cookies == null || cookies.isEmpty()) {
            return null;
        }

        CookieOrigin origin = new CookieOrigin(domain, port, path, useSecure);
        @SuppressWarnings("deprecation")
        BestMatchSpec cookieSpec = new BestMatchSpec();
        Map<String, Cookie> retVal = new HashMap<String, Cookie>();
        for (Cookie cookie : cookies) {
            if (cookieSpec.match(cookie, origin)) {
                retVal.put(cookie.getName(), cookie);
            }
        }
        return retVal;
    }

    /**
     * 批量设置Cookie
     *
     * @param cookies   cookie键值对图
     * @param domain    作用域 不可为空
     * @param path      路径 传null默认为"/"
     * @param useSecure 是否使用安全机制 传null 默认为false
     * @return 是否成功设置cookie
     */
    public boolean setCookie(Map<String, String> cookies, String domain, String path, Boolean useSecure) {
        synchronized (cookieStore) {
            if (domain == null) {
                return false;
            }
            if (path == null) {
                path = "/";
            }
            if (useSecure == null) {
                useSecure = false;
            }
            if (cookies == null || cookies.isEmpty()) {
                return true;
            }
            Set<Entry<String, String>> set = cookies.entrySet();
            String key = null;
            String value = null;
            for (Entry<String, String> entry : set) {
                key = entry.getKey();
                value = entry.getValue();
                if (key == null || key.isEmpty() || value == null || value.isEmpty()) {
                    throw new IllegalArgumentException("cookies key and value both can not be empty");
                }
                BasicClientCookie cookie = new BasicClientCookie(key, value);
                cookie.setDomain(domain);
                cookie.setPath(path);
                cookie.setSecure(useSecure);
                cookieStore.addCookie(cookie);
            }
            return true;
        }
    }

    /**
     * 设置单个Cookie
     *
     * @param key       Cookie键
     * @param value     Cookie值
     * @param domain    作用域 不可为空
     * @param path      路径 传null默认为"/"
     * @param useSecure 是否使用安全机制 传null 默认为false
     * @return 是否成功设置cookie
     */
    public boolean setCookie(String key, String value, String domain, String path, Boolean useSecure) {
        Map<String, String> cookies = new HashMap<String, String>();
        cookies.put(key, value);
        return setCookie(cookies, domain, path, useSecure);
    }

    private void saveCookies(String url, HttpResponse response) {
        Header[] cookieHeaders = response.getHeaders("Set-Cookie");
        if (cookieHeaders == null || cookieHeaders.length == 0)
            return;

        String domain = getDomain(url);
        @SuppressWarnings("unused")
        String key, value, expires;
        String path = "/";
        for (int i = 0; i < cookieHeaders.length; i++) {
            String valueStr = cookieHeaders[i].getValue();
            String[] valueArray = valueStr.split(";");
            String[] pairArray = valueArray[0].split("=");
            key = pairArray[0];
            value = pairArray[1];
            for (int j = 1; j < pairArray.length; j++) {
                String pair = pairArray[j];
                if (pair.contains("Expires=")) {
                    pairArray = pair.split("=");
                    expires = pairArray[1];

                } else if (pair.contains("Path=")) {
                    pairArray = pair.split("=");
                    path = pairArray[1];
                }
            }
            if (!StringUtils.isBlank(key) && !StringUtils.isBlank(value))
                setCookie(key, value, domain, path, false);
        }
    }

    public String getDomain(String url) {
        if (StringUtils.isBlank(url))
            return null;
        String domain = "";
        Pattern p = Pattern.compile("(?<=http://|\\.)[^.]*?\\.(com|cn|net|org|biz|info|cc|tv)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = p.matcher(url);
        if (matcher.find())
            domain = matcher.group();
        return domain;
    }
}
