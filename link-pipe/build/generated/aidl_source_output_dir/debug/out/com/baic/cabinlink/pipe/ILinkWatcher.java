/*
 * This file is auto-generated.  DO NOT MODIFY.
 */
package com.baic.cabinlink.pipe;
public interface ILinkWatcher extends android.os.IInterface
{
  /** Default implementation for ILinkWatcher. */
  public static class Default implements com.baic.cabinlink.pipe.ILinkWatcher
  {
    @Override public void onAvailable(java.lang.String capabilityId, com.baic.cabinlink.pipe.ICapabilityPipe pipe, int version) throws android.os.RemoteException
    {
    }
    @Override public void onUnavailable(java.lang.String capabilityId) throws android.os.RemoteException
    {
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements com.baic.cabinlink.pipe.ILinkWatcher
  {
    /** Construct the stub at attach it to the interface. */
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an com.baic.cabinlink.pipe.ILinkWatcher interface,
     * generating a proxy if needed.
     */
    public static com.baic.cabinlink.pipe.ILinkWatcher asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof com.baic.cabinlink.pipe.ILinkWatcher))) {
        return ((com.baic.cabinlink.pipe.ILinkWatcher)iin);
      }
      return new com.baic.cabinlink.pipe.ILinkWatcher.Stub.Proxy(obj);
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
        case TRANSACTION_onAvailable:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          com.baic.cabinlink.pipe.ICapabilityPipe _arg1;
          _arg1 = com.baic.cabinlink.pipe.ICapabilityPipe.Stub.asInterface(data.readStrongBinder());
          int _arg2;
          _arg2 = data.readInt();
          this.onAvailable(_arg0, _arg1, _arg2);
          break;
        }
        case TRANSACTION_onUnavailable:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          this.onUnavailable(_arg0);
          break;
        }
        default:
        {
          return super.onTransact(code, data, reply, flags);
        }
      }
      return true;
    }
    private static class Proxy implements com.baic.cabinlink.pipe.ILinkWatcher
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
      @Override public void onAvailable(java.lang.String capabilityId, com.baic.cabinlink.pipe.ICapabilityPipe pipe, int version) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(capabilityId);
          _data.writeStrongInterface(pipe);
          _data.writeInt(version);
          boolean _status = mRemote.transact(Stub.TRANSACTION_onAvailable, _data, null, android.os.IBinder.FLAG_ONEWAY);
        }
        finally {
          _data.recycle();
        }
      }
      @Override public void onUnavailable(java.lang.String capabilityId) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(capabilityId);
          boolean _status = mRemote.transact(Stub.TRANSACTION_onUnavailable, _data, null, android.os.IBinder.FLAG_ONEWAY);
        }
        finally {
          _data.recycle();
        }
      }
    }
    static final int TRANSACTION_onAvailable = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
    static final int TRANSACTION_onUnavailable = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
  }
  public static final java.lang.String DESCRIPTOR = "com.baic.cabinlink.pipe.ILinkWatcher";
  public void onAvailable(java.lang.String capabilityId, com.baic.cabinlink.pipe.ICapabilityPipe pipe, int version) throws android.os.RemoteException;
  public void onUnavailable(java.lang.String capabilityId) throws android.os.RemoteException;
}
