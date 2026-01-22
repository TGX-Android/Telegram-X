/*
 * Copyright 2021 The Android Open Source Project
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

package androidx.media3.exoplayer.rtsp;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Util.getBytesFromHexString;
import static androidx.media3.exoplayer.rtsp.RtpPacket.getNextSequenceNumber;
import static androidx.media3.exoplayer.rtsp.RtpPacket.getPreviousSequenceNumber;
import static com.google.common.truth.Truth.assertThat;

import androidx.media3.common.C;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link RtpPacket}. */
@RunWith(AndroidJUnit4.class)
public final class RtpPacketTest {
  /*
    10.. .... = Version: RFC 1889 Version (2)
    ..0. .... = Padding: False
    ...0 .... = Extension: False
    .... 0000 = Contributing source identifiers count: 0
    1... .... = Marker: True
    Payload type: DynamicRTP-Type-96 (96)
    Sequence number: 22159
    Timestamp: 55166400
    Synchronization Source identifier: 0xd76ef1a6 (3614372262)
    Payload: 019fb174427f00006c10c4008962e33ceb5f1fde8ee2d0d9…
  */
  private final byte[] rtpData =
      getBytesFromHexString(
          "80e0568f0349c5c0d76ef1a6019fb174427f00006c10c4008962e33ceb5f1fde8ee2d0d9b169651024c83b24c3a0f274ea327e2440ae0d3e2ed194beaa2c91edaa5d1e1df7ce30d1ca3726804d2db37765cf3d174338459623bc627c15c687045390a8d702f623a8dbe49e5c7896dbd7105daecb02ce30c0eee324c0c21ed820a0e67344c7a6e10859");
  private final byte[] rtpPayloadData =
      Arrays.copyOfRange(rtpData, RtpPacket.MIN_HEADER_SIZE, rtpData.length);

  /*
   10.. .... = Version: RFC 1889 Version (2)
   ..0. .... = Padding: False
   ...0 .... = Extension: False
   .... 0000 = Contributing source identifiers count: 0
   1... .... = Marker: True
   Payload type: DynamicRTP-Type-96 (96)
   Sequence number: 29234
   Timestamp: 3688686074
   Synchronization Source identifier: 0xf5fe62a4 (4127089316)
   Payload: 419a246c43bffea996000003000003000003000003000003…
  */
  private final byte[] rtpDataWithLargeTimestamp =
      getBytesFromHexString(
          "80e07232dbdce1faf5fe62a4419a246c43bffea99600000300000300000300000300000300000300000300000300000300000300000300000300000300000300000300000300000300002ce0");
  private final byte[] rtpWithLargeTimestampPayloadData =
      Arrays.copyOfRange(
          rtpDataWithLargeTimestamp, RtpPacket.MIN_HEADER_SIZE, rtpDataWithLargeTimestamp.length);

