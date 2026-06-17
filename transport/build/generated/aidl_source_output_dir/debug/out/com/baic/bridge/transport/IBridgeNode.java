/*
 * This file is auto-generated.  DO NOT MODIFY.
 */
package com.baic.bridge.transport;
public interface IBridgeNode extends android.os.IInterface
{
  /** Default implementation for IBridgeNode. */
  public static class Default implements com.baic.bridge.transport.IBridgeNode
  {
    /**
     * 投递一条信封（REQUEST/RESPONSE/EVENT/HELLO 统一入口）。
     * oneway：投递即返回，绝不同步阻塞调用线程——对端再慢也不会拖垮本端 Binder 线程池或触发 ANR。
     * 请求-响应的配对由上层 correlationId + 超时计时器完成，而非 Binder 同步返回值。
     */
    @Override public void deliver(com.baic.bridge.transport.BridgeEnvelope envelope) throws android.os.RemoteException
    {
    }
    /**
     * 注册反向通道：建连方把自己的 IBridgeNode 回调交给对端，形成全双工。
     * 这是 lite 形态（无 Service 的纯客户端 / 宿主外挂）能收到 response/event 的关键。
     * 非 oneway：建连时同步确认一次，确保对端已登记反向通道再开始收发。
     */
    @Override public void attach(com.baic.bridge.transport.IBridgeNode peer, java.lang.String peerNodeId) throws android.os.RemoteException
    {
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements com.baic.bridge.transport.IBridgeNode
  {
    /** Construct the stub at attach it to the interface. */
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an com.baic.bridge.transport.IBridgeNode interface,
     * generating a proxy if needed.
     */
    public static com.baic.bridge.transport.IBridgeNode asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof com.baic.bridge.transport.IBridgeNode))) {
        return ((com.baic.bridge.transport.IBridgeNode)iin);
      }
      return new com.baic.bridge.transport.IBridgeNode.Stub.Proxy(obj);
    }
    @Override public android.os.IBinder asBinder()
    {
      return this;
    }
    @Override public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException
    {
      java.lang.String descriptor = DESCRIPTOR;
      if (code >= android.os.IBinder.FIRST_CALL_TRANSACTION && code <= android.os.IBinder.LAST_CALL_TRANSACTION) {
        data.enforceInterface(descriptor);
      }
      switch (code)
      {
        case INTERFACE_TRANSACTION:
        {
          reply.writeString(descriptor);
          return true;
        }
      }
      switch (code)
      {
        case TRANSACTION_deliver:
        {
          com.baic.bridge.transport.BridgeEnvelope _arg0;
          _arg0 = _Parcel.readTypedObject(data, com.baic.bridge.transport.BridgeEnvelope.CREATOR);
          this.deliver(_arg0);
          break;
        }
        case TRANSACTION_attach:
        {
          com.baic.bridge.transport.IBridgeNode _arg0;
          _arg0 = com.baic.bridge.transport.IBridgeNode.Stub.asInterface(data.readStrongBinder());
          java.lang.String _arg1;
          _arg1 = data.readString();
          this.attach(_arg0, _arg1);
          reply.writeNoException();
          break;
        }
        default:
        {
          return super.onTransact(code, data, reply, flags);
        }
      }
      return true;
    }
    private static class Proxy implements com.baic.bridge.transport.IBridgeNode
    {
      private android.os.IBinder mRemote;
      Proxy(android.os.IBinder remote)
      {
        mRemote = remote;
      }
      @Override public android.os.IBinder asBinder()
      {
        return mRemote;
      }
      public java.lang.String getInterfaceDescriptor()
      {
        return DESCRIPTOR;
      }
      /**
       * 投递一条信封（REQUEST/RESPONSE/EVENT/HELLO 统一入口）。
       * oneway：投递即返回，绝不同步阻塞调用线程——对端再慢也不会拖垮本端 Binder 线程池或触发 ANR。
       * 请求-响应的配对由上层 correlationId + 超时计时器完成，而非 Binder 同步返回值。
       */
      @Override public void deliver(com.baic.bridge.transport.BridgeEnvelope envelope) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _Parcel.writeTypedObject(_data, envelope, 0);
          boolean _status = mRemote.transact(Stub.TRANSACTION_deliver, _data, null, android.os.IBinder.FLAG_ONEWAY);
        }
        finally {
          _data.recycle();
        }
      }
      /**
       * 注册反向通道：建连方把自己的 IBridgeNode 回调交给对端，形成全双工。
       * 这是 lite 形态（无 Service 的纯客户端 / 宿主外挂）能收到 response/event 的关键。
       * 非 oneway：建连时同步确认一次，确保对端已登记反向通道再开始收发。
       */
      @Override public void attach(com.baic.bridge.transport.IBridgeNode peer, java.lang.String peerNodeId) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeStrongInterface(peer);
          _data.writeString(peerNodeId);
          boolean _status = mRemote.transact(Stub.TRANSACTION_attach, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
    }
    static final int TRANSACTION_deliver = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
    static final int TRANSACTION_attach = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
  }
  public static final java.lang.String DESCRIPTOR = "com.baic.bridge.transport.IBridgeNode";
  /**
   * 投递一条信封（REQUEST/RESPONSE/EVENT/HELLO 统一入口）。
   * oneway：投递即返回，绝不同步阻塞调用线程——对端再慢也不会拖垮本端 Binder 线程池或触发 ANR。
   * 请求-响应的配对由上层 correlationId + 超时计时器完成，而非 Binder 同步返回值。
   */
  public void deliver(com.baic.bridge.transport.BridgeEnvelope envelope) throws android.os.RemoteException;
  /**
   * 注册反向通道：建连方把自己的 IBridgeNode 回调交给对端，形成全双工。
   * 这是 lite 形态（无 Service 的纯客户端 / 宿主外挂）能收到 response/event 的关键。
   * 非 oneway：建连时同步确认一次，确保对端已登记反向通道再开始收发。
   */
  public void attach(com.baic.bridge.transport.IBridgeNode peer, java.lang.String peerNodeId) throws android.os.RemoteException;
  /** @hide */
  static class _Parcel {
    static private <T> T readTypedObject(
        android.os.Parcel parcel,
        android.os.Parcelable.Creator<T> c) {
      if (parcel.readInt() != 0) {
          return c.createFromParcel(parcel);
      } else {
          return null;
      }
    }
    static private <T extends android.os.Parcelable> void writeTypedObject(
        android.os.Parcel parcel, T value, int parcelableFlags) {
      if (value != null) {
        parcel.writeInt(1);
        value.writeToParcel(parcel, parcelableFlags);
      } else {
        parcel.writeInt(0);
      }
    }
  }
}
