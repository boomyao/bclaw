#include <jni.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>
#include <pthread.h>

#include <android/log.h>
#include <enet/enet.h>

#define LOG_TAG "BclawEnet"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

typedef struct BclawEnetSession {
    ENetHost* host;
    ENetPeer* peer;
    pthread_mutex_t mutex;
} BclawEnetSession;

static BclawEnetSession* ptr_from_handle(jlong handle) {
    return (BclawEnetSession*)(intptr_t)handle;
}

JNIEXPORT jlong JNICALL
Java_com_bclaw_app_remote_NativeEnet_nativeCreate(
        JNIEnv* env,
        jobject thiz,
        jstring host_name,
        jint port,
        jlong connect_data,
        jint timeout_ms) {
    (void)thiz;

    const char* host_chars = (*env)->GetStringUTFChars(env, host_name, NULL);
    if (host_chars == NULL) {
        return 0;
    }

    if (enet_initialize() != 0) {
        LOGE("enet_initialize failed");
        (*env)->ReleaseStringUTFChars(env, host_name, host_chars);
        return 0;
    }

    ENetAddress remote_address;
    if (enet_address_set_host(&remote_address, host_chars) != 0 ||
        enet_address_set_port(&remote_address, (enet_uint16)port) != 0) {
        LOGE("failed to resolve ENet host %s:%d", host_chars, port);
        (*env)->ReleaseStringUTFChars(env, host_name, host_chars);
        return 0;
    }
    (*env)->ReleaseStringUTFChars(env, host_name, host_chars);

    BclawEnetSession* session = (BclawEnetSession*)calloc(1, sizeof(BclawEnetSession));
    if (session == NULL) {
        return 0;
    }

    if (pthread_mutex_init(&session->mutex, NULL) != 0) {
        free(session);
        return 0;
    }

    session->host = enet_host_create(
            remote_address.address.ss_family,
            NULL,
            1,
            0x30,
            0,
            0);
    if (session->host == NULL) {
        LOGE("enet_host_create failed");
        pthread_mutex_destroy(&session->mutex);
        free(session);
        return 0;
    }

    session->peer = enet_host_connect(
            session->host,
            &remote_address,
            0x30,
            (enet_uint32)connect_data);
    if (session->peer == NULL) {
        LOGE("enet_host_connect failed");
        enet_host_destroy(session->host);
        pthread_mutex_destroy(&session->mutex);
        free(session);
        return 0;
    }

    ENetEvent event;
    int err = enet_host_service(session->host, &event, (enet_uint32)timeout_ms);
    if (err <= 0 || event.type != ENET_EVENT_TYPE_CONNECT) {
        LOGE("ENet connect failed: service=%d event=%d", err, err > 0 ? (int)event.type : -1);
        enet_peer_reset(session->peer);
        enet_host_destroy(session->host);
        pthread_mutex_destroy(&session->mutex);
        free(session);
        return 0;
    }

    enet_host_flush(session->host);
    enet_peer_timeout(session->peer, 2, 10000, 10000);
    return (jlong)(intptr_t)session;
}

JNIEXPORT jboolean JNICALL
Java_com_bclaw_app_remote_NativeEnet_nativeSend(
        JNIEnv* env,
        jobject thiz,
        jlong handle,
        jbyteArray payload,
        jint channel_id,
        jboolean reliable,
        jboolean unsequenced) {
    (void)thiz;
    BclawEnetSession* session = ptr_from_handle(handle);
    if (session == NULL || session->host == NULL || session->peer == NULL || payload == NULL) {
        return JNI_FALSE;
    }

    jsize payload_len = (*env)->GetArrayLength(env, payload);
    jbyte* payload_bytes = (*env)->GetByteArrayElements(env, payload, NULL);
    if (payload_bytes == NULL) {
        return JNI_FALSE;
    }

    enet_uint32 flags = 0;
    if (reliable) {
        flags |= ENET_PACKET_FLAG_RELIABLE;
    }
    if (unsequenced) {
        flags |= ENET_PACKET_FLAG_UNSEQUENCED;
    }

    ENetPacket* packet = enet_packet_create(payload_bytes, (size_t)payload_len, flags);
    (*env)->ReleaseByteArrayElements(env, payload, payload_bytes, JNI_ABORT);
    if (packet == NULL) {
        return JNI_FALSE;
    }

    pthread_mutex_lock(&session->mutex);
    int send_result = enet_peer_send(session->peer, (enet_uint8)channel_id, packet);
    if (send_result == 0) {
        enet_host_flush(session->host);
        for (int i = 0; i < 3; i++) {
            enet_host_service(session->host, NULL, 0);
        }
    }
    pthread_mutex_unlock(&session->mutex);

    if (send_result != 0) {
        enet_packet_destroy(packet);
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

JNIEXPORT jint JNICALL
Java_com_bclaw_app_remote_NativeEnet_nativeService(
        JNIEnv* env,
        jobject thiz,
        jlong handle,
        jint timeout_ms) {
    (void)env;
    (void)thiz;
    BclawEnetSession* session = ptr_from_handle(handle);
    if (session == NULL || session->host == NULL) {
        return -1;
    }

    ENetEvent event;
    pthread_mutex_lock(&session->mutex);
    int result = enet_host_service(session->host, &event, (enet_uint32)timeout_ms);
    if (result > 0 && event.type == ENET_EVENT_TYPE_RECEIVE && event.packet != NULL) {
        enet_packet_destroy(event.packet);
    }
    pthread_mutex_unlock(&session->mutex);

    if (result < 0) {
        return -1;
    }
    return result > 0 ? (jint)event.type : 0;
}

JNIEXPORT void JNICALL
Java_com_bclaw_app_remote_NativeEnet_nativeClose(
        JNIEnv* env,
        jobject thiz,
        jlong handle) {
    (void)env;
    (void)thiz;
    BclawEnetSession* session = ptr_from_handle(handle);
    if (session == NULL) {
        return;
    }

    pthread_mutex_lock(&session->mutex);
    if (session->peer != NULL) {
        enet_peer_disconnect_now(session->peer, 0);
        session->peer = NULL;
    }
    if (session->host != NULL) {
        enet_host_destroy(session->host);
        session->host = NULL;
    }
    pthread_mutex_unlock(&session->mutex);

    pthread_mutex_destroy(&session->mutex);
    free(session);
}
