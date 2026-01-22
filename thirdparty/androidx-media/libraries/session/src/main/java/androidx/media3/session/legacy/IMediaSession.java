/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.media3.session.legacy;

import static androidx.annotation.RestrictTo.Scope.LIBRARY;
import static androidx.media3.common.util.Assertions.checkNotNull;

import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.media3.common.util.UnstableApi;
import org.checkerframework.checker.nullness.qual.PolyNull;

/** Interface to a MediaSessionCompat. */
@UnstableApi
@RestrictTo(LIBRARY)
public interface IMediaSession extends android.os.IInterface {
  /** Local-side IPC implementation stub class. */
  public abstract static class Stub extends android.os.Binder implements IMediaSession {
    private static final String DESCRIPTOR = "android.support.v4.media.session.IMediaSession";

    /** Construct the stub at attach it to the interface. */
    // Using this in constructor
    @SuppressWarnings({"method.invocation.invalid", "argument.type.incompatible"})
    public Stub() {
      this.attachInterface(this, DESCRIPTOR);
    }

    /**
     * Cast an IBinder object into an androidx.media3.session.legacy.IMediaSession interface,
     * generating a proxy if needed.
     */
    public static @PolyNull IMediaSession asInterface(android.os.@PolyNull IBinder obj) {
      if ((obj == null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin != null) && (iin instanceof IMediaSession))) {
        return ((IMediaSession) iin);
      }
      return new Proxy(obj);
    }

    @Override
    public android.os.IBinder asBinder() {
      return this;
    }

