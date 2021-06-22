package io.grpc.internal;

import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.netty.channel.ChannelFuture;
import io.grpc.netty.shaded.io.netty.channel.ChannelHandlerContext;
import io.grpc.netty.shaded.io.netty.channel.ChannelPromise;
import io.grpc.netty.shaded.io.netty.handler.codec.http2.*;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GrpcJavaReflectiveFramesListener {
  private static final Logger logger =
      LoggerFactory.getLogger(GrpcJavaReflectiveFramesListener.class);

  public static void set(ManagedChannel channel) {
    try {
      ForwardingManagedChannel orphanWrapper = (ForwardingManagedChannel) channel;
      Field delegateField = ForwardingManagedChannel.class.getDeclaredField("delegate");
      delegateField.setAccessible(true);
      ManagedChannelImpl impl = (ManagedChannelImpl) delegateField.get(orphanWrapper);
      Field subchannelsField = ManagedChannelImpl.class.getDeclaredField("subchannels");
      subchannelsField.setAccessible(true);
      HashSet<InternalSubchannel> subchannels =
          (HashSet<InternalSubchannel>) subchannelsField.get(impl);
      InternalSubchannel internalSubchannel = subchannels.iterator().next();
      Field transportsField = InternalSubchannel.class.getDeclaredField("transports");
      transportsField.setAccessible(true);
      ArrayList<InternalSubchannel.CallTracingTransport> transports =
          (ArrayList<InternalSubchannel.CallTracingTransport>)
              transportsField.get(internalSubchannel);
      InternalSubchannel.CallTracingTransport callTracingTransport = transports.iterator().next();
      Field yetAnotherDelegate =
          InternalSubchannel.CallTracingTransport.class.getDeclaredField("delegate");
      yetAnotherDelegate.setAccessible(true);
      ForwardingConnectionClientTransport callCredentialsApplyingTransport =
          (ForwardingConnectionClientTransport) yetAnotherDelegate.get(callTracingTransport);
      ConnectionClientTransport nettyClientTransport = callCredentialsApplyingTransport.delegate();
      Class<?> nettyClientTransportClass =
          Class.forName("io.grpc.netty.shaded.io.grpc.netty.NettyClientTransport");
      Field handlerField = nettyClientTransportClass.getDeclaredField("handler");
      handlerField.setAccessible(true);
      Http2ConnectionHandler nettyClientHandler =
          (Http2ConnectionHandler) handlerField.get(nettyClientTransport);
      StreamBufferingEncoder streamBufferingEncoder =
          (StreamBufferingEncoder) nettyClientHandler.encoder();
      Field encoderField = Http2ConnectionHandler.class.getDeclaredField("encoder");
      encoderField.setAccessible(true);
      OutboundPingFramesListener listenerEncoder =
          new OutboundPingFramesListener(streamBufferingEncoder);
      encoderField.set(nettyClientHandler, listenerEncoder);
      Http2ConnectionDecoder decoder = nettyClientHandler.decoder();
      Http2FrameListener decoderListener = decoder.frameListener();
      decoder.frameListener(new InboundPingFrameListener(decoderListener));
    } catch (NoSuchFieldException | IllegalAccessException | ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  static class InboundPingFrameListener extends Http2FrameListenerDecorator {
    int rounds;

    public InboundPingFrameListener(Http2FrameListener listener) {
      super(listener);
    }

    @Override
    public void onPingRead(ChannelHandlerContext ctx, long data) throws Http2Exception {
      logger.info("round {} - I am grpc-java client and receiving PING frames", ++rounds);
      super.onPingRead(ctx, data);
    }
  }

  static class OutboundPingFramesListener extends DecoratingHttp2ConnectionEncoder {
    int rounds;

    public OutboundPingFramesListener(Http2ConnectionEncoder delegate) {
      super(delegate);
    }

    @Override
    public ChannelFuture writePing(
        ChannelHandlerContext ctx, boolean ack, long data, ChannelPromise promise) {
      if (!ack) {
        logger.info("round {} - I am grpc-java client and sending PING frames", ++rounds);
      }
      return super.writePing(ctx, ack, data, promise);
    }
  }
}