  /*
   10.. .... = Version: RFC 1889 Version (2)
   ..0. .... = Padding: False
   ...1 .... = Extension: True
   .... 0000 = Contributing source identifiers count: 0
   0... .... = Marker: False
   Payload type: DynamicRTP-Type-96 (96)
   Sequence number: 61514
   Timestamp: 2000000000
   Synchronization Source identifier: 0x35ff2773 (905914227)
   extension: 00a20003a94f000062150100cbca0100
   Payload: 7c85b841bc439048000834f1a6943c00040bf038ee4de07acb6d…
  */
  private final byte[] rtpDataWithHeaderExtension =
      getBytesFromHexString(
          "9060f04a7735940035ff277300a20003a94f000062150100cbca01007c85b841bc439048000834f1a6943c00040bf038ee4de07acb6dc67cb44716d9b61800600f1041214b121de2af09a8063ff2d88fedf7f565eafb9c44412a8e5a247d0ac76a6a8566a6ff593f9711114b6c625ca1363950ae8524a37c75c509a806833fd4bbeb6dda6db697aef12d709a80910e522bb3e2e793eb3c37995c4429448f2ba8b16bcb825ca11c3dffb3ff50ba8c5a3e5ffaff978b7e1350037d7dce4ddc906dbfff50ba8069ace7a9df442fffde2afc26a004b076dd611ebedb8bf15fd9596fc47e03e1008a32013d454401f8590ea42b6a67a3cf4da90aa006aca053283cf09c0e42c000444519045f3a0002c4c0e5e0f81e8316c5b16cbf3737c462bd5f87cc66b3e508a10128ac18d5656c78a6e293f10e0252c1819c2040fc5b16fe222296c4da247284ae892a16de65db7236a9bab718da108d05f09bf85d22bebb3ff11ff178b8da7c52c52fc4428b07ae1f1a9e0e3cc963136d542b2d698b6e84d572fbfbffcf1eafaf5af5e3d4092e33443b7b7ff09a8008ae5d661597bdbcfeb54dbd944c711014a2161cd76dc63b5087d087befe7ffb97e5484d5025f4e5fda36ffdcb500975c917ac1357fffea6484d401565b4a77bba2ff34135b711004583df39c7a16669f2840edca371fec8f575fff5f8f5049f3b1fff7d709a8338aaffeff1ad4981350cb5bffbfef09a87f043fff5f426a1132f7f5ffd09a821786b3fff7d426a057b80dfff6fe84d430c37fe9a7baed09a800769f54dd97df7dbbd3dc9e23e23158888250743e4da9d997974501d0879f2fe581b62b3f7711825831ca84b9421d3e4fa4d0874bc9ef895b771190dac47e55003a076252c032ccac0d4583c67078e3f101a946e260152754c800402a134002756425e7bb77f97e2305b1094e1c2c067a62284172a506a260011df686ed758e28c461702e001778034a407a1b16cd4ff11105bb0003784c552d810f6e5c5ce79e03e84381fd1008d9e471b8bb0cb2e20c70a34a8ab3fff1100a945d03f257e3cc499e2be9ae9072000f94896b5ca506e1c577fb28fc442b2503e0bce41bc1abacb5e05d6320455c3f3505681ec1c1a888a3240aab928b10001004f2c10782ac03e640ba8691007cc559a827a3cbfe221e600288540a914651e4be0e87c007b983f8cc550080038df00d6c8001818aa92448153c01ef80fc3b362084bf97f111059618ed4580e84505d02d6242f5d8c8e03f9e0705036e1400025385840c64c3a16da22241291e7e6fc9f1111ef1e738583a11ef2b553f1e62380ce79c845ac2901008910430ea3c00040078592b101bb030b2e33d97f111ac5000811454c01a83a00f138d0c0022d06cc09476727596154030742a4141bc3415c9264021789002af000f9e1b8bfcbf88852be0e4e2381400a8d266a1e00e1a9f10f02c1610c6780f29f05440792d0210684000a79f0625601b88c16e958f5eb6583012a944252f8ca00ba920f9fc7ad1500025061e018ca88000b8d7bfc132625ae297f8885641a000100c052c006be1d8000801faf80014b19614e521f0e87c9f21b000170d4c410c0372c007a4320d40600315906e156fda684435100f47855e2c05c73a5985d4009135a0e927d09e1dead7fffb752c63af06890b07fb9531297e0d0af0e03b1fb7c9d3f20a02fb1d7bca101c2c05064c0b78b2cb0ff0ba800dec69a27563d71bbb570bb95510da8fad5695a1814378ab8b7e135001be5e47055c6ba97975bbd75be5f4c826a2ee52bffdbdb6e96750516c6ab5077a0f5035b219ffbfa8f50997537f7aecbe223426e66b5426b057565b5f25bc7a98c6f047ffd7c26a037df97eaffedc4629dccd6a8448d0afa8b742146e4fbf09a80eeeecb2aed7ff7e221ab99a3aaf312d83fcaa857e25ece7f9950ba847b6574eafb7fffbf16f09a879268b7fff951d04d4007eabcf048f7cdedd046c754d447f11fd47a8011ba4b0ed3fdfe69");

