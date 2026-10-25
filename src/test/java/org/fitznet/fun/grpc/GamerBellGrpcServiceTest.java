package org.fitznet.fun.grpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;
import org.fitznet.fun.grpc.proto.DeviceLogLevel;
import org.fitznet.fun.grpc.proto.DeviceLogRequest;
import org.fitznet.fun.grpc.proto.FirmwareChunk;
import org.fitznet.fun.grpc.proto.GamerBellServiceGrpc;
import org.fitznet.fun.grpc.proto.GetCountRequest;
import org.fitznet.fun.grpc.proto.GetLatestFirmwareRequest;
import org.fitznet.fun.service.FirmwareService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.grpc.test.autoconfigure.LocalGrpcServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@SpringBootTest
class GamerBellGrpcServiceTest {

    @LocalGrpcServerPort
    private int port;

    @MockitoBean
    private FirmwareService firmwareService;

    private ManagedChannel channel;
    private GamerBellServiceGrpc.GamerBellServiceBlockingStub stub;

    @BeforeEach
    void setUp() {
        channel = ManagedChannelBuilder.forAddress("localhost", port).usePlaintext().build();
        stub = GamerBellServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void tearDown() {
        channel.shutdownNow();
    }

    private List<FirmwareChunk> firmware(String currentVersion) {
        List<FirmwareChunk> chunks = new ArrayList<>();
        stub.getLatestFirmware(GetLatestFirmwareRequest.newBuilder()
                        .setCurrentVersion(currentVersion)
                        .setDeviceMac("AA:BB")
                        .build())
                .forEachRemaining(chunks::add);
        return chunks;
    }

    @Test
    void correlationId_isEchoedInResponseHeaders() {
        Metadata.Key<String> key = Metadata.Key.of("x-correlation-id", Metadata.ASCII_STRING_MARSHALLER);
        Metadata sent = new Metadata();
        sent.put(key, "trace-abc-123");
        AtomicReference<Metadata> received = new AtomicReference<>();
        stub.withInterceptors(
                        MetadataUtils.newAttachHeadersInterceptor(sent),
                        MetadataUtils.newCaptureMetadataInterceptor(received, new AtomicReference<>()))
                .getCount(GetCountRequest.getDefaultInstance());
        assertEquals("trace-abc-123", received.get().get(key));
    }

    @Test
    void correlationId_isGeneratedWhenMissing() {
        Metadata.Key<String> key = Metadata.Key.of("x-correlation-id", Metadata.ASCII_STRING_MARSHALLER);
        AtomicReference<Metadata> received = new AtomicReference<>();
        stub.withInterceptors(MetadataUtils.newCaptureMetadataInterceptor(received, new AtomicReference<>()))
                .getCount(GetCountRequest.getDefaultInstance());
        assertEquals(8, received.get().get(key).length());
    }

    @Test
    void getCount_returnsSessionCount() {
        assertEquals(0, stub.getCount(GetCountRequest.getDefaultInstance()).getCount());
    }

    @Test
    void firmware_upToDate_sendsOnlyInfo() {
        when(firmwareService.getLatestVersion()).thenReturn("v1.0.0");

        List<FirmwareChunk> chunks = firmware("v1.0.0");

        assertEquals(1, chunks.size());
        assertTrue(chunks.get(0).getInfo().getUpToDate());
    }

    @Test
    void firmware_updateAvailable_streamsBinaryInChunks() throws Exception {
        byte[] binary = new byte[GamerBellGrpcService.FIRMWARE_CHUNK_BYTES * 2 + 17];
        for (int i = 0; i < binary.length; i++) {
            binary[i] = (byte) i;
        }
        when(firmwareService.getLatestVersion()).thenReturn("v2.0.0");
        when(firmwareService.ensureFirmwareReady("v2.0.0")).thenReturn(true);
        when(firmwareService.getFirmwareFile()).thenReturn(new ByteArrayResource(binary));

        List<FirmwareChunk> chunks = firmware("v1.0.0");

        assertEquals("v2.0.0", chunks.get(0).getInfo().getLatestVersion());
        assertEquals(binary.length, chunks.get(0).getInfo().getSizeBytes());
        ByteArrayOutputStream received = new ByteArrayOutputStream();
        for (FirmwareChunk chunk : chunks.subList(1, chunks.size())) {
            chunk.getData().writeTo(received);
        }
        assertEquals(4, chunks.size());
        assertArrayEquals(binary, received.toByteArray());
    }

    @Test
    void firmware_downloadFailed_isUnavailable() {
        when(firmwareService.getLatestVersion()).thenReturn("v2.0.0");
        when(firmwareService.ensureFirmwareReady("v2.0.0")).thenReturn(false);

        StatusRuntimeException ex = assertThrows(StatusRuntimeException.class, () -> firmware("v1.0.0"));
        assertEquals(Status.Code.UNAVAILABLE, ex.getStatus().getCode());
    }

    @Test
    void logDevice_acceptsValidAndRejectsMissingFields() {
        stub.logDevice(DeviceLogRequest.newBuilder()
                .setDeviceId("bell-1")
                .setLevel(DeviceLogLevel.DEVICE_LOG_LEVEL_WARN)
                .setSource("wifi")
                .setMessage("reconnecting")
                .build());

        StatusRuntimeException ex = assertThrows(StatusRuntimeException.class,
                () -> stub.logDevice(DeviceLogRequest.newBuilder().setDeviceId("bell-1").build()));
        assertEquals(Status.Code.INVALID_ARGUMENT, ex.getStatus().getCode());
    }
}
