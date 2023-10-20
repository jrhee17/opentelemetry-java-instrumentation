package io.opentelemetry.javaagent.instrumentation.armeria.v1_3;

import static org.assertj.core.api.Assertions.assertThat;

import com.linecorp.armeria.client.ClientRequestContext;
import com.linecorp.armeria.client.ClientRequestContextCaptor;
import com.linecorp.armeria.client.Clients;
import com.linecorp.armeria.server.ServerBuilder;
import com.linecorp.armeria.server.grpc.GrpcService;
import com.linecorp.armeria.testing.junit4.server.ServerRule;
import example.GreeterGrpc;
import example.Helloworld;
import io.grpc.stub.StreamObserver;
import org.junit.ClassRule;
import org.junit.Test;

public class ArmeriaGrpcClientTest {

  @ClassRule
  public static ServerRule server = new ServerRule() {
    @Override
    protected void configure(ServerBuilder sb) {
      sb.service(GrpcService.builder()
          .addService(new GreeterGrpc.GreeterImplBase() {
            @Override
            public void sayHello(Helloworld.Request request,
                StreamObserver<Helloworld.Response> responseObserver) {
              responseObserver.onNext(Helloworld.Response.newBuilder()
                  .setMessage("hello").build());
              responseObserver.onCompleted();
            }
          })
          .build());
    }
  };

  @Test
  public void grpcClientHasDecorator() {
    try (ClientRequestContextCaptor captor = Clients.newContextCaptor()) {
      GreeterGrpc.GreeterBlockingStub stub = Clients.newClient("gproto+" + server.httpUri(),
          GreeterGrpc.GreeterBlockingStub.class);
      Helloworld.Response res = stub.sayHello(Helloworld.Request.getDefaultInstance());
      assertThat(res.getMessage()).isEqualTo("hello");
      assertThat(captor.size()).isEqualTo(1);
      ClientRequestContext ctx = captor.get();
      assertThat(ctx.options().decoration().decorators()).containsExactly(ArmeriaSingletons.CLIENT_DECORATOR);
    }
  }
}
