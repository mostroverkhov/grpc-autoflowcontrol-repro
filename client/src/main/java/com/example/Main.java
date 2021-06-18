package com.example;

import io.grpc.Codec;
import io.grpc.CompressorRegistry;
import io.grpc.DecompressorRegistry;
import io.grpc.ManagedChannel;
import io.grpc.internal.GrpcJavaReflectiveFramesListener;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientResponseObserver;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
  private static final Logger logger = LoggerFactory.getLogger(Main.class);

  public static void main(String[] args) throws Exception {
    CompressorRegistry compressorRegistry = CompressorRegistry.newEmptyInstance();
    compressorRegistry.register(Codec.Identity.NONE);
    DecompressorRegistry decompressorRegistry =
        DecompressorRegistry.emptyInstance().with(Codec.Identity.NONE, true);
    ManagedChannel channel =
        NettyChannelBuilder.forAddress("localhost", 9099)
            .usePlaintext()
            // .flowControlWindow(10_000)
            .compressorRegistry(compressorRegistry)
            .decompressorRegistry(decompressorRegistry)
            .build();

    Counter counter = new Counter();

    ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    ScheduledFuture<?> counterFuture =
        executorService.scheduleAtFixedRate(
            () -> {
              int c = counter.responses();
              logger.info("Client received messages: {}", c);
            },
            1,
            1,
            TimeUnit.SECONDS);

    TestServiceGrpc.TestServiceStub client = TestServiceGrpc.newStub(channel);

    Request request = Request.newBuilder().setTimestamp(System.currentTimeMillis()).build();

    AtomicBoolean hasListener = new AtomicBoolean();

    counter.requests(
        requests -> {
          for (int i = 0; i < requests; i++) {
            client.reply(
                request,
                new ClientResponseObserver<Request, Response>() {
                  @Override
                  public void beforeStart(ClientCallStreamObserver<Request> requestStream) {}

                  @Override
                  public void onNext(Response value) {
                    if (!hasListener.get() && hasListener.compareAndSet(false, true)) {
                      GrpcJavaReflectiveFramesListener.set(channel);
                    }
                    counter.count();
                  }

                  @Override
                  public void onError(Throwable t) {
                    logger.error("Received error", t);
                    counterFuture.cancel(true);
                    channel.shutdownNow();
                  }

                  @Override
                  public void onCompleted() {}
                });
          }
        });
    channel.awaitTermination(365, TimeUnit.DAYS);
  }

  private static class Counter {
    private static final int REQUESTS = 77;

    private final AtomicInteger responses = new AtomicInteger();
    private final AtomicInteger requests = new AtomicInteger(REQUESTS);
    private volatile IntConsumer requestsConsumer;

    void count() {
      responses.incrementAndGet();
      int r = requests.decrementAndGet();
      if (r == REQUESTS / 2) {
        requests.addAndGet(REQUESTS);
        requestsConsumer.accept(REQUESTS);
      }
    }

    void requests(IntConsumer requestsConsumer) {
      this.requestsConsumer = requestsConsumer;
      requestsConsumer.accept(REQUESTS);
    }

    int responses() {
      return responses.getAndSet(0);
    }
  }
}
