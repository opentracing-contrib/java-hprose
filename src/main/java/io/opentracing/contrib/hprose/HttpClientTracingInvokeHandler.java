package io.opentracing.contrib.hprose;

import hprose.client.ClientContext;
import hprose.common.HproseContext;
import hprose.common.InvokeHandler;
import hprose.common.InvokeSettings;
import hprose.common.NextInvokeHandler;
import hprose.util.concurrent.Action;
import hprose.util.concurrent.Promise;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Hprose have two type library: pure client edition and all-in-one client edition.
 *
 * {@link HttpClientTracingInvokeHandler} should be used in pure client edition.
 * Also it is the parent class of {@link HttpClientTracingExInvokeHandler}.
 *
 * You can add {@link HttpClientTracingExInvokeHandler} into the invoke chain,
 * it will process create an OpenTracing span and inject OpenTracing context for out-process propagation
 *
 */
public class HttpClientTracingInvokeHandler implements InvokeHandler {
    public static final String KEY_NAME = "io.opentracing.active-span";
    private final Tracer tracer;

    /**
     * Use {@TracerLoader} to get a tracer implementation.
     */
    public HttpClientTracingInvokeHandler() {
        tracer = TracerLoader.loadTracer();
    }

    /**
     * Set the tracer implementation.
     * @param tracer , a tracer implementation.
     */
    public HttpClientTracingInvokeHandler(Tracer tracer) {
        this.tracer = tracer;
    }

    /**
     * Create a span
     * @param parentSpan , maybe not existed, if this span is the first of a trace.
     * @param operationName, ref to OpenTracing Spec
     * @return
     */
    private Span createSpanFromParent(Object parentSpan, String operationName) {
        if (parentSpan != null) {
            return tracer.buildSpan(operationName).asChildOf((Span) parentSpan).start();
        } else {
            return tracer.buildSpan(operationName).start();
        }
    }

    /**
     * Get Service Header
     *
     * @param context Hprose Rpc Context
     * @return
     */
    private Map<String, List<String>> getHttpHeader(HproseContext context) {
        Map<String, List<String>> header = (Map<String, List<String>>) (context.get("httpHeader"));
        if (header == null) {
            return new HashMap<String, List<String>>();
        }
        return header;
    }

    /**
     * Create an OpenTracing span with tags, events.
     * Inject OpenTracing context for out-process propagation.
     *
     * @param name ServiceName.
     * @param args All service args.
     * @param context Hprose service context
     * @param next The ref to next invoke handler on invoke chain.
     * @return the Promise of Invoke result. Ref to usage of Promise: https://github.com/hprose/hprose-java/wiki/Hprose-%E4%B8%AD%E9%97%B4%E4%BB%B6#%E8%B0%83%E7%94%A8%E4%B8%AD%E9%97%B4%E4%BB%B6
     */
    public Promise<Object> handle(String name, Object[] args, HproseContext context, NextInvokeHandler next) {
        final Span span = createSpanFromParent(context.get(KEY_NAME), name);
        final ClientContext clientContext = (ClientContext) context;
        final InvokeSettings settings = clientContext.getSettings();
        span.setTag("hprose.method_name", name);
        span.setTag("hprose.return_type", settings.getReturnType().toString());
        span.setTag("hprose.mode", settings.getMode().toString());
        span.setTag("hprose.byref", settings.isByref());
        span.setTag("hprose.async", settings.isAsync());
        span.setTag("hprose.oneway", settings.isOneway());
        span.setTag("hprose.simple", settings.isSimple());
        span.setTag("hprose.idempotent", settings.isIdempotent());
        span.setTag("hprose.failswitch", settings.isFailswitch());
        span.setTag("hprose.retry", settings.getRetry());
        span.setTag("hprose.timeout", settings.getTimeout());
        final Map<String, List<String>> httpHeader = getHttpHeader(context);
        tracer.inject(span.context(), Format.Builtin.HTTP_HEADERS, new TextMap() {
            @Override
            public Iterator<Map.Entry<String, String>> iterator() {
                throw new UnsupportedOperationException("TextMapInjectAdapter should only be used with Tracer.inject()");
            }

            @Override
            public void put(String key, String value) {
                httpHeader.put(key, Arrays.asList(value));
            }
        });
        context.set("httpHeader", httpHeader);
        return next.handle(name, args, context).whenComplete(new Action<Object>() {
            public void call(Object value) throws Throwable {
                if (value instanceof Throwable) {
                    Throwable error = ((Throwable) value);
                    if (error.getCause() == null) {
                        span.log(error.getCause().getMessage());
                    } else {
                        span.log(error.getMessage());
                    }
                } else {
                    span.log("Call completed");
                }
                span.finish();
            }
        });
    }

}
