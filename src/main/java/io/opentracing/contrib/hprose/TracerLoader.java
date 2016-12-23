package io.opentracing.contrib.hprose;

import io.opentracing.NoopTracerFactory;
import io.opentracing.Tracer;

import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.ServiceLoader;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Tracer Implementation Loader.
 * <p>
 * The loader uses {@link java.util.ServiceLoader}.
 * And make sure in the classpath, there is only one implementation,
 * if exist more than one,
 * loader will use {@link io.opentracing.NoopTracer} instead, to avoid implicit choice.
 */
public class TracerLoader {
    private static final Logger LOGGER = Logger.getLogger(TracerLoader.class.getName());

    /**
     * Find a {@link Tracer} implementation.
     *
     * @return {@link Tracer} implementation.
     */
    public static Tracer loadTracer() {
        try {
            Iterator<Tracer> tracers = ServiceLoader.load(Tracer.class).iterator();
            if (tracers.hasNext()) {
                Tracer tracer = tracers.next();
                if (!tracers.hasNext()) {
                    return tracer;
                }
                LOGGER.log(Level.WARNING, "More than one Tracer service implementation found. " + "Falling back to NoopTracer implementation.");
            }
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, ex.getLocalizedMessage());
        }
        return NoopTracerFactory.create();
    }
}
