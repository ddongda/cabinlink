/*
 * This file is auto-generated.  DO NOT MODIFY.
 */
package com.baic.cabinlink.pipe;
public interface ILinkKernel extends android.os.IInterface
{
  /** Default implementation for ILinkKernel. */
  public static class Default implements com.baic.cabinlink.pipe.ILinkKernel
  {
    /** @return 0=成功 -1=无权限 -2=id非法 -3=pipe无效 */
    @Override public int register(java.lang.String capabilityId, int version, com.baic.cabinlink.pipe.ICapabilityPipe pipe) throws android.os.RemoteException
    {
      return 0;
    }
    @Override public void unregister(java.lang.String capabilityId) throws android.os.RemoteException
    {
    }
    /** 已注册返回 pipe，否则 null（消费方常用 waitFor） */
    @Override public com.baic.cabinlink.pipe.ICapabilityPipe query(java.lang.String capabilityId) throws android.os.RemoteException
    {
      return null;
    }
    /** 等待能力上线：已在线立即回调；崩溃后恢复也会再次回调（reattach 依据） */
    @Override public void waitFor(java.lang.String capabilityId, com.baic.cabinlink.pipe.ILinkWatcher watcher) throws android.os.RemoteException
    {
    }
    @Override public void unwatch(java.lang.String capabilityId, com.baic.cabinlink.pipe.ILinkWatcher watcher) throws android.os.RemoteException
    {
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements com.baic.cabinlink.pipe.ILinkKernel
  {
    /** Construct the stub at attach it to the interface. */
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an com.baic.cabinlink.pipe.ILinkKernel interface,
     * generating a proxy if needed.
     */
    public static com.baic.cabinlink.pipe.ILinkKernel asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof com.baic.cabinlink.pipe.ILinkKernel))) {
        return ((com.baic.cabinlink.pipe.ILinkKernel)iin);
      }
      return new com.baic.cabinlink.pipe.ILinkKernel.Stub.Proxy(obj);
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
        case TRANSACTION_register:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          int _arg1;
          _arg1 = data.readInt();
          com.baic.cabinlink.pipe.ICapabilityPipe _arg2;
          _arg2 = com.baic.cabinlink.pipe.ICapabilityPipe.Stub.asInterface(data.readStrongBinder());
          int _result = this.register(_arg0, _arg1, _arg2);
          reply.writeNoException();
          reply.writeInt(_result);
          break;
        }
        case TRANSACTION_unregister:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          this.unregister(_arg0);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_query:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          com.baic.cabinlink.pipe.ICapabilityPipe _result = this.query(_arg0);
          reply.writeNoException();
          reply.writeStrongInterface(_result);
          break;
        }
        case TRANSACTION_waitFor:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          com.baic.cabinlink.pipe.ILinkWatcher _arg1;
          _arg1 = com.baic.cabinlink.pipe.ILinkWatcher.Stub.asInterface(data.readStrongBinder());
          this.waitFor(_arg0, _arg1);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_unwatch:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          com.baic.cabinlink.pipe.ILinkWatcher _arg1;
          _arg1 = com.baic.cabinlink.pipe.ILinkWatcher.Stub.asInterface(data.readStrongBinder());
          this.unwatch(_arg0, _arg1);
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
    private static class Proxy implements com.baic.cabinlink.pipe.ILinkKernel
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
      /** @return 0=成功 -1=无权限 -2=id非法 -3=pipe无效 */
      @Override public int register(java.lang.String capabilityId, int version, com.baic.cabinlink.pipe.ICapabilityPipe pipe) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        int _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(capabilityId);
          _data.writeInt(version);
          _data.writeStrongInterface(pipe);
          boolean _status = mRemote.transact(Stub.TRANSACTION_register, _data, _reply, 0);
          _reply.readException();
          _result = _reply.readInt();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public void unregister(java.lang.String capabilityId) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(capabilityId);
          boolean _status = mRemote.transact(Stub.TRANSACTION_unregister, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      /** 已注册返回 pipe，否则 null（消费方常用 waitFor） */
      @Override public com.baic.cabinlink.pipe.ICapabilityPipe query(java.lang.String capabilityId) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        com.baic.cabinlink.pipe.ICapabilityPipe _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(capabilityId);
          boolean _status = mRemote.transact(Stub.TRANSACTION_query, _data, _reply, 0);
          _reply.readException();
          _result = com.baic.cabinlink.pipe.ICapabilityPipe.Stub.asInterface(_reply.readStrongBinder());
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      /** 等待能力上线：已在线立即回调；崩溃后恢复也会再次回调（reattach 依据） */
      @Override public void waitFor(java.lang.String capabilityId, com.baic.cabinlink.pipe.ILinkWatcher watcher) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(capabilityId);
          _data.writeStrongInterface(watcher);
          boolean _status = mRemote.transact(Stub.TRANSACTION_waitFor, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      @Override public void unwatch(java.lang.String capabilityId, com.baic.cabinlink.pipe.ILinkWatcher watcher) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(capabilityId);
          _data.writeStrongInterface(watcher);
          boolean _status = mRemote.transact(Stub.TRANSACTION_unwatch, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
    }
    static final int TRANSACTION_register = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
    static final int TRANSACTION_unregister = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
    static final int TRANSACTION_query = (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
    static final int TRANSACTION_waitFor = (android.os.IBinder.FIRST_CALL_TRANSACTION + 3);
    static final int TRANSACTION_unwatch = (android.os.IBinder.FIRST_CALL_TRANSACTION + 4);
  }
  public static final java.lang.String DESCRIPTOR = "com.baic.cabinlink.pipe.ILinkKernel";
  /** @return 0=成功 -1=无权限 -2=id非法 -3=pipe无效 */
  public int register(java.lang.String capabilityId, int version, com.baic.cabinlink.pipe.ICapabilityPipe pipe) throws android.os.RemoteException;
  public void unregister(java.lang.String capabilityId) throws android.os.RemoteException;
  /** 已注册返回 pipe，否则 null（消费方常用 waitFor） */
  public com.baic.cabinlink.pipe.ICapabilityPipe query(java.lang.String capabilityId) throws android.os.RemoteException;
  /** 等待能力上线：已在线立即回调；崩溃后恢复也会再次回调（reattach 依据） */
  public void waitFor(java.lang.String capabilityId, com.baic.cabinlink.pipe.ILinkWatcher watcher) throws android.os.RemoteException;
  public void unwatch(java.lang.String capabilityId, com.baic.cabinlink.pipe.ILinkWatcher watcher) throws android.os.RemoteException;
}
