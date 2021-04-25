package com.winson.mockhttputils.config;


import com.google.common.base.Stopwatch;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import javafx.util.Pair;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Slf4j
public class CommonClient {

    private static Gson gson;
    private static OkHttpClient okHttpClient;

    private CommonClient() {
        okHttpClient = new OkHttpClient().newBuilder().readTimeout(5, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS).connectTimeout(5, TimeUnit.SECONDS)
                .connectionPool(new ConnectionPool(2000, 50, TimeUnit.SECONDS)).build();
    }

    private static class HttpClientInstance {
        private static final CommonClient INSTANCE = new CommonClient();
    }

    public static CommonClient getInstance() {
        return HttpClientInstance.INSTANCE;
    }

    private static Request.Builder addHeaders(Request.Builder builder, Map<String, String> headers) {
        if (headers == null) {
            return builder;
        }
        Headers head = Headers.of(headers);
        builder.headers(head);
        return builder;
    }

    private static RequestBody installRequestBody(Map<String, String> params) {
        okhttp3.FormBody.Builder builder = new FormBody.Builder();
        if (params != null) {
            params.entrySet().stream().filter(e -> e.getValue() != null)
                    .forEach(e -> builder.add(e.getKey(), e.getValue()));
        }
        return builder.build();

    }

    private static RequestBody installRequestBody(String param) {
        MediaType mediaType = MediaType.parse("text/plain; charset=utf-8");
        return RequestBody.create(mediaType, param);
    }

    private static RequestBody installRequestBodyForJson(String json) {
        MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
        return RequestBody.create(mediaType, json);
    }

    private static String installRequestUrl(String requestUrl, Map<String, String> params) {
        if (params == null) {
            return requestUrl;
        }
        HttpUrl.Builder builder = HttpUrl.parse(requestUrl).newBuilder();
        params.forEach(builder::addQueryParameter);
        return builder.toString();
    }

    public Request buildPostRequest(String requestUrl, Map<String, String> headers, List<Pair<String, String>> params) {
        Request.Builder builder = new Request.Builder().url(requestUrl);
        if (headers != null) {
            Headers head = Headers.of(headers);
            builder.headers(head);
        }
        okhttp3.FormBody.Builder bodyBuilder = new FormBody.Builder();
        if (params != null) {
            params.stream().filter(e -> e.getValue() != null).forEach(e -> bodyBuilder.add(e.getKey(), e.getValue()));
        }
        builder.post(bodyBuilder.build());
        return builder.build();
    }

    public Request installPostRequest(String requestUrl, Map<String, String> headers, Map<String, String> params) {
        Request.Builder builder = new Request.Builder().url(requestUrl);
        if (headers != null) {
            builder = addHeaders(builder, headers);
        }
        builder.post(installRequestBody(params));
        return builder.build();
    }

    public Request installPostJsonRequest(String requestUrl, Map<String, String> headers, Map<String, Object> params) {
        Request.Builder builder = new Request.Builder().url(requestUrl);
        if (headers != null) {
            builder = addHeaders(builder, headers);
        }
        builder.post(installRequestBodyForJson(gson.toJson(params)));
        return builder.build();
    }

    public Request installPostRequest(String requestUrl, Map<String, String> headers, String param) {
        Request.Builder builder = new Request.Builder().url(requestUrl);
        if (headers != null) {
            builder = addHeaders(builder, headers);
        }
        builder.post(installRequestBody(param));
        return builder.build();
    }

    public Request installGetRequest(String requestUrl, Map<String, String> headers, Map<String, String> params) {
        Request.Builder builder = new Request.Builder();
        if (headers != null) {
            builder = addHeaders(builder, headers);
        }
        return builder.url(installRequestUrl(requestUrl, params)).build();
    }

    public Request installPostRequestForJson(String requestUrl, Map<String, String> headers, String json) {
        Request.Builder builder = new Request.Builder().url(requestUrl);
        if (headers != null) {
            builder = addHeaders(builder, headers);
        }
        builder.post(installRequestBodyForJson(json));
        return builder.build();
    }

