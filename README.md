# java-hprose
OpenTracing instrumentation for the Hprose Remote Object Service Engine

[![Build Status](https://travis-ci.org/opentracing-contrib/java-hprose.svg?branch=master)](https://travis-ci.org/opentracing-contrib/java-hprose)

## Quick start
This repo support the opentracing in hprose-java, if hprose-java use http or https as network protocol.

### Server side
#### Using config 
* If you use `hprose servlet` to open hprose services. Add `init-param` on the `hprose servlet`.
```xml
<init-param>
    <param-name>invoke</param-name>
    <param-value>io.opentracing.contrib.hprose.HttpServiceTracingInvokeHandler</param-value>
</init-param>
```

_If you use other InvokeHandlers, except `HttpServiceTracingInvokeHandler`, you should use comma(`,`) between these InvokeHandlers._

#### Using code
```java
package hprose.exam.server;
import hprose.common.HproseMethods;
import hprose.server.HproseServlet;
import io.opentracing.contrib.hprose.HttpServiceTracingInvokeHandler;

public class MyHproseServlet extends HproseServlet {
    public String hello(String name) {
        return "Hello " + name;
    }
    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        service.use(new HttpServiceTracingInvokeHandler());
        service.add("hello", this);
    }
}
```

* By default, `HttpServiceTracingInvokeHandler` use java `ServiceLoader` to locate OpenTracing tracer implementation. The implementation must be **unique**, if more than one implemetations are found, `HttpServiceTracingInvokeHandler` don't use any of them, but swtich to NoopTracer.
* if you want to use the particular tracer implementation, you can use `HttpServiceTracingInvokeHandler`'constructor to set the tracer instance. like this:
```java
service.use(new HttpServiceTracingInvokeHandler(tracer));
```

### Client side
* Use hprose_for_java_x edition, choose `HttpClientTracingExInvokeHandler`.
```java
client.use(new HttpClientTracingExInvokeHandler(tracer));
```

* Use pure client edition, hprose_client_for_java_x edition, choose `HttpClientTracingInvokeHandler`.
```java
client.use(new HttpClientTracingInvokeHandler(tracer));
```

* Also, like server side, you can use java `ServiceLoader` to locate OpenTracing tracer implementation, or use `HttpClientTracingExInvokeHandler` or `HttpClientTracingInvokeHandler` constructor to set the tracer instance.
