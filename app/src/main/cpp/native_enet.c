#include <jni.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <pthread.h>

#include <android/log.h>
#include <enet/enet.h>
#include <rs.h>

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

static pthread_once_t rs_once = PTHREAD_ONCE_INIT;

static void init_rs_once(void) {
    reed_solomon_init();
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

JNIEXPORT jobjectArray JNICALL
Java_com_bclaw_app_remote_NativeEnet_nativeRecoverFec(
        JNIEnv* env,
        jobject thiz,
        jint data_shards,
        jint parity_shards,
        jint shard_size,
        jobjectArray input_shards) {
    (void)thiz;

    if (data_shards <= 0 ||
        parity_shards <= 0 ||
        shard_size <= 0 ||
        data_shards + parity_shards > DATA_SHARDS_MAX ||
        input_shards == NULL ||
        (*env)->GetArrayLength(env, input_shards) < data_shards + parity_shards) {
        return NULL;
    }

    const int total_shards = data_shards + parity_shards;
    uint8_t** shards = (uint8_t**)calloc((size_t)total_shards, sizeof(uint8_t*));
    uint8_t* marks = (uint8_t*)calloc((size_t)total_shards, sizeof(uint8_t));
    if (shards == NULL || marks == NULL) {
        free(shards);
        free(marks);
        return NULL;
    }

    int present = 0;
    for (int i = 0; i < total_shards; i++) {
        shards[i] = (uint8_t*)calloc((size_t)shard_size, sizeof(uint8_t));
        if (shards[i] == NULL) {
            for (int j = 0; j < i; j++) {
                free(shards[j]);
            }
            free(shards);
            free(marks);
            return NULL;
        }

        jbyteArray shard = (jbyteArray)(*env)->GetObjectArrayElement(env, input_shards, i);
        if (shard == NULL) {
            marks[i] = 1;
            continue;
        }

        jsize length = (*env)->GetArrayLength(env, shard);
        jsize copy_len = length < shard_size ? length : shard_size;
        if (copy_len > 0) {
            (*env)->GetByteArrayRegion(env, shard, 0, copy_len, (jbyte*)shards[i]);
        }
        (*env)->DeleteLocalRef(env, shard);
        marks[i] = 0;
        present++;
    }

    jobjectArray result = NULL;
    if (present >= data_shards) {
        pthread_once(&rs_once, init_rs_once);
        reed_solomon* rs = reed_solomon_new(data_shards, parity_shards);
        if (rs != NULL) {
            int decode_result = reed_solomon_decode(rs, shards, marks, total_shards, shard_size);
            reed_solomon_release(rs);

            if (decode_result == 0) {
                jclass byte_array_class = (*env)->FindClass(env, "[B");
                if (byte_array_class != NULL) {
                    result = (*env)->NewObjectArray(env, data_shards, byte_array_class, NULL);
                    if (result != NULL) {
                        for (int i = 0; i < data_shards; i++) {
                            jbyteArray out = (*env)->NewByteArray(env, shard_size);
                            if (out == NULL) {
                                result = NULL;
                                break;
                            }
                            (*env)->SetByteArrayRegion(env, out, 0, shard_size, (jbyte*)shards[i]);
                            (*env)->SetObjectArrayElement(env, result, i, out);
                            (*env)->DeleteLocalRef(env, out);
                        }
                    }
                    (*env)->DeleteLocalRef(env, byte_array_class);
                }
            }
        }
    }

    for (int i = 0; i < total_shards; i++) {
        free(shards[i]);
    }
    free(shards);
    free(marks);

    return result;
}