    public interface SuccessGetter {
        boolean isSuccess(JsonObject x);
    }

    public interface CodeGetter {
        String getCode(JsonObject x);
    }

    public interface DataGetter {
        JsonElement get(JsonObject x);
    }

    public interface MsgGetter {
        String get(JsonObject x);
    }

    @Data
    public static class Response<T> {
        private boolean success;
        private String msg;
        private String code;
        private T data;
    }

    public Response<?> execute(Request request, SuccessGetter successGetter, MsgGetter msgGetter, DataGetter dataGetter,
                               Type type) {
        return execute(request, successGetter, msgGetter, dataGetter, type, null);
    }

    public Response<?> execute(Request request, SuccessGetter successGetter, MsgGetter msgGetter, DataGetter dataGetter,
                               Type type, CodeGetter codeGetter) {
        Stopwatch stopWatch = Stopwatch.createStarted();
        Response<?> response = new Response<>();
        String result = null;
        try {
            okhttp3.Response responses = okHttpClient.newCall(request).execute();
            boolean successful = responses.isSuccessful();
            if (!successful) {
                response.setSuccess(false);
                return response;
            }
            result = responses.body().string();
            JsonObject jsonObject = gson.fromJson(result, null);
            response.success = successGetter.isSuccess(jsonObject);
            if (codeGetter != null) {
                response.code = codeGetter.getCode(jsonObject);
            } else {
                response.code = jsonObject.has("code") ? jsonObject.get("code").getAsString() : null;
            }
            if (response.success) {
                response.data = gson.fromJson(dataGetter.get(jsonObject), type);
            } else {
                response.msg = msgGetter.get(jsonObject);
            }
        } catch (Exception ex) {
            response.success = false;
            response.msg = "系统繁忙";
            log.error(ex.getMessage(), ex);
        }
        return response;
    }

    public <T> Response<T> execute(Request request, SuccessGetter successGetter, MsgGetter msgGetter, Class<T> tClass) {
        Stopwatch stopWatch = Stopwatch.createStarted();
        Response<T> response = new Response<>();
        String result = null;
        try {
            okhttp3.Response responses = okHttpClient.newCall(request).execute();
            result = responses.body().string();
            JsonObject jsonObject = gson.fromJson(result, null);
            response.success = successGetter.isSuccess(jsonObject);
            response.code = jsonObject.has("code") ? jsonObject.get("code").getAsString() : null;
            if (response.success) {
                jsonObject.remove("code");
                response.data = gson.fromJson(gson.toJson(jsonObject), tClass);
            } else {
                response.msg = msgGetter.get(jsonObject);
            }
        } catch (Exception ex) {
            response.success = false;
            response.msg = "系统繁忙";
            log.error(ex.getMessage(), ex);
        }
        return response;
    }

    public <T> Response<?> execute(Request request, SuccessGetter successGetter, MsgGetter msgGetter,
                                   DataGetter dataGetter, Class<T> tClass) {
        return execute(request, successGetter, msgGetter, dataGetter, tClass, null);
    }

    public <T> Response<?> execute(Request request, SuccessGetter successGetter, CodeGetter codeGetter, MsgGetter msgGetter,
                                   DataGetter dataGetter, Class<T> tClass) {
        return execute(request, successGetter, msgGetter, dataGetter, tClass, codeGetter);
    }

    public String execute(Request request) {
        Stopwatch stopWatch = Stopwatch.createStarted();
        try {
            okhttp3.Response response = okHttpClient.newCall(request).execute();
            return Objects.requireNonNull(response.body()).string();
        } catch (Exception ex) {

            log.error(ex.getMessage(), ex);
        }
        return null;
    }

    public int executeForCode(Request request) {
        Stopwatch stopWatch = Stopwatch.createStarted();
        try (okhttp3.Response response = okHttpClient.newCall(request).execute()) {
            return response.code();
        } catch (Exception ex) {

            log.error(ex.getMessage(), ex);
        }
        return -1;
    }
}