  private final byte[] rtpDataExtension = getBytesFromHexString("00a20003a94f000062150100cbca0100");

  private final byte[] rtpWithHeaderExtensionPayloadData =
      Arrays.copyOfRange(
          rtpDataWithHeaderExtension,
          RtpPacket.MIN_HEADER_SIZE + rtpDataExtension.length,
          rtpDataWithHeaderExtension.length);

  @Test
  public void parseRtpPacket() {
    RtpPacket packet = checkNotNull(RtpPacket.parse(rtpData, rtpData.length));

    assertThat(packet.version).isEqualTo(RtpPacket.RTP_VERSION);
    assertThat(packet.padding).isFalse();
    assertThat(packet.extension).isFalse();
    assertThat(packet.csrcCount).isEqualTo(0);
    assertThat(packet.csrc).hasLength(0);
    assertThat(packet.marker).isTrue();
    assertThat(packet.payloadType).isEqualTo(96);
    assertThat(packet.sequenceNumber).isEqualTo(22159);
    assertThat(packet.timestamp).isEqualTo(55166400);
    assertThat(packet.ssrc).isEqualTo(0xD76EF1A6);
    assertThat(packet.payloadData).isEqualTo(rtpPayloadData);
  }

  @Test
  public void parseRtpPacketWithLargeTimestamp() {
    RtpPacket packet =
        checkNotNull(RtpPacket.parse(rtpDataWithLargeTimestamp, rtpDataWithLargeTimestamp.length));

    assertThat(packet.version).isEqualTo(RtpPacket.RTP_VERSION);
    assertThat(packet.padding).isFalse();
    assertThat(packet.extension).isFalse();
    assertThat(packet.csrcCount).isEqualTo(0);
    assertThat(packet.csrc).hasLength(0);
    assertThat(packet.marker).isTrue();
    assertThat(packet.payloadType).isEqualTo(96);
    assertThat(packet.sequenceNumber).isEqualTo(29234);
    assertThat(packet.timestamp).isEqualTo(3688686074L);
    assertThat(packet.ssrc).isEqualTo(0xf5fe62a4);
    assertThat(packet.payloadData).isEqualTo(rtpWithLargeTimestampPayloadData);
  }

  @Test
  public void parseRtpPacketWithHeaderExtension_createsRtpPacketWithoutHeaderExtension() {
    RtpPacket packet =
        checkNotNull(
            RtpPacket.parse(rtpDataWithHeaderExtension, rtpDataWithHeaderExtension.length));

    assertThat(packet.version).isEqualTo(RtpPacket.RTP_VERSION);
    assertThat(packet.padding).isFalse();
    // created RtpPacket object will parse but not save the extension data
    assertThat(packet.extension).isFalse();
    assertThat(packet.csrcCount).isEqualTo(0);
    assertThat(packet.csrc).hasLength(0);
    assertThat(packet.marker).isFalse();
    assertThat(packet.payloadType).isEqualTo(96);
    assertThat(packet.sequenceNumber).isEqualTo(61514);
    assertThat(packet.timestamp).isEqualTo(2000000000);
    assertThat(packet.ssrc).isEqualTo(0x35ff2773);
    assertThat(packet.payloadData).isEqualTo(rtpWithHeaderExtensionPayloadData);
  }

  @Test
  public void writetoBuffer_withProperlySizedBuffer_writesPacket() {
    int packetByteLength = rtpData.length;
    RtpPacket packet = checkNotNull(RtpPacket.parse(rtpData, packetByteLength));

    byte[] testBuffer = new byte[packetByteLength];
    int writtenBytes = packet.writeToBuffer(testBuffer, /* offset= */ 0, packetByteLength);

    assertThat(writtenBytes).isEqualTo(packetByteLength);
    assertThat(testBuffer).isEqualTo(rtpData);
  }

