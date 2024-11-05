package ru.netology;


import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class Request {
    private String nameMethod;
    private List<String> headers;
    private String path;
    private byte [] body;

    private List <NameValuePair> queryParams;



    public Request(String nameMethod, String path, List<String> headers) {
        this.nameMethod = nameMethod;
        this.headers = headers;
        this.path = path;
    }
    public void setQueryParams(List<NameValuePair> queryParams) {
        this.queryParams = queryParams;
    }

    public String getNameMethod() {
        return nameMethod;
    }

    public void setNameMethod(String nameMethod) {
        this.nameMethod = nameMethod;
    }

    public List<String> getHeaders() {
        return headers;
    }

    public void setHeaders(List<String> headers) {
        this.headers = headers;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public byte[] getBody() {
        return body;
    }

    public void setBody(byte[] body) {
        this.body = body;
    }

    public List<NameValuePair> getQueryParams(){
        return queryParams;
    }
    public List<NameValuePair> getQueryParam(String name){
        return queryParams.stream().filter(k->k.getName().equals(name)).collect(Collectors.toList());
    }

    @Override
    public String toString() {
        return "Request{" +
                "nameMethod='" + nameMethod + '\'' +
                ",\n headers=" + headers +
                ",\n path='" + path + '\'' +
                ",\n body=" + Arrays.toString(body) +
                ",\n queryParams=" + queryParams +
                '}';
    }
}
