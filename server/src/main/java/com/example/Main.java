package com.example;

import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import java.net.InetSocketAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
  private static final Logger logger = LoggerFactory.getLogger(Main.class);

  public static void main(String[] args) throws Exception {
    InetSocketAddress address = new InetSocketAddress("localhost", 9099);
    Server server =
        NettyServerBuilder.forAddress(address).addService(new DefaultTestServer()).build().start();

    logger.info("==> server bound on {}:{}", address.getHostName(), address.getPort());

    server.awaitTermination();
  }

  public static class DefaultTestServer extends TestServiceGrpc.TestServiceImplBase {

    @Override
    public void reply(Request request, StreamObserver<Response> responseObserver) {
      responseObserver.onNext(Response.newBuilder().setTimestamp(request.getTimestamp()).build());
      responseObserver.onCompleted();
    }

    @Override
    public void serverStream(Request request, StreamObserver<Response> responseObserver) {
      responseObserver.onError(new UnsupportedOperationException());
    }

    @Override
    public StreamObserver<Request> clientStream(StreamObserver<Response> responseObserver) {
      responseObserver.onError(new UnsupportedOperationException());
      return new StreamObserver<Request>() {
        @Override
        public void onNext(Request value) {}

        @Override
        public void onError(Throwable t) {}

        @Override
        public void onCompleted() {}
      };
    }

    @Override
    public StreamObserver<Request> bidiStream(StreamObserver<Response> responseObserver) {
      responseObserver.onError(new UnsupportedOperationException());
      return new StreamObserver<Request>() {
        @Override
        public void onNext(Request value) {}

        @Override
        public void onError(Throwable t) {}

        @Override
        public void onCompleted() {}
      };
    }
  }
}
