package net.emiva.openfire.netty;

import java.util.concurrent.TimeUnit;
import org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor;
import net.emiva.concurrent.NamedThreadFactory;

public class RequestThreadPoolExecutor extends OrderedMemoryAwareThreadPoolExecutor
{
    private final static int CORE_THREAD_TIMEOUT_SEC = 30;

    public RequestThreadPoolExecutor()
    {
        super(16,
                0, 0,
                CORE_THREAD_TIMEOUT_SEC, TimeUnit.SECONDS,
                new NamedThreadFactory("Native-Transport-Requests"));
    }
}
