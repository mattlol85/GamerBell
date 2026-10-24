package org.fitznet.fun.grpc;

import com.google.protobuf.ByteString;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.fitznet.fun.controller.GamerBellController;
import org.fitznet.fun.dto.DeviceLogDto;
import org.fitznet.fun.dto.DeviceLogLevel;
import org.fitznet.fun.grpc.proto.DeviceLogRequest;
import org.fitznet.fun.grpc.proto.DeviceLogResponse;
import org.fitznet.fun.grpc.proto.FirmwareChunk;
import org.fitznet.fun.grpc.proto.FirmwareInfo;
import org.fitznet.fun.grpc.proto.GamerBellServiceGrpc;
import org.fitznet.fun.grpc.proto.GetCountRequest;
import org.fitznet.fun.grpc.proto.GetCountResponse;
import org.fitznet.fun.grpc.proto.GetLatestFirmwareRequest;
import org.fitznet.fun.service.ButtonService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.grpc.server.service.GrpcService;

import java.io.IOException;
import java.io.InputStream;

import static org.fitznet.fun.utils.Constants.ESP32_ERROR_HEADER;
import static org.fitznet.fun.utils.Constants.LATEST_VERSION_HEADER;

/**
 * gRPC equivalent of {@link GamerBellController}. Firmware and device-log RPCs delegate to the
 * controller so the OTA metrics, MDC logging and validation are shared with REST.
 */
@Slf4j
@GrpcService
public class GamerBellGrpcService extends GamerBellServiceGrpc.GamerBellServiceImplBase {

    static final int FIRMWARE_CHUNK_BYTES = 64 * 1024;

    private final GamerBellController gamerBellController;
    private final ButtonService buttonService;

    public GamerBellGrpcService(GamerBellController gamerBellController, ButtonService buttonService) {
        this.gamerBellController = gamerBellController;
        this.buttonService = buttonService;
    }

    @Override
    public void getCount(GetCountRequest request, StreamObserver<GetCountResponse> observer) {
        long sessionCount = buttonService.getSessionCount();
        log.info("gRPC session count request - count={}", sessionCount);
        observer.onNext(GetCountResponse.newBuilder().setCount(sessionCount).build());
        observer.onCompleted();
    }

    @Override
    public void getLatestFirmware(GetLatestFirmwareRequest request, StreamObserver<FirmwareChunk> observer) {
        ResponseEntity<Resource> response = gamerBellController.checkForUpdate(
                request.hasCurrentVersion() ? request.getCurrentVersion() : null,
                request.hasDeviceMac() ? request.getDeviceMac() : null);

        if (response.getStatusCode() == HttpStatus.NOT_MODIFIED) {
            observer.onNext(info(FirmwareInfo.newBuilder().setUpToDate(true)));
            observer.onCompleted();
            return;
        }
        if (response.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE) {
            observer.onError(Status.UNAVAILABLE
                    .withDescription(response.getHeaders().getFirst(ESP32_ERROR_HEADER))
                    .asRuntimeException());
            return;
        }
        Resource firmware = response.getBody();
        if (!response.getStatusCode().is2xxSuccessful() || firmware == null) {
            observer.onError(Status.INTERNAL
                    .withDescription(response.getHeaders().getFirst(ESP32_ERROR_HEADER))
                    .asRuntimeException());
            return;
        }

        String latestVersion = response.getHeaders().getFirst(LATEST_VERSION_HEADER);
        observer.onNext(info(FirmwareInfo.newBuilder()
                .setLatestVersion(latestVersion != null ? latestVersion : "")
                .setSizeBytes(response.getHeaders().getContentLength())));
        try (InputStream in = firmware.getInputStream()) {
            byte[] buffer = new byte[FIRMWARE_CHUNK_BYTES];
            int read;
            while ((read = in.read(buffer)) != -1) {
                observer.onNext(FirmwareChunk.newBuilder().setData(ByteString.copyFrom(buffer, 0, read)).build());
            }
        } catch (IOException e) {
            log.error("Error streaming firmware over gRPC - version={}, error={}", latestVersion, e.getMessage());
            observer.onError(Status.INTERNAL.withDescription("Error reading firmware file").asRuntimeException());
            return;
        }
        observer.onCompleted();
    }

    @Override
    public void logDevice(DeviceLogRequest request, StreamObserver<DeviceLogResponse> observer) {
        DeviceLogDto dto = DeviceLogDto.builder()
                .deviceId(request.getDeviceId())
                .firmwareVersion(request.hasFirmwareVersion() ? request.getFirmwareVersion() : null)
                .level(toLevel(request.getLevel()))
                .source(request.hasSource() ? request.getSource() : null)
                .message(request.getMessage())
                .build();
        if (gamerBellController.logDeviceError(dto).getStatusCode().is2xxSuccessful()) {
            observer.onNext(DeviceLogResponse.getDefaultInstance());
            observer.onCompleted();
        } else {
            observer.onError(Status.INVALID_ARGUMENT
                    .withDescription("device_id and message are required; fields are limited to 200 characters")
                    .asRuntimeException());
        }
    }

    private static FirmwareChunk info(FirmwareInfo.Builder info) {
        return FirmwareChunk.newBuilder().setInfo(info).build();
    }

    private static DeviceLogLevel toLevel(org.fitznet.fun.grpc.proto.DeviceLogLevel level) {
        return switch (level) {
            case DEVICE_LOG_LEVEL_ERROR -> DeviceLogLevel.ERROR;
            case DEVICE_LOG_LEVEL_WARN -> DeviceLogLevel.WARN;
            case DEVICE_LOG_LEVEL_INFO -> DeviceLogLevel.INFO;
            default -> null;
        };
    }
}