    @Override
    public boolean onTransact(
        int code, android.os.Parcel data, @Nullable android.os.Parcel reply, int flags)
        throws android.os.RemoteException {
      String descriptor = DESCRIPTOR;
      switch (code) {
        case INTERFACE_TRANSACTION:
          {
            checkNotNull(reply).writeString(descriptor);
            return true;
          }
        case TRANSACTION_sendCommand:
          {
            data.enforceInterface(descriptor);
            String _arg0;
            _arg0 = data.readString();
            android.os.Bundle _arg1;
            if ((0 != data.readInt())) {
              _arg1 = android.os.Bundle.CREATOR.createFromParcel(data);
            } else {
              _arg1 = null;
            }
            MediaSessionCompat.ResultReceiverWrapper _arg2;
            if ((0 != data.readInt())) {
              _arg2 = MediaSessionCompat.ResultReceiverWrapper.CREATOR.createFromParcel(data);
            } else {
              _arg2 = null;
            }
            this.sendCommand(_arg0, _arg1, _arg2);
            checkNotNull(reply).writeNoException();
            return true;
          }
        case TRANSACTION_sendMediaButton:
          {
            data.enforceInterface(descriptor);
            android.view.KeyEvent _arg0;
            if ((0 != data.readInt())) {
              _arg0 = android.view.KeyEvent.CREATOR.createFromParcel(data);
            } else {
              _arg0 = null;
            }
            boolean _result = this.sendMediaButton(_arg0);
            checkNotNull(reply).writeNoException();
            checkNotNull(reply).writeInt(((_result) ? (1) : (0)));
            return true;
          }
        case TRANSACTION_registerCallbackListener:
          {
            data.enforceInterface(descriptor);
            IMediaControllerCallback _arg0;
            _arg0 = IMediaControllerCallback.Stub.asInterface(data.readStrongBinder());
            this.registerCallbackListener(_arg0);
            checkNotNull(reply).writeNoException();
            return true;
          }
        case TRANSACTION_unregisterCallbackListener:
          {
            data.enforceInterface(descriptor);
            IMediaControllerCallback _arg0;
            _arg0 = IMediaControllerCallback.Stub.asInterface(data.readStrongBinder());
            this.unregisterCallbackListener(_arg0);
            checkNotNull(reply).writeNoException();
            return true;
          }
        case TRANSACTION_isTransportControlEnabled:
          {
            data.enforceInterface(descriptor);
            boolean _result = this.isTransportControlEnabled();
            checkNotNull(reply).writeNoException();
            checkNotNull(reply).writeInt(((_result) ? (1) : (0)));
            return true;
          }
        case TRANSACTION_getPackageName:
          {
            data.enforceInterface(descriptor);
            String _result = this.getPackageName();
            checkNotNull(reply).writeNoException();
            checkNotNull(reply).writeString(_result);
            return true;
          }
        case TRANSACTION_getTag:
          {
            data.enforceInterface(descriptor);
            String _result = this.getTag();
            checkNotNull(reply).writeNoException();
            checkNotNull(reply).writeString(_result);
            return true;
          }
        case TRANSACTION_getLaunchPendingIntent:
          {
            data.enforceInterface(descriptor);
            android.app.PendingIntent _result = this.getLaunchPendingIntent();
            checkNotNull(reply).writeNoException();
            if ((_result != null)) {
              checkNotNull(reply).writeInt(1);
              _result.writeToParcel(reply, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
            } else {
              checkNotNull(reply).writeInt(0);
            }
            return true;
          }
        case TRANSACTION_getFlags:
          {
            data.enforceInterface(descriptor);
            long _result = this.getFlags();
            checkNotNull(reply).writeNoException();
            checkNotNull(reply).writeLong(_result);
            return true;
          }
        case TRANSACTION_getVolumeAttributes:
          {
            data.enforceInterface(descriptor);
            ParcelableVolumeInfo _result = this.getVolumeAttributes();
            checkNotNull(reply).writeNoException();
            if ((_result != null)) {
              checkNotNull(reply).writeInt(1);
              _result.writeToParcel(reply, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
            } else {
              checkNotNull(reply).writeInt(0);
            }
            return true;
          }
        case TRANSACTION_adjustVolume:
          {
            data.enforceInterface(descriptor);
            int _arg0;
            _arg0 = data.readInt();
            int _arg1;
            _arg1 = data.readInt();
            String _arg2;
            _arg2 = data.readString();
            this.adjustVolume(_arg0, _arg1, _arg2);
            checkNotNull(reply).writeNoException();
            return true;
          }
        case TRANSACTION_setVolumeTo:
          {
            data.enforceInterface(descriptor);
            int _arg0;
            _arg0 = data.readInt();
            int _arg1;
            _arg1 = data.readInt();
            String _arg2;
            _arg2 = data.readString();
            this.setVolumeTo(_arg0, _arg1, _arg2);
            checkNotNull(reply).writeNoException();
            return true;
          }
        case TRANSACTION_getMetadata:
          {
            data.enforceInterface(descriptor);
            MediaMetadataCompat _result = this.getMetadata();
            checkNotNull(reply).writeNoException();
            if ((_result != null)) {
              checkNotNull(reply).writeInt(1);
              _result.writeToParcel(reply, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
            } else {
              checkNotNull(reply).writeInt(0);
            }
            return true;
          }
        case TRANSACTION_getPlaybackState:
          {
            data.enforceInterface(descriptor);
            PlaybackStateCompat _result = this.getPlaybackState();
            checkNotNull(reply).writeNoException();
            if ((_result != null)) {
              checkNotNull(reply).writeInt(1);
              _result.writeToParcel(reply, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
            } else {
              checkNotNull(reply).writeInt(0);
            }
            return true;
          }
        case TRANSACTION_getQueue:
          {
            data.enforceInterface(descriptor);
            java.util.List<MediaSessionCompat.QueueItem> _result = this.getQueue();
            checkNotNull(reply).writeNoException();
            checkNotNull(reply).writeTypedList(_result);
            return true;
          }
        case TRANSACTION_getQueueTitle:
          {
            data.enforceInterface(descriptor);
            CharSequence _result = this.getQueueTitle();
            checkNotNull(reply).writeNoException();
            if (_result != null) {
              checkNotNull(reply).writeInt(1);
              android.text.TextUtils.writeToParcel(
                  _result, reply, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
            } else {
              checkNotNull(reply).writeInt(0);
            }
            return true;
          }
        case TRANSACTION_getExtras:
          {
            data.enforceInterface(descriptor);
            android.os.Bundle _result = this.getExtras();
            checkNotNull(reply).writeNoException();
            if ((_result != null)) {
              checkNotNull(reply).writeInt(1);
              _result.writeToParcel(reply, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
            } else {
              checkNotNull(reply).writeInt(0);
            }
            return true;
          }
        case TRANSACTION_getRatingType:
          {
            data.enforceInterface(descriptor);
            int _result = this.getRatingType();
            checkNotNull(reply).writeNoException();
            checkNotNull(reply).writeInt(_result);
            return true;
          }
        case TRANSACTION_isCaptioningEnabled:
          {
            data.enforceInterface(descriptor);
            boolean _result = this.isCaptioningEnabled();
            checkNotNull(reply).writeNoException();
            checkNotNull(reply).writeInt(((_result) ? (1) : (0)));
            return true;
          }
        case TRANSACTION_getRepeatMode:
          {
            data.enforceInterface(descriptor);
            int _result = this.getRepeatMode();
            checkNotNull(reply).writeNoException();
            checkNotNull(reply).writeInt(_result);
            return true;
          }
        case TRANSACTION_isShuffleModeEnabledRemoved:
          {
            data.enforceInterface(descriptor);
            boolean _result = this.isShuffleModeEnabledRemoved();
            checkNotNull(reply).writeNoException();
            checkNotNull(reply).writeInt(((_result) ? (1) : (0)));
            return true;
          }
        case TRANSACTION_getShuffleMode:
          {
            data.enforceInterface(descriptor);
            int _result = this.getShuffleMode();
            checkNotNull(reply).writeNoException();
            checkNotNull(reply).writeInt(_result);
            return true;
          }
        case TRANSACTION_addQueueItem:
          {
            data.enforceInterface(descriptor);
            MediaDescriptionCompat _arg0;
            if ((0 != data.readInt())) {
              _arg0 = MediaDescriptionCompat.CREATOR.createFromParcel(data);
            } else {
              _arg0 = null;
            }
            this.addQueueItem(_arg0);
            checkNotNull(reply).writeNoException();
            return true;
          }
        case TRANSACTION_addQueueItemAt:
          {
            data.enforceInterface(descriptor);
            MediaDescriptionCompat _arg0;
            if ((0 != data.readInt())) {
              _arg0 = MediaDescriptionCompat.CREATOR.createFromParcel(data);
            } else {
              _arg0 = null;
            }
            int _arg1;
            _arg1 = data.readInt();
            this.addQueueItemAt(_arg0, _arg1);
            checkNotNull(reply).writeNoException();
            return true;
          }
        case TRANSACTION_removeQueueItem:
          {
            data.enforceInterface(descriptor);
            MediaDescriptionCompat _arg0;
            if ((0 != data.readInt())) {
              _arg0 = MediaDescriptionCompat.CREATOR.createFromParcel(data);
            } else {
              _arg0 = null;
            }
            this.removeQueueItem(_arg0);
            checkNotNull(reply).writeNoException();
            return true;
          }
        case TRANSACTION_removeQueueItemAt:
          {
            data.enforceInterface(descriptor);
            int _arg0;
            _arg0 = data.readInt();
            this.removeQueueItemAt(_arg0);
            checkNotNull(reply).writeNoException();
            return true;
          }
        case TRANSACTION_getSessionInfo:
          {
            data.enforceInterface(descriptor);
            android.os.Bundle _result = this.getSessionInfo();
            checkNotNull(reply).writeNoException();
            if ((_result != null)) {
              checkNotNull(reply).writeInt(1);
              _result.writeToParcel(reply, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
            } else {
              checkNotNull(reply).writeInt(0);
            }
            return true;
          }
        case TRANSACTION_prepare:
          {
            data.enforceInterface(descriptor);
            this.prepare();
            checkNotNull(reply).writeNoException();
            return true;
          }
        case TRANSACTION_prepareFromMediaId:
          {
            data.enforceInterface(descriptor);
            String _arg0;
            _arg0 = data.readString();
            android.os.Bundle _arg1;
            if ((0 != data.readInt())) {
              _arg1 = android.os.Bundle.CREATOR.createFromParcel(data);
            } else {
              _arg1 = null;
            }
            this.prepareFromMediaId(_arg0, _arg1);
            checkNotNull(reply).writeNoException();
            return true;
          }
        case TRANSACTION_prepareFromSearch:
          {
            data.enforceInterface(descriptor);
            String _arg0;
            _arg0 = data.readString();
            android.os.Bundle _arg1;
            if ((0 != data.readInt())) {
              _arg1 = android.os.Bundle.CREATOR.createFromParcel(data);
            } else {
              _arg1 = null;
            }
            this.prepareFromSearch(_arg0, _arg1);
            checkNotNull(reply).writeNoException();
            return true;
          }
        case TRANSACTION_prepareFromUri:
          {
            data.enforceInterface(descriptor);
            android.net.Uri _arg0;
            if ((0 != data.readInt())) {
              _arg0 = android.net.Uri.CREATOR.createFromParcel(data);
            } else {
              _arg0 = null;
            }
            android.os.Bundle _arg1;
            if ((0 != data.readInt())) {
              _arg1 = android.os.Bundle.CREATOR.createFromParcel(data);
            } else {
              _arg1 = null;
            }
            this.prepareFromUri(_arg0, _arg1);
            checkNotNull(reply).writeNoException();
            return true;
          }
        case TRANSACTION_play:
          {
            data.enforceInterface(descriptor);
            this.play();
            checkNotNull(reply).writeNoException();
            return true;
          }
        case TRANSACTION_playFromMediaId:
          {
            data.enforceInterface(descriptor);
            String _arg0;
            _arg0 = data.readString();
            android.os.Bundle _arg1;
            if ((0 != data.readInt())) {
              _arg1 = android.os.Bundle.CREATOR.createFromParcel(data);
            } else {
              _arg1 = null;
            }
            this.playFromMediaId(_arg0, _arg1);
            checkNotNull(reply).writeNoException();
            return true;
          }
        case TRANSACTION_playFromSearch:
          {
            data.enforceInterface(descriptor);
            String _arg0;
            _arg0 = data.readString();
            android.os.Bundle _arg1;
            if ((0 != data.readInt())) {
              _arg1 = android.os.Bundle.CREATOR.createFromParcel(data);
            } else {
              _arg1 = null;
            }
            this.playFromSearch(_arg0, _arg1);
            checkNotNull(reply).writeNoException();
            return true;
          }
        case TRANSACTION_playFromUri:
          {
            data.enforceInterface(descriptor);
            android.net.Uri _arg0;
            if ((0 != data.readInt())) {
              _arg0 = android.net.Uri.CREATOR.createFromParcel(data);
            } else {
              _arg0 = null;
            }
            android.os.Bundle _arg1;
            if ((0 != data.readInt())) {
              _arg1 = android.os.Bundle.CREATOR.createFromParcel(data);
            } else {
              _arg1 = null;
            }
            this.playFromUri(_arg0, _arg1);
            checkNotNull(reply).writeNoException();
            return true;
          }
        case TRANSACTION_skipToQueueItem:
          {
            data.enforceInterface(descriptor);
            long _arg0;
            _arg0 = data.readLong();
            this.skipToQueueItem(_arg0);
            checkNotNull(reply).writeNoException();
            return true;
          }
        case TRANSACTION_pause:
          {
            data.enforceInterface(descriptor);
            this.pause();
            checkNotNull(reply).writeNoException();
            return true;
          }
        case TRANSACTION_stop:
          {
            data.enforceInterface(descriptor);
            this.stop();
            checkNotNull(reply).writeNoException();
            return true;
          }
        case TRANSACTION_next:
          {
            data.enforceInterface(descriptor);
            this.next();
            checkNotNull(reply).writeNoException();
            return true;
          }
        case TRANSACTION_previous:
          {
            data.enforceInterface(descriptor);
            this.previous();
            checkNotNull(reply).writeNoException();
            return true;
          }
        case TRANSACTION_fastForward:
          {
            data.enforceInterface(descriptor);
            this.fastForward();
            checkNotNull(reply).writeNoException();
            return true;
          }
        case TRANSACTION_rewind:
          {
            data.enforceInterface(descriptor);
            this.rewind();
            checkNotNull(reply).writeNoException();
            return true;
          }
        case TRANSACTION_seekTo:
          {
            data.enforceInterface(descriptor);
            long _arg0;
            _arg0 = data.readLong();
            this.seekTo(_arg0);
            checkNotNull(reply).writeNoException();
            return true;
          }
        case TRANSACTION_rate:
          {
            data.enforceInterface(descriptor);
            RatingCompat _arg0;
            if ((0 != data.readInt())) {
              _arg0 = RatingCompat.CREATOR.createFromParcel(data);
            } else {
              _arg0 = null;
            }
            this.rate(_arg0);
            checkNotNull(reply).writeNoException();
            return true;
          }
        case TRANSACTION_rateWithExtras:
          {
            data.enforceInterface(descriptor);
            RatingCompat _arg0;
            if ((0 != data.readInt())) {
              _arg0 = RatingCompat.CREATOR.createFromParcel(data);
            } else {
              _arg0 = null;
            }
            android.os.Bundle _arg1;
            if ((0 != data.readInt())) {
              _arg1 = android.os.Bundle.CREATOR.createFromParcel(data);
            } else {
              _arg1 = null;
            }
            this.rateWithExtras(_arg0, _arg1);
            checkNotNull(reply).writeNoException();
            return true;
          }
        case TRANSACTION_setPlaybackSpeed:
          {
            data.enforceInterface(descriptor);
            float _arg0;
            _arg0 = data.readFloat();
            this.setPlaybackSpeed(_arg0);
            checkNotNull(reply).writeNoException();
            return true;
          }
        case TRANSACTION_setCaptioningEnabled:
          {
            data.enforceInterface(descriptor);
            boolean _arg0;
            _arg0 = (0 != data.readInt());
            this.setCaptioningEnabled(_arg0);
            checkNotNull(reply).writeNoException();
            return true;
          }
        case TRANSACTION_setRepeatMode:
          {
            data.enforceInterface(descriptor);
            int _arg0;
            _arg0 = data.readInt();
            this.setRepeatMode(_arg0);
            checkNotNull(reply).writeNoException();
            return true;
          }
        case TRANSACTION_setShuffleModeEnabledRemoved:
          {
            data.enforceInterface(descriptor);
            boolean _arg0;
            _arg0 = (0 != data.readInt());
            this.setShuffleModeEnabledRemoved(_arg0);
            checkNotNull(reply).writeNoException();
            return true;
          }
        case TRANSACTION_setShuffleMode:
          {
            data.enforceInterface(descriptor);
            int _arg0;
            _arg0 = data.readInt();
            this.setShuffleMode(_arg0);
            checkNotNull(reply).writeNoException();
            return true;
          }
        case TRANSACTION_sendCustomAction:
          {
            data.enforceInterface(descriptor);
            String _arg0;
            _arg0 = data.readString();
            android.os.Bundle _arg1;
            if ((0 != data.readInt())) {
              _arg1 = android.os.Bundle.CREATOR.createFromParcel(data);
            } else {
              _arg1 = null;
            }
            this.sendCustomAction(_arg0, _arg1);
            checkNotNull(reply).writeNoException();
            return true;
          }
        default:
          {
            return super.onTransact(code, data, reply, flags);
          }
      }
    }

    private static class Proxy implements IMediaSession {
      private android.os.IBinder mRemote;

      Proxy(android.os.IBinder remote) {
        mRemote = remote;
      }

      @Override
      public android.os.IBinder asBinder() {
        return mRemote;
      }

      public String getInterfaceDescriptor() {
        return DESCRIPTOR;
      }

      // Next ID: 50

      @Override
      public void sendCommand(
          @Nullable String command,
          @Nullable android.os.Bundle args,
          @Nullable MediaSessionCompat.ResultReceiverWrapper cb)
          throws android.os.RemoteException {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(command);
          if ((args != null)) {
            _data.writeInt(1);
            args.writeToParcel(_data, 0);
          } else {
            _data.writeInt(0);
          }
          if ((cb != null)) {
            _data.writeInt(1);
            cb.writeToParcel(_data, 0);
          } else {
            _data.writeInt(0);
          }
          boolean _status = mRemote.transact(Stub.TRANSACTION_sendCommand, _data, _reply, 0);
          if (!_status && getDefaultImpl() != null) {
            checkNotNull(getDefaultImpl()).sendCommand(command, args, cb);
            return;
          }
          _reply.readException();
        } finally {
          _reply.recycle();
          _data.recycle();
        }
      }

      @Override
      public boolean sendMediaButton(@Nullable android.view.KeyEvent mediaButton)
          throws android.os.RemoteException {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        boolean _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          if ((mediaButton != null)) {
            _data.writeInt(1);
            mediaButton.writeToParcel(_data, 0);
          } else {
            _data.writeInt(0);
          }
          boolean _status = mRemote.transact(Stub.TRANSACTION_sendMediaButton, _data, _reply, 0);
          if (!_status && getDefaultImpl() != null) {
            return checkNotNull(getDefaultImpl()).sendMediaButton(mediaButton);
          }
          _reply.readException();
          _result = (0 != _reply.readInt());
        } finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }

      @SuppressWarnings("argument.type.incompatible") // writeStrongBinder not annotated correctly
      @Override
      public void registerCallbackListener(@Nullable IMediaControllerCallback cb)
          throws android.os.RemoteException {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeStrongBinder((((cb != null)) ? (cb.asBinder()) : (null)));
          boolean _status =
              mRemote.transact(Stub.TRANSACTION_registerCallbackListener, _data, _reply, 0);
          if (!_status && getDefaultImpl() != null) {
            checkNotNull(getDefaultImpl()).registerCallbackListener(cb);
            return;
          }
          _reply.readException();
        } finally {
          _reply.recycle();
          _data.recycle();
        }
      }

      @SuppressWarnings("argument.type.incompatible") // writeStrongBinder not annotated correctly
      @Override
      public void unregisterCallbackListener(@Nullable IMediaControllerCallback cb)
          throws android.os.RemoteException {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeStrongBinder((((cb != null)) ? (cb.asBinder()) : (null)));
          boolean _status =
              mRemote.transact(Stub.TRANSACTION_unregisterCallbackListener, _data, _reply, 0);
          if (!_status && getDefaultImpl() != null) {
            checkNotNull(getDefaultImpl()).unregisterCallbackListener(cb);
            return;
          }
          _reply.readException();
        } finally {
          _reply.recycle();
          _data.recycle();
        }
      }

      @Override
      public boolean isTransportControlEnabled() throws android.os.RemoteException {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        boolean _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status =
              mRemote.transact(Stub.TRANSACTION_isTransportControlEnabled, _data, _reply, 0);
          if (!_status && getDefaultImpl() != null) {
            return checkNotNull(getDefaultImpl()).isTransportControlEnabled();
          }
          _reply.readException();
          _result = (0 != _reply.readInt());
        } finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }

      @Nullable
      @Override
      public String getPackageName() throws android.os.RemoteException {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        String _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_getPackageName, _data, _reply, 0);
          if (!_status && getDefaultImpl() != null) {
            return checkNotNull(getDefaultImpl()).getPackageName();
          }
          _reply.readException();
          _result = _reply.readString();
        } finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }

      @Nullable
      @Override
      public String getTag() throws android.os.RemoteException {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        String _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_getTag, _data, _reply, 0);
          if (!_status && getDefaultImpl() != null) {
            return checkNotNull(getDefaultImpl()).getTag();
          }
          _reply.readException();
          _result = _reply.readString();
        } finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }

      @Nullable
      @Override
      public android.app.PendingIntent getLaunchPendingIntent() throws android.os.RemoteException {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        android.app.PendingIntent _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status =
              mRemote.transact(Stub.TRANSACTION_getLaunchPendingIntent, _data, _reply, 0);
          if (!_status && getDefaultImpl() != null) {
            return checkNotNull(getDefaultImpl()).getLaunchPendingIntent();
          }
          _reply.readException();
          if ((0 != _reply.readInt())) {
            _result = android.app.PendingIntent.CREATOR.createFromParcel(_reply);
          } else {
            _result = null;
          }
        } finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }

      @Override
      public long getFlags() throws android.os.RemoteException {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        long _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_getFlags, _data, _reply, 0);
          if (!_status && getDefaultImpl() != null) {
            return checkNotNull(getDefaultImpl()).getFlags();
          }
          _reply.readException();
          _result = _reply.readLong();
        } finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }

      @Nullable
      @Override
      public ParcelableVolumeInfo getVolumeAttributes() throws android.os.RemoteException {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        ParcelableVolumeInfo _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status =
              mRemote.transact(Stub.TRANSACTION_getVolumeAttributes, _data, _reply, 0);
          if (!_status && getDefaultImpl() != null) {
            return checkNotNull(getDefaultImpl()).getVolumeAttributes();
          }
          _reply.readException();
          if ((0 != _reply.readInt())) {
            _result = ParcelableVolumeInfo.CREATOR.createFromParcel(_reply);
          } else {
            _result = null;
          }
        } finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }

      @Override
      public void adjustVolume(int direction, int flags, @Nullable String packageName)
          throws android.os.RemoteException {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeInt(direction);
          _data.writeInt(flags);
          _data.writeString(packageName);
          boolean _status = mRemote.transact(Stub.TRANSACTION_adjustVolume, _data, _reply, 0);
          if (!_status && getDefaultImpl() != null) {
            checkNotNull(getDefaultImpl()).adjustVolume(direction, flags, packageName);
            return;
          }
          _reply.readException();
        } finally {
          _reply.recycle();
          _data.recycle();
        }
      }

      @Override
      public void setVolumeTo(int value, int flags, @Nullable String packageName)
          throws android.os.RemoteException {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeInt(value);
          _data.writeInt(flags);
          _data.writeString(packageName);
          boolean _status = mRemote.transact(Stub.TRANSACTION_setVolumeTo, _data, _reply, 0);
          if (!_status && getDefaultImpl() != null) {
            checkNotNull(getDefaultImpl()).setVolumeTo(value, flags, packageName);
            return;
          }
          _reply.readException();
        } finally {
          _reply.recycle();
          _data.recycle();
        }
      }

      @Nullable
      @Override
      public MediaMetadataCompat getMetadata() throws android.os.RemoteException {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        MediaMetadataCompat _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_getMetadata, _data, _reply, 0);
          if (!_status && getDefaultImpl() != null) {
            return checkNotNull(getDefaultImpl()).getMetadata();
          }
          _reply.readException();
          if ((0 != _reply.readInt())) {
            _result = MediaMetadataCompat.CREATOR.createFromParcel(_reply);
          } else {
            _result = null;
          }
        } finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }

      @Nullable
      @Override
      public PlaybackStateCompat getPlaybackState() throws android.os.RemoteException {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        PlaybackStateCompat _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_getPlaybackState, _data, _reply, 0);
          if (!_status && getDefaultImpl() != null) {
            return checkNotNull(getDefaultImpl()).getPlaybackState();
          }
          _reply.readException();
          if ((0 != _reply.readInt())) {
            _result = PlaybackStateCompat.CREATOR.createFromParcel(_reply);
          } else {
            _result = null;
          }
        } finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }

      @Nullable
      @Override
      public java.util.List<MediaSessionCompat.QueueItem> getQueue()
          throws android.os.RemoteException {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        java.util.List<MediaSessionCompat.QueueItem> _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_getQueue, _data, _reply, 0);
          if (!_status && getDefaultImpl() != null) {
            return checkNotNull(getDefaultImpl()).getQueue();
          }
          _reply.readException();
          _result = _reply.createTypedArrayList(MediaSessionCompat.QueueItem.CREATOR);
        } finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }

      @Nullable
      @Override
      public CharSequence getQueueTitle() throws android.os.RemoteException {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        CharSequence _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_getQueueTitle, _data, _reply, 0);
          if (!_status && getDefaultImpl() != null) {
            return checkNotNull(getDefaultImpl()).getQueueTitle();
          }
          _reply.readException();
          if (0 != _reply.readInt()) {
            _result = android.text.TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(_reply);
          } else {
            _result = null;
          }
        } finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }

      @Nullable
      @Override
      public android.os.Bundle getExtras() throws android.os.RemoteException {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        android.os.Bundle _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_getExtras, _data, _reply, 0);
          if (!_status && getDefaultImpl() != null) {
            return checkNotNull(getDefaultImpl()).getExtras();
          }
          _reply.readException();
          if ((0 != _reply.readInt())) {
            _result = android.os.Bundle.CREATOR.createFromParcel(_reply);
          } else {
            _result = null;
          }
        } finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }

      @Override
      public int getRatingType() throws android.os.RemoteException {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        int _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_getRatingType, _data, _reply, 0);
          if (!_status && getDefaultImpl() != null) {
            return checkNotNull(getDefaultImpl()).getRatingType();
          }
          _reply.readException();
          _result = _reply.readInt();
        } finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }

      @Override
      public boolean isCaptioningEnabled() throws android.os.RemoteException {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        boolean _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status =
              mRemote.transact(Stub.TRANSACTION_isCaptioningEnabled, _data, _reply, 0);
          if (!_status && getDefaultImpl() != null) {
            return checkNotNull(getDefaultImpl()).isCaptioningEnabled();
          }
          _reply.readException();
          _result = (0 != _reply.readInt());
        } finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }

      @Override
      public int getRepeatMode() throws android.os.RemoteException {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        int _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_getRepeatMode, _data, _reply, 0);
          if (!_status && getDefaultImpl() != null) {
            return checkNotNull(getDefaultImpl()).getRepeatMode();
          }
          _reply.readException();
          _result = _reply.readInt();
        } finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }

      @Override
      public boolean isShuffleModeEnabledRemoved() throws android.os.RemoteException {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        boolean _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status =
              mRemote.transact(Stub.TRANSACTION_isShuffleModeEnabledRemoved, _data, _reply, 0);
          if (!_status && getDefaultImpl() != null) {
            return checkNotNull(getDefaultImpl()).isShuffleModeEnabledRemoved();
          }
          _reply.readException();
          _result = (0 != _reply.readInt());
        } finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }

      @Override
      public int getShuffleMode() throws android.os.RemoteException {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        int _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_getShuffleMode, _data, _reply, 0);
          if (!_status && getDefaultImpl() != null) {
            return checkNotNull(getDefaultImpl()).getShuffleMode();
          }
          _reply.readException();
          _result = _reply.readInt();
        } finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }

      @Override
      public void addQueueItem(@Nullable MediaDescriptionCompat description)
          throws android.os.RemoteException {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          if ((description != null)) {
            _data.writeInt(1);
            description.writeToParcel(_data, 0);
          } else {
            _data.writeInt(0);
          }
          boolean _status = mRemote.transact(Stub.TRANSACTION_addQueueItem, _data, _reply, 0);
          if (!_status && getDefaultImpl() != null) {
            checkNotNull(getDefaultImpl()).addQueueItem(description);
            return;
          }
          _reply.readException();
        } finally {
          _reply.recycle();
          _data.recycle();
        }
      }

      @Override
      public void addQueueItemAt(@Nullable MediaDescriptionCompat description, int index)
          throws android.os.RemoteException {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          if ((description != null)) {
            _data.writeInt(1);
            description.writeToParcel(_data, 0);
          } else {
            _data.writeInt(0);
          }
          _data.writeInt(index);
          boolean _status = mRemote.transact(Stub.TRANSACTION_addQueueItemAt, _data, _reply, 0);
          if (!_status && getDefaultImpl() != null) {
            checkNotNull(getDefaultImpl()).addQueueItemAt(description, index);
            return;
          }
          _reply.readException();
        } finally {
          _reply.recycle();
          _data.recycle();
        }
      }

      @Override
      public void removeQueueItem(@Nullable MediaDescriptionCompat description)
          throws android.os.RemoteException {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          if ((description != null)) {
            _data.writeInt(1);
            description.writeToParcel(_data, 0);
          } else {
            _data.writeInt(0);
          }
          boolean _status = mRemote.transact(Stub.TRANSACTION_removeQueueItem, _data, _reply, 0);
          if (!_status && getDefaultImpl() != null) {
            checkNotNull(getDefaultImpl()).removeQueueItem(description);
            return;
          }
          _reply.readException();
        } finally {
          _reply.recycle();
          _data.recycle();
        }
      }

      @Override
      public void removeQueueItemAt(int index) throws android.os.RemoteException {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeInt(index);
          boolean _status = mRemote.transact(Stub.TRANSACTION_removeQueueItemAt, _data, _reply, 0);
          if (!_status && getDefaultImpl() != null) {
            checkNotNull(getDefaultImpl()).removeQueueItemAt(index);
            return;
          }
          _reply.readException();
        } finally {
          _reply.recycle();
          _data.recycle();
        }
      }

      @Nullable
      @Override
      public android.os.Bundle getSessionInfo() throws android.os.RemoteException {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        android.os.Bundle _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_getSessionInfo, _data, _reply, 0);
          if (!_status && getDefaultImpl() != null) {
            return checkNotNull(getDefaultImpl()).getSessionInfo();
          }
          _reply.readException();
          if ((0 != _reply.readInt())) {
            _result = android.os.Bundle.CREATOR.createFromParcel(_reply);
          } else {
            _result = null;
          }
        } finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }

      // These commands are for the TransportControls

      @Override
      public void prepare() throws android.os.RemoteException {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_prepare, _data, _reply, 0);
          if (!_status && getDefaultImpl() != null) {
            checkNotNull(getDefaultImpl()).prepare();
            return;
          }
          _reply.readException();
        } finally {
          _reply.recycle();
          _data.recycle();
        }
      }

      @Override
      public void prepareFromMediaId(@Nullable String uri, @Nullable android.os.Bundle extras)
          throws android.os.RemoteException {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(uri);
          if ((extras != null)) {
            _data.writeInt(1);
            extras.writeToParcel(_data, 0);
          } else {
            _data.writeInt(0);
          }
          boolean _status = mRemote.transact(Stub.TRANSACTION_prepareFromMediaId, _data, _reply, 0);
          if (!_status && getDefaultImpl() != null) {
            checkNotNull(getDefaultImpl()).prepareFromMediaId(uri, extras);
            return;
          }
          _reply.readException();
        } finally {
          _reply.recycle();
          _data.recycle();
        }
      }

      @Override
      public void prepareFromSearch(@Nullable String string, @Nullable android.os.Bundle extras)
          throws android.os.RemoteException {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(string);
          if ((extras != null)) {
            _data.writeInt(1);
            extras.writeToParcel(_data, 0);
          } else {
            _data.writeInt(0);
          }
          boolean _status = mRemote.transact(Stub.TRANSACTION_prepareFromSearch, _data, _reply, 0);
          if (!_status && getDefaultImpl() != null) {
            checkNotNull(getDefaultImpl()).prepareFromSearch(string, extras);
            return;
          }
          _reply.readException();
        } finally {
          _reply.recycle();
          _data.recycle();
        }
      }

      @Override
      public void prepareFromUri(@Nullable android.net.Uri uri, @Nullable android.os.Bundle extras)
          throws android.os.RemoteException {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          if ((uri != null)) {
            _data.writeInt(1);
            uri.writeToParcel(_data, 0);
          } else {
            _data.writeInt(0);
          }
          if ((extras != null)) {
            _data.writeInt(1);
            extras.writeToParcel(_data, 0);
          } else {
            _data.writeInt(0);
          }
          boolean _status = mRemote.transact(Stub.TRANSACTION_prepareFromUri, _data, _reply, 0);
          if (!_status && getDefaultImpl() != null) {
            checkNotNull(getDefaultImpl()).prepareFromUri(uri, extras);
            return;
          }
          _reply.readException();
        } finally {
          _reply.recycle();
          _data.recycle();
        }
      }

      @Override
      public void play() throws android.os.RemoteException {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_play, _data, _reply, 0);
          if (!_status && getDefaultImpl() != null) {
            checkNotNull(getDefaultImpl()).play();
            return;
          }
          _reply.readException();
        } finally {
          _reply.recycle();
          _data.recycle();
        }
      }

      @Override
      public void playFromMediaId(@Nullable String uri, @Nullable android.os.Bundle extras)
          throws android.os.RemoteException {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(uri);
          if ((extras != null)) {
            _data.writeInt(1);
            extras.writeToParcel(_data, 0);
          } else {
            _data.writeInt(0);
          }
          boolean _status = mRemote.transact(Stub.TRANSACTION_playFromMediaId, _data, _reply, 0);
          if (!_status && getDefaultImpl() != null) {
            checkNotNull(getDefaultImpl()).playFromMediaId(uri, extras);
            return;
          }
          _reply.readException();
        } finally {
          _reply.recycle();
          _data.recycle();
        }
      }

      @Override
      public void playFromSearch(@Nullable String string, @Nullable android.os.Bundle extras)
          throws android.os.RemoteException {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(string);
          if ((extras != null)) {
            _data.writeInt(1);
            extras.writeToParcel(_data, 0);
          } else {
            _data.writeInt(0);
          }
          boolean _status = mRemote.transact(Stub.TRANSACTION_playFromSearch, _data, _reply, 0);
          if (!_status && getDefaultImpl() != null) {
            checkNotNull(getDefaultImpl()).playFromSearch(string, extras);
            return;
          }
          _reply.readException();
        } finally {
          _reply.recycle();
          _data.recycle();
        }
      }

      @Override
      public void playFromUri(@Nullable android.net.Uri uri, @Nullable android.os.Bundle extras)
          throws android.os.RemoteException {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          if ((uri != null)) {
            _data.writeInt(1);
            uri.writeToParcel(_data, 0);
          } else {
            _data.writeInt(0);
          }
          if ((extras != null)) {
            _data.writeInt(1);
            extras.writeToParcel(_data, 0);
          } else {
            _data.writeInt(0);
          }
          boolean _status = mRemote.transact(Stub.TRANSACTION_playFromUri, _data, _reply, 0);
          if (!_status && getDefaultImpl() != null) {
            checkNotNull(getDefaultImpl()).playFromUri(uri, extras);
            return;
          }
          _reply.readException();
        } finally {
          _reply.recycle();
          _data.recycle();
        }
      }

      @Override
      public void skipToQueueItem(long id) throws android.os.RemoteException {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeLong(id);
          boolean _status = mRemote.transact(Stub.TRANSACTION_skipToQueueItem, _data, _reply, 0);
          if (!_status && getDefaultImpl() != null) {
            checkNotNull(getDefaultImpl()).skipToQueueItem(id);
            return;
          }
          _reply.readException();
        } finally {
          _reply.recycle();
          _data.recycle();
        }
      }

      @Override
      public void pause() throws android.os.RemoteException {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_pause, _data, _reply, 0);
          if (!_status && getDefaultImpl() != null) {
            checkNotNull(getDefaultImpl()).pause();
            return;
          }
          _reply.readException();
        } finally {
          _reply.recycle();
          _data.recycle();
        }
      }

      @Override
      public void stop() throws android.os.RemoteException {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_stop, _data, _reply, 0);
          if (!_status && getDefaultImpl() != null) {
            checkNotNull(getDefaultImpl()).stop();
            return;
          }
          _reply.readException();
        } finally {
          _reply.recycle();
          _data.recycle();
        }
      }

      @Override
      public void next() throws android.os.RemoteException {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_next, _data, _reply, 0);
          if (!_status && getDefaultImpl() != null) {
            checkNotNull(getDefaultImpl()).next();
            return;
          }
          _reply.readException();
        } finally {
          _reply.recycle();
          _data.recycle();
        }
      }

      @Override
      public void previous() throws android.os.RemoteException {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_previous, _data, _reply, 0);
          if (!_status && getDefaultImpl() != null) {
            checkNotNull(getDefaultImpl()).previous();
            return;
          }
          _reply.readException();
        } finally {
          _reply.recycle();
          _data.recycle();
        }
      }

      @Override
      public void fastForward() throws android.os.RemoteException {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_fastForward, _data, _reply, 0);
          if (!_status && getDefaultImpl() != null) {
            checkNotNull(getDefaultImpl()).fastForward();
            return;
          }
          _reply.readException();
        } finally {
          _reply.recycle();
          _data.recycle();
        }
      }

      @Override
      public void rewind() throws android.os.RemoteException {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_rewind, _data, _reply, 0);
          if (!_status && getDefaultImpl() != null) {
            checkNotNull(getDefaultImpl()).rewind();
            return;
          }
          _reply.readException();
        } finally {
          _reply.recycle();
          _data.recycle();
        }
      }

      @Override
      public void seekTo(long pos) throws android.os.RemoteException {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeLong(pos);
          boolean _status = mRemote.transact(Stub.TRANSACTION_seekTo, _data, _reply, 0);
          if (!_status && getDefaultImpl() != null) {
            checkNotNull(getDefaultImpl()).seekTo(pos);
            return;
          }
          _reply.readException();
        } finally {
          _reply.recycle();
          _data.recycle();
        }
      }

      @Override
      public void rate(@Nullable RatingCompat rating) throws android.os.RemoteException {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          if ((rating != null)) {
            _data.writeInt(1);
            rating.writeToParcel(_data, 0);
          } else {
            _data.writeInt(0);
          }
          boolean _status = mRemote.transact(Stub.TRANSACTION_rate, _data, _reply, 0);
          if (!_status && getDefaultImpl() != null) {
            checkNotNull(getDefaultImpl()).rate(rating);
            return;
          }
          _reply.readException();
        } finally {
          _reply.recycle();
          _data.recycle();
        }
      }

      @Override
      public void rateWithExtras(@Nullable RatingCompat rating, @Nullable android.os.Bundle extras)
          throws android.os.RemoteException {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          if ((rating != null)) {
            _data.writeInt(1);
            rating.writeToParcel(_data, 0);
          } else {
            _data.writeInt(0);
          }
          if ((extras != null)) {
            _data.writeInt(1);
            extras.writeToParcel(_data, 0);
          } else {
            _data.writeInt(0);
          }
          boolean _status = mRemote.transact(Stub.TRANSACTION_rateWithExtras, _data, _reply, 0);
          if (!_status && getDefaultImpl() != null) {
            checkNotNull(getDefaultImpl()).rateWithExtras(rating, extras);
            return;
          }
          _reply.readException();
        } finally {
          _reply.recycle();
          _data.recycle();
        }
      }

      @Override
      public void setPlaybackSpeed(float speed) throws android.os.RemoteException {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeFloat(speed);
          boolean _status = mRemote.transact(Stub.TRANSACTION_setPlaybackSpeed, _data, _reply, 0);
          if (!_status && getDefaultImpl() != null) {
            checkNotNull(getDefaultImpl()).setPlaybackSpeed(speed);
            return;
          }
          _reply.readException();
        } finally {
          _reply.recycle();
          _data.recycle();
        }
      }

      @Override
      public void setCaptioningEnabled(boolean enabled) throws android.os.RemoteException {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeInt(((enabled) ? (1) : (0)));
          boolean _status =
              mRemote.transact(Stub.TRANSACTION_setCaptioningEnabled, _data, _reply, 0);
          if (!_status && getDefaultImpl() != null) {
            checkNotNull(getDefaultImpl()).setCaptioningEnabled(enabled);
            return;
          }
          _reply.readException();
        } finally {
          _reply.recycle();
          _data.recycle();
        }
      }

      @Override
      public void setRepeatMode(int repeatMode) throws android.os.RemoteException {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeInt(repeatMode);
          boolean _status = mRemote.transact(Stub.TRANSACTION_setRepeatMode, _data, _reply, 0);
          if (!_status && getDefaultImpl() != null) {
            checkNotNull(getDefaultImpl()).setRepeatMode(repeatMode);
            return;
          }
          _reply.readException();
        } finally {
          _reply.recycle();
          _data.recycle();
        }
      }

      @Override
      public void setShuffleModeEnabledRemoved(boolean shuffleMode)
          throws android.os.RemoteException {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeInt(((shuffleMode) ? (1) : (0)));
          boolean _status =
              mRemote.transact(Stub.TRANSACTION_setShuffleModeEnabledRemoved, _data, _reply, 0);
          if (!_status && getDefaultImpl() != null) {
            checkNotNull(getDefaultImpl()).setShuffleModeEnabledRemoved(shuffleMode);
            return;
          }
          _reply.readException();
        } finally {
          _reply.recycle();
          _data.recycle();
        }
      }

      @Override
      public void setShuffleMode(int shuffleMode) throws android.os.RemoteException {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeInt(shuffleMode);
          boolean _status = mRemote.transact(Stub.TRANSACTION_setShuffleMode, _data, _reply, 0);
          if (!_status && getDefaultImpl() != null) {
            checkNotNull(getDefaultImpl()).setShuffleMode(shuffleMode);
            return;
          }
          _reply.readException();
        } finally {
          _reply.recycle();
          _data.recycle();
        }
      }

      @Override
      public void sendCustomAction(@Nullable String action, @Nullable android.os.Bundle args)
          throws android.os.RemoteException {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(action);
          if ((args != null)) {
            _data.writeInt(1);
            args.writeToParcel(_data, 0);
          } else {
            _data.writeInt(0);
          }
          boolean _status = mRemote.transact(Stub.TRANSACTION_sendCustomAction, _data, _reply, 0);
          if (!_status && getDefaultImpl() != null) {
            checkNotNull(getDefaultImpl()).sendCustomAction(action, args);
            return;
          }
          _reply.readException();
        } finally {
          _reply.recycle();
          _data.recycle();
        }
      }

      @Nullable public static IMediaSession sDefaultImpl;
    }

    static final int TRANSACTION_sendCommand = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
    static final int TRANSACTION_sendMediaButton = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
    static final int TRANSACTION_registerCallbackListener =
        (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
    static final int TRANSACTION_unregisterCallbackListener =
        (android.os.IBinder.FIRST_CALL_TRANSACTION + 3);
    static final int TRANSACTION_isTransportControlEnabled =
        (android.os.IBinder.FIRST_CALL_TRANSACTION + 4);
    static final int TRANSACTION_getPackageName = (android.os.IBinder.FIRST_CALL_TRANSACTION + 5);
    static final int TRANSACTION_getTag = (android.os.IBinder.FIRST_CALL_TRANSACTION + 6);
    static final int TRANSACTION_getLaunchPendingIntent =
        (android.os.IBinder.FIRST_CALL_TRANSACTION + 7);
    static final int TRANSACTION_getFlags = (android.os.IBinder.FIRST_CALL_TRANSACTION + 8);
    static final int TRANSACTION_getVolumeAttributes =
        (android.os.IBinder.FIRST_CALL_TRANSACTION + 9);
    static final int TRANSACTION_adjustVolume = (android.os.IBinder.FIRST_CALL_TRANSACTION + 10);
    static final int TRANSACTION_setVolumeTo = (android.os.IBinder.FIRST_CALL_TRANSACTION + 11);
    static final int TRANSACTION_getMetadata = (android.os.IBinder.FIRST_CALL_TRANSACTION + 26);
    static final int TRANSACTION_getPlaybackState =
        (android.os.IBinder.FIRST_CALL_TRANSACTION + 27);
    static final int TRANSACTION_getQueue = (android.os.IBinder.FIRST_CALL_TRANSACTION + 28);
    static final int TRANSACTION_getQueueTitle = (android.os.IBinder.FIRST_CALL_TRANSACTION + 29);
    static final int TRANSACTION_getExtras = (android.os.IBinder.FIRST_CALL_TRANSACTION + 30);
    static final int TRANSACTION_getRatingType = (android.os.IBinder.FIRST_CALL_TRANSACTION + 31);
    static final int TRANSACTION_isCaptioningEnabled =
        (android.os.IBinder.FIRST_CALL_TRANSACTION + 44);
    static final int TRANSACTION_getRepeatMode = (android.os.IBinder.FIRST_CALL_TRANSACTION + 36);
    static final int TRANSACTION_isShuffleModeEnabledRemoved =
        (android.os.IBinder.FIRST_CALL_TRANSACTION + 37);
    static final int TRANSACTION_getShuffleMode = (android.os.IBinder.FIRST_CALL_TRANSACTION + 46);
    static final int TRANSACTION_addQueueItem = (android.os.IBinder.FIRST_CALL_TRANSACTION + 40);
    static final int TRANSACTION_addQueueItemAt = (android.os.IBinder.FIRST_CALL_TRANSACTION + 41);
    static final int TRANSACTION_removeQueueItem = (android.os.IBinder.FIRST_CALL_TRANSACTION + 42);
    static final int TRANSACTION_removeQueueItemAt =
        (android.os.IBinder.FIRST_CALL_TRANSACTION + 43);
    static final int TRANSACTION_getSessionInfo = (android.os.IBinder.FIRST_CALL_TRANSACTION + 49);
    static final int TRANSACTION_prepare = (android.os.IBinder.FIRST_CALL_TRANSACTION + 32);
    static final int TRANSACTION_prepareFromMediaId =
        (android.os.IBinder.FIRST_CALL_TRANSACTION + 33);
    static final int TRANSACTION_prepareFromSearch =
        (android.os.IBinder.FIRST_CALL_TRANSACTION + 34);
    static final int TRANSACTION_prepareFromUri = (android.os.IBinder.FIRST_CALL_TRANSACTION + 35);
    static final int TRANSACTION_play = (android.os.IBinder.FIRST_CALL_TRANSACTION + 12);
    static final int TRANSACTION_playFromMediaId = (android.os.IBinder.FIRST_CALL_TRANSACTION + 13);
    static final int TRANSACTION_playFromSearch = (android.os.IBinder.FIRST_CALL_TRANSACTION + 14);
    static final int TRANSACTION_playFromUri = (android.os.IBinder.FIRST_CALL_TRANSACTION + 15);
    static final int TRANSACTION_skipToQueueItem = (android.os.IBinder.FIRST_CALL_TRANSACTION + 16);
    static final int TRANSACTION_pause = (android.os.IBinder.FIRST_CALL_TRANSACTION + 17);
    static final int TRANSACTION_stop = (android.os.IBinder.FIRST_CALL_TRANSACTION + 18);
    static final int TRANSACTION_next = (android.os.IBinder.FIRST_CALL_TRANSACTION + 19);
    static final int TRANSACTION_previous = (android.os.IBinder.FIRST_CALL_TRANSACTION + 20);
    static final int TRANSACTION_fastForward = (android.os.IBinder.FIRST_CALL_TRANSACTION + 21);
    static final int TRANSACTION_rewind = (android.os.IBinder.FIRST_CALL_TRANSACTION + 22);
    static final int TRANSACTION_seekTo = (android.os.IBinder.FIRST_CALL_TRANSACTION + 23);
    static final int TRANSACTION_rate = (android.os.IBinder.FIRST_CALL_TRANSACTION + 24);
    static final int TRANSACTION_rateWithExtras = (android.os.IBinder.FIRST_CALL_TRANSACTION + 50);
    static final int TRANSACTION_setPlaybackSpeed =
        (android.os.IBinder.FIRST_CALL_TRANSACTION + 48);
    static final int TRANSACTION_setCaptioningEnabled =
        (android.os.IBinder.FIRST_CALL_TRANSACTION + 45);
    static final int TRANSACTION_setRepeatMode = (android.os.IBinder.FIRST_CALL_TRANSACTION + 38);
    static final int TRANSACTION_setShuffleModeEnabledRemoved =
        (android.os.IBinder.FIRST_CALL_TRANSACTION + 39);
    static final int TRANSACTION_setShuffleMode = (android.os.IBinder.FIRST_CALL_TRANSACTION + 47);
    static final int TRANSACTION_sendCustomAction =
        (android.os.IBinder.FIRST_CALL_TRANSACTION + 25);

    public static boolean setDefaultImpl(IMediaSession impl) {
      // Only one user of this interface can use this function
      // at a time. This is a heuristic to detect if two different
      // users in the same process use this function.
      if (Proxy.sDefaultImpl != null) {
        throw new IllegalStateException("setDefaultImpl() called twice");
      }
      if (impl != null) {
        Proxy.sDefaultImpl = impl;
        return true;
      }
      return false;
    }

    @Nullable
    public static IMediaSession getDefaultImpl() {
      return Proxy.sDefaultImpl;
    }
  }

  // Next ID: 50

  public void sendCommand(
      @Nullable String command,
      @Nullable android.os.Bundle args,
      @Nullable MediaSessionCompat.ResultReceiverWrapper cb)
      throws android.os.RemoteException;

  public boolean sendMediaButton(@Nullable android.view.KeyEvent mediaButton)
      throws android.os.RemoteException;

  public void registerCallbackListener(@Nullable IMediaControllerCallback cb)
      throws android.os.RemoteException;

  public void unregisterCallbackListener(@Nullable IMediaControllerCallback cb)
      throws android.os.RemoteException;

  public boolean isTransportControlEnabled() throws android.os.RemoteException;

  @Nullable
  public String getPackageName() throws android.os.RemoteException;

  @Nullable
  public String getTag() throws android.os.RemoteException;

  @Nullable
  public android.app.PendingIntent getLaunchPendingIntent() throws android.os.RemoteException;

  public long getFlags() throws android.os.RemoteException;

  @Nullable
  public ParcelableVolumeInfo getVolumeAttributes() throws android.os.RemoteException;

  public void adjustVolume(int direction, int flags, @Nullable String packageName)
      throws android.os.RemoteException;

  public void setVolumeTo(int value, int flags, @Nullable String packageName)
      throws android.os.RemoteException;

  @Nullable
  public MediaMetadataCompat getMetadata() throws android.os.RemoteException;

  @Nullable
  public PlaybackStateCompat getPlaybackState() throws android.os.RemoteException;

  @Nullable
  public java.util.List<MediaSessionCompat.QueueItem> getQueue() throws android.os.RemoteException;

  @Nullable
  public CharSequence getQueueTitle() throws android.os.RemoteException;

  @Nullable
  public android.os.Bundle getExtras() throws android.os.RemoteException;

  public int getRatingType() throws android.os.RemoteException;

  public boolean isCaptioningEnabled() throws android.os.RemoteException;

  public int getRepeatMode() throws android.os.RemoteException;

  public boolean isShuffleModeEnabledRemoved() throws android.os.RemoteException;

  public int getShuffleMode() throws android.os.RemoteException;

  public void addQueueItem(@Nullable MediaDescriptionCompat description)
      throws android.os.RemoteException;

  public void addQueueItemAt(@Nullable MediaDescriptionCompat description, int index)
      throws android.os.RemoteException;

  public void removeQueueItem(@Nullable MediaDescriptionCompat description)
      throws android.os.RemoteException;

  public void removeQueueItemAt(int index) throws android.os.RemoteException;

  @Nullable
  public android.os.Bundle getSessionInfo() throws android.os.RemoteException;

  // These commands are for the TransportControls

  public void prepare() throws android.os.RemoteException;

  public void prepareFromMediaId(@Nullable String uri, @Nullable android.os.Bundle extras)
      throws android.os.RemoteException;

  public void prepareFromSearch(@Nullable String string, @Nullable android.os.Bundle extras)
      throws android.os.RemoteException;

  public void prepareFromUri(@Nullable android.net.Uri uri, @Nullable android.os.Bundle extras)
      throws android.os.RemoteException;

  public void play() throws android.os.RemoteException;

  public void playFromMediaId(@Nullable String uri, @Nullable android.os.Bundle extras)
      throws android.os.RemoteException;

  public void playFromSearch(@Nullable String string, @Nullable android.os.Bundle extras)
      throws android.os.RemoteException;

  public void playFromUri(@Nullable android.net.Uri uri, @Nullable android.os.Bundle extras)
      throws android.os.RemoteException;

  public void skipToQueueItem(long id) throws android.os.RemoteException;

  public void pause() throws android.os.RemoteException;

  public void stop() throws android.os.RemoteException;

  public void next() throws android.os.RemoteException;

  public void previous() throws android.os.RemoteException;

  public void fastForward() throws android.os.RemoteException;

  public void rewind() throws android.os.RemoteException;

  public void seekTo(long pos) throws android.os.RemoteException;

  public void rate(@Nullable RatingCompat rating) throws android.os.RemoteException;

  public void rateWithExtras(@Nullable RatingCompat rating, @Nullable android.os.Bundle extras)
      throws android.os.RemoteException;

  public void setPlaybackSpeed(float speed) throws android.os.RemoteException;

  public void setCaptioningEnabled(boolean enabled) throws android.os.RemoteException;

  public void setRepeatMode(int repeatMode) throws android.os.RemoteException;

  public void setShuffleModeEnabledRemoved(boolean shuffleMode) throws android.os.RemoteException;

  public void setShuffleMode(int shuffleMode) throws android.os.RemoteException;

  public void sendCustomAction(@Nullable String action, @Nullable android.os.Bundle args)
      throws android.os.RemoteException;
}