  @Test
  public void writetoBuffer_withBufferTooSmall_doesNotWritePacket() {
    int packetByteLength = rtpData.length;
    RtpPacket packet = checkNotNull(RtpPacket.parse(rtpData, packetByteLength));

    byte[] testBuffer = new byte[packetByteLength / 2];
    int writtenBytes = packet.writeToBuffer(testBuffer, /* offset= */ 0, packetByteLength);

    assertThat(writtenBytes).isEqualTo(C.LENGTH_UNSET);
  }

  @Test
  public void writetoBuffer_withProperlySizedBufferButSmallLengthParameter_doesNotWritePacket() {
    int packetByteLength = rtpData.length;
    RtpPacket packet = checkNotNull(RtpPacket.parse(rtpData, packetByteLength));

    byte[] testBuffer = new byte[packetByteLength];
    int writtenBytes = packet.writeToBuffer(testBuffer, /* offset= */ 0, packetByteLength / 2);

    assertThat(writtenBytes).isEqualTo(C.LENGTH_UNSET);
  }

  @Test
  public void writetoBuffer_withProperlySizedBufferButNotEnoughSpaceLeft_doesNotWritePacket() {
    int packetByteLength = rtpData.length;
    RtpPacket packet = checkNotNull(RtpPacket.parse(rtpData, packetByteLength));

    byte[] testBuffer = new byte[packetByteLength];
    int writtenBytes =
        packet.writeToBuffer(testBuffer, /* offset= */ packetByteLength - 1, packetByteLength);

    assertThat(writtenBytes).isEqualTo(C.LENGTH_UNSET);
  }

  @Test
  public void buildRtpPacket() {
    RtpPacket builtPacket =
        new RtpPacket.Builder()
            .setPadding(false)
            .setMarker(true)
            .setPayloadType((byte) 96)
            .setSequenceNumber(22159)
            .setTimestamp(55166400)
            .setSsrc(0xD76EF1A6)
            .setPayloadData(rtpPayloadData)
            .build();

    RtpPacket parsedPacket = checkNotNull(RtpPacket.parse(rtpData, rtpData.length));

    // Test equals function.
    assertThat(parsedPacket).isEqualTo(builtPacket);
  }

  @Test
  public void buildRtpPacketWithLargeTimestamp_matchesPacketData() {
    RtpPacket builtPacket =
        new RtpPacket.Builder()
            .setPadding(false)
            .setMarker(true)
            .setPayloadType((byte) 96)
            .setSequenceNumber(29234)
            .setTimestamp(3688686074L)
            .setSsrc(0xf5fe62a4)
            .setPayloadData(rtpWithLargeTimestampPayloadData)
            .build();

    int packetSize = RtpPacket.MIN_HEADER_SIZE + builtPacket.payloadData.length;
    byte[] builtPacketBytes = new byte[packetSize];
    builtPacket.writeToBuffer(builtPacketBytes, /* offset= */ 0, packetSize);
    assertThat(builtPacketBytes).isEqualTo(rtpDataWithLargeTimestamp);
  }

  @Test
  public void getNextSequenceNumber_invokingAtWrapOver() {
    assertThat(getNextSequenceNumber(65534)).isEqualTo(65535);
    assertThat(getNextSequenceNumber(65535)).isEqualTo(0);
    assertThat(getNextSequenceNumber(0)).isEqualTo(1);
  }

  @Test
  public void getPreviousSequenceNumber_invokingAtWrapOver() {
    assertThat(getPreviousSequenceNumber(1)).isEqualTo(0);
    assertThat(getPreviousSequenceNumber(0)).isEqualTo(65535);
    assertThat(getPreviousSequenceNumber(65535)).isEqualTo(65534);
  }

  @Test
  public void getSequenceNumber_isSymmetric() {
    for (int i = 0; i < RtpPacket.MAX_SEQUENCE_NUMBER; i++) {
      assertThat(getPreviousSequenceNumber(getNextSequenceNumber(i))).isEqualTo(i);
      assertThat(getNextSequenceNumber(getPreviousSequenceNumber(i))).isEqualTo(i);
    }
  }
}
