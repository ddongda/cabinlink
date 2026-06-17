/*
 * This file is auto-generated.  DO NOT MODIFY.
 */
package com.baic.cabinlink.pipe;
public interface IPipeReply extends android.os.IInterface
{
  /** Default implementation for IPipeReply. */
  public static class Default implements com.baic.cabinlink.pipe.IPipeReply
  {
    /** code: 0=OK；1xx 总线保留段；2xx 起业务错误（见 runtime LinkResult） */
    @Override public void onResult(int code, android.os.Bundle data) throws android.os.RemoteException
    {
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements com.baic.cabinlink.pipe.IPipeReply
  {
    /** Construct the stub at attach it to the interface. */
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an com.baic.cabinlink.pipe.IPipeReply interface,
     * generating a proxy if needed.
     */
    public static com.baic.cabinlink.pipe.IPipeReply asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof com.baic.cabinlink.pipe.IPipeReply))) {
        return ((com.baic.cabinlink.pipe.IPipeReply)iin);
      }
      return new com.baic.cabinlink.pipe.IPipeReply.Stub.Proxy(obj);
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
        case TRANSACTION_onResult:
        {
          int _arg0;
          _arg0 = data.readInt();
          android.os.Bundle _arg1;
          _arg1 = _Parcel.readTypedObject(data, android.os.Bundle.CREATOR);
          this.onResult(_arg0, _arg1);
          break;
        }
        default:
        {
          return super.onTransact(code, data, reply, flags);
        }
      }
      return true;
    }
    private static class Proxy implements com.baic.cabinlink.pipe.IPipeReply
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
      /** code: 0=OK；1xx 总线保留段；2xx 起业务错误（见 runtime LinkResult） */
      @Override public void onResult(int code, android.os.Bundle data) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeInt(code);
          _Parcel.writeTypedObject(_data, data, 0);
          boolean _status = mRemote.transact(Stub.TRANSACTION_onResult, _data, null, android.os.IBinder.FLAG_ONEWAY);
        }
        finally {
          _data.recycle();
        }
      }
    }
    static final int TRANSACTION_onResult = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
  }
  public static final java.lang.String DESCRIPTOR = "com.baic.cabinlink.pipe.IPipeReply";
  /** code: 0=OK；1xx 总线保留段；2xx 起业务错误（见 runtime LinkResult） */
  public void onResult(int code, android.os.Bundle data) throws android.os.RemoteException;
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
