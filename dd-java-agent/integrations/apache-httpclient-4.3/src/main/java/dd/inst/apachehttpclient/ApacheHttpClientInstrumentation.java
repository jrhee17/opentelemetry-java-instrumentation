package dd.inst.apachehttpclient;

import static dd.trace.ClassLoaderMatcher.classLoaderHasClasses;
import static net.bytebuddy.matcher.ElementMatchers.*;

import com.datadoghq.agent.integration.DDTracingClientExec;
import com.google.auto.service.AutoService;
import dd.trace.DDAdvice;
import dd.trace.Instrumenter;
import io.opentracing.util.GlobalTracer;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.impl.execchain.ClientExecChain;

@AutoService(Instrumenter.class)
public class ApacheHttpClientInstrumentation implements Instrumenter {

  @Override
  public AgentBuilder instrument(AgentBuilder agentBuilder) {
    return agentBuilder
        .type(
            named("org.apache.http.impl.client.HttpClientBuilder"),
            classLoaderHasClasses(
                "org.apache.http.HttpException",
                "org.apache.http.HttpRequest",
                "org.apache.http.client.RedirectStrategy",
                "org.apache.http.client.methods.CloseableHttpResponse",
                "org.apache.http.client.methods.HttpExecutionAware",
                "org.apache.http.client.methods.HttpRequestWrapper",
                "org.apache.http.client.protocol.HttpClientContext",
                "org.apache.http.conn.routing.HttpRoute",
                "org.apache.http.impl.execchain.ClientExecChain"))
        .transform(
            DDAdvice.create()
                .advice(
                    isMethod().and(named("decorateProtocolExec")),
                    ApacheHttpClientAdvice.class.getName()))
        .asDecorator();
  }

  public static class ApacheHttpClientAdvice {
    /** Strategy: add our tracing exec to the apache exec chain. */
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void addTracingExec(@Advice.Return(readOnly = false) ClientExecChain execChain) {
      execChain =
          new DDTracingClientExec(
              execChain, DefaultRedirectStrategy.INSTANCE, false, GlobalTracer.get());
    }
  }
}
