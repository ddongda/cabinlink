/*
 * This file is auto-generated.  DO NOT MODIFY.
 */
package com.baic.cabinlink.pipe;
public interface ICapabilityPipe extends android.os.IInterface
{
  /** Default implementation for ICapabilityPipe. */
  public static class Default implements com.baic.cabinlink.pipe.ICapabilityPipe
  {
    /** Call 原语：1 次 IPC 发起，reply 异步回执（含统一错误码） */
    @Override public void invoke(int opCode, android.os.Bundle args, com.baic.cabinlink.pipe.IPipeReply reply) throws android.os.RemoteException
    {
    }
    /**
     * Event/Property 订阅。topics 含属性主题（PROP_TOPIC_BASE+propId）时，
     * 提供方必须立即向该 callback 补推一次全量快照（不变量#3）。
     */
    @Override public void subscribe(int[] topics, com.baic.cabinlink.pipe.IPipeCallback callback) throws android.os.RemoteException
    {
    }
    @Override public void unsubscribe(com.baic.cabinlink.pipe.IPipeCallback callback) throws android.os.RemoteException
    {
    }
    /** Property 快照批量拉取（重连兜底；常态读消费端本地镜像） */
    @Override public android.os.Bundle snapshot(int[] propertyIds) throws android.os.RemoteException
    {
      return null;
    }
    /** 统一健康检查：内核 HealthMonitor 无差别 ping，须原样回传 nonce */
    @Override public int ping(int nonce) throws android.os.RemoteException
    {
      return 0;
    }
    /** 协商：contract version 等元信息，消费端据此做版本门禁 */
    @Override public android.os.Bundle describe() throws android.os.RemoteException
    {
      return null;
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements com.baic.cabinlink.pipe.ICapabilityPipe
  {
    /** Construct the stub at attach it to the interface. */
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an com.baic.cabinlink.pipe.ICapabilityPipe interface,
     * generating a proxy if needed.
     */
    public static com.baic.cabinlink.pipe.ICapabilityPipe asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof com.baic.cabinlink.pipe.ICapabilityPipe))) {
        return ((com.baic.cabinlink.pipe.ICapabilityPipe)iin);
      }
      return new com.baic.cabinlink.pipe.ICapabilityPipe.Stub.Proxy(obj);
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
        case TRANSACTION_invoke:
        {
          int _arg0;
          _arg0 = data.readInt();
          android.os.Bundle _arg1;
          _arg1 = _Parcel.readTypedObject(data, android.os.Bundle.CREATOR);
          com.baic.cabinlink.pipe.IPipeReply _arg2;
          _arg2 = com.baic.cabinlink.pipe.IPipeReply.Stub.asInterface(data.readStrongBinder());
          this.invoke(_arg0, _arg1, _arg2);
          break;
        }
        case TRANSACTION_subscribe:
        {
          int[] _arg0;
          _arg0 = data.createIntArray();
          com.baic.cabinlink.pipe.IPipeCallback _arg1;
          _arg1 = com.baic.cabinlink.pipe.IPipeCallback.Stub.asInterface(data.readStrongBinder());
          this.subscribe(_arg0, _arg1);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_unsubscribe:
        {
          com.baic.cabinlink.pipe.IPipeCallback _arg0;
          _arg0 = com.baic.cabinlink.pipe.IPipeCallback.Stub.asInterface(data.readStrongBinder());
          this.unsubscribe(_arg0);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_snapshot:
        {
          int[] _arg0;
          _arg0 = data.createIntArray();
          android.os.Bundle _result = this.snapshot(_arg0);
          reply.writeNoException();
          _Parcel.writeTypedObject(reply, _result, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
          break;
        }
        case TRANSACTION_ping:
        {
          int _arg0;
          _arg0 = data.readInt();
          int _result = this.ping(_arg0);
          reply.writeNoException();
          reply.writeInt(_result);
          break;
        }
        case TRANSACTION_describe:
        {
          android.os.Bundle _result = this.describe();
          reply.writeNoException();
          _Parcel.writeTypedObject(reply, _result, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
          break;
        }
        default:
        {
          return super.onTransact(code, data, reply, flags);
        }
      }
      return true;
    }
    private static class Proxy implements com.baic.cabinlink.pipe.ICapabilityPipe
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
      /** Call 原语：1 次 IPC 发起，reply 异步回执（含统一错误码） */
      @Override public void invoke(int opCode, android.os.Bundle args, com.baic.cabinlink.pipe.IPipeReply reply) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeInt(opCode);
          _Parcel.writeTypedObject(_data, args, 0);
          _data.writeStrongInterface(reply);
          boolean _status = mRemote.transact(Stub.TRANSACTION_invoke, _data, null, android.os.IBinder.FLAG_ONEWAY);
        }
        finally {
          _data.recycle();
        }
      }
      /**
       * Event/Property 订阅。topics 含属性主题（PROP_TOPIC_BASE+propId）时，
       * 提供方必须立即向该 callback 补推一次全量快照（不变量#3）。
       */
      @Override public void subscribe(int[] topics, com.baic.cabinlink.pipe.IPipeCallback callback) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeIntArray(topics);
          _data.writeStrongInterface(callback);
          boolean _status = mRemote.transact(Stub.TRANSACTION_subscribe, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      @Override public void unsubscribe(com.baic.cabinlink.pipe.IPipeCallback callback) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeStrongInterface(callback);
          boolean _status = mRemote.transact(Stub.TRANSACTION_unsubscribe, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      /** Property 快照批量拉取（重连兜底；常态读消费端本地镜像） */
      @Override public android.os.Bundle snapshot(int[] propertyIds) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        android.os.Bundle _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeIntArray(propertyIds);
          boolean _status = mRemote.transact(Stub.TRANSACTION_snapshot, _data, _reply, 0);
          _reply.readException();
          _result = _Parcel.readTypedObject(_reply, android.os.Bundle.CREATOR);
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      /** 统一健康检查：内核 HealthMonitor 无差别 ping，须原样回传 nonce */
      @Override public int ping(int nonce) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        int _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeInt(nonce);
          boolean _status = mRemote.transact(Stub.TRANSACTION_ping, _data, _reply, 0);
          _reply.readException();
          _result = _reply.readInt();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      /** 协商：contract version 等元信息，消费端据此做版本门禁 */
      @Override public android.os.Bundle describe() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        android.os.Bundle _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_describe, _data, _reply, 0);
          _reply.readException();
          _result = _Parcel.readTypedObject(_reply, android.os.Bundle.CREATOR);
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
    }
    static final int TRANSACTION_invoke = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
    static final int TRANSACTION_subscribe = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
    static final int TRANSACTION_unsubscribe = (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
    static final int TRANSACTION_snapshot = (android.os.IBinder.FIRST_CALL_TRANSACTION + 3);
    static final int TRANSACTION_ping = (android.os.IBinder.FIRST_CALL_TRANSACTION + 4);
    static final int TRANSACTION_describe = (android.os.IBinder.FIRST_CALL_TRANSACTION + 5);
  }
  public static final java.lang.String DESCRIPTOR = "com.baic.cabinlink.pipe.ICapabilityPipe";
  /** Call 原语：1 次 IPC 发起，reply 异步回执（含统一错误码） */
  public void invoke(int opCode, android.os.Bundle args, com.baic.cabinlink.pipe.IPipeReply reply) throws android.os.RemoteException;
  /**
   * Event/Property 订阅。topics 含属性主题（PROP_TOPIC_BASE+propId）时，
   * 提供方必须立即向该 callback 补推一次全量快照（不变量#3）。
   */
  public void subscribe(int[] topics, com.baic.cabinlink.pipe.IPipeCallback callback) throws android.os.RemoteException;
  public void unsubscribe(com.baic.cabinlink.pipe.IPipeCallback callback) throws android.os.RemoteException;
  /** Property 快照批量拉取（重连兜底；常态读消费端本地镜像） */
  public android.os.Bundle snapshot(int[] propertyIds) throws android.os.RemoteException;
  /** 统一健康检查：内核 HealthMonitor 无差别 ping，须原样回传 nonce */
  public int ping(int nonce) throws android.os.RemoteException;
  /** 协商：contract version 等元信息，消费端据此做版本门禁 */
  public android.os.Bundle describe() throws android.os.RemoteException;
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
