package io.opentracing.contrib.hprose;

import hprose.common.HproseContext;
import hprose.common.NextInvokeHandler;
import hprose.server.HproseHttpService;
import hprose.server.HttpContext;
import hprose.util.concurrent.Promise;
import io.opentracing.Tracer;

/**
 * Hprose have two type library: pure client edition and all-in-one client edition.
 * <p>
 * {@link HttpClientTracingExInvokeHandler} should be used in all-in-one client edition.
 * You can add {@link HttpClientTracingExInvokeHandler} into the invoke chain,
 * it will process create an OpenTracing span and inject OpenTracing context for out-process propagation.
 */
public class HttpClientTracingExInvokeHandler extends HttpClientTracingInvokeHandler {

    /**
     * Use {@TracerLoader} to get a tracer implementation.
     */
    public HttpClientTracingExInvokeHandler() {
        super();
    }

    /**
     * Set the tracer implementation.
     *
     * @param tracer , a tracer implementation.
     */
    public HttpClientTracingExInvokeHandler(Tracer tracer) {
        super(tracer);
    }

    /**
     * Create an OpenTracing span and inject OpenTracing context for out-process propagation.
     * It will use {@link HproseHttpService#getCurrentContext()} to obtain the active-span.
     *
     * @param name    ServiceName.
     * @param args    All service args.
     * @param context Hprose service context
     * @param next    The ref to next invoke handler on invoke chain.
     * @return the Promise of Invoke result. Ref to usage of Promise: https://github.com/hprose/hprose-java/wiki/Hprose-%E4%B8%AD%E9%97%B4%E4%BB%B6#%E8%B0%83%E7%94%A8%E4%B8%AD%E9%97%B4%E4%BB%B6
     */
    @Override
    public Promise<Object> handle(String name, Object[] args, HproseContext context, NextInvokeHandler next) {
        HttpContext httpContext = HproseHttpService.getCurrentContext();
        if (httpContext != null) {
            context.set(KEY_NAME, httpContext.get(KEY_NAME));
        }
        return super.handle(name, args, context, next);
    }
}
