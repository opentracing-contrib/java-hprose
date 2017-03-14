package io.opentracing.contrib.hprose;

import hprose.common.HproseContext;
import hprose.common.InvokeHandler;
import hprose.common.NextInvokeHandler;
import hprose.server.HttpContext;
import hprose.util.concurrent.Action;
import hprose.util.concurrent.Promise;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMapExtractAdapter;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;

/**
 * Hprose Server side invoke handler.
 *
 * {@link HttpServiceTracingInvokeHandler} extract the OpenTracing Context from http header.
 * And create span on server side.
 *
 */
public class HttpServiceTracingInvokeHandler implements InvokeHandler {
    private final Tracer tracer;

    /**
     * Use {@link TracerLoader} to get a tracer implementation.
     */
    public HttpServiceTracingInvokeHandler()  {
        tracer = TracerLoader.loadTracer();
    }

    /**
     * Set the tracer implementation.
     * @param tracer , a tracer implementation.
     */
    public HttpServiceTracingInvokeHandler(Tracer tracer) {
        this.tracer = tracer;
    }

    /**
     * Extract OpenTracing context from head, and create span.
     * @param headers HTTP headers.
     * @param operationName span's operation name.
     * @return
     */
    private Span getSpanFromHeaders(Map<String, String> headers, String operationName) {
        Span span;
        try {
            SpanContext parentSpanCtx = tracer.extract(Format.Builtin.HTTP_HEADERS,
                new TextMapExtractAdapter(headers));
            if (parentSpanCtx == null) {
                span = tracer.buildSpan(operationName).start();
            } else {
                span = tracer.buildSpan(operationName).asChildOf(parentSpanCtx).start();
            }
        } catch (IllegalArgumentException iae){
            span = tracer.buildSpan(operationName)
                .withTag("Error", "Extract failed and an IllegalArgumentException was thrown")
                .start();
        }
        return span;
    }

    /**
     * Get HTTP headers from {@link HttpContext}, and convert it to {@link Map}
     *
     * @param httpContext
     * @return
     */
    private Map<String, String> getHttpHeader(HttpContext httpContext) {
        Map<String, String> header = new HashMap<String, String>();
        HttpServletRequest request = httpContext.getRequest();
        for (Enumeration<String> names = request.getHeaderNames(); names.hasMoreElements();) {
            String name = names.nextElement();
            header.put(name, request.getHeader(name));
        }
        return header;
    }

    /**
     * Extract the OpenTracing Context,
     * tag and log span
     *
     * @param name ServiceName.
     * @param args All service args.
     * @param context Hprose service context
     * @param next The ref to next invoke handler on invoke chain.
     * @return the Promise of Invoke result. Ref to usage of Promise: https://github.com/hprose/hprose-java/wiki/Hprose-%E4%B8%AD%E9%97%B4%E4%BB%B6#%E8%B0%83%E7%94%A8%E4%B8%AD%E9%97%B4%E4%BB%B6
     */
    public Promise<Object> handle(String name, Object[] args, HproseContext context, NextInvokeHandler next) {
        final HttpContext httpContext = (HttpContext)context;
        final Span span = getSpanFromHeaders(getHttpHeader(httpContext), name);
        span.setTag("hprose.method_name", name);
        span.setTag("hprose.method_alias", httpContext.getRemoteMethod().aliasName);
        span.setTag("hprose.method_type", httpContext.getRemoteMethod().method.toGenericString());
        span.setTag("hprose.mode", httpContext.getRemoteMethod().mode.toString());
        span.setTag("hprose.byref", httpContext.isByref());
        span.setTag("hprose.oneway", httpContext.getRemoteMethod().oneway);
        span.setTag("hprose.simple", httpContext.getRemoteMethod().simple);
        context.set(HttpClientTracingInvokeHandler.KEY_NAME, span);
        return next.handle(name, args, context).whenComplete(new Action<Object>() {
            public void call(Object value) throws Throwable {
                if (value instanceof Throwable) {
                    Throwable error = ((Throwable)value);
                    if (error.getCause() == null) {
                        span.log(error.getCause().getMessage());
                    }
                    else {
                        span.log(error.getMessage());
                    }
                }
                else {
                    span.log("Call completed");
                }
                span.finish();
            }
        });
    }

}
